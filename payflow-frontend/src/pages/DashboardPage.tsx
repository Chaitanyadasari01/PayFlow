import React, { useEffect, useState, useMemo } from 'react';
import { useNavigate, Link } from 'react-router-dom';
import { paymentApi } from '../api/paymentApi';
import type { Payment } from '../types';
import { getErrorMessage } from '../api/client';
import { StatusBadge } from '../components/common/StatusBadge';
import { LoadingSpinner } from '../components/common/LoadingSpinner';
import { ErrorAlert } from '../components/common/ErrorAlert';
import {
  CreditCard,
  CheckCircle2,
  XCircle,
  RotateCcw,
  Plus,
  RefreshCw,
  TrendingUp,
  ArrowRight,
} from 'lucide-react';
import {
  ResponsiveContainer,
  BarChart,
  Bar,
  XAxis,
  YAxis,
  Tooltip,
  PieChart,
  Pie,
  Cell,
} from 'recharts';

export const DashboardPage: React.FC = () => {
  const [payments, setPayments] = useState<Payment[]>([]);
  const [loading, setLoading] = useState<boolean>(true);
  const [error, setError] = useState<string | null>(null);
  const navigate = useNavigate();

  const fetchDashboardData = async () => {
    setLoading(true);
    setError(null);
    try {
      // Fetch up to 50 recent payments for client analytics
      const res = await paymentApi.listPayments({ page: 0, size: 50 });
      setPayments(res.content);
    } catch (err) {
      setError(getErrorMessage(err));
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    fetchDashboardData();
  }, []);

  // Compute Metrics
  const stats = useMemo(() => {
    let totalCount = payments.length;
    let totalVolume = 0;
    let successCount = 0;
    let failedCount = 0;
    let refundedCount = 0;
    let refundedAmountEstimate = 0;

    payments.forEach((p) => {
      const amt = Number(p.amount) || 0;
      totalVolume += amt;
      if (p.status === 'SUCCESS') successCount++;
      else if (p.status === 'FAILED') failedCount++;
      else if (p.status === 'REFUNDED') {
        refundedCount++;
        refundedAmountEstimate += amt;
      } else if (p.status === 'PARTIALLY_REFUNDED') {
        refundedCount++;
        // partial estimate approx half of original payment
        refundedAmountEstimate += amt * 0.5;
      }
    });

    return {
      totalCount,
      totalVolume,
      successCount,
      failedCount,
      refundedCount,
      refundedAmountEstimate,
    };
  }, [payments]);

  // Chart Data: Status breakdown
  const statusChartData = useMemo(() => {
    const counts: Record<string, number> = {};
    payments.forEach((p) => {
      counts[p.status] = (counts[p.status] || 0) + 1;
    });
    return Object.keys(counts).map((k) => ({
      name: k,
      value: counts[k],
    }));
  }, [payments]);

  // Chart Data: Volume by currency or recent payments
  const recentVolumeData = useMemo(() => {
    return payments
      .slice(0, 7)
      .reverse()
      .map((p, idx) => ({
        label: p.createdAt ? new Date(p.createdAt).toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' }) : `#${idx + 1}`,
        amount: Number(p.amount) || 0,
        status: p.status,
      }));
  }, [payments]);

  const STATUS_COLORS: Record<string, string> = {
    SUCCESS: '#10B981',
    PARTIALLY_REFUNDED: '#0284C7',
    REFUNDED: '#7C3AED',
    FAILED: '#EF4444',
    PENDING: '#F59E0B',
  };

  if (loading && payments.length === 0) {
    return <LoadingSpinner fullPage message="Loading PayFlow Dashboard metrics..." />;
  }

  return (
    <div>
      {/* Top Banner */}
      <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', marginBottom: '1.75rem', flexWrap: 'wrap', gap: '1rem' }}>
        <div>
          <h1 style={{ fontSize: '1.65rem', fontWeight: 800, color: '#0B192C', letterSpacing: '-0.02em' }}>
            Payment Overview
          </h1>
          <p style={{ color: '#64748B', fontSize: '0.9rem', marginTop: '2px' }}>
            Real-time simulation metrics, idempotency operations & transaction ledger
          </p>
        </div>

        <div style={{ display: 'flex', gap: '0.75rem' }}>
          <button
            onClick={fetchDashboardData}
            className="btn btn-secondary"
            title="Refresh dashboard stats"
          >
            <RefreshCw style={{ width: '15px', height: '15px' }} />
            <span>Refresh</span>
          </button>
          <button
            onClick={() => navigate('/payments/new')}
            className="btn btn-primary"
            style={{ backgroundColor: '#008DDA' }}
          >
            <Plus style={{ width: '16px', height: '16px' }} />
            <span>Create Payment</span>
          </button>
        </div>
      </div>

      {error && <ErrorAlert message={error} onRetry={fetchDashboardData} />}

      {/* KPI Cards Grid */}
      <div
        style={{
          display: 'grid',
          gridTemplateColumns: 'repeat(auto-fit, minmax(240px, 1fr))',
          gap: '1.25rem',
          marginBottom: '2rem',
        }}
      >
        {/* Total Payments */}
        <div className="card" style={{ display: 'flex', alignItems: 'center', gap: '1.25rem' }}>
          <div
            style={{
              width: '48px',
              height: '48px',
              borderRadius: '10px',
              backgroundColor: '#EFF6FF',
              color: '#1D4ED8',
              display: 'flex',
              alignItems: 'center',
              justifyContent: 'center',
            }}
          >
            <CreditCard style={{ width: '24px', height: '24px' }} />
          </div>
          <div>
            <div style={{ fontSize: '0.8rem', fontWeight: 600, color: '#64748B', textTransform: 'uppercase' }}>
              Total Payments
            </div>
            <div style={{ fontSize: '1.6rem', fontWeight: 800, color: '#0F172A', marginTop: '2px' }}>
              {stats.totalCount}
            </div>
            <div style={{ fontSize: '0.78rem', color: '#64748B' }}>
              Vol: ${stats.totalVolume.toFixed(2)} USD
            </div>
          </div>
        </div>

        {/* Successful Payments */}
        <div className="card" style={{ display: 'flex', alignItems: 'center', gap: '1.25rem' }}>
          <div
            style={{
              width: '48px',
              height: '48px',
              borderRadius: '10px',
              backgroundColor: '#ECFDF5',
              color: '#059669',
              display: 'flex',
              alignItems: 'center',
              justifyContent: 'center',
            }}
          >
            <CheckCircle2 style={{ width: '24px', height: '24px' }} />
          </div>
          <div>
            <div style={{ fontSize: '0.8rem', fontWeight: 600, color: '#64748B', textTransform: 'uppercase' }}>
              Successful Payments
            </div>
            <div style={{ fontSize: '1.6rem', fontWeight: 800, color: '#059669', marginTop: '2px' }}>
              {stats.successCount}
            </div>
            <div style={{ fontSize: '0.78rem', color: '#64748B' }}>
              {stats.totalCount > 0
                ? `${Math.round((stats.successCount / stats.totalCount) * 100)}% approval rate`
                : '0% rate'}
            </div>
          </div>
        </div>

        {/* Failed Payments */}
        <div className="card" style={{ display: 'flex', alignItems: 'center', gap: '1.25rem' }}>
          <div
            style={{
              width: '48px',
              height: '48px',
              borderRadius: '10px',
              backgroundColor: '#FEF2F2',
              color: '#DC2626',
              display: 'flex',
              alignItems: 'center',
              justifyContent: 'center',
            }}
          >
            <XCircle style={{ width: '24px', height: '24px' }} />
          </div>
          <div>
            <div style={{ fontSize: '0.8rem', fontWeight: 600, color: '#64748B', textTransform: 'uppercase' }}>
              Failed Payments
            </div>
            <div style={{ fontSize: '1.6rem', fontWeight: 800, color: '#DC2626', marginTop: '2px' }}>
              {stats.failedCount}
            </div>
            <div style={{ fontSize: '0.78rem', color: '#64748B' }}>
              Declined, timeout or limit
            </div>
          </div>
        </div>

        {/* Refunded */}
        <div className="card" style={{ display: 'flex', alignItems: 'center', gap: '1.25rem' }}>
          <div
            style={{
              width: '48px',
              height: '48px',
              borderRadius: '10px',
              backgroundColor: '#F3E8FF',
              color: '#7C3AED',
              display: 'flex',
              alignItems: 'center',
              justifyContent: 'center',
            }}
          >
            <RotateCcw style={{ width: '24px', height: '24px' }} />
          </div>
          <div>
            <div style={{ fontSize: '0.8rem', fontWeight: 600, color: '#64748B', textTransform: 'uppercase' }}>
              Refunded Payments
            </div>
            <div style={{ fontSize: '1.6rem', fontWeight: 800, color: '#7C3AED', marginTop: '2px' }}>
              {stats.refundedCount}
            </div>
            <div style={{ fontSize: '0.78rem', color: '#64748B' }}>
              ~${stats.refundedAmountEstimate.toFixed(2)} refunded
            </div>
          </div>
        </div>
      </div>

      {/* Analytics Charts Row */}
      <div
        style={{
          display: 'grid',
          gridTemplateColumns: 'repeat(auto-fit, minmax(360px, 1fr))',
          gap: '1.25rem',
          marginBottom: '2rem',
        }}
      >
        {/* Recent Volume Bar Chart */}
        <div className="card">
          <div className="card-header">
            <div>
              <div className="card-title">Recent Payment Amounts</div>
              <div style={{ fontSize: '0.8rem', color: '#64748B' }}>Last 7 transactions amount (USD)</div>
            </div>
            <TrendingUp style={{ width: '18px', height: '18px', color: '#64748B' }} />
          </div>
          <div style={{ height: '240px', width: '100%' }}>
            {recentVolumeData.length === 0 ? (
              <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'center', height: '100%', color: '#94A3B8' }}>
                No recent payment data
              </div>
            ) : (
              <ResponsiveContainer width="100%" height="100%">
                <BarChart data={recentVolumeData}>
                  <XAxis dataKey="label" stroke="#94A3B8" fontSize={12} />
                  <YAxis stroke="#94A3B8" fontSize={12} />
                  <Tooltip
                    formatter={(val: unknown) => {
                      const num = typeof val === 'number' ? val : Number(val) || 0;
                      return [`$${num.toFixed(2)}`, 'Amount'];
                    }}
                  />
                  <Bar dataKey="amount" fill="#008DDA" radius={[4, 4, 0, 0]} />
                </BarChart>
              </ResponsiveContainer>
            )}
          </div>
        </div>

        {/* Status Distribution Pie Chart */}
        <div className="card">
          <div className="card-header">
            <div>
              <div className="card-title">Status Breakdown</div>
              <div style={{ fontSize: '0.8rem', color: '#64748B' }}>Distribution across all transactions</div>
            </div>
          </div>
          <div style={{ height: '240px', width: '100%' }}>
            {statusChartData.length === 0 ? (
              <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'center', height: '100%', color: '#94A3B8' }}>
                No status breakdown available
              </div>
            ) : (
              <ResponsiveContainer width="100%" height="100%">
                <PieChart>
                  <Pie
                    data={statusChartData}
                    dataKey="value"
                    nameKey="name"
                    cx="50%"
                    cy="50%"
                    innerRadius={55}
                    outerRadius={85}
                    paddingAngle={3}
                  >
                    {statusChartData.map((entry) => (
                      <Cell
                        key={`cell-${entry.name}`}
                        fill={STATUS_COLORS[entry.name] || '#94A3B8'}
                      />
                    ))}
                  </Pie>
                  <Tooltip />
                </PieChart>
              </ResponsiveContainer>
            )}
          </div>
        </div>
      </div>

      {/* Recent Transactions Table */}
      <div className="card">
        <div className="card-header">
          <div className="card-title">Recent Transactions</div>
          <Link
            to="/payments"
            style={{
              display: 'inline-flex',
              alignItems: 'center',
              gap: '4px',
              fontSize: '0.85rem',
              fontWeight: 600,
              color: '#008DDA',
              textDecoration: 'none',
            }}
          >
            <span>View All</span>
            <ArrowRight style={{ width: '14px', height: '14px' }} />
          </Link>
        </div>

        {payments.length === 0 ? (
          <div style={{ textAlign: 'center', padding: '3rem 1rem', color: '#64748B' }}>
            <CreditCard style={{ width: '36px', height: '36px', color: '#CBD5E1', marginBottom: '0.5rem' }} />
            <p style={{ fontWeight: 600 }}>No payments recorded yet</p>
            <p style={{ fontSize: '0.85rem', marginTop: '2px' }}>Create your first simulated transaction to see it here.</p>
            <button
              onClick={() => navigate('/payments/new')}
              className="btn btn-primary"
              style={{ marginTop: '1rem', backgroundColor: '#008DDA' }}
            >
              Create Payment
            </button>
          </div>
        ) : (
          <div className="data-table-wrapper">
            <table className="data-table">
              <thead>
                <tr>
                  <th>Payment ID</th>
                  <th>Amount</th>
                  <th>Method</th>
                  <th>Status</th>
                  <th>Description</th>
                  <th>Date</th>
                  <th>Action</th>
                </tr>
              </thead>
              <tbody>
                {payments.slice(0, 5).map((p) => (
                  <tr key={p.paymentId}>
                    <td>
                      <code style={{ fontSize: '0.8rem', color: '#0369A1' }}>
                        {p.paymentId.substring(0, 8)}...
                      </code>
                    </td>
                    <td style={{ fontWeight: 700 }}>
                      ${Number(p.amount).toFixed(2)} {p.currency}
                    </td>
                    <td style={{ textTransform: 'capitalize' }}>
                      {p.paymentMethod.replace('_', ' ')}
                    </td>
                    <td>
                      <StatusBadge status={p.status} size="sm" />
                    </td>
                    <td style={{ color: '#64748B', maxWidth: '200px', whiteSpace: 'nowrap', overflow: 'hidden', textOverflow: 'ellipsis' }}>
                      {p.description || '—'}
                    </td>
                    <td style={{ fontSize: '0.8rem', color: '#64748B' }}>
                      {p.createdAt ? new Date(p.createdAt).toLocaleDateString() : '—'}
                    </td>
                    <td>
                      <button
                        onClick={() => navigate(`/payments/${p.paymentId}`)}
                        className="btn btn-secondary"
                        style={{ padding: '3px 8px', fontSize: '0.78rem' }}
                      >
                        Details
                      </button>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}
      </div>
    </div>
  );
};
