import React, { useState } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import { useAuth } from '../context/AuthContext';
import SEO from '../components/common/SEO';

export default function Register() {
  const { register } = useAuth();
  const navigate = useNavigate();

  const [name, setName] = useState('');
  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const [confirmPassword, setConfirmPassword] = useState('');
  const [error, setError] = useState('');
  const [success, setSuccess] = useState('');
  const [loading, setLoading] = useState(false);

  const handleSubmit = async (e) => {
    e.preventDefault();
    setError('');
    setSuccess('');

    if (!name || !email || !password) {
      setError('Please complete all required fields.');
      return;
    }

    if (password.length < 6) {
      setError('Password must be at least 6 characters.');
      return;
    }

    if (password !== confirmPassword) {
      setError('Passwords do not match.');
      return;
    }

    try {
      setLoading(true);
      await register(name, email, password);
      setSuccess('Account created successfully! Redirecting to login...');
      setTimeout(() => {
        navigate('/login', { state: { registeredEmail: email } });
      }, 1500);
    } catch (err) {
      setError(err.message || 'Registration failed. The email may already be in use.');
    } finally {
      setLoading(false);
    }
  };

  return (
    <main className="min-vh-100 d-flex flex-column justify-content-center align-items-center py-5 px-3" style={{ backgroundColor: 'var(--surface)' }}>
      <SEO
        title="Staff Registration | MedTrack – AVS"
        description="Register a hospital staff account for MedTrack – AVS."
        canonicalPath="/register"
        noindex={false}
      />
      {/* Brand Header */}
      <div className="text-center mb-4">
        <div className="d-inline-flex align-items-center justify-content-center p-3 rounded-4 shadow-sm mb-3" style={{ backgroundColor: 'var(--surface-container-lowest)', border: '1px solid var(--surface-variant)' }}>
          <img src="/medtrack-logo.svg" alt="MedTrack Logo" width="48" height="48" />
        </div>
        <h1 className="h2 fw-bold mb-1" style={{ color: 'var(--primary)', letterSpacing: '-0.02em' }}>
          MedTrack <span style={{ color: 'var(--secondary)' }}>– AVS</span>
        </h1>
        <p className="text-muted small mb-0">Hospital Staff Account Registration</p>
      </div>

      {/* Registration Card */}
      <div className="stitch-card p-4 p-sm-5 w-100 shadow-sm" style={{ maxWidth: '440px' }}>
        <div className="mb-4 text-center">
          <h2 className="h4 fw-bold mb-1">Create Account</h2>
          <p className="text-muted small">Standard clinical staff onboarding (STAFF role)</p>
        </div>

        {error && (
          <div className="alert alert-danger d-flex align-items-center gap-2 p-3 rounded-3 small mb-3 border-0" role="alert" style={{ backgroundColor: 'var(--error-container)', color: 'var(--on-error-container)' }}>
            <svg width="20" height="20" viewBox="0 0 24 24" fill="currentColor" aria-hidden="true"><path d="M12 2C6.48 2 2 6.48 2 12s4.48 10 10 10 10-4.48 10-10S17.52 2 12 2zm1 15h-2v-2h2v2zm0-4h-2V7h2v6z"/></svg>
            <span>{error}</span>
          </div>
        )}

        {success && (
          <div className="alert alert-success d-flex align-items-center gap-2 p-3 rounded-3 small mb-3 border-0" role="alert" style={{ backgroundColor: 'var(--success-container)', color: 'var(--on-success-container)' }}>
            <svg width="20" height="20" viewBox="0 0 24 24" fill="currentColor" aria-hidden="true"><path d="M12 2C6.48 2 2 6.48 2 12s4.48 10 10 10 10-4.48 10-10S17.52 2 12 2zm-2 15l-5-5 1.41-1.41L10 14.17l7.59-7.59L19 8l-9 9z"/></svg>
            <span>{success}</span>
          </div>
        )}

        <form onSubmit={handleSubmit} noValidate>
          <div className="mb-3">
            <label className="form-label small fw-semibold" htmlFor="nameInput">
              Full Name
            </label>
            <input
              id="nameInput"
              type="text"
              className="form-control stitch-search-input"
              style={{ paddingLeft: '14px' }}
              placeholder="e.g. Nurse Sarah Jenkins"
              value={name}
              onChange={(e) => setName(e.target.value)}
              required
              autoFocus
            />
          </div>

          <div className="mb-3">
            <label className="form-label small fw-semibold" htmlFor="regEmailInput">
              Hospital Email
            </label>
            <input
              id="regEmailInput"
              type="email"
              className="form-control stitch-search-input"
              style={{ paddingLeft: '14px' }}
              placeholder="e.g. s.jenkins@hospital.org"
              value={email}
              onChange={(e) => setEmail(e.target.value)}
              required
            />
          </div>

          <div className="mb-3">
            <label className="form-label small fw-semibold" htmlFor="regPasswordInput">
              Password
            </label>
            <input
              id="regPasswordInput"
              type="password"
              className="form-control stitch-search-input"
              style={{ paddingLeft: '14px' }}
              placeholder="Min. 6 characters"
              value={password}
              onChange={(e) => setPassword(e.target.value)}
              required
            />
          </div>

          <div className="mb-4">
            <label className="form-label small fw-semibold" htmlFor="confirmPasswordInput">
              Confirm Password
            </label>
            <input
              id="confirmPasswordInput"
              type="password"
              className="form-control stitch-search-input"
              style={{ paddingLeft: '14px' }}
              placeholder="Re-enter password"
              value={confirmPassword}
              onChange={(e) => setConfirmPassword(e.target.value)}
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
                Registering...
              </>
            ) : (
              'Create Account'
            )}
          </button>
        </form>

        <div className="mt-4 pt-2 text-center border-top">
          <p className="text-muted small mb-0">
            Already registered?{' '}
            <Link to="/login" className="fw-semibold text-decoration-none" style={{ color: 'var(--primary)' }}>
              Sign In Here
            </Link>
          </p>
        </div>
      </div>
    </main>
  );
}
