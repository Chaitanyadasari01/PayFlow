import React, { useState, useEffect } from 'react';
import { useNavigate, Link } from 'react-router-dom';
import { useAuth } from '../context/AuthContext';
import { authApi } from '../api/authApi';
import { getErrorMessage } from '../api/client';
import { ErrorAlert } from '../components/common/ErrorAlert';
import { Lock, Mail, User, ArrowRight } from 'lucide-react';

export const RegisterPage: React.FC = () => {
  const [fullName, setFullName] = useState('');
  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const { login, isAuthenticated } = useAuth();
  const navigate = useNavigate();

  useEffect(() => {
    if (isAuthenticated) {
      navigate('/');
    }
  }, [isAuthenticated, navigate]);

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    setError(null);

    if (password.length < 8) {
      setError('Password must be at least 8 characters long.');
      return;
    }

    setLoading(true);

    try {
      const data = await authApi.register({ email, password, fullName });
      login(data.token, { email: data.email, fullName: data.fullName });
      navigate('/');
    } catch (err) {
      setError(getErrorMessage(err));
    } finally {
      setLoading(false);
    }
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
          maxWidth: '460px',
          backgroundColor: '#FFFFFF',
          borderRadius: '12px',
          padding: '2.5rem',
          boxShadow: '0 20px 25px -5px rgba(0, 0, 0, 0.3), 0 8px 10px -6px rgba(0, 0, 0, 0.2)',
        }}
      >
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
            Create your account
          </h1>
          <p style={{ fontSize: '0.88rem', color: '#64748B', marginTop: '0.25rem' }}>
            Get started with simulated payments
          </p>
        </div>

        {error && <ErrorAlert message={error} onDismiss={() => setError(null)} />}

        <form onSubmit={handleSubmit}>
          <div className="form-group">
            <label className="form-label" htmlFor="fullName">
              Full Name
            </label>
            <div style={{ position: 'relative' }}>
              <User
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
                id="fullName"
                type="text"
                required
                className="form-control"
                style={{ paddingLeft: '34px' }}
                placeholder="Jane Doe"
                value={fullName}
                onChange={(e) => setFullName(e.target.value)}
              />
            </div>
          </div>

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
                placeholder="jane@company.com"
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
                placeholder="Minimum 8 characters"
                value={password}
                onChange={(e) => setPassword(e.target.value)}
              />
            </div>
            <div className="form-help">Must be at least 8 characters long</div>
          </div>

          <button
            type="submit"
            disabled={loading}
            className="btn btn-primary"
            style={{ width: '100%', padding: '0.75rem', marginTop: '0.5rem', backgroundColor: '#1E3E62' }}
          >
            {loading ? 'Creating Account...' : 'Register'}
            {!loading && <ArrowRight style={{ width: '16px', height: '16px' }} />}
          </button>
        </form>

        <div style={{ textAlign: 'center', marginTop: '1.5rem', fontSize: '0.85rem', color: '#64748B' }}>
          Already have an account?{' '}
          <Link to="/login" style={{ color: '#008DDA', fontWeight: 600, textDecoration: 'none' }}>
            Sign in
          </Link>
        </div>
      </div>
    </div>
  );
};
