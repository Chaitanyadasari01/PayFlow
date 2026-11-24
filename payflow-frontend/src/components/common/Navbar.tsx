import React from 'react';
import { useNavigate } from 'react-router-dom';
import { useAuth } from '../../context/AuthContext';
import { Menu, Plus, LogOut, User as UserIcon } from 'lucide-react';

interface NavbarProps {
  onToggleSidebar: () => void;
}

export const Navbar: React.FC<NavbarProps> = ({ onToggleSidebar }) => {
  const { user, logout } = useAuth();
  const navigate = useNavigate();

  const handleLogout = () => {
    logout();
    navigate('/login');
  };

  return (
    <header
      style={{
        height: '64px',
        backgroundColor: '#FFFFFF',
        borderBottom: '1px solid #E2E8F0',
        display: 'flex',
        alignItems: 'center',
        justifyContent: 'space-between',
        padding: '0 1.5rem',
        position: 'sticky',
        top: 0,
        zIndex: 30,
      }}
    >
      {/* Left: Mobile hamburger */}
      <div style={{ display: 'flex', alignItems: 'center', gap: '1rem' }}>
        <button
          onClick={onToggleSidebar}
          className="mobile-menu-btn"
          style={{
            background: 'none',
            border: 'none',
            color: '#475569',
            cursor: 'pointer',
            padding: '6px',
            display: 'none',
          }}
          aria-label="Toggle Navigation"
        >
          <Menu style={{ width: '22px', height: '22px' }} />
        </button>

        <div style={{ display: 'flex', alignItems: 'center', gap: '8px' }}>
          <span
            style={{
              width: '8px',
              height: '8px',
              borderRadius: '50%',
              backgroundColor: '#10B981',
              boxShadow: '0 0 0 2px rgba(16, 185, 129, 0.2)',
            }}
          />
          <span style={{ fontSize: '0.8rem', color: '#64748B', fontWeight: 500 }}>
            API Connected (port 8080)
          </span>
        </div>
      </div>

      {/* Right: Actions & User Info */}
      <div style={{ display: 'flex', alignItems: 'center', gap: '1rem' }}>
        <button
          onClick={() => navigate('/payments/new')}
          style={{
            display: 'inline-flex',
            alignItems: 'center',
            gap: '6px',
            backgroundColor: '#008DDA',
            color: '#FFFFFF',
            border: 'none',
            borderRadius: '6px',
            padding: '0.45rem 0.9rem',
            fontSize: '0.85rem',
            fontWeight: 600,
            cursor: 'pointer',
            transition: 'background-color 0.15s ease',
          }}
          onMouseOver={(e) => (e.currentTarget.style.backgroundColor = '#0070AA')}
          onMouseOut={(e) => (e.currentTarget.style.backgroundColor = '#008DDA')}
        >
          <Plus style={{ width: '16px', height: '16px' }} />
          <span>New Payment</span>
        </button>

        <div style={{ height: '24px', width: '1px', backgroundColor: '#E2E8F0' }} />

        {/* User Badge */}
        <div style={{ display: 'flex', alignItems: 'center', gap: '10px' }}>
          <div
            style={{
              width: '34px',
              height: '34px',
              borderRadius: '50%',
              backgroundColor: '#E0F2FE',
              color: '#0369A1',
              display: 'flex',
              alignItems: 'center',
              justifyContent: 'center',
              fontWeight: 700,
              fontSize: '0.85rem',
            }}
          >
            {user?.fullName ? user.fullName.charAt(0).toUpperCase() : <UserIcon style={{ width: '16px', height: '16px' }} />}
          </div>
          <div className="user-details" style={{ display: 'flex', flexDirection: 'column' }}>
            <span style={{ fontSize: '0.85rem', fontWeight: 600, color: '#1E293B' }}>
              {user?.fullName || 'User'}
            </span>
            <span style={{ fontSize: '0.72rem', color: '#64748B' }}>
              {user?.email}
            </span>
          </div>
        </div>

        <button
          onClick={handleLogout}
          title="Sign out"
          style={{
            background: 'none',
            border: 'none',
            color: '#64748B',
            cursor: 'pointer',
            padding: '6px',
            borderRadius: '6px',
            display: 'flex',
            alignItems: 'center',
            transition: 'color 0.15s ease',
          }}
          onMouseOver={(e) => (e.currentTarget.style.color = '#EF4444')}
          onMouseOut={(e) => (e.currentTarget.style.color = '#64748B')}
        >
          <LogOut style={{ width: '18px', height: '18px' }} />
        </button>
      </div>
    </header>
  );
};
