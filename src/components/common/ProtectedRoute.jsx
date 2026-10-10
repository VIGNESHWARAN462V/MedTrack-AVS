import React from 'react';
import { Navigate, useLocation } from 'react-router-dom';
import { useAuth } from '../../context/AuthContext';

export default function ProtectedRoute({ children, allowedRoles }) {
  const { isAuthenticated, loading, roles } = useAuth();
  const location = useLocation();

  if (loading) {
    return (
      <div className="min-vh-100 d-flex justify-content-center align-items-center" style={{ backgroundColor: 'var(--surface)' }}>
        <div className="text-center">
          <div className="spinner-border text-teal mb-3" style={{ color: 'var(--primary)', width: '2.5rem', height: '2.5rem' }} role="status">
            <span className="visually-hidden">Loading session...</span>
          </div>
          <div className="text-muted small">Verifying credentials...</div>
        </div>
      </div>
    );
  }

  if (!isAuthenticated) {
    return <Navigate to="/login" state={{ from: location }} replace />;
  }

  if (allowedRoles && allowedRoles.length > 0) {
    const hasRole = roles.some((r) => allowedRoles.includes(r));
    if (!hasRole) {
      return (
        <div className="container py-5 text-center">
          <div className="stitch-card p-5 mx-auto" style={{ maxWidth: '500px' }}>
            <span className="material-symbols-outlined text-danger fs-1 mb-3">lock</span>
            <h4 className="fw-bold mb-2">Access Restricted</h4>
            <p className="text-muted small mb-4">
              Your account does not have sufficient permissions to view this section.
            </p>
          </div>
        </div>
      );
    }
  }

  return children;
}
