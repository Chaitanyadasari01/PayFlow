import React, { useState } from 'react';
import { Outlet } from 'react-router-dom';
import { Sidebar } from '../common/Sidebar';
import { Navbar } from '../common/Navbar';

export const DashboardLayout: React.FC = () => {
  const [sidebarOpen, setSidebarOpen] = useState(false);

  return (
    <div style={{ display: 'flex', minHeight: '100vh', backgroundColor: '#F8FAFC' }}>
      {/* Mobile backdrop */}
      {sidebarOpen && (
        <div
          onClick={() => setSidebarOpen(false)}
          style={{
            position: 'fixed',
            inset: 0,
            backgroundColor: 'rgba(15, 23, 42, 0.6)',
            zIndex: 35,
          }}
        />
      )}

      {/* Sidebar */}
      <Sidebar isOpen={sidebarOpen} onClose={() => setSidebarOpen(false)} />

      {/* Main Content Area */}
      <div style={{ flex: 1, display: 'flex', flexDirection: 'column', minWidth: 0 }}>
        <Navbar onToggleSidebar={() => setSidebarOpen((prev) => !prev)} />
        <main style={{ flex: 1, padding: '2rem', maxWidth: '1400px', width: '100%', margin: '0 auto' }}>
          <Outlet />
        </main>
      </div>
    </div>
  );
};
