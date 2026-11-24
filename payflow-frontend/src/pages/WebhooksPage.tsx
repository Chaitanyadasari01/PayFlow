import React, { useState, useEffect } from 'react';
import { paymentApi } from '../api/paymentApi';
import { webhookApi } from '../api/webhookApi';
import type { WebhookEvent, Payment } from '../types';
import { getErrorMessage } from '../api/client';
import { StatusBadge } from '../components/common/StatusBadge';
import { LoadingSpinner } from '../components/common/LoadingSpinner';
import { ErrorAlert } from '../components/common/ErrorAlert';
import {
  Webhook,
  Send,
  RefreshCw,
  Search,
  CheckCircle,
  ExternalLink,
  Info,
} from 'lucide-react';
import { Link } from 'react-router-dom';

export const WebhooksPage: React.FC = () => {
  const [events, setEvents] = useState<WebhookEvent[]>([]);
  const [payments, setPayments] = useState<Payment[]>([]);
  const [loading, setLoading] = useState<boolean>(true);
  const [error, setError] = useState<string | null>(null);

  // Manual retry state
  const [retryLoading, setRetryLoading] = useState<boolean>(false);
  const [retryMsg, setRetryMsg] = useState<string | null>(null);

  // Filter
  const [selectedPaymentId, setSelectedPaymentId] = useState<string>('ALL');

  const fetchAllEvents = async () => {
    setLoading(true);
    setError(null);
    try {
      // Fetch recent payments first to collect their IDs
      const pRes = await paymentApi.listPayments({ page: 0, size: 25 });
      setPayments(pRes.content);

      // Collect webhook events for each payment in parallel
      const eventPromises = pRes.content.map((p) =>
        webhookApi.getEventsForPayment(p.paymentId).catch(() => [] as WebhookEvent[])
      );
      const results = await Promise.all(eventPromises);
      const flattened = results.flat();

      // Sort newest first
      flattened.sort((a, b) => new Date(b.createdAt).getTime() - new Date(a.createdAt).getTime());
      setEvents(flattened);
    } catch (err) {
      setError(getErrorMessage(err));
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    fetchAllEvents();
  }, []);

  const handleTriggerRetry = async () => {
    setRetryLoading(true);
    setRetryMsg(null);
    try {
      const res = await webhookApi.triggerRetry();
      setRetryMsg(res.message || 'Webhook retry cycle executed successfully');
      await fetchAllEvents();
    } catch (err) {
      setError(getErrorMessage(err));
    } finally {
      setRetryLoading(false);
    }
  };

  const filteredEvents = events.filter((e) => {
    if (selectedPaymentId === 'ALL') return true;
    return e.paymentId === selectedPaymentId;
  });

  return (
    <div>
      {/* Page Header */}
      <div
        style={{
          display: 'flex',
          alignItems: 'center',
          justifyContent: 'space-between',
          marginBottom: '1.5rem',
          flexWrap: 'wrap',
          gap: '1rem',
        }}
      >
        <div>
          <h1 style={{ fontSize: '1.65rem', fontWeight: 800, color: '#0B192C', letterSpacing: '-0.02em' }}>
            Webhook Events
          </h1>
          <p style={{ color: '#64748B', fontSize: '0.9rem', marginTop: '2px' }}>
            Inspect event delivery status, exponential backoff payloads & dispatch triggers
          </p>
        </div>

        <div style={{ display: 'flex', gap: '0.75rem' }}>
          <button
            onClick={fetchAllEvents}
            className="btn btn-secondary"
            title="Refresh events"
          >
            <RefreshCw style={{ width: '15px', height: '15px' }} />
            <span>Refresh</span>
          </button>
          <button
            onClick={handleTriggerRetry}
            disabled={retryLoading}
            className="btn btn-primary"
            style={{ backgroundColor: '#1E3E62' }}
          >
            <Send style={{ width: '15px', height: '15px' }} />
            <span>{retryLoading ? 'Retrying...' : 'Trigger Retry Cycle'}</span>
          </button>
        </div>
      </div>

      {error && <ErrorAlert message={error} onRetry={fetchAllEvents} />}
      {retryMsg && (
        <div style={{ backgroundColor: '#EFF6FF', color: '#1E40AF', padding: '0.85rem 1.15rem', borderRadius: '8px', border: '1px solid #BFDBFE', marginBottom: '1.25rem', display: 'flex', alignItems: 'center', gap: '8px', fontSize: '0.9rem' }}>
          <CheckCircle style={{ width: '18px', height: '18px', color: '#3B82F6' }} />
          <span>{retryMsg}</span>
        </div>
      )}

      {/* Info Callout */}
      <div
        style={{
          backgroundColor: '#F8FAFC',
          borderRadius: '8px',
          border: '1px solid #E2E8F0',
          padding: '1rem',
          marginBottom: '1.5rem',
          display: 'flex',
          alignItems: 'flex-start',
          gap: '10px',
          fontSize: '0.85rem',
          color: '#334155',
        }}
      >
        <Info style={{ width: '18px', height: '18px', color: '#008DDA', flexShrink: 0, marginTop: '2px' }} />
        <div>
          <strong>Exponential Backoff Policy:</strong> The backend automatically retries failed deliveries using exponential backoff (<code>5s, 10s, 20s, 40s, 80s</code>) up to 5 attempts before marking status as <code>FAILED</code>.
        </div>
      </div>

      {/* Filter by Payment */}
      <div className="card" style={{ marginBottom: '1.25rem', padding: '0.85rem 1.25rem' }}>
        <div style={{ display: 'flex', alignItems: 'center', gap: '1rem', flexWrap: 'wrap' }}>
          <label style={{ fontSize: '0.85rem', fontWeight: 600, color: '#475569', display: 'flex', alignItems: 'center', gap: '6px' }}>
            <Search style={{ width: '15px', height: '15px' }} />
            <span>Filter by Payment:</span>
          </label>
          <select
            className="form-select"
            style={{ width: 'auto', minWidth: '240px' }}
            value={selectedPaymentId}
            onChange={(e) => setSelectedPaymentId(e.target.value)}
          >
            <option value="ALL">All Payments ({events.length} total events)</option>
            {payments.map((p) => (
              <option key={p.paymentId} value={p.paymentId}>
                {p.paymentId.substring(0, 8)}... (${Number(p.amount).toFixed(2)} {p.currency} - {p.status})
              </option>
            ))}
          </select>
        </div>
      </div>

      {/* Events List */}
      {loading ? (
        <LoadingSpinner message="Loading webhook events..." />
      ) : filteredEvents.length === 0 ? (
        <div className="card" style={{ textAlign: 'center', padding: '3.5rem 1rem', color: '#64748B' }}>
          <Webhook style={{ width: '36px', height: '36px', color: '#CBD5E1', marginBottom: '0.75rem' }} />
          <h3 style={{ fontSize: '1rem', fontWeight: 700, color: '#334155' }}>No webhook events found</h3>
          <p style={{ fontSize: '0.85rem', marginTop: '4px' }}>
            Perform payment operations to trigger asynchronous webhook notifications.
          </p>
        </div>
      ) : (
        <div style={{ display: 'flex', flexDirection: 'column', gap: '1rem' }}>
          {filteredEvents.map((wh) => (
            <div key={wh.eventId} className="card">
              <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', marginBottom: '0.75rem', flexWrap: 'wrap', gap: '0.5rem' }}>
                <div style={{ display: 'flex', alignItems: 'center', gap: '10px' }}>
                  <span style={{ fontWeight: 800, fontSize: '1rem', color: '#0B192C' }}>
                    {wh.eventType}
                  </span>
                  <StatusBadge status={wh.status} size="sm" />
                </div>

                <div style={{ fontSize: '0.8rem', color: '#64748B' }}>
                  {wh.createdAt ? new Date(wh.createdAt).toLocaleString() : '—'}
                </div>
              </div>

              <div style={{ display: 'flex', gap: '1.5rem', marginBottom: '0.75rem', fontSize: '0.82rem', flexWrap: 'wrap' }}>
                <div>
                  <span style={{ color: '#64748B' }}>Event ID: </span>
                  <code style={{ color: '#0F172A', fontWeight: 600 }}>{wh.eventId}</code>
                </div>
                {wh.paymentId && (
                  <div>
                    <span style={{ color: '#64748B' }}>Payment: </span>
                    <Link
                      to={`/payments/${wh.paymentId}`}
                      style={{ color: '#008DDA', fontWeight: 600, textDecoration: 'none', display: 'inline-flex', alignItems: 'center', gap: '3px' }}
                    >
                      <span>{wh.paymentId.substring(0, 8)}...</span>
                      <ExternalLink style={{ width: '12px', height: '12px' }} />
                    </Link>
                  </div>
                )}
                <div>
                  <span style={{ color: '#64748B' }}>Attempts: </span>
                  <span style={{ fontWeight: 600, color: wh.attempts > 1 ? '#F59E0B' : '#0F172A' }}>
                    {wh.attempts}
                  </span>
                </div>
                {wh.nextRetryAt && (
                  <div>
                    <span style={{ color: '#64748B' }}>Next Retry: </span>
                    <span style={{ color: '#0284C7' }}>{new Date(wh.nextRetryAt).toLocaleTimeString()}</span>
                  </div>
                )}
              </div>

              {/* JSON Payload viewer */}
              <div style={{ backgroundColor: '#0B192C', color: '#F8FAFC', borderRadius: '6px', padding: '0.85rem', fontSize: '0.78rem', fontFamily: 'monospace', overflowX: 'auto', maxHeight: '200px' }}>
                <pre style={{ margin: 0 }}>
                  {(() => {
                    try {
                      return JSON.stringify(JSON.parse(wh.payload), null, 2);
                    } catch {
                      return wh.payload;
                    }
                  })()}
                </pre>
              </div>
            </div>
          ))}
        </div>
      )}
    </div>
  );
};
