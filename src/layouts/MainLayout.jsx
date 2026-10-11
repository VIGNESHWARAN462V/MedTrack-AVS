import React, { useState, useEffect } from 'react';
import { Outlet, useNavigate } from 'react-router-dom';
import Header from '../components/common/Header';
import Sidebar from '../components/common/Sidebar';
import AssetDetailModal from '../components/assets/AssetDetailModal';
import { assetService } from '../services/assetService';
import { alertService } from '../services/alertService';
import SEO from '../components/common/SEO';

export default function MainLayout() {
  const [mobileSidebarOpen, setMobileSidebarOpen] = useState(false);
  const [activeAssetModal, setActiveAssetModal] = useState(null);
  const [activeAlertCount, setActiveAlertCount] = useState(0);
  const navigate = useNavigate();

  useEffect(() => {
    let isMounted = true;
    async function loadAlerts() {
      try {
        const alerts = await alertService.getAlerts({ resolved: false });
        if (isMounted && Array.isArray(alerts)) {
          setActiveAlertCount(alerts.length);
        }
      } catch (err) {
        // Non-blocking for layout
      }
    }
    loadAlerts();
    return () => { isMounted = false; };
  }, []);

  // Dynamically load Material Symbols font for application console routes
  useEffect(() => {
    if (typeof document !== 'undefined' && !document.querySelector('link[data-material-symbols]')) {
      const link = document.createElement('link');
      link.rel = 'stylesheet';
      link.href = 'https://fonts.googleapis.com/css2?family=Material+Symbols+Outlined:opsz,wght,FILL,GRAD@24,400,0..1,0&display=swap';
      link.setAttribute('data-material-symbols', 'true');
      document.head.appendChild(link);
    }
  }, []);

  const handleSelectAsset = (asset) => {
    setActiveAssetModal(asset);
  };

  const handleSelectAssetById = async (assetId) => {
    try {
      const data = await assetService.getAssetById(assetId);
      setActiveAssetModal(data);
    } catch (err) {
      console.error('Failed to load asset', err);
    }
  };

  const handleTransfer = (asset) => {
    navigate('/transfers', { state: { targetAsset: asset } });
  };

  return (
    <div className="app-shell min-vh-100 position-relative">
      <SEO title="Hospital Asset Tracking Console | MedTrack – AVS" noindex={true} />
      <Header
        toggleMobileSidebar={() => setMobileSidebarOpen(!mobileSidebarOpen)}
        isMobileSidebarOpen={mobileSidebarOpen}
        openAlertsModal={() => navigate('/alerts')}
        activeAlertCount={activeAlertCount}
      />

      <div className="app-main-layout">
        <Sidebar
          showMobile={mobileSidebarOpen}
          closeMobileSidebar={() => setMobileSidebarOpen(false)}
          activeAlertCount={activeAlertCount}
        />

        <main className="app-content">
          <div className="container-fluid p-3 p-md-4">
            <Outlet context={{ onSelectAsset: handleSelectAsset, onSelectAssetById: handleSelectAssetById }} />
          </div>
        </main>
      </div>

      {/* Global Asset Detail Modal */}
      {activeAssetModal && (
        <AssetDetailModal
          asset={activeAssetModal}
          onClose={() => setActiveAssetModal(null)}
          onTransfer={handleTransfer}
        />
      )}
    </div>
  );
}
