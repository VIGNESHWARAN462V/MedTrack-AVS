import React, { useEffect, useState, useRef } from 'react';
import { useNavigate, useSearchParams, Link } from 'react-router-dom';
import { useAuth } from '../context/AuthContext';

export default function AuthCallback() {
  const [searchParams] = useSearchParams();
  const navigate = useNavigate();
  const { handleOAuthCallback } = useAuth();

  const [error, setError] = useState('');
  const [processing, setProcessing] = useState(true);
  const exchangeAttempted = useRef(false);

  useEffect(() => {
    const code = searchParams.get('code');

    if (!code) {
      setError('No authorization code provided in callback.');
      setProcessing(false);
      return;
    }

    if (exchangeAttempted.current) {
      return;
    }
    exchangeAttempted.current = true;

    async function exchangeToken() {
      try {
        await handleOAuthCallback(code);
        navigate('/dashboard', { replace: true });
      } catch (err) {
        setError(err.message || 'Failed to complete Google authentication. The authorization code may have expired.');
      } finally {
        setProcessing(false);
      }
    }

    exchangeToken();
  }, [searchParams, handleOAuthCallback, navigate]);

  return (
    <div className="min-vh-100 d-flex flex-column justify-content-center align-items-center py-5 px-3" style={{ backgroundColor: 'var(--surface)' }}>
      <div className="stitch-card p-5 text-center shadow-sm w-100" style={{ maxWidth: '440px' }}>
        {processing ? (
          <div>
            <div className="spinner-border text-teal mb-3" style={{ color: 'var(--primary)', width: '3rem', height: '3rem' }} role="status">
              <span className="visually-hidden">Loading...</span>
            </div>
            <h5 className="fw-bold mb-2">Authenticating with Google...</h5>
            <p className="text-muted small mb-0">Establishing secure clinical session and validating authorization tokens.</p>
          </div>
        ) : error ? (
          <div>
            <div className="rounded-circle d-inline-flex p-3 mb-3" style={{ backgroundColor: 'var(--error-container)', color: 'var(--on-error-container)' }}>
              <span className="material-symbols-outlined fs-2">error</span>
            </div>
            <h5 className="fw-bold text-danger mb-2">Authentication Failed</h5>
            <p className="text-muted small mb-4">{error}</p>
            <Link to="/login" className="btn btn-stitch-primary w-100 justify-content-center py-2">
              Return to Login
            </Link>
          </div>
        ) : null}
      </div>
    </div>
  );
}
