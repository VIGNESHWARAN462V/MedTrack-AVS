import React, { useState } from 'react';
import { Link, useNavigate, useLocation } from 'react-router-dom';
import { useAuth } from '../context/AuthContext';
import { authService } from '../services/authService';
import SEO from '../components/common/SEO';

export default function Login() {
  const { login } = useAuth();
  const navigate = useNavigate();
  const location = useLocation();

  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const [error, setError] = useState('');
  const [loading, setLoading] = useState(false);

  // Return to intended page or default to /dashboard
  const from = (location.state?.from?.pathname && location.state.from.pathname !== '/') 
    ? location.state.from.pathname 
    : '/dashboard';

  const handleSubmit = async (e) => {
    e.preventDefault();
    setError('');

    if (!email || !password) {
      setError('Please provide both email and password.');
      return;
    }

    try {
      setLoading(true);
      await login(email, password);
      navigate(from, { replace: true });
    } catch (err) {
      setError(err.message || 'Authentication failed. Please check your credentials.');
    } finally {
      setLoading(false);
    }
  };

  const handleGoogleSignIn = () => {
    // Start real Spring Security OAuth2 authorization flow
    window.location.href = authService.getGoogleOAuthUrl();
  };

  return (
    <div className="min-vh-100 d-flex flex-column justify-content-center align-items-center py-5 px-3" style={{ backgroundColor: 'var(--surface)' }}>
      <SEO title="Sign In | MedTrack – AVS" description="Sign in to MedTrack – AVS Hospital Asset Tracking Console." noindex={true} />
      {/* Brand Header */}
      <div className="text-center mb-4">
        <div className="d-inline-flex align-items-center justify-content-center p-3 rounded-4 shadow-sm mb-3" style={{ backgroundColor: 'var(--surface-container-lowest)', border: '1px solid var(--surface-variant)' }}>
          <img src="/medtrack-logo.svg" alt="MedTrack Logo" width="48" height="48" />
        </div>
        <h2 className="fw-bold mb-1" style={{ color: 'var(--primary)', letterSpacing: '-0.02em' }}>
          MedTrack <span style={{ color: 'var(--secondary)' }}>– AVS</span>
        </h2>
        <p className="text-muted small mb-0">Hospital Asset Tracking & Geolocation Console</p>
      </div>

      {/* Login Card */}
      <div className="stitch-card p-4 p-sm-5 w-100 shadow-sm" style={{ maxWidth: '440px' }}>
        <div className="mb-4 text-center">
          <h4 className="fw-bold mb-1">Sign In</h4>
          <p className="text-muted small">Enter your hospital credentials to access the console</p>
        </div>

        {error && (
          <div className="alert alert-danger d-flex align-items-center gap-2 p-3 rounded-3 small mb-3 border-0" role="alert" style={{ backgroundColor: 'var(--error-container)', color: 'var(--on-error-container)' }}>
            <span className="material-symbols-outlined fs-5">error</span>
            <span>{error}</span>
          </div>
        )}

        {/* Real Google Sign-In Button */}
        <button
          type="button"
          onClick={handleGoogleSignIn}
          className="btn btn-outline-secondary w-100 py-2 d-flex align-items-center justify-content-center gap-2 rounded-3 mb-3 fw-semibold"
          style={{ borderColor: 'var(--outline-variant)', color: 'var(--on-surface)', backgroundColor: 'var(--surface-container-lowest)' }}
        >
          <svg width="18" height="18" viewBox="0 0 24 24">
            <path fill="#4285F4" d="M22.56 12.25c0-.78-.07-1.53-.2-2.25H12v4.26h5.92c-.26 1.37-1.04 2.53-2.21 3.31v2.77h3.57c2.08-1.92 3.28-4.74 3.28-8.09z" />
            <path fill="#34A853" d="M12 23c2.97 0 5.46-.98 7.28-2.66l-3.57-2.77c-.98.66-2.23 1.06-3.71 1.06-2.86 0-5.29-1.93-6.16-4.53H2.18v2.84C3.99 20.53 7.7 23 12 23z" />
            <path fill="#FBBC05" d="M5.84 14.09c-.22-.66-.35-1.36-.35-2.09s.13-1.43.35-2.09V7.06H2.18C1.43 8.55 1 10.22 1 12s.43 3.45 1.18 4.94l2.85-2.22.81-.63z" />
            <path fill="#EA4335" d="M12 5.38c1.62 0 3.06.56 4.21 1.64l3.15-3.15C17.45 2.09 14.97 1 12 1 7.7 1 3.99 3.47 2.18 7.06l3.66 2.84c.87-2.6 3.3-4.52 6.16-4.52z" />
          </svg>
          Sign in with Google
        </button>

        <div className="d-flex align-items-center my-3">
          <hr className="flex-grow-1 my-0 text-muted opacity-25" />
          <span className="px-2 small text-muted text-uppercase" style={{ fontSize: '0.72rem', letterSpacing: '0.05em' }}>or email</span>
          <hr className="flex-grow-1 my-0 text-muted opacity-25" />
        </div>

        {/* Local Email/Password Form */}
        <form onSubmit={handleSubmit} noValidate>
          <div className="mb-3">
            <label className="form-label small fw-semibold" htmlFor="emailInput">
              Hospital Email
            </label>
            <input
              id="emailInput"
              type="email"
              className="form-control stitch-search-input"
              style={{ paddingLeft: '14px' }}
              placeholder="e.g. operator@hospital.org"
              value={email}
              onChange={(e) => setEmail(e.target.value)}
              required
              autoFocus
            />
          </div>

          <div className="mb-4">
            <label className="form-label small fw-semibold" htmlFor="passwordInput">
              Password
            </label>
            <input
              id="passwordInput"
              type="password"
              className="form-control stitch-search-input"
              style={{ paddingLeft: '14px' }}
              placeholder="Enter your password"
              value={password}
              onChange={(e) => setPassword(e.target.value)}
              required
            />
          </div>

          <button
            type="submit"
            className="btn btn-stitch-primary w-100 justify-content-center py-2 fs-6 shadow-sm"
            disabled={loading}
          >
            {loading ? (
              <>
                <span className="spinner-border spinner-border-sm me-2" role="status" aria-hidden="true" />
                Signing In...
              </>
            ) : (
              'Sign In'
            )}
          </button>
        </form>

        <div className="mt-4 pt-2 text-center border-top">
          <p className="text-muted small mb-0">
            Don't have an account?{' '}
            <Link to="/register" className="fw-semibold text-decoration-none" style={{ color: 'var(--primary)' }}>
              Register Here
            </Link>
          </p>
        </div>
      </div>
    </div>
  );
}
