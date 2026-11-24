import React from 'react';
import { Loader2 } from 'lucide-react';

interface LoadingSpinnerProps {
  message?: string;
  fullPage?: boolean;
}

export const LoadingSpinner: React.FC<LoadingSpinnerProps> = ({
  message = 'Loading data...',
  fullPage = false,
}) => {
  const content = (
    <div
      style={{
        display: 'flex',
        flexDirection: 'column',
        alignItems: 'center',
        justifyContent: 'center',
        padding: '3rem 1rem',
        color: '#475569',
      }}
    >
      <Loader2
        style={{
          width: '32px',
          height: '32px',
          animation: 'spin 1s linear infinite',
          color: '#1E3E62',
        }}
      />
      {message && (
        <p style={{ marginTop: '0.75rem', fontSize: '0.9rem', fontWeight: 500 }}>
          {message}
        </p>
      )}
    </div>
  );

  if (fullPage) {
    return (
      <div
        style={{
          display: 'flex',
          alignItems: 'center',
          justifyContent: 'center',
          minHeight: '60vh',
          width: '100%',
        }}
      >
        {content}
      </div>
    );
  }

  return content;
};
