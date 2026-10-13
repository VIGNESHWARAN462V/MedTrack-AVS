import React, { Suspense, lazy, useLayoutEffect } from 'react';
import { BrowserRouter, Routes, Route, Navigate } from 'react-router-dom';
import { AuthProvider } from './context/AuthContext';
import ProtectedRoute from './components/common/ProtectedRoute';
import LoadingFallback from './components/common/LoadingFallback';
const MainLayout = lazy(() => import('./layouts/MainLayout'));

// Public pages (Lightweight initial bundle)
import LandingPage from './pages/LandingPage';
import Login from './pages/Login';
const AboutPage = lazy(() => import('./pages/AboutPage'));
const Register = lazy(() => import('./pages/Register'));
const AuthCallback = lazy(() => import('./pages/AuthCallback'));

// Protected Application Console Routes (Lazy Loaded with Heavy Libraries Isolated)
const DashboardPage = lazy(() => import('./pages/DashboardPage'));
const AssetsPage = lazy(() => import('./pages/AssetsPage'));
const LiveTrackingPage = lazy(() => import('./pages/LiveTrackingPage'));
const QrScannerPage = lazy(() => import('./pages/QrScannerPage'));
const TransfersPage = lazy(() => import('./pages/TransfersPage'));
const AnalyticsPage = lazy(() => import('./pages/AnalyticsPage'));
const AlertsPage = lazy(() => import('./pages/AlertsPage'));
const SettingsPage = lazy(() => import('./pages/SettingsPage'));
const AiAssistantPage = lazy(() => import('./pages/AiAssistantPage'));

export default function App() {
  useLayoutEffect(() => {
    const shell = document.getElementById('initial-shell');
    if (shell) {
      shell.remove();
    }
  }, []);

  return (
    <BrowserRouter>
      <AuthProvider>
        <Suspense fallback={<LoadingFallback />}>
          <Routes>
            {/* Public Routes */}
            <Route path="/" element={<LandingPage />} />
            <Route path="/about" element={<AboutPage />} />
            <Route path="/login" element={<Login />} />
            <Route path="/register" element={<Register />} />
            <Route path="/auth/callback" element={<AuthCallback />} />

            {/* Protected Application Console Routes */}
            <Route
              element={
                <ProtectedRoute>
                  <MainLayout />
                </ProtectedRoute>
              }
            >
              <Route path="/dashboard" element={<DashboardPage />} />
              <Route path="/assets" element={<AssetsPage />} />
              <Route path="/live-tracking" element={<LiveTrackingPage />} />
              <Route path="/scan-qr" element={<QrScannerPage />} />
              <Route path="/transfers" element={<TransfersPage />} />
              <Route path="/analytics" element={<AnalyticsPage />} />
              <Route path="/alerts" element={<AlertsPage />} />
              <Route path="/ai-assistant" element={<AiAssistantPage />} />
              <Route path="/settings" element={<SettingsPage />} />
            </Route>

            {/* Catch-all redirect to public landing page */}
            <Route path="*" element={<Navigate to="/" replace />} />
          </Routes>
        </Suspense>
      </AuthProvider>
    </BrowserRouter>
  );
}
