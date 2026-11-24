import React, { useState, useEffect } from 'react';
import { useNavigate, useLocation, Link } from 'react-router-dom';
import { useAuth } from '../context/AuthContext';
import { authApi } from '../api/authApi';
import { getErrorMessage } from '../api/client';
import { ErrorAlert } from '../components/common/ErrorAlert';
import { Lock, Mail, ArrowRight, ShieldCheck } from 'lucide-react';

export const LoginPage: React.FC = () => {
  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const { login, isAuthenticated } = useAuth();
  const navigate = useNavigate();
  const location = useLocation();

  useEffect(() => {
    if (isAuthenticated) {
      navigate('/');
    }
    const params = new URLSearchParams(location.search);
    if (params.get('expired')) {
      setError('Your session has expired. Please sign in again.');
    }
  }, [isAuthenticated, navigate, location]);

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    setError(null);
    setLoading(true);

    try {
      const data = await authApi.login({ email, password });
      login(data.token, { email: data.email, fullName: data.fullName });
      navigate('/');
    } catch (err) {
      setError(getErrorMessage(err));
    } finally {
      setLoading(false);
    }
  };

  const setDemoUser = (userEmail: string) => {
    setEmail(userEmail);
    setPassword('SecurePass123!');
  };

  return (
    <div
      style={{
        minHeight: '100vh',
        display: 'flex',
        alignItems: 'center',
        justifyContent: 'center',
        backgroundColor: '#0B192C',
        padding: '1.5rem',
      }}
    >
      <div
        style={{
          width: '100%',
          maxWidth: '440px',
          backgroundColor: '#FFFFFF',
          borderRadius: '12px',
          padding: '2.5rem',
          boxShadow: '0 20px 25px -5px rgba(0, 0, 0, 0.3), 0 8px 10px -6px rgba(0, 0, 0, 0.2)',
        }}
      >
        {/* Brand */}
        <div style={{ textAlign: 'center', marginBottom: '2rem' }}>
          <div
            style={{
              width: '48px',
              height: '48px',
              borderRadius: '10px',
              backgroundColor: '#008DDA',
              color: '#FFFFFF',
              display: 'inline-flex',
              alignItems: 'center',
              justifyContent: 'center',
              fontWeight: 800,
              fontSize: '1.4rem',
              marginBottom: '0.75rem',
              boxShadow: '0 4px 12px rgba(0, 141, 218, 0.3)',
            }}
          >
            P
          </div>
          <h1 style={{ fontSize: '1.4rem', fontWeight: 800, color: '#0B192C' }}>
            Sign in to PayFlow
          </h1>
          <p style={{ fontSize: '0.88rem', color: '#64748B', marginTop: '0.25rem' }}>
            Payment Simulator & Idempotency Engine
          </p>
        </div>

        {error && <ErrorAlert message={error} onDismiss={() => setError(null)} />}

        <form onSubmit={handleSubmit}>
          <div className="form-group">
            <label className="form-label" htmlFor="email">
              Email Address
            </label>
            <div style={{ position: 'relative' }}>
              <Mail
                style={{
                  position: 'absolute',
                  left: '10px',
                  top: '50%',
                  transform: 'translateY(-50%)',
                  width: '16px',
                  height: '16px',
                  color: '#94A3B8',
                }}
              />
              <input
                id="email"
                type="email"
                required
                className="form-control"
                style={{ paddingLeft: '34px' }}
                placeholder="name@company.com"
                value={email}
                onChange={(e) => setEmail(e.target.value)}
              />
            </div>
          </div>

          <div className="form-group">
            <label className="form-label" htmlFor="password">
              Password
            </label>
            <div style={{ position: 'relative' }}>
              <Lock
                style={{
                  position: 'absolute',
                  left: '10px',
                  top: '50%',
                  transform: 'translateY(-50%)',
                  width: '16px',
                  height: '16px',
                  color: '#94A3B8',
                }}
              />
              <input
                id="password"
                type="password"
                required
                className="form-control"
                style={{ paddingLeft: '34px' }}
                placeholder="••••••••••••"
                value={password}
                onChange={(e) => setPassword(e.target.value)}
              />
            </div>
          </div>

          <button
            type="submit"
            disabled={loading}
            className="btn btn-primary"
            style={{ width: '100%', padding: '0.75rem', marginTop: '0.5rem', backgroundColor: '#1E3E62' }}
          >
            {loading ? 'Authenticating...' : 'Sign In'}
            {!loading && <ArrowRight style={{ width: '16px', height: '16px' }} />}
          </button>
        </form>

        {/* Quick Demo Pill */}
        <div
          style={{
            marginTop: '1.5rem',
            padding: '0.75rem',
            borderRadius: '6px',
            backgroundColor: '#F1F5F9',
            fontSize: '0.8rem',
            color: '#475569',
          }}
        >
          <div style={{ display: 'flex', alignItems: 'center', gap: '4px', fontWeight: 600, color: '#0B192C' }}>
            <ShieldCheck style={{ width: '14px', height: '14px', color: '#008DDA' }} />
            <span>Test Account Available</span>
          </div>
          <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', marginTop: '4px' }}>
            <span>user: <code>realuser@payflow.com</code></span>
            <button
              type="button"
              onClick={() => setDemoUser('realuser@payflow.com')}
              style={{
                background: 'none',
                border: 'none',
                color: '#008DDA',
                fontWeight: 600,
                fontSize: '0.75rem',
                cursor: 'pointer',
                textDecoration: 'underline',
              }}
            >
              Fill Demo
            </button>
          </div>
        </div>

        <div style={{ textAlign: 'center', marginTop: '1.5rem', fontSize: '0.85rem', color: '#64748B' }}>
          Don't have an account?{' '}
          <Link to="/register" style={{ color: '#008DDA', fontWeight: 600, textDecoration: 'none' }}>
            Register here
          </Link>
        </div>
      </div>
    </div>
  );
};
