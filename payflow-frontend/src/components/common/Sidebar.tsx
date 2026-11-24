import React from 'react';
import { NavLink } from 'react-router-dom';
import {
  LayoutDashboard,
  CreditCard,
  PlusCircle,
  Webhook,
  ExternalLink,
  ShieldCheck,
} from 'lucide-react';

interface SidebarProps {
  isOpen: boolean;
  onClose?: () => void;
}

export const Sidebar: React.FC<SidebarProps> = ({ isOpen, onClose }) => {
  const navItems = [
    { label: 'Dashboard', path: '/', icon: LayoutDashboard },
    { label: 'Transactions', path: '/payments', icon: CreditCard },
    { label: 'New Payment', path: '/payments/new', icon: PlusCircle },
    { label: 'Webhook Events', path: '/webhooks', icon: Webhook },
  ];

  return (
    <aside
      className={`sidebar-container ${isOpen ? 'open' : ''}`}
      style={{
        width: '260px',
        backgroundColor: '#0B192C',
        color: '#E2E8F0',
        display: 'flex',
        flexDirection: 'column',
        height: '100vh',
        position: 'sticky',
        top: 0,
        zIndex: 40,
        flexShrink: 0,
        borderRight: '1px solid rgba(255, 255, 255, 0.08)',
      }}
    >
      {/* Brand Header */}
      <div
        style={{
          padding: '1.5rem 1.25rem',
          display: 'flex',
          alignItems: 'center',
          gap: '10px',
          borderBottom: '1px solid rgba(255, 255, 255, 0.08)',
        }}
      >
        <div
          style={{
            width: '36px',
            height: '36px',
            borderRadius: '8px',
            backgroundColor: '#008DDA',
            display: 'flex',
            alignItems: 'center',
            justifyContent: 'center',
            color: '#FFFFFF',
            fontWeight: 800,
            fontSize: '1.1rem',
            boxShadow: '0 2px 8px rgba(0, 141, 218, 0.4)',
          }}
        >
          P
        </div>
        <div>
          <div style={{ fontSize: '1.15rem', fontWeight: 700, color: '#FFFFFF', letterSpacing: '-0.01em' }}>
            PayFlow
          </div>
          <div style={{ fontSize: '0.72rem', color: '#94A3B8', textTransform: 'uppercase', letterSpacing: '0.06em' }}>
            Payment Simulator
          </div>
        </div>
      </div>

      {/* Navigation */}
      <nav style={{ padding: '1.25rem 0.75rem', flex: 1, display: 'flex', flexDirection: 'column', gap: '4px' }}>
        <div style={{ padding: '0 0.5rem 0.5rem', fontSize: '0.72rem', fontWeight: 600, color: '#64748B', textTransform: 'uppercase', letterSpacing: '0.08em' }}>
          Menu
        </div>
        {navItems.map((item) => {
          const Icon = item.icon;
          return (
            <NavLink
              key={item.path}
              to={item.path}
              onClick={onClose}
              end={item.path === '/'}
              style={({ isActive }) => ({
                display: 'flex',
                alignItems: 'center',
                gap: '12px',
                padding: '0.65rem 0.85rem',
                borderRadius: '8px',
                fontSize: '0.88rem',
                fontWeight: isActive ? 600 : 500,
                color: isActive ? '#FFFFFF' : '#94A3B8',
                backgroundColor: isActive ? '#1E3E62' : 'transparent',
                textDecoration: 'none',
                transition: 'all 0.15s ease',
              })}
            >
              <Icon style={{ width: '18px', height: '18px' }} />
              <span>{item.label}</span>
            </NavLink>
          );
        })}

        <div style={{ margin: '1.5rem 0 0.5rem', padding: '0 0.5rem 0.5rem', fontSize: '0.72rem', fontWeight: 600, color: '#64748B', textTransform: 'uppercase', letterSpacing: '0.08em', borderTop: '1px solid rgba(255, 255, 255, 0.08)', paddingTop: '1rem' }}>
          Developer Tools
        </div>
        <a
          href="http://localhost:8080/swagger-ui/index.html"
          target="_blank"
          rel="noopener noreferrer"
          style={{
            display: 'flex',
            alignItems: 'center',
            justifyContent: 'space-between',
            padding: '0.65rem 0.85rem',
            borderRadius: '8px',
            fontSize: '0.88rem',
            color: '#94A3B8',
            textDecoration: 'none',
            transition: 'all 0.15s ease',
          }}
        >
          <div style={{ display: 'flex', alignItems: 'center', gap: '12px' }}>
            <ExternalLink style={{ width: '18px', height: '18px' }} />
            <span>Swagger API Docs</span>
          </div>
        </a>
      </nav>

      {/* Simulator Info Footer */}
      <div
        style={{
          padding: '1rem',
          margin: '0.75rem',
          borderRadius: '8px',
          backgroundColor: 'rgba(30, 62, 98, 0.4)',
          border: '1px solid rgba(255, 255, 255, 0.06)',
          fontSize: '0.78rem',
          color: '#94A3B8',
        }}
      >
        <div style={{ display: 'flex', alignItems: 'center', gap: '6px', color: '#38BDF8', fontWeight: 600, marginBottom: '4px' }}>
          <ShieldCheck style={{ width: '14px', height: '14px' }} />
          <span>Simulation Engine</span>
        </div>
        <div style={{ lineHeight: 1.4 }}>
          Safe sandbox. Amounts with <code>.99</code> decline, <code>.77</code> timeout, <code>&gt;$10k</code> over limit.
        </div>
      </div>
    </aside>
  );
};
