import React from 'react';
import { Link, useNavigate } from 'react-router-dom';
import { useAuth } from '../../context/AuthContext';

export default function Header({
  toggleMobileSidebar,
  isMobileSidebarOpen = false,
  openAlertsModal,
  activeAlertCount = 0
}) {
  const { user, logout } = useAuth();
  const navigate = useNavigate();

  const handleLogout = async () => {
    await logout();
    navigate('/login', { replace: true });
  };

  // Derive display initials
  const displayName = user?.name || 'Staff User';
  const displayRole = user?.roles?.[0] || user?.role || 'STAFF';
  const initials = displayName
    .split(' ')
    .filter(Boolean)
    .map((n) => n[0])
    .slice(0, 2)
    .join('')
    .toUpperCase() || 'ST';

  return (
    <header className="app-header d-flex align-items-center justify-content-between px-3 px-lg-4">
      {/* Left: Hamburger & Brand */}
      <div className="d-flex align-items-center gap-2 gap-md-3">
        <button
          type="button"
          className="btn btn-sm btn-light border-0 d-lg-none d-flex align-items-center justify-content-center p-2 rounded-2"
          onClick={toggleMobileSidebar}
          aria-label="Open navigation"
          aria-expanded={isMobileSidebarOpen}
          aria-controls="app-sidebar-drawer"
        >
          <span className="material-symbols-outlined fs-5">menu</span>
        </button>

        <Link to="/dashboard" className="d-flex align-items-center gap-2 text-decoration-none">
          <img
            src="/medtrack-logo.svg"
            alt="MedTrack Logo"
            width="32"
            height="32"
            className="rounded-2"
          />
          <span className="fs-5 fw-bold text-teal d-none d-sm-inline" style={{ color: 'var(--primary)', letterSpacing: '-0.02em' }}>
            MedTrack <span style={{ color: 'var(--secondary)' }}>– AVS</span>
          </span>
        </Link>
      </div>

      {/* Middle: Universal Search Bar */}
      <div className="flex-grow-1 mx-3 mx-lg-5 d-none d-md-block" style={{ maxWidth: '440px' }}>
        <div className="stitch-search-wrapper">
          <span className="material-symbols-outlined stitch-search-icon fs-5">search</span>
          <input
            type="text"
            className="stitch-search-input"
            placeholder="Search assets, telemetry, locations, or staff..."
          />
        </div>
      </div>

      {/* Right: Quick Links, Notification Bell, User Avatar, Logout */}
      <div className="d-flex align-items-center gap-2 gap-sm-3">
        <Link
          to="/about"
          className="btn btn-sm d-none d-xl-inline-flex align-items-center gap-1 text-decoration-none px-2 py-1 rounded-2"
          style={{ color: 'var(--on-surface-variant)', fontSize: '0.85rem', fontWeight: 500 }}
        >
          <span className="material-symbols-outlined fs-6">info</span>
          About Project
        </Link>

        {/* Alerts Bell */}
        <button
          type="button"
          onClick={openAlertsModal}
          className="btn btn-sm btn-light rounded-circle position-relative p-2 d-flex align-items-center justify-content-center"
          title={activeAlertCount > 0 ? `${activeAlertCount} active system alerts` : 'No active alerts'}
          style={{ width: '38px', height: '38px', backgroundColor: 'var(--surface-container-low)' }}
        >
          <span className="material-symbols-outlined fs-5 text-secondary">notifications</span>
          {activeAlertCount > 0 && (
            <span
              className="position-absolute top-0 start-100 translate-middle badge rounded-pill"
              style={{ backgroundColor: 'var(--error)', fontSize: '0.65rem' }}
            >
              {activeAlertCount}
            </span>
          )}
        </button>

        {/* User Profile */}
        <div className="d-flex align-items-center gap-2 ps-1">
          {user?.profileImage ? (
            <img
              src={user.profileImage}
              alt={displayName}
              className="rounded-circle shadow-sm"
              style={{ width: '38px', height: '38px', objectFit: 'cover' }}
            />
          ) : (
            <div
              className="rounded-circle d-flex align-items-center justify-content-center fw-bold shadow-sm"
              style={{
                width: '38px',
                height: '38px',
                backgroundColor: 'var(--primary-container)',
                color: 'var(--on-primary)',
                fontSize: '0.85rem'
              }}
            >
              {initials}
            </div>
          )}
          <div className="d-none d-lg-block text-start lh-1">
            <div className="fw-semibold text-dark" style={{ fontSize: '0.85rem' }}>{displayName}</div>
            <small style={{ color: 'var(--outline)', fontSize: '0.725rem' }}>{displayRole}</small>
          </div>

          {/* Logout Button */}
          <button
            type="button"
            onClick={handleLogout}
            className="btn btn-sm btn-light rounded-2 p-1 d-flex align-items-center justify-content-center ms-1 text-muted"
            title="Sign Out"
            style={{ backgroundColor: 'transparent' }}
          >
            <span className="material-symbols-outlined fs-5">logout</span>
          </button>
        </div>
      </div>
    </header>
  );
}
