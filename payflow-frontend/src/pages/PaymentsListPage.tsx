import React, { useState, useEffect } from 'react';
import { useNavigate } from 'react-router-dom';
import { paymentApi } from '../api/paymentApi';
import type { Payment } from '../types';
import { getErrorMessage } from '../api/client';
import { StatusBadge } from '../components/common/StatusBadge';
import { LoadingSpinner } from '../components/common/LoadingSpinner';
import { ErrorAlert } from '../components/common/ErrorAlert';
import {
  Plus,
  RefreshCw,
  Search,
  ChevronLeft,
  ChevronRight,
  SlidersHorizontal,
} from 'lucide-react';

export const PaymentsListPage: React.FC = () => {
  const [payments, setPayments] = useState<Payment[]>([]);
  const [loading, setLoading] = useState<boolean>(true);
  const [error, setError] = useState<string | null>(null);

  // Pagination state
  const [currentPage, setCurrentPage] = useState<number>(0);
  const [pageSize, setPageSize] = useState<number>(10);
  const [totalPages, setTotalPages] = useState<number>(1);
  const [totalElements, setTotalElements] = useState<number>(0);

  // Filter & Search
  const [statusFilter, setStatusFilter] = useState<string>('ALL');
  const [searchQuery, setSearchQuery] = useState<string>('');

  const navigate = useNavigate();

  const fetchPayments = async (page: number = currentPage, size: number = pageSize) => {
    setLoading(true);
    setError(null);
    try {
      const res = await paymentApi.listPayments({ page, size });
      setPayments(res.content);
      setCurrentPage(res.page);
      setTotalPages(res.totalPages || 1);
      setTotalElements(res.totalElements);
    } catch (err) {
      setError(getErrorMessage(err));
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    fetchPayments(currentPage, pageSize);
  }, [currentPage, pageSize]);

  // Client-side filtering
  const filteredPayments = payments.filter((p) => {
    const matchesStatus =
      statusFilter === 'ALL' || p.status.toUpperCase() === statusFilter.toUpperCase();
    const query = searchQuery.toLowerCase().trim();
    const matchesSearch =
      !query ||
      p.paymentId.toLowerCase().includes(query) ||
      (p.description && p.description.toLowerCase().includes(query)) ||
      (p.providerTransactionId && p.providerTransactionId.toLowerCase().includes(query));
    return matchesStatus && matchesSearch;
  });

  const statusTabs = [
    { label: 'All', value: 'ALL' },
    { label: 'Success', value: 'SUCCESS' },
    { label: 'Failed', value: 'FAILED' },
    { label: 'Partially Refunded', value: 'PARTIALLY_REFUNDED' },
    { label: 'Refunded', value: 'REFUNDED' },
  ];

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
            Transactions
          </h1>
          <p style={{ color: '#64748B', fontSize: '0.9rem', marginTop: '2px' }}>
            Complete ledger of {totalElements} payment operations
          </p>
        </div>

        <div style={{ display: 'flex', gap: '0.75rem' }}>
          <button
            onClick={() => fetchPayments(currentPage, pageSize)}
            className="btn btn-secondary"
            title="Reload records"
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
            <span>New Payment</span>
          </button>
        </div>
      </div>

      {error && <ErrorAlert message={error} onRetry={() => fetchPayments(currentPage, pageSize)} />}

      {/* Filter and Search Bar */}
      <div className="card" style={{ marginBottom: '1.25rem', padding: '1rem 1.25rem' }}>
        <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', flexWrap: 'wrap', gap: '1rem' }}>
          {/* Status Tabs */}
          <div style={{ display: 'flex', flexWrap: 'wrap', gap: '6px' }}>
            {statusTabs.map((tab) => {
              const active = statusFilter === tab.value;
              return (
                <button
                  key={tab.value}
                  onClick={() => setStatusFilter(tab.value)}
                  style={{
                    padding: '5px 12px',
                    borderRadius: '6px',
                    fontSize: '0.82rem',
                    fontWeight: active ? 700 : 500,
                    backgroundColor: active ? '#1E3E62' : '#F1F5F9',
                    color: active ? '#FFFFFF' : '#475569',
                    border: 'none',
                    cursor: 'pointer',
                    transition: 'all 0.15s ease',
                  }}
                >
                  {tab.label}
                </button>
              );
            })}
          </div>

          {/* Search Box */}
          <div style={{ position: 'relative', width: '280px', maxWidth: '100%' }}>
            <Search
              style={{
                position: 'absolute',
                left: '10px',
                top: '50%',
                transform: 'translateY(-50%)',
                width: '15px',
                height: '15px',
                color: '#94A3B8',
              }}
            />
            <input
              type="text"
              className="form-control"
              style={{ paddingLeft: '32px', fontSize: '0.85rem' }}
              placeholder="Search ID or description..."
              value={searchQuery}
              onChange={(e) => setSearchQuery(e.target.value)}
            />
          </div>
        </div>
      </div>

      {/* Main Table Card */}
      <div className="card" style={{ padding: 0, overflow: 'hidden' }}>
        {loading ? (
          <div style={{ padding: '3rem' }}>
            <LoadingSpinner message="Fetching transactions..." />
          </div>
        ) : filteredPayments.length === 0 ? (
          <div style={{ textAlign: 'center', padding: '3.5rem 1rem', color: '#64748B' }}>
            <SlidersHorizontal style={{ width: '36px', height: '36px', color: '#CBD5E1', marginBottom: '0.75rem' }} />
            <h3 style={{ fontSize: '1rem', fontWeight: 700, color: '#334155' }}>No transactions found</h3>
            <p style={{ fontSize: '0.85rem', marginTop: '4px' }}>
              {statusFilter !== 'ALL' || searchQuery
                ? 'Try clearing the active filters or search terms.'
                : 'No payments exist yet. Create your first payment!'}
            </p>
          </div>
        ) : (
          <div className="data-table-wrapper" style={{ border: 'none', borderRadius: 0 }}>
            <table className="data-table">
              <thead>
                <tr>
                  <th>Payment ID</th>
                  <th>Amount</th>
                  <th>Status</th>
                  <th>Method</th>
                  <th>Description</th>
                  <th>Provider Ref</th>
                  <th>Created At</th>
                  <th>Action</th>
                </tr>
              </thead>
              <tbody>
                {filteredPayments.map((p) => (
                  <tr key={p.paymentId}>
                    <td>
                      <code style={{ fontSize: '0.82rem', color: '#0369A1', fontWeight: 600 }}>
                        {p.paymentId}
                      </code>
                    </td>
                    <td style={{ fontWeight: 700, whiteSpace: 'nowrap' }}>
                      ${Number(p.amount).toFixed(2)} <span style={{ fontSize: '0.75rem', color: '#64748B' }}>{p.currency}</span>
                    </td>
                    <td>
                      <StatusBadge status={p.status} size="sm" />
                    </td>
                    <td style={{ textTransform: 'capitalize', whiteSpace: 'nowrap' }}>
                      {p.paymentMethod.replace('_', ' ')}
                    </td>
                    <td style={{ color: '#475569', maxWidth: '220px', overflow: 'hidden', textOverflow: 'ellipsis', whiteSpace: 'nowrap' }}>
                      {p.description || '—'}
                    </td>
                    <td style={{ fontSize: '0.78rem', color: '#64748B' }}>
                      {p.providerTransactionId ? <code>{p.providerTransactionId}</code> : '—'}
                    </td>
                    <td style={{ fontSize: '0.8rem', color: '#64748B', whiteSpace: 'nowrap' }}>
                      {p.createdAt ? new Date(p.createdAt).toLocaleString() : '—'}
                    </td>
                    <td>
                      <button
                        onClick={() => navigate(`/payments/${p.paymentId}`)}
                        className="btn btn-secondary"
                        style={{ padding: '4px 10px', fontSize: '0.78rem' }}
                      >
                        View
                      </button>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}

        {/* Pagination Bar */}
        <div
          style={{
            display: 'flex',
            alignItems: 'center',
            justifyContent: 'space-between',
            padding: '1rem 1.25rem',
            borderTop: '1px solid #E2E8F0',
            backgroundColor: '#FAFAFA',
            fontSize: '0.85rem',
            color: '#64748B',
            flexWrap: 'wrap',
            gap: '0.75rem',
          }}
        >
          <div style={{ display: 'flex', alignItems: 'center', gap: '8px' }}>
            <span>Rows per page:</span>
            <select
              value={pageSize}
              onChange={(e) => {
                setPageSize(Number(e.target.value));
                setCurrentPage(0);
              }}
              style={{
                padding: '3px 6px',
                borderRadius: '4px',
                border: '1px solid #CBD5E1',
                fontSize: '0.8rem',
              }}
            >
              <option value={5}>5</option>
              <option value={10}>10</option>
              <option value={25}>25</option>
              <option value={50}>50</option>
            </select>
            <span>
              Page {currentPage + 1} of {totalPages} ({totalElements} total)
            </span>
          </div>

          <div style={{ display: 'flex', gap: '6px' }}>
            <button
              onClick={() => setCurrentPage((p) => Math.max(0, p - 1))}
              disabled={currentPage === 0 || loading}
              className="btn btn-secondary"
              style={{ padding: '4px 8px', fontSize: '0.8rem' }}
            >
              <ChevronLeft style={{ width: '14px', height: '14px' }} />
              Previous
            </button>
            <button
              onClick={() => setCurrentPage((p) => Math.min(totalPages - 1, p + 1))}
              disabled={currentPage >= totalPages - 1 || loading}
              className="btn btn-secondary"
              style={{ padding: '4px 8px', fontSize: '0.8rem' }}
            >
              Next
              <ChevronRight style={{ width: '14px', height: '14px' }} />
            </button>
          </div>
        </div>
      </div>
    </div>
  );
};
