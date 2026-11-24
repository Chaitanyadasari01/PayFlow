import React from 'react';
import type { PaymentStatus, RefundStatus, WebhookStatus } from '../../types';

interface StatusBadgeProps {
  status: PaymentStatus | RefundStatus | WebhookStatus | string;
  size?: 'sm' | 'md';
}

export const StatusBadge: React.FC<StatusBadgeProps> = ({ status, size = 'md' }) => {
  const getBadgeStyle = (val: string) => {
    switch (val.toUpperCase()) {
      case 'SUCCESS':
      case 'DELIVERED':
        return {
          bg: '#E8F5E9',
          color: '#2E7D32',
          border: '#C8E6C9',
          dot: '#43A047',
          label: val === 'DELIVERED' ? 'Delivered' : 'Success',
        };
      case 'PARTIALLY_REFUNDED':
        return {
          bg: '#E1F5FE',
          color: '#0277BD',
          border: '#B3E5FC',
          dot: '#039BE5',
          label: 'Partially Refunded',
        };
      case 'REFUNDED':
        return {
          bg: '#EDE7F6',
          color: '#512DA8',
          border: '#D1C4E9',
          dot: '#673AB7',
          label: 'Refunded',
        };
      case 'REFUND_PENDING':
      case 'PROCESSING':
      case 'PENDING':
        return {
          bg: '#FFF8E1',
          color: '#F57F17',
          border: '#FFECB3',
          dot: '#FFB300',
          label: val.replace('_', ' '),
        };
      case 'FAILED':
        return {
          bg: '#FFEBEE',
          color: '#C62828',
          border: '#FFCDD2',
          dot: '#E53935',
          label: 'Failed',
        };
      default:
        return {
          bg: '#ECEFF1',
          color: '#455A64',
          border: '#CFD8DC',
          dot: '#78909C',
          label: val,
        };
    }
  };

  const style = getBadgeStyle(status);
  const isSm = size === 'sm';

  return (
    <span
      style={{
        display: 'inline-flex',
        alignItems: 'center',
        gap: '6px',
        padding: isSm ? '2px 8px' : '4px 10px',
        borderRadius: '9999px',
        fontSize: isSm ? '11px' : '12px',
        fontWeight: 600,
        backgroundColor: style.bg,
        color: style.color,
        border: `1px solid ${style.border}`,
        letterSpacing: '0.02em',
        textTransform: 'uppercase',
      }}
    >
      <span
        style={{
          width: isSm ? '6px' : '7px',
          height: isSm ? '6px' : '7px',
          borderRadius: '50%',
          backgroundColor: style.dot,
        }}
      />
      {style.label}
    </span>
  );
};
