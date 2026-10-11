import React, { useEffect } from 'react';
import { NavLink } from 'react-router-dom';

const baseNavItems = [
  { path: '/dashboard', label: 'Dashboard', icon: 'dashboard' },
  { path: '/assets', label: 'Assets', icon: 'inventory_2' },
  { path: '/live-tracking', label: 'Live Tracking', icon: 'location_searching' },
  { path: '/scan-qr', label: 'Scan QR', icon: 'qr_code_scanner' },
  { path: '/transfers', label: 'Transfers', icon: 'swap_horiz' },
  { path: '/analytics', label: 'Analytics', icon: 'analytics' },
  { path: '/alerts', label: 'Alerts', icon: 'notifications_active', isAlertItem: true },
  { path: '/ai-assistant', label: 'AI Assistant', icon: 'smart_toy' },
  { path: '/about', label: 'About Project', icon: 'info' },
  { path: '/settings', label: 'Settings', icon: 'settings' },
];

export default function Sidebar({ showMobile, closeMobileSidebar, activeAlertCount = 0 }) {
  // Body scroll lock and Escape key listener when mobile drawer is open
  useEffect(() => {
    if (!showMobile) return;

    const originalOverflow = document.body.style.overflow;
    document.body.style.overflow = 'hidden';

    const handleKeyDown = (event) => {
      if (event.key === 'Escape') {
        closeMobileSidebar();
      }
    };

    window.addEventListener('keydown', handleKeyDown);

    return () => {
      document.body.style.overflow = originalOverflow;
      window.removeEventListener('keydown', handleKeyDown);
    };
  }, [showMobile, closeMobileSidebar]);

  // Automatically close mobile drawer when window resizes to desktop breakpoint (>= 992px)
  useEffect(() => {
    const handleResize = () => {
      if (window.innerWidth >= 992 && showMobile) {
        closeMobileSidebar();
      }
    };

    window.addEventListener('resize', handleResize);
    return () => window.removeEventListener('resize', handleResize);
  }, [showMobile, closeMobileSidebar]);

  return (
    <>
      {showMobile && (
        <div
          className="sidebar-backdrop d-lg-none"
          onClick={closeMobileSidebar}
          aria-hidden="true"
        />
      )}

      <aside
        id="app-sidebar-drawer"
        aria-label="Application navigation"
        className={`app-sidebar p-3 d-flex flex-column ${showMobile ? 'show-mobile' : ''}`}
      >
        <div className="d-flex flex-column flex-grow-1">
          {/* Drawer Header: Title and Mobile Close Button */}
          <div className="d-flex align-items-center justify-content-between px-2 pb-2 mb-2 border-bottom">
            <div className="d-flex align-items-center gap-2">
              <img
                src="/medtrack-logo.svg"
                alt="MedTrack Logo"
                width="22"
                height="22"
                className="rounded-1 d-lg-none"
              />
              <span
                className="text-uppercase"
                style={{
                  fontSize: '0.72rem',
                  color: 'var(--outline)',
                  letterSpacing: '0.08em',
                  fontWeight: 700
                }}
              >
                Operational Console
              </span>
            </div>
            <button
              type="button"
              className="btn btn-sm btn-light border-0 d-lg-none d-flex align-items-center justify-content-center p-1 rounded-2 sidebar-close-btn"
              onClick={closeMobileSidebar}
              aria-label="Close navigation"
              title="Close navigation"
              style={{ width: '32px', height: '32px' }}
            >
              <span className="material-symbols-outlined fs-5">close</span>
            </button>
          </div>

          <nav className="d-flex flex-column" aria-label="Main Navigation">
            {baseNavItems.map((item) => {
              const showAlertBadge = item.isAlertItem && activeAlertCount > 0;
              return (
                <NavLink
                  key={item.path}
                  to={item.path}
                  end={item.path === '/dashboard'}
                  onClick={closeMobileSidebar}
                  className={({ isActive }) => `nav-stitch-link ${isActive ? 'active' : ''}`}
                  aria-current={({ isActive }) => (isActive ? 'page' : undefined)}
                >
                  <span className="material-symbols-outlined fs-5 flex-shrink-0">{item.icon}</span>
                  <span className="flex-grow-1 text-truncate pe-2">{item.label}</span>
                  {showAlertBadge && (
                    <span
                      className="badge rounded-pill flex-shrink-0"
                      style={{
                        backgroundColor: 'var(--error-container)',
                        color: 'var(--on-error-container)',
                        fontSize: '0.7rem',
                        padding: '0.25em 0.55em'
                      }}
                    >
                      {activeAlertCount}
                    </span>
                  )}
                </NavLink>
              );
            })}
          </nav>
        </div>

        {/* Bottom Infrastructure Status Badge */}
        <div
          className="p-3 rounded-3 mt-3 mt-lg-auto flex-shrink-0"
          style={{
            backgroundColor: 'var(--surface-container-low)',
            border: '1px solid var(--surface-variant)'
          }}
        >
          <div className="d-flex align-items-center gap-2 mb-1">
            <span
              className="spinner-grow spinner-grow-sm text-success"
              role="status"
              style={{ width: '8px', height: '8px' }}
            />
            <span className="fw-semibold text-dark" style={{ fontSize: '0.785rem' }}>
              Telemetry Stream Active
            </span>
          </div>
          <div style={{ fontSize: '0.72rem', color: 'var(--outline)' }}>
            STOMP over WebSocket • Microsecond GPS sync verified.
          </div>
        </div>
      </aside>
    </>
  );
}
