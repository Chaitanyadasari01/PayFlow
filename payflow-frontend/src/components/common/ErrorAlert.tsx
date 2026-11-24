import React from 'react';
import { AlertCircle, RefreshCw, X } from 'lucide-react';

interface ErrorAlertProps {
  message: string;
  onRetry?: () => void;
  onDismiss?: () => void;
}

export const ErrorAlert: React.FC<ErrorAlertProps> = ({ message, onRetry, onDismiss }) => {
  if (!message) return null;

  return (
    <div
      style={{
        display: 'flex',
        alignItems: 'center',
        justifyContent: 'space-between',
        padding: '0.85rem 1.15rem',
        borderRadius: '8px',
        backgroundColor: '#FEF2F2',
        border: '1px solid #FCA5A5',
        color: '#991B1B',
        marginBottom: '1rem',
        fontSize: '0.9rem',
      }}
    >
      <div style={{ display: 'flex', alignItems: 'center', gap: '0.65rem' }}>
        <AlertCircle style={{ width: '18px', height: '18px', flexShrink: 0, color: '#DC2626' }} />
        <span>{message}</span>
      </div>
      <div style={{ display: 'flex', alignItems: 'center', gap: '0.5rem' }}>
        {onRetry && (
          <button
            onClick={onRetry}
            style={{
              display: 'inline-flex',
              alignItems: 'center',
              gap: '4px',
              padding: '4px 10px',
              fontSize: '0.8rem',
              fontWeight: 600,
              color: '#991B1B',
              backgroundColor: '#FEE2E2',
              border: '1px solid #F87171',
              borderRadius: '6px',
              cursor: 'pointer',
            }}
          >
            <RefreshCw style={{ width: '12px', height: '12px' }} />
            Retry
          </button>
        )}
        {onDismiss && (
          <button
            onClick={onDismiss}
            style={{
              background: 'none',
              border: 'none',
              cursor: 'pointer',
              color: '#991B1B',
              padding: '2px',
              display: 'flex',
              alignItems: 'center',
            }}
          >
            <X style={{ width: '16px', height: '16px' }} />
          </button>
        )}
      </div>
    </div>
  );
};
