import React, { useEffect, useState } from 'react';
import { useParams, useNavigate } from 'react-router-dom';
import { paymentApi } from '../api/paymentApi';
import { webhookApi } from '../api/webhookApi';
import type { Payment, Refund, WebhookEvent } from '../types';
import { getErrorMessage } from '../api/client';
import { StatusBadge } from '../components/common/StatusBadge';
import { LoadingSpinner } from '../components/common/LoadingSpinner';
import { ErrorAlert } from '../components/common/ErrorAlert';
import {
  ArrowLeft,
  RotateCcw,
  Webhook,
  Calendar,
  CheckCircle,
  Copy,
  Check,
  Send,
  AlertTriangle,
} from 'lucide-react';

export const PaymentDetailPage: React.FC = () => {
  const { paymentId } = useParams<{ paymentId: string }>();
  const navigate = useNavigate();

  const [payment, setPayment] = useState<Payment | null>(null);
  const [refunds, setRefunds] = useState<Refund[]>([]);
  const [webhooks, setWebhooks] = useState<WebhookEvent[]>([]);
  const [loading, setLoading] = useState<boolean>(true);
  const [error, setError] = useState<string | null>(null);
  const [copied, setCopied] = useState<boolean>(false);

  // Refund Modal state
  const [isRefundModalOpen, setIsRefundModalOpen] = useState<boolean>(false);
  const [refundAmount, setRefundAmount] = useState<string>('');
  const [refundReason, setRefundReason] = useState<string>('');
  const [refundLoading, setRefundLoading] = useState<boolean>(false);
  const [refundError, setRefundError] = useState<string | null>(null);
  const [refundSuccessMsg, setRefundSuccessMsg] = useState<string | null>(null);

  // Webhook retry state
  const [retryLoading, setRetryLoading] = useState<boolean>(false);
  const [retrySuccessMsg, setRetrySuccessMsg] = useState<string | null>(null);

  // Active sub-tab: 'refunds' | 'webhooks'
  const [activeTab, setActiveTab] = useState<'refunds' | 'webhooks'>('refunds');

  const fetchDetails = async () => {
    if (!paymentId) return;
    setLoading(true);
    setError(null);
    try {
      const [payData, refData, whData] = await Promise.all([
        paymentApi.getPayment(paymentId),
        paymentApi.listRefundsForPayment(paymentId).catch(() => [] as Refund[]),
        webhookApi.getEventsForPayment(paymentId).catch(() => [] as WebhookEvent[]),
      ]);
      setPayment(payData);
      setRefunds(refData);
      setWebhooks(whData);
    } catch (err) {
      setError(getErrorMessage(err));
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    fetchDetails();
  }, [paymentId]);

  // Compute total refunded and remaining balance
  const totalRefunded = refunds
    .filter((r) => r.status === 'SUCCESS')
    .reduce((sum, r) => sum + Number(r.amount), 0);

  const originalAmount = Number(payment?.amount || 0);
  const remainingRefundable = Math.max(0, originalAmount - totalRefunded);
  const canRefund =
    payment?.status === 'SUCCESS' ||
    payment?.status === 'PARTIALLY_REFUNDED' ||
    payment?.status === 'REFUND_PENDING';

  const handleCopyId = () => {
    if (paymentId) {
      navigator.clipboard.writeText(paymentId);
      setCopied(true);
      setTimeout(() => setCopied(false), 2000);
    }
  };

  const handleOpenRefundModal = (fullRefund: boolean = false) => {
    setRefundError(null);
    setRefundSuccessMsg(null);
    if (fullRefund) {
      setRefundAmount(remainingRefundable.toFixed(2));
      setRefundReason('Full refund requested');
    } else {
      setRefundAmount('');
      setRefundReason('');
    }
    setIsRefundModalOpen(true);
  };

  const handleProcessRefund = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!paymentId) return;
    setRefundError(null);

    const amt = parseFloat(refundAmount);
    if (isNaN(amt) || amt <= 0) {
      setRefundError('Please provide a valid refund amount.');
      return;
    }
    if (amt > remainingRefundable + 0.0001) {
      setRefundError(`Refund amount cannot exceed remaining refundable balance of $${remainingRefundable.toFixed(2)}.`);
      return;
    }

    setRefundLoading(true);
    try {
      const newRefund = await paymentApi.createRefund(paymentId, {
        amount: amt,
        reason: refundReason,
      });
      setRefundSuccessMsg(`Refund of $${Number(newRefund.amount).toFixed(2)} processed successfully!`);
      setIsRefundModalOpen(false);
      // Refresh details to update payment status and refund list
      await fetchDetails();
    } catch (err) {
      setRefundError(getErrorMessage(err));
    } finally {
      setRefundLoading(false);
    }
  };

  const handleTriggerWebhookRetry = async () => {
    setRetryLoading(true);
    setRetrySuccessMsg(null);
    try {
      const res = await webhookApi.triggerRetry();
      setRetrySuccessMsg(res.message || 'Webhook retry cycle executed');
      await fetchDetails();
    } catch (err) {
      setError(getErrorMessage(err));
    } finally {
      setRetryLoading(false);
    }
  };

  if (loading && !payment) {
    return <LoadingSpinner fullPage message="Fetching payment details..." />;
  }

  if (!payment) {
    return (
      <div>
        <button onClick={() => navigate('/payments')} className="btn btn-secondary" style={{ marginBottom: '1rem' }}>
          <ArrowLeft style={{ width: '16px', height: '16px' }} />
          Back to Payments
        </button>
        <ErrorAlert message={error || 'Payment not found.'} />
      </div>
    );
  }

  return (
    <div style={{ maxWidth: '1000px', margin: '0 auto' }}>
      {/* Back button */}
      <div style={{ marginBottom: '1.25rem' }}>
        <button
          onClick={() => navigate('/payments')}
          className="btn btn-secondary"
          style={{ padding: '4px 10px', fontSize: '0.82rem' }}
        >
          <ArrowLeft style={{ width: '14px', height: '14px' }} />
          <span>Back to Transactions</span>
        </button>
      </div>

      {error && <ErrorAlert message={error} onDismiss={() => setError(null)} />}
      {refundSuccessMsg && (
        <div style={{ backgroundColor: '#ECFDF5', color: '#065F46', padding: '0.85rem 1.15rem', borderRadius: '8px', border: '1px solid #A7F3D0', marginBottom: '1rem', display: 'flex', alignItems: 'center', gap: '8px', fontSize: '0.9rem' }}>
          <CheckCircle style={{ width: '18px', height: '18px', color: '#059669' }} />
          <span>{refundSuccessMsg}</span>
        </div>
      )}
      {retrySuccessMsg && (
        <div style={{ backgroundColor: '#EFF6FF', color: '#1E40AF', padding: '0.85rem 1.15rem', borderRadius: '8px', border: '1px solid #BFDBFE', marginBottom: '1rem', display: 'flex', alignItems: 'center', gap: '8px', fontSize: '0.9rem' }}>
          <CheckCircle style={{ width: '18px', height: '18px', color: '#3B82F6' }} />
          <span>{retrySuccessMsg}</span>
        </div>
      )}

      {/* Main Payment Header Card */}
      <div className="card" style={{ marginBottom: '1.5rem' }}>
        <div style={{ display: 'flex', alignItems: 'flex-start', justifyContent: 'space-between', flexWrap: 'wrap', gap: '1rem' }}>
          <div>
            <div style={{ display: 'flex', alignItems: 'center', gap: '8px', marginBottom: '6px' }}>
              <span style={{ fontSize: '0.8rem', color: '#64748B', fontWeight: 600, textTransform: 'uppercase' }}>
                Payment ID
              </span>
              <button
                onClick={handleCopyId}
                style={{
                  background: 'none',
                  border: 'none',
                  cursor: 'pointer',
                  color: copied ? '#10B981' : '#64748B',
                  display: 'inline-flex',
                  alignItems: 'center',
                  gap: '4px',
                  fontSize: '0.75rem',
                }}
              >
                {copied ? <Check style={{ width: '12px', height: '12px' }} /> : <Copy style={{ width: '12px', height: '12px' }} />}
                <span>{copied ? 'Copied' : 'Copy'}</span>
              </button>
            </div>
            <h1 style={{ fontSize: '1.4rem', fontWeight: 800, color: '#0B192C', fontFamily: 'monospace' }}>
              {payment.paymentId}
            </h1>
            <div style={{ display: 'flex', alignItems: 'center', gap: '1rem', marginTop: '0.5rem', flexWrap: 'wrap' }}>
              <StatusBadge status={payment.status} />
              <span style={{ fontSize: '0.85rem', color: '#64748B', display: 'flex', alignItems: 'center', gap: '4px' }}>
                <Calendar style={{ width: '14px', height: '14px' }} />
                {payment.createdAt ? new Date(payment.createdAt).toLocaleString() : '—'}
              </span>
            </div>
          </div>

          {/* Refund Actions */}
          {canRefund && remainingRefundable > 0 && (
            <div style={{ display: 'flex', gap: '0.5rem', flexWrap: 'wrap' }}>
              <button
                onClick={() => handleOpenRefundModal(false)}
                className="btn btn-secondary"
                style={{ padding: '0.55rem 1rem' }}
              >
                <RotateCcw style={{ width: '15px', height: '15px' }} />
                <span>Partial Refund</span>
              </button>
              <button
                onClick={() => handleOpenRefundModal(true)}
                className="btn btn-danger"
                style={{ padding: '0.55rem 1rem' }}
              >
                <RotateCcw style={{ width: '15px', height: '15px' }} />
                <span>Full Refund (${remainingRefundable.toFixed(2)})</span>
              </button>
            </div>
          )}
        </div>

        {/* Financial Metrics Strip */}
        <div
          style={{
            display: 'grid',
            gridTemplateColumns: 'repeat(auto-fit, minmax(160px, 1fr))',
            gap: '1rem',
            marginTop: '1.5rem',
            paddingTop: '1.25rem',
            borderTop: '1px solid #F1F5F9',
          }}
        >
          <div>
            <div style={{ fontSize: '0.75rem', color: '#64748B', textTransform: 'uppercase', fontWeight: 600 }}>Original Amount</div>
            <div style={{ fontSize: '1.35rem', fontWeight: 800, color: '#0F172A', marginTop: '2px' }}>
              ${originalAmount.toFixed(2)} <span style={{ fontSize: '0.8rem', color: '#64748B' }}>{payment.currency}</span>
            </div>
          </div>

          <div>
            <div style={{ fontSize: '0.75rem', color: '#64748B', textTransform: 'uppercase', fontWeight: 600 }}>Refunded To Date</div>
            <div style={{ fontSize: '1.35rem', fontWeight: 800, color: '#7C3AED', marginTop: '2px' }}>
              ${totalRefunded.toFixed(2)}
            </div>
          </div>

          <div>
            <div style={{ fontSize: '0.75rem', color: '#64748B', textTransform: 'uppercase', fontWeight: 600 }}>Remaining Balance</div>
            <div style={{ fontSize: '1.35rem', fontWeight: 800, color: remainingRefundable > 0 ? '#059669' : '#94A3B8', marginTop: '2px' }}>
              ${remainingRefundable.toFixed(2)}
            </div>
          </div>

          <div>
            <div style={{ fontSize: '0.75rem', color: '#64748B', textTransform: 'uppercase', fontWeight: 600 }}>Payment Method</div>
            <div style={{ fontSize: '1rem', fontWeight: 600, color: '#334155', marginTop: '4px', textTransform: 'capitalize' }}>
              {payment.paymentMethod.replace('_', ' ')}
            </div>
          </div>
        </div>

        {/* Failure reason if any */}
        {payment.failureReason && (
          <div
            style={{
              marginTop: '1.25rem',
              padding: '0.85rem 1rem',
              borderRadius: '8px',
              backgroundColor: '#FEF2F2',
              border: '1px solid #FCA5A5',
              color: '#991B1B',
              fontSize: '0.85rem',
              display: 'flex',
              alignItems: 'center',
              gap: '8px',
            }}
          >
            <AlertTriangle style={{ width: '16px', height: '16px', color: '#DC2626', flexShrink: 0 }} />
            <span><strong>Decline / Error Reason:</strong> {payment.failureReason}</span>
          </div>
        )}

        {/* Transaction details grid */}
        <div style={{ marginTop: '1.5rem', display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(220px, 1fr))', gap: '1rem', fontSize: '0.85rem' }}>
          <div>
            <span style={{ color: '#64748B' }}>Provider Reference: </span>
            <code style={{ color: '#1E293B' }}>{payment.providerTransactionId || 'None'}</code>
          </div>
          <div>
            <span style={{ color: '#64748B' }}>Description: </span>
            <span style={{ color: '#1E293B', fontWeight: 500 }}>{payment.description || '—'}</span>
          </div>
          <div>
            <span style={{ color: '#64748B' }}>Last Updated: </span>
            <span style={{ color: '#1E293B' }}>{payment.updatedAt ? new Date(payment.updatedAt).toLocaleString() : '—'}</span>
          </div>
        </div>
      </div>

      {/* Tabs: Refunds & Webhooks */}
      <div style={{ display: 'flex', gap: '8px', marginBottom: '1rem' }}>
        <button
          onClick={() => setActiveTab('refunds')}
          style={{
            display: 'inline-flex',
            alignItems: 'center',
            gap: '6px',
            padding: '8px 16px',
            borderRadius: '8px',
            fontSize: '0.88rem',
            fontWeight: activeTab === 'refunds' ? 700 : 500,
            backgroundColor: activeTab === 'refunds' ? '#1E3E62' : '#FFFFFF',
            color: activeTab === 'refunds' ? '#FFFFFF' : '#475569',
            border: activeTab === 'refunds' ? '1px solid #1E3E62' : '1px solid #E2E8F0',
            cursor: 'pointer',
          }}
        >
          <RotateCcw style={{ width: '15px', height: '15px' }} />
          <span>Refunds ({refunds.length})</span>
        </button>

        <button
          onClick={() => setActiveTab('webhooks')}
          style={{
            display: 'inline-flex',
            alignItems: 'center',
            gap: '6px',
            padding: '8px 16px',
            borderRadius: '8px',
            fontSize: '0.88rem',
            fontWeight: activeTab === 'webhooks' ? 700 : 500,
            backgroundColor: activeTab === 'webhooks' ? '#1E3E62' : '#FFFFFF',
            color: activeTab === 'webhooks' ? '#FFFFFF' : '#475569',
            border: activeTab === 'webhooks' ? '1px solid #1E3E62' : '1px solid #E2E8F0',
            cursor: 'pointer',
          }}
        >
          <Webhook style={{ width: '15px', height: '15px' }} />
          <span>Webhook Events ({webhooks.length})</span>
        </button>
      </div>

      {/* Tab 1: Refunds Content */}
      {activeTab === 'refunds' && (
        <div className="card" style={{ padding: 0, overflow: 'hidden' }}>
          <div style={{ padding: '1rem 1.25rem', borderBottom: '1px solid #F1F5F9', display: 'flex', alignItems: 'center', justifyContent: 'space-between' }}>
            <div style={{ fontWeight: 700, fontSize: '0.95rem', color: '#1E293B' }}>
              Refund History for this Transaction
            </div>
            {canRefund && remainingRefundable > 0 && (
              <button
                onClick={() => handleOpenRefundModal(false)}
                className="btn btn-primary"
                style={{ backgroundColor: '#1E3E62', padding: '4px 10px', fontSize: '0.78rem' }}
              >
                + Issue Refund
              </button>
            )}
          </div>

          {refunds.length === 0 ? (
            <div style={{ textAlign: 'center', padding: '3rem 1rem', color: '#64748B' }}>
              <RotateCcw style={{ width: '32px', height: '32px', color: '#CBD5E1', marginBottom: '0.5rem' }} />
              <p style={{ fontWeight: 600 }}>No refunds issued</p>
              <p style={{ fontSize: '0.85rem', marginTop: '2px' }}>This payment has not undergone any partial or full refunds.</p>
            </div>
          ) : (
            <div className="data-table-wrapper" style={{ border: 'none' }}>
              <table className="data-table">
                <thead>
                  <tr>
                    <th>Refund ID</th>
                    <th>Amount</th>
                    <th>Status</th>
                    <th>Reason</th>
                    <th>Provider Refund ID</th>
                    <th>Date</th>
                  </tr>
                </thead>
                <tbody>
                  {refunds.map((r) => (
                    <tr key={r.refundId}>
                      <td>
                        <code style={{ fontSize: '0.8rem', color: '#0369A1' }}>{r.refundId}</code>
                      </td>
                      <td style={{ fontWeight: 700 }}>
                        ${Number(r.amount).toFixed(2)}
                      </td>
                      <td>
                        <StatusBadge status={r.status} size="sm" />
                      </td>
                      <td style={{ color: '#475569' }}>{r.reason || '—'}</td>
                      <td>
                        <code style={{ fontSize: '0.78rem' }}>{r.providerRefundId || '—'}</code>
                      </td>
                      <td style={{ fontSize: '0.8rem', color: '#64748B' }}>
                        {r.createdAt ? new Date(r.createdAt).toLocaleString() : '—'}
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          )}
        </div>
      )}

      {/* Tab 2: Webhooks Content */}
      {activeTab === 'webhooks' && (
        <div className="card" style={{ padding: 0, overflow: 'hidden' }}>
          <div style={{ padding: '1rem 1.25rem', borderBottom: '1px solid #F1F5F9', display: 'flex', alignItems: 'center', justifyContent: 'space-between', flexWrap: 'wrap', gap: '0.5rem' }}>
            <div>
              <div style={{ fontWeight: 700, fontSize: '0.95rem', color: '#1E293B' }}>
                Webhook Event Log
              </div>
              <div style={{ fontSize: '0.78rem', color: '#64748B' }}>
                Simulated asynchronous webhooks dispatched to notification endpoints
              </div>
            </div>
            <button
              onClick={handleTriggerWebhookRetry}
              disabled={retryLoading}
              className="btn btn-secondary"
              style={{ padding: '4px 10px', fontSize: '0.8rem' }}
            >
              <Send style={{ width: '13px', height: '13px' }} />
              <span>{retryLoading ? 'Retrying...' : 'Trigger Retry Cycle'}</span>
            </button>
          </div>

          {webhooks.length === 0 ? (
            <div style={{ textAlign: 'center', padding: '3rem 1rem', color: '#64748B' }}>
              <Webhook style={{ width: '32px', height: '32px', color: '#CBD5E1', marginBottom: '0.5rem' }} />
              <p style={{ fontWeight: 600 }}>No webhook events recorded</p>
              <p style={{ fontSize: '0.85rem', marginTop: '2px' }}>Events are emitted when payments or refunds complete.</p>
            </div>
          ) : (
            <div style={{ padding: '1.25rem', display: 'flex', flexDirection: 'column', gap: '1rem' }}>
              {webhooks.map((wh) => (
                <div
                  key={wh.eventId}
                  style={{
                    borderRadius: '8px',
                    border: '1px solid #E2E8F0',
                    backgroundColor: '#F8FAFC',
                    padding: '1rem',
                  }}
                >
                  <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', marginBottom: '0.65rem', flexWrap: 'wrap', gap: '0.5rem' }}>
                    <div style={{ display: 'flex', alignItems: 'center', gap: '8px' }}>
                      <span style={{ fontWeight: 700, fontSize: '0.88rem', color: '#0F172A' }}>
                        {wh.eventType}
                      </span>
                      <StatusBadge status={wh.status} size="sm" />
                    </div>
                    <span style={{ fontSize: '0.75rem', color: '#64748B' }}>
                      Attempts: {wh.attempts} • Created: {wh.createdAt ? new Date(wh.createdAt).toLocaleString() : '—'}
                    </span>
                  </div>

                  <div style={{ fontSize: '0.8rem', color: '#475569', marginBottom: '0.5rem' }}>
                    Event ID: <code>{wh.eventId}</code>
                  </div>

                  <div style={{ backgroundColor: '#0B192C', color: '#E2E8F0', borderRadius: '6px', padding: '0.75rem', fontSize: '0.78rem', fontFamily: 'monospace', overflowX: 'auto', maxHeight: '180px' }}>
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
      )}

      {/* Refund Modal Dialog */}
      {isRefundModalOpen && (
        <div className="modal-overlay">
          <div className="modal-content">
            <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', marginBottom: '1rem' }}>
              <h2 style={{ fontSize: '1.15rem', fontWeight: 700, color: '#0B192C' }}>
                Issue Refund
              </h2>
              <button
                onClick={() => setIsRefundModalOpen(false)}
                style={{ background: 'none', border: 'none', cursor: 'pointer', fontSize: '1.25rem', color: '#94A3B8' }}
              >
                ✕
              </button>
            </div>

            {refundError && <ErrorAlert message={refundError} onDismiss={() => setRefundError(null)} />}

            <div style={{ backgroundColor: '#F1F5F9', borderRadius: '6px', padding: '0.75rem', fontSize: '0.82rem', marginBottom: '1rem' }}>
              <div style={{ display: 'flex', justifyContent: 'space-between' }}>
                <span style={{ color: '#64748B' }}>Original Amount:</span>
                <span style={{ fontWeight: 600 }}>${originalAmount.toFixed(2)}</span>
              </div>
              <div style={{ display: 'flex', justifyContent: 'space-between', marginTop: '4px' }}>
                <span style={{ color: '#64748B' }}>Already Refunded:</span>
                <span style={{ fontWeight: 600, color: '#7C3AED' }}>${totalRefunded.toFixed(2)}</span>
              </div>
              <div style={{ display: 'flex', justifyContent: 'space-between', marginTop: '4px', borderTop: '1px solid #CBD5E1', paddingTop: '4px' }}>
                <span style={{ fontWeight: 700, color: '#0F172A' }}>Available to Refund:</span>
                <span style={{ fontWeight: 800, color: '#059669' }}>${remainingRefundable.toFixed(2)}</span>
              </div>
            </div>

            <form onSubmit={handleProcessRefund}>
              <div className="form-group">
                <label className="form-label" htmlFor="refundAmount">
                  Refund Amount ($)
                </label>
                <input
                  id="refundAmount"
                  type="number"
                  step="0.01"
                  required
                  min="0.01"
                  max={remainingRefundable}
                  className="form-control"
                  placeholder={`Max: ${remainingRefundable.toFixed(2)}`}
                  value={refundAmount}
                  onChange={(e) => setRefundAmount(e.target.value)}
                />
              </div>

              <div className="form-group">
                <label className="form-label" htmlFor="refundReason">
                  Reason for Refund
                </label>
                <input
                  id="refundReason"
                  type="text"
                  className="form-control"
                  placeholder="e.g. Defective merchandise, customer cancelled"
                  value={refundReason}
                  onChange={(e) => setRefundReason(e.target.value)}
                />
              </div>

              <div style={{ display: 'flex', justifyContent: 'flex-end', gap: '8px', marginTop: '1.25rem' }}>
                <button
                  type="button"
                  onClick={() => setIsRefundModalOpen(false)}
                  className="btn btn-secondary"
                >
                  Cancel
                </button>
                <button
                  type="submit"
                  disabled={refundLoading}
                  className="btn btn-primary"
                  style={{ backgroundColor: '#1E3E62' }}
                >
                  {refundLoading ? 'Processing...' : 'Confirm Refund'}
                </button>
              </div>
            </form>
          </div>
        </div>
      )}
    </div>
  );
};
