import React, { useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { paymentApi } from '../api/paymentApi';
import type { Payment } from '../types';
import { getErrorMessage } from '../api/client';
import { ErrorAlert } from '../components/common/ErrorAlert';
import { StatusBadge } from '../components/common/StatusBadge';
import {
  CreditCard,
  KeyRound,
  RefreshCw,
  CheckCircle,
  ArrowRight,
  ShieldAlert,
} from 'lucide-react';

export const CreatePaymentPage: React.FC = () => {
  const [amount, setAmount] = useState<string>('150.00');
  const [currency, setCurrency] = useState<string>('USD');
  const [paymentMethod, setPaymentMethod] = useState<string>('credit_card');
  const [description, setDescription] = useState<string>('Standard Order');
  const [idempotencyKey, setIdempotencyKey] = useState<string>(() => crypto.randomUUID());
  const [useIdempotency, setUseIdempotency] = useState<boolean>(true);

  const [loading, setLoading] = useState<boolean>(false);
  const [error, setError] = useState<string | null>(null);
  const [createdPayment, setCreatedPayment] = useState<Payment | null>(null);

  const navigate = useNavigate();

  const handleGenerateKey = () => {
    setIdempotencyKey(crypto.randomUUID());
  };

  const handleApplyPreset = (amt: string, desc: string) => {
    setAmount(amt);
    setDescription(desc);
  };

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    setError(null);
    setCreatedPayment(null);

    const parsedAmount = parseFloat(amount);
    if (isNaN(parsedAmount) || parsedAmount <= 0) {
      setError('Please provide a valid positive payment amount.');
      return;
    }

    setLoading(true);

    try {
      const payment = await paymentApi.createPayment(
        {
          amount: parsedAmount,
          currency,
          paymentMethod,
          description,
        },
        useIdempotency ? idempotencyKey : undefined
      );
      setCreatedPayment(payment);
    } catch (err) {
      setError(getErrorMessage(err));
    } finally {
      setLoading(false);
    }
  };

  return (
    <div style={{ maxWidth: '800px', margin: '0 auto' }}>
      {/* Page Header */}
      <div style={{ marginBottom: '1.5rem' }}>
        <h1 style={{ fontSize: '1.65rem', fontWeight: 800, color: '#0B192C', letterSpacing: '-0.02em' }}>
          Create Payment
        </h1>
        <p style={{ color: '#64748B', fontSize: '0.9rem', marginTop: '2px' }}>
          Execute safe simulated transactions with concurrency-safe idempotency keys
        </p>
      </div>

      {error && <ErrorAlert message={error} onDismiss={() => setError(null)} />}

      {/* Success Notification */}
      {createdPayment && (
        <div
          style={{
            backgroundColor: '#ECFDF5',
            border: '1px solid #A7F3D0',
            borderRadius: '10px',
            padding: '1.25rem',
            marginBottom: '1.5rem',
            display: 'flex',
            alignItems: 'flex-start',
            gap: '1rem',
          }}
        >
          <CheckCircle style={{ width: '22px', height: '22px', color: '#059669', flexShrink: 0, marginTop: '2px' }} />
          <div style={{ flex: 1 }}>
            <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', flexWrap: 'wrap', gap: '0.5rem' }}>
              <span style={{ fontWeight: 700, color: '#065F46', fontSize: '0.95rem' }}>
                Payment Processed: ${Number(createdPayment.amount).toFixed(2)} {createdPayment.currency}
              </span>
              <StatusBadge status={createdPayment.status} />
            </div>

            <div style={{ marginTop: '0.5rem', fontSize: '0.85rem', color: '#047857' }}>
              <div>
                ID: <code style={{ fontWeight: 600 }}>{createdPayment.paymentId}</code>
              </div>
              {createdPayment.providerTransactionId && (
                <div>Provider TX: <code>{createdPayment.providerTransactionId}</code></div>
              )}
              {createdPayment.failureReason && (
                <div style={{ color: '#DC2626', marginTop: '4px', fontWeight: 600 }}>
                  Reason: {createdPayment.failureReason}
                </div>
              )}
            </div>

            <div style={{ marginTop: '0.75rem', display: 'flex', gap: '0.75rem' }}>
              <button
                onClick={() => navigate(`/payments/${createdPayment.paymentId}`)}
                className="btn btn-primary"
                style={{ backgroundColor: '#059669', padding: '0.4rem 0.85rem', fontSize: '0.82rem' }}
              >
                <span>View Details & Refunds</span>
                <ArrowRight style={{ width: '14px', height: '14px' }} />
              </button>
              <button
                onClick={() => {
                  setCreatedPayment(null);
                  handleGenerateKey();
                }}
                className="btn btn-secondary"
                style={{ padding: '0.4rem 0.85rem', fontSize: '0.82rem' }}
              >
                Create Another
              </button>
            </div>
          </div>
        </div>
      )}

      {/* Main Form Card */}
      <div className="card">
        {/* Simulation Shortcuts Bar */}
        <div style={{ marginBottom: '1.5rem', padding: '1rem', backgroundColor: '#F8FAFC', borderRadius: '8px', border: '1px solid #E2E8F0' }}>
          <div style={{ fontSize: '0.8rem', fontWeight: 700, color: '#334155', textTransform: 'uppercase', marginBottom: '0.5rem', display: 'flex', alignItems: 'center', gap: '6px' }}>
            <ShieldAlert style={{ width: '14px', height: '14px', color: '#008DDA' }} />
            <span>Quick Test Simulation Scenarios</span>
          </div>
          <div style={{ display: 'flex', flexWrap: 'wrap', gap: '8px' }}>
            <button
              type="button"
              className="sim-pill success"
              onClick={() => handleApplyPreset('150.00', 'Approved Order')}
            >
              $150.00 (Approved)
            </button>
            <button
              type="button"
              className="sim-pill decline"
              onClick={() => handleApplyPreset('49.99', 'Simulate Insufficient Funds')}
            >
              $49.99 (Declined .99)
            </button>
            <button
              type="button"
              className="sim-pill error"
              onClick={() => handleApplyPreset('100.77', 'Simulate Gateway Timeout')}
            >
              $100.77 (Timeout .77)
            </button>
            <button
              type="button"
              className="sim-pill decline"
              onClick={() => handleApplyPreset('15000.00', 'Over Limit Order')}
            >
              $15,000.00 (&gt;$10k Limit)
            </button>
          </div>
        </div>

        <form onSubmit={handleSubmit}>
          {/* Amount and Currency */}
          <div style={{ display: 'grid', gridTemplateColumns: '2fr 1fr', gap: '1rem' }}>
            <div className="form-group">
              <label className="form-label" htmlFor="amount">
                Amount
              </label>
              <div style={{ position: 'relative' }}>
                <span
                  style={{
                    position: 'absolute',
                    left: '12px',
                    top: '50%',
                    transform: 'translateY(-50%)',
                    fontWeight: 700,
                    color: '#64748B',
                  }}
                >
                  $
                </span>
                <input
                  id="amount"
                  type="number"
                  step="0.01"
                  required
                  min="0.01"
                  className="form-control"
                  style={{ paddingLeft: '28px', fontSize: '1.05rem', fontWeight: 600 }}
                  value={amount}
                  onChange={(e) => setAmount(e.target.value)}
                />
              </div>
            </div>

            <div className="form-group">
              <label className="form-label" htmlFor="currency">
                Currency
              </label>
              <select
                id="currency"
                className="form-select"
                value={currency}
                onChange={(e) => setCurrency(e.target.value)}
              >
                <option value="USD">USD ($)</option>
                <option value="EUR">EUR (€)</option>
                <option value="GBP">GBP (£)</option>
              </select>
            </div>
          </div>

          {/* Payment Method */}
          <div className="form-group">
            <label className="form-label" htmlFor="paymentMethod">
              Payment Method
            </label>
            <select
              id="paymentMethod"
              className="form-select"
              value={paymentMethod}
              onChange={(e) => setPaymentMethod(e.target.value)}
            >
              <option value="credit_card">Credit Card (Simulated)</option>
              <option value="debit_card">Debit Card (Simulated)</option>
              <option value="bank_transfer">Bank Transfer (ACH / Wire)</option>
            </select>
          </div>

          {/* Description */}
          <div className="form-group">
            <label className="form-label" htmlFor="description">
              Description / Reference
            </label>
            <input
              id="description"
              type="text"
              className="form-control"
              placeholder="e.g. Invoice #2024-001 or Subscription renewal"
              value={description}
              onChange={(e) => setDescription(e.target.value)}
            />
          </div>

          {/* Idempotency Key Section */}
          <div
            style={{
              padding: '1.25rem',
              backgroundColor: '#F8FAFC',
              borderRadius: '8px',
              border: '1px solid #E2E8F0',
              marginBottom: '1.5rem',
            }}
          >
            <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', marginBottom: '0.65rem' }}>
              <label style={{ display: 'flex', alignItems: 'center', gap: '8px', cursor: 'pointer', fontSize: '0.88rem', fontWeight: 600, color: '#1E293B' }}>
                <input
                  type="checkbox"
                  checked={useIdempotency}
                  onChange={(e) => setUseIdempotency(e.target.checked)}
                  style={{ width: '16px', height: '16px', accentColor: '#008DDA' }}
                />
                <KeyRound style={{ width: '15px', height: '15px', color: '#008DDA' }} />
                <span>Enable Idempotency-Key Header</span>
              </label>

              {useIdempotency && (
                <button
                  type="button"
                  onClick={handleGenerateKey}
                  style={{
                    background: 'none',
                    border: 'none',
                    color: '#008DDA',
                    fontSize: '0.78rem',
                    fontWeight: 600,
                    cursor: 'pointer',
                    display: 'flex',
                    alignItems: 'center',
                    gap: '4px',
                  }}
                >
                  <RefreshCw style={{ width: '12px', height: '12px' }} />
                  <span>Generate New UUID</span>
                </button>
              )}
            </div>

            {useIdempotency && (
              <>
                <input
                  type="text"
                  className="form-control"
                  style={{ fontFamily: 'monospace', fontSize: '0.85rem', backgroundColor: '#FFFFFF' }}
                  value={idempotencyKey}
                  onChange={(e) => setIdempotencyKey(e.target.value)}
                  placeholder="Unique UUID"
                />
                <div className="form-help">
                  Prevents duplicate charges. Submitting the exact same key will return the cached response without re-charging.
                </div>
              </>
            )}
          </div>

          {/* Submit Button */}
          <button
            type="submit"
            disabled={loading}
            className="btn btn-primary"
            style={{ width: '100%', padding: '0.8rem', fontSize: '0.95rem', backgroundColor: '#1E3E62' }}
          >
            <CreditCard style={{ width: '18px', height: '18px' }} />
            <span>{loading ? 'Processing Transaction...' : `Pay $${parseFloat(amount || '0').toFixed(2)} ${currency}`}</span>
          </button>
        </form>
      </div>
    </div>
  );
};
