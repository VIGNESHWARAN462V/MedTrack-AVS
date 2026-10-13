import React, { useState, useMemo, useRef, useCallback, useEffect } from 'react';
import facilityService from '../../services/facilityService';

/**
 * Returns distinct department theme styling for rooms.
 */
function getDepartmentTheme(deptCode) {
  const code = (deptCode || '').toUpperCase();
  switch (code) {
    case 'ER':
      return {
        fill: 'rgba(239, 68, 68, 0.16)',
        stroke: '#dc2626',
        selectedStroke: '#991b1b',
        text: '#991b1b',
        badgeBg: '#fee2e2',
        badgeText: '#991b1b',
        name: 'Emergency',
      };
    case 'ICU':
      return {
        fill: 'rgba(245, 158, 11, 0.16)',
        stroke: '#d97706',
        selectedStroke: '#b45309',
        text: '#92400e',
        badgeBg: '#fef3c7',
        badgeText: '#92400e',
        name: 'Intensive Care',
      };
    case 'CARD':
      return {
        fill: 'rgba(59, 130, 246, 0.16)',
        stroke: '#2563eb',
        selectedStroke: '#1d4ed8',
        text: '#1e40af',
        badgeBg: '#dbeafe',
        badgeText: '#1e40af',
        name: 'Cardiology',
      };
    case 'RAD':
      return {
        fill: 'rgba(168, 85, 247, 0.16)',
        stroke: '#9333ea',
        selectedStroke: '#7e22ce',
        text: '#6b21a8',
        badgeBg: '#f3e8ff',
        badgeText: '#6b21a8',
        name: 'Radiology',
      };
    case 'GW':
      return {
        fill: 'rgba(20, 184, 166, 0.16)',
        stroke: '#0d9488',
        selectedStroke: '#0f766e',
        text: '#115e59',
        badgeBg: '#ccfbf1',
        badgeText: '#115e59',
        name: 'General Ward',
      };
    default:
      return {
        fill: 'rgba(100, 116, 139, 0.14)',
        stroke: '#64748b',
        selectedStroke: '#334155',
        text: '#334155',
        badgeBg: '#f1f5f9',
        badgeText: '#334155',
        name: 'Clinical Facility',
      };
  }
}

/**
 * Returns distinct asset status styling and colors for markers.
 */
export function getAssetStatusTheme(status) {
  switch ((status || '').toUpperCase()) {
    case 'AVAILABLE':
      return {
        fill: '#10b981', // emerald
        stroke: '#059669',
        pulse: 'rgba(16, 185, 129, 0.45)',
        badgeBg: '#d1fae5',
        badgeText: '#065f46',
        label: 'Available',
      };
    case 'IN_USE':
      return {
        fill: '#0ea5e9', // sky blue
        stroke: '#0284c7',
        pulse: 'rgba(14, 165, 233, 0.45)',
        badgeBg: '#e0f2fe',
        badgeText: '#0369a1',
        label: 'In Use',
      };
    case 'MAINTENANCE':
      return {
        fill: '#f59e0b', // amber
        stroke: '#d97706',
        pulse: 'rgba(245, 158, 11, 0.45)',
        badgeBg: '#fef3c7',
        badgeText: '#92400e',
        label: 'Maintenance',
      };
    case 'ASSIGNED':
      return {
        fill: '#8b5cf6', // violet
        stroke: '#7c3aed',
        pulse: 'rgba(139, 92, 246, 0.45)',
        badgeBg: '#ede9fe',
        badgeText: '#5b21b6',
        label: 'Assigned',
      };
    default:
      return {
        fill: '#64748b', // slate
        stroke: '#475569',
        pulse: 'rgba(100, 116, 139, 0.35)',
        badgeBg: '#f1f5f9',
        badgeText: '#334155',
        label: status || 'Unknown',
      };
  }
}

/**
 * Formats floor number into human friendly title.
 */
function formatFloorLabel(floor) {
  if (!floor) return '';
  if (floor.displayName) return floor.displayName;
  if (floor.floorNumber == null) return 'Floor';
  if (floor.floorNumber < 0) return `Basement (Level ${floor.floorNumber})`;
  if (floor.floorNumber === 0) return 'Ground Floor (Level 0)';
  return `Floor ${floor.floorNumber}`;
}

export function formatLocationStatusLabel(status) {
  switch (status) {
    case 'RECENT_CHECK_IN':
      return 'Recent QR Check-In';
    case 'CONFIGURED_INDOOR':
      return 'Configured Indoor Position';
    case 'VERIFIED_INDOOR':
      return 'Verified Indoor';
    case 'OUTDOOR_GPS':
      return 'Outdoor GPS';
    case 'LAST_KNOWN':
      return 'Last Known Position (Stale)';
    case 'LOCATION_UNAVAILABLE':
    default:
      return 'Location Unavailable';
  }
}

export function formatSourceLabel(source) {
  switch (source) {
    case 'QR_CHECKIN':
      return 'Clinical QR Check-In';
    case 'MANUAL_CONFIG':
      return 'Manual Configuration';
    case 'BLE_BEACON':
      return 'BLE Beacon Telemetry';
    case 'WIFI_POSITIONING':
      return 'Wi-Fi Positioning Telemetry';
    default:
      return source || 'Unspecified Source';
  }
}

export default function IndoorFloorMap({
  hospital,
  building,
  floors = [],
  selectedFloor,
  onSelectFloor = () => {},
  rooms = [],
  loadingRooms = false,
  selectedRoom = null,
  onSelectRoom = () => {},
  onBackToCampus = () => {},
  // Phase 6: Assets & Positioning Mode
  assets = [],
  selectedAsset = null,
  onSelectAsset = () => {},
  isPositioningMode = false,
  positioningAsset = null,
  onSaveIndoorPosition = () => {},
  onCancelPositioning = () => {},
}) {
  const containerRef = useRef(null);
  const svgRef = useRef(null);

  // Zoom & Pan state
  const [zoom, setZoom] = useState(1);
  const [pan, setPan] = useState({ x: 0, y: 0 });
  const [isDragging, setIsDragging] = useState(false);
  const [dragStart, setDragStart] = useState({ x: 0, y: 0 });
  const [imageError, setImageError] = useState(false);
  const [authenticatedBlobUrl, setAuthenticatedBlobUrl] = useState(null);
  const activeBlobUrlRef = useRef(null);

  // Manual positioning pending state
  const [pendingPoint, setPendingPoint] = useState(null);
  const [isSavingPosition, setIsSavingPosition] = useState(false);

  // Reset zoom & pan when floor changes and load authenticated floor plan blob
  useEffect(() => {
    setZoom(1);
    setPan({ x: 0, y: 0 });
    setImageError(false);

    let isCurrent = true;

    if (selectedFloor?.floorPlanImageUrl) {
      facilityService
        .fetchFloorPlanImageBlob(selectedFloor.id)
        .then((blob) => {
          if (!isCurrent) return;
          if (activeBlobUrlRef.current) {
            URL.revokeObjectURL(activeBlobUrlRef.current);
          }
          const objectUrl = URL.createObjectURL(blob);
          activeBlobUrlRef.current = objectUrl;
          setAuthenticatedBlobUrl(objectUrl);
        })
        .catch((err) => {
          if (!isCurrent) return;
          console.error('Failed to load authenticated floor plan image blob:', err);
          setImageError(true);
        });
    } else {
      if (activeBlobUrlRef.current) {
        URL.revokeObjectURL(activeBlobUrlRef.current);
        activeBlobUrlRef.current = null;
      }
      setAuthenticatedBlobUrl(null);
    }

    return () => {
      isCurrent = false;
      if (activeBlobUrlRef.current) {
        URL.revokeObjectURL(activeBlobUrlRef.current);
        activeBlobUrlRef.current = null;
      }
    };
  }, [selectedFloor?.id, selectedFloor?.floorPlanImageUrl]);

  // Reset pending positioning point when entering/leaving positioning mode or changing asset
  useEffect(() => {
    if (isPositioningMode && positioningAsset) {
      const initX = positioningAsset.indoorX ?? positioningAsset.indoorx ?? 50.0;
      const initY = positioningAsset.indoorY ?? positioningAsset.indoory ?? 50.0;
      const matchedRoom = rooms.find((rm) => {
        const rx = rm.xPosition ?? rm.xposition ?? 0;
        const ry = rm.yPosition ?? rm.yposition ?? 0;
        const rw = rm.width ?? 10;
        const rh = rm.height ?? 10;
        return (
          initX >= rx - 0.001 &&
          initX <= rx + rw + 0.001 &&
          initY >= ry - 0.001 &&
          initY <= ry + rh + 0.001
        );
      });
      setPendingPoint({
        x: initX,
        y: initY,
        room: matchedRoom || null,
      });
    } else {
      setPendingPoint(null);
      setIsSavingPosition(false);
    }
  }, [isPositioningMode, positioningAsset, rooms]);

  // SVG Canvas dimensions
  const canvasWidth = useMemo(() => {
    return selectedFloor?.floorPlanWidth && selectedFloor.floorPlanWidth > 200
      ? selectedFloor.floorPlanWidth
      : 1000;
  }, [selectedFloor]);

  const canvasHeight = useMemo(() => {
    return selectedFloor?.floorPlanHeight && selectedFloor.floorPlanHeight > 150
      ? selectedFloor.floorPlanHeight
      : 650;
  }, [selectedFloor]);



  // Zoom controls
  const handleZoomIn = () => setZoom((prev) => Math.min(prev + 0.25, 3.0));
  const handleZoomOut = () => setZoom((prev) => Math.max(prev - 0.25, 0.75));
  const handleResetZoom = () => {
    setZoom(1);
    setPan({ x: 0, y: 0 });
  };

  // Mouse pan handlers (disabled while dragging in positioning mode if clicking on canvas)
  const handleMouseDown = (e) => {
    if (isPositioningMode) return;
    if (e.button !== 0) return;
    setIsDragging(true);
    setDragStart({ x: e.clientX - pan.x, y: e.clientY - pan.y });
  };

  const handleMouseMove = (e) => {
    if (!isDragging || isPositioningMode) return;
    setPan({
      x: e.clientX - dragStart.x,
      y: e.clientY - dragStart.y,
    });
  };

  const handleMouseUp = () => {
    setIsDragging(false);
  };

  // Wheel zoom handler
  const handleWheel = useCallback((e) => {
    e.preventDefault();
    const delta = e.deltaY < 0 ? 0.15 : -0.15;
    setZoom((prev) => Math.min(Math.max(prev + delta, 0.75), 3.0));
  }, []);

  useEffect(() => {
    const el = containerRef.current;
    if (!el) return;
    el.addEventListener('wheel', handleWheel, { passive: false });
    return () => {
      el.removeEventListener('wheel', handleWheel);
    };
  }, [handleWheel]);

  // Handle clicking on the SVG canvas during Positioning Mode
  const handleCanvasClick = (e) => {
    if (!isPositioningMode) return;
    const svgEl = svgRef.current;
    if (!svgEl) return;

    const rect = svgEl.getBoundingClientRect();
    const clickXPixels = e.clientX - rect.left;
    const clickYPixels = e.clientY - rect.top;

    if (rect.width <= 0 || rect.height <= 0) return;

    const pctX = Math.max(0, Math.min(100, (clickXPixels / rect.width) * 100));
    const pctY = Math.max(0, Math.min(100, (clickYPixels / rect.height) * 100));

    const roundedX = parseFloat(pctX.toFixed(2));
    const roundedY = parseFloat(pctY.toFixed(2));

    // Detect if point lands inside a room
    const matchedRoom = rooms.find((rm) => {
      const rx = rm.xPosition ?? rm.xposition ?? 0;
      const ry = rm.yPosition ?? rm.yposition ?? 0;
      const rw = rm.width ?? 10;
      const rh = rm.height ?? 10;
      return (
        roundedX >= rx - 0.001 &&
        roundedX <= rx + rw + 0.001 &&
        roundedY >= ry - 0.001 &&
        roundedY <= ry + rh + 0.001
      );
    });

    setPendingPoint({
      x: roundedX,
      y: roundedY,
      room: matchedRoom || null,
    });
  };

  const handleConfirmSavePosition = async () => {
    if (!pendingPoint || !selectedFloor) return;
    setIsSavingPosition(true);
    try {
      await onSaveIndoorPosition({
        floorId: selectedFloor.id,
        roomId: pendingPoint.room?.id || null,
        indoorX: pendingPoint.x,
        indoorY: pendingPoint.y,
      });
    } finally {
      setIsSavingPosition(false);
    }
  };

  const hasFloorPlanImage = Boolean(authenticatedBlobUrl && !imageError);

  return (
    <div className="card border-0 shadow-sm rounded-3 overflow-hidden d-flex flex-column" style={{ minHeight: '620px' }}>
      {/* Top Facility Breadcrumb & Actions Bar */}
      <div className="card-header bg-white py-2 px-3 border-bottom d-flex align-items-center justify-content-between flex-wrap gap-2">
        <div className="d-flex align-items-center gap-2 flex-wrap">
          <button
            type="button"
            className="btn btn-sm btn-outline-secondary d-flex align-items-center gap-1 py-1 px-2"
            onClick={onBackToCampus}
            title="Return to Outdoor Campus Map"
          >
            <span className="material-symbols-outlined fs-6">arrow_back</span>
            <span className="fw-semibold">Campus Map</span>
          </button>

          <span className="text-muted small">&bull;</span>

          <div className="d-flex align-items-center gap-1 text-dark small">
            <span className="material-symbols-outlined fs-6 text-teal" style={{ color: '#005c55' }}>local_hospital</span>
            <span className="fw-semibold">{hospital?.name || 'Hospital'}</span>
            <span className="text-muted mx-1">/</span>
            <span className="material-symbols-outlined fs-6 text-primary">apartment</span>
            <span className="fw-bold">{building?.name || 'Building'}</span>
            <span className="text-muted mx-1">/</span>
            <span className="badge bg-light text-dark border fw-semibold">
              {formatFloorLabel(selectedFloor)}
            </span>
          </div>
        </div>

        {/* Zoom Controls */}
        <div className="d-flex align-items-center gap-1">
          <button
            type="button"
            className="btn btn-sm btn-light border py-1 px-2 text-dark"
            onClick={handleZoomIn}
            title="Zoom In"
          >
            <span className="material-symbols-outlined fs-6">add</span>
          </button>
          <button
            type="button"
            className="btn btn-sm btn-light border py-1 px-2 text-dark"
            onClick={handleZoomOut}
            title="Zoom Out"
          >
            <span className="material-symbols-outlined fs-6">remove</span>
          </button>
          <button
            type="button"
            className="btn btn-sm btn-light border py-1 px-2 text-dark"
            onClick={handleResetZoom}
            title="Reset Pan & Zoom"
          >
            <span className="material-symbols-outlined fs-6">fit_screen</span>
          </button>
        </div>
      </div>

      {/* Floor Navigation Pills / Tabs Bar */}
      <div className="bg-white border-bottom px-3 py-2 d-flex align-items-center justify-content-between gap-2 overflow-auto" style={{ whiteSpace: 'nowrap' }}>
        <div className="d-flex align-items-center gap-2">
          <span className="text-muted small fw-bold text-uppercase d-flex align-items-center gap-1" style={{ fontSize: '0.72rem' }}>
            <span className="material-symbols-outlined fs-6 text-teal" style={{ color: '#005c55' }}>layers</span>
            <span>BUILDING FLOORS:</span>
          </span>

          {floors.length === 0 ? (
            <span className="text-muted small">No floors configured.</span>
          ) : (
            floors.map((fl) => {
              const isSelected = selectedFloor && selectedFloor.id === fl.id;
              return (
                <button
                  key={fl.id}
                  type="button"
                  className={`btn btn-sm rounded-pill indoor-nav-btn px-3 py-1 ${
                    isSelected ? 'btn-stitch-primary shadow-sm' : 'btn-light border text-dark'
                  }`}
                  onClick={() => onSelectFloor(fl)}
                  disabled={isPositioningMode}
                >
                  <span className="fw-semibold">{formatFloorLabel(fl)}</span>
                  {fl.floorNumber != null && (
                    <span className={`badge ms-1 rounded-pill ${isSelected ? 'bg-white text-dark' : 'bg-secondary text-white'}`} style={{ fontSize: '0.65rem' }}>
                      L{fl.floorNumber}
                    </span>
                  )}
                </button>
              );
            })
          )}
        </div>

        {/* Floor Badges */}
        <div className="d-flex align-items-center gap-2">
          <span className="badge bg-teal-subtle text-teal border small d-flex align-items-center gap-1" style={{ fontSize: '0.72rem', color: '#005c55' }}>
            <span className="material-symbols-outlined fs-6">precision_manufacturing</span>
            <span>{assets.length} Asset{assets.length === 1 ? '' : 's'} on Floor</span>
          </span>

          {hasFloorPlanImage ? (
            <span className="badge bg-success-subtle text-success border border-success-subtle small d-flex align-items-center gap-1" style={{ fontSize: '0.72rem' }}>
              <span className="material-symbols-outlined fs-6">image</span>
              <span>Architectural Blueprint</span>
            </span>
          ) : (
            <span className="badge bg-secondary-subtle text-secondary border border-secondary-subtle small d-flex align-items-center gap-1" style={{ fontSize: '0.72rem' }}>
              <span className="material-symbols-outlined fs-6">grid_on</span>
              <span>Schematic Grid View</span>
            </span>
          )}
        </div>
      </div>

      {/* Blueprint Image Loading Error Fallback Notice */}
      {imageError && (
        <div className="bg-warning-subtle text-warning-emphasis px-3 py-1 small border-bottom d-flex align-items-center gap-1" style={{ fontSize: '0.75rem' }}>
          <span className="material-symbols-outlined fs-6">warning</span>
          <span>Architectural blueprint could not be loaded. Displaying schematic coordinate grid fallback.</span>
        </div>
      )}

      {/* Manual Positioning Mode Banner Overlay */}
      {isPositioningMode && (
        <div className="bg-primary text-white px-3 py-2 d-flex align-items-center justify-content-between flex-wrap gap-2 shadow-sm" style={{ zIndex: 20 }}>
          <div className="d-flex align-items-center gap-2">
            <span className="material-symbols-outlined fs-5 animate-pulse">pin_drop</span>
            <div>
              <div className="fw-bold" style={{ fontSize: '0.85rem' }}>
                Positioning Asset: {positioningAsset?.assetCode} — {positioningAsset?.assetName}
              </div>
              <div className="small opacity-90" style={{ fontSize: '0.75rem' }}>
                Click anywhere on the floor plan to place the marker. Coordinate percentage system (0.0% - 100.0%).
              </div>
            </div>
          </div>

          <div className="d-flex align-items-center gap-2">
            {pendingPoint ? (
              <span className="badge bg-white text-primary font-monospace py-1 px-2" style={{ fontSize: '0.75rem' }}>
                X: {pendingPoint.x}% &bull; Y: {pendingPoint.y}%
                {pendingPoint.room ? ` (${pendingPoint.room.roomNumber})` : ' (Open Area)'}
              </span>
            ) : (
              <span className="badge bg-warning text-dark py-1 px-2" style={{ fontSize: '0.75rem' }}>
                Click floor plan to set position
              </span>
            )}

            <button
              type="button"
              className="btn btn-sm btn-light fw-bold py-1 px-3 d-flex align-items-center gap-1"
              onClick={handleConfirmSavePosition}
              disabled={!pendingPoint || isSavingPosition}
            >
              {isSavingPosition ? (
                <>
                  <span className="spinner-border spinner-border-sm" role="status" />
                  <span>Saving...</span>
                </>
              ) : (
                <>
                  <span className="material-symbols-outlined fs-6">check</span>
                  <span>Save Location</span>
                </>
              )}
            </button>

            <button
              type="button"
              className="btn btn-sm btn-outline-light py-1 px-2"
              onClick={onCancelPositioning}
              disabled={isSavingPosition}
            >
              Cancel
            </button>
          </div>
        </div>
      )}

      {/* Interactive SVG Floor Plan Canvas Container */}
      <div
        ref={containerRef}
        className={`position-relative flex-grow-1 overflow-hidden d-flex align-items-center justify-content-center ${
          hasFloorPlanImage ? 'bg-light' : 'blueprint-grid'
        }`}
        style={{
          cursor: isPositioningMode ? 'crosshair' : isDragging ? 'grabbing' : 'grab',
          userSelect: 'none',
          minHeight: '440px',
        }}
        onMouseDown={handleMouseDown}
        onMouseMove={handleMouseMove}
        onMouseUp={handleMouseUp}
        onMouseLeave={handleMouseUp}
      >
        {loadingRooms ? (
          <div className="text-center p-4">
            <div className="spinner-border text-teal mb-2" role="status" style={{ color: '#005c55' }} />
            <div className="text-muted fw-semibold small">Loading floor plan &amp; room geometries...</div>
          </div>
        ) : !selectedFloor ? (
          <div className="text-center p-4 text-muted">
            <span className="material-symbols-outlined fs-1 text-secondary mb-2">layers</span>
            <p className="mb-0">Please select a building floor level to inspect indoor rooms.</p>
          </div>
        ) : (
          <div
            style={{
              transform: `translate(${pan.x}px, ${pan.y}px) scale(${zoom})`,
              transformOrigin: 'center center',
              transition: isDragging ? 'none' : 'transform 0.15s ease-out',
            }}
          >
            <svg
              ref={svgRef}
              width={canvasWidth}
              height={canvasHeight}
              viewBox={`0 0 ${canvasWidth} ${canvasHeight}`}
              onClick={handleCanvasClick}
              style={{
                maxWidth: '100%',
                maxHeight: '100%',
                display: 'block',
                background: '#ffffff',
                boxShadow: '0 8px 30px rgba(0, 0, 0, 0.08)',
                borderRadius: '8px',
                border: isPositioningMode ? '2px dashed #0284c7' : '1px solid #cbd5e1',
              }}
            >
              <defs>
                {/* Schematic Grid Pattern */}
                <pattern id="schematicGrid" width="40" height="40" patternUnits="userSpaceOnUse">
                  <path d="M 40 0 L 0 0 0 40" fill="none" stroke="#e2e8f0" strokeWidth="1" />
                  <path d="M 20 0 L 20 40 M 0 20 L 40 20" fill="none" stroke="#f1f5f9" strokeWidth="0.5" />
                </pattern>

                {/* Subtle outer glow for selected room */}
                <filter id="roomGlow" x="-20%" y="-20%" width="140%" height="140%">
                  <feDropShadow dx="0" dy="4" stdDeviation="6" floodColor="#005c55" floodOpacity="0.4" />
                </filter>

                {/* Marker drop shadow */}
                <filter id="markerShadow" x="-30%" y="-30%" width="160%" height="160%">
                  <feDropShadow dx="0" dy="3" stdDeviation="3" floodColor="#000000" floodOpacity="0.35" />
                </filter>

                {/* Target reticle animation style */}
                <style>{`
                  @keyframes targetReticle {
                    0%, 100% { transform: scale(1); }
                    50% { transform: scale(1.15); }
                  }
                  .target-reticle {
                    transform-origin: center;
                    animation: targetReticle 1.5s ease-in-out infinite;
                    will-change: transform;
                  }
                `}</style>
              </defs>

              {/* 1. Background Grid / Canvas Base */}
              <rect width={canvasWidth} height={canvasHeight} fill="url(#schematicGrid)" rx="8" />

              {/* 2. Optional Architectural CAD Floor Plan Image */}
              {hasFloorPlanImage && (
                <image
                  href={authenticatedBlobUrl}
                  width={canvasWidth}
                  height={canvasHeight}
                  preserveAspectRatio="xMidYMid meet"
                  onError={() => setImageError(true)}
                  opacity="0.88"
                />
              )}

              {/* 3. Outer Building Floor Perimeter Guide (Schematic Boundary) */}
              <rect
                x="15"
                y="15"
                width={canvasWidth - 30}
                height={canvasHeight - 30}
                fill="none"
                stroke="#94a3b8"
                strokeWidth="2"
                strokeDasharray="6, 6"
                rx="6"
              />

              {/* Floor Header Label in Top-Left Corner of Canvas */}
              <g transform="translate(25, 35)">
                <text fill="#64748b" fontSize="12" fontWeight="700" letterSpacing="1">
                  {building?.code || 'BLD'} &bull; {formatFloorLabel(selectedFloor).toUpperCase()}
                </text>
              </g>

              {/* 4. Render Configured Rooms */}
              {rooms.map((room) => {
                const isSelected = selectedRoom && selectedRoom.id === room.id;
                const theme = getDepartmentTheme(room.departmentCode);

                // Calculate pixel coordinates from percentages
                const rx = ((room.xPosition ?? room.xposition ?? 0) / 100) * canvasWidth;
                const ry = ((room.yPosition ?? room.yposition ?? 0) / 100) * canvasHeight;
                const rw = Math.max(((room.width ?? 10) / 100) * canvasWidth, 60);
                const rh = Math.max(((room.height ?? 10) / 100) * canvasHeight, 45);

                const maxCharLength = Math.max(Math.floor(rw / 8), 6);
                const displayName =
                  room.name.length > maxCharLength
                    ? room.name.slice(0, maxCharLength) + '…'
                    : room.name;

                return (
                  <g
                    key={room.id}
                    role="button"
                    tabIndex="0"
                    aria-label={`${room.name} (${room.roomNumber})`}
                    className="indoor-room-group"
                    onClick={(e) => {
                      if (isPositioningMode) return;
                      e.stopPropagation();
                      onSelectRoom(room);
                    }}
                    onKeyDown={(e) => {
                      if (isPositioningMode) return;
                      if (e.key === 'Enter' || e.key === ' ') {
                        e.preventDefault();
                        onSelectRoom(room);
                      }
                    }}
                    style={{ outline: 'none', cursor: isPositioningMode ? 'crosshair' : 'pointer' }}
                  >
                    <title>{`${room.name} (${room.roomNumber})\nDept: ${room.departmentName || room.departmentCode || 'None'}\nBounds: X=${room.xPosition}%, Y=${room.yPosition}%, W=${room.width}%, H=${room.height}%`}</title>

                    {/* Room Rectangle */}
                    <rect
                      x={rx}
                      y={ry}
                      width={rw}
                      height={rh}
                      rx="6"
                      ry="6"
                      fill={isSelected ? 'rgba(0, 92, 85, 0.28)' : theme.fill}
                      stroke={isSelected ? '#005c55' : theme.stroke}
                      strokeWidth={isSelected ? '3' : '1.5'}
                      className={`indoor-room-rect ${isSelected ? 'indoor-room-selected' : ''}`}
                      filter={isSelected ? 'url(#roomGlow)' : undefined}
                    />

                    {/* Room Number Badge */}
                    <g transform={`translate(${rx + 6}, ${ry + 8})`}>
                      <rect
                        x="0"
                        y="0"
                        width={Math.min(rw - 12, (room.roomNumber?.length || 4) * 7.5 + 8)}
                        height="16"
                        rx="4"
                        fill={isSelected ? '#005c55' : theme.stroke}
                      />
                      <text
                        x="4"
                        y="11"
                        fill="#ffffff"
                        fontSize="9.5"
                        fontWeight="700"
                        fontFamily="monospace"
                      >
                        {room.roomNumber}
                      </text>
                    </g>

                    {/* Room Name Text */}
                    {rh > 38 && (
                      <text
                        x={rx + 8}
                        y={ry + 34}
                        fill="#0f172a"
                        fontSize="11.5"
                        fontWeight={isSelected ? '700' : '600'}
                        fontFamily="inherit"
                      >
                        {displayName}
                      </text>
                    )}

                    {/* Department Tag */}
                    {rh > 55 && (room.departmentCode || room.departmentName) && (
                      <text
                        x={rx + 8}
                        y={ry + 48}
                        fill={theme.text}
                        fontSize="9.5"
                        fontWeight="600"
                        fontFamily="inherit"
                      >
                        {room.departmentCode || room.departmentName}
                      </text>
                    )}

                    {/* Selection Pin Indicator */}
                    {isSelected && (
                      <circle
                        cx={rx + rw - 10}
                        cy={ry + 10}
                        r="4"
                        fill="#005c55"
                        stroke="#ffffff"
                        strokeWidth="1.5"
                      />
                    )}
                  </g>
                );
              })}

              {/* 5. Render Floor Assets Markers (Phase 6) */}
              {assets.map((asset) => {
                const posX = asset.indoorX ?? asset.indoorx;
                const posY = asset.indoorY ?? asset.indoory;

                // Only render assets with valid percentage coordinates on this floor
                if (posX == null || posY == null || isNaN(posX) || isNaN(posY)) {
                  return null;
                }

                const ax = (posX / 100) * canvasWidth;
                const ay = (posY / 100) * canvasHeight;
                const isSelected = selectedAsset && selectedAsset.id === asset.id;
                const statusTheme = getAssetStatusTheme(asset.status);

                return (
                  <g
                    key={`asset-marker-${asset.id}`}
                    role="button"
                    tabIndex="0"
                    aria-label={`Asset ${asset.assetCode}: ${asset.assetName}`}
                    className="indoor-asset-marker"
                    onClick={(e) => {
                      if (isPositioningMode) return;
                      e.stopPropagation();
                      onSelectAsset(asset);
                    }}
                    onKeyDown={(e) => {
                      if (isPositioningMode) return;
                      if (e.key === 'Enter' || e.key === ' ') {
                        e.preventDefault();
                        onSelectAsset(asset);
                      }
                    }}
                    style={{ outline: 'none', cursor: isPositioningMode ? 'crosshair' : 'pointer' }}
                  >
                    <title>{`${asset.assetName} (${asset.assetCode})\nOperational Status: ${asset.status}\nLocation Status: ${formatLocationStatusLabel(asset.locationStatus)}\nRoom: ${asset.roomNumber || 'Corridor/Open Area'}\nCoordinates: (${posX.toFixed(1)}%, ${posY.toFixed(1)}%)\nSource: ${formatSourceLabel(asset.indoorLocationSource)}\nLast Updated: ${asset.indoorUpdatedAt ? new Date(asset.indoorUpdatedAt).toLocaleString() : 'N/A'}`}</title>

                    {/* Radar Pulse Wave Ring */}
                    <circle
                      cx={ax}
                      cy={ay}
                      r="12"
                      fill={statusTheme.pulse}
                      className="indoor-radar-pulse"
                    />

                    {/* Selection Highlight Ring */}
                    {isSelected && (
                      <circle
                        cx={ax}
                        cy={ay}
                        r="18"
                        fill="none"
                        stroke="#005c55"
                        strokeWidth="2.5"
                        strokeDasharray="4, 3"
                      />
                    )}

                    {/* Outer Pin Body with drop shadow */}
                    <circle
                      cx={ax}
                      cy={ay}
                      r="10"
                      fill={statusTheme.fill}
                      stroke="#ffffff"
                      strokeWidth="2.5"
                      filter="url(#markerShadow)"
                    />

                    {/* Medical Cross / Asset Icon in Center */}
                    <path
                      d={`M ${ax - 4} ${ay} L ${ax + 4} ${ay} M ${ax} ${ay - 4} L ${ax} ${ay + 4}`}
                      stroke="#ffffff"
                      strokeWidth="2"
                      strokeLinecap="round"
                    />

                    {/* Label Pill Under Marker */}
                    <g transform={`translate(${ax}, ${ay + 15})`}>
                      <rect
                        x="-26"
                        y="0"
                        width="52"
                        height="14"
                        rx="7"
                        fill={isSelected ? '#005c55' : '#1e293b'}
                        stroke="#ffffff"
                        strokeWidth="1"
                        opacity="0.95"
                      />
                      <text
                        x="0"
                        y="10"
                        textAnchor="middle"
                        fill="#ffffff"
                        fontSize="8.5"
                        fontWeight="700"
                        fontFamily="monospace"
                      >
                        {asset.assetCode}
                      </text>
                    </g>
                  </g>
                );
              })}

              {/* 6. Render Temporary Positioning Marker (When Admin is in Positioning Mode) */}
              {isPositioningMode && pendingPoint && (
                <g
                  className="target-reticle"
                  style={{
                    transformOrigin: `${(pendingPoint.x / 100) * canvasWidth}px ${(pendingPoint.y / 100) * canvasHeight}px`,
                  }}
                >
                  {/* Outer pulsating reticle */}
                  <circle
                    cx={(pendingPoint.x / 100) * canvasWidth}
                    cy={(pendingPoint.y / 100) * canvasHeight}
                    r="22"
                    fill="rgba(2, 132, 199, 0.25)"
                    stroke="#0284c7"
                    strokeWidth="2"
                    strokeDasharray="4, 3"
                  />

                  {/* Crosshair lines */}
                  <line
                    x1={(pendingPoint.x / 100) * canvasWidth - 28}
                    y1={(pendingPoint.y / 100) * canvasHeight}
                    x2={(pendingPoint.x / 100) * canvasWidth + 28}
                    y2={(pendingPoint.y / 100) * canvasHeight}
                    stroke="#0284c7"
                    strokeWidth="1.5"
                  />
                  <line
                    x1={(pendingPoint.x / 100) * canvasWidth}
                    y1={(pendingPoint.y / 100) * canvasHeight - 28}
                    x2={(pendingPoint.x / 100) * canvasWidth}
                    y2={(pendingPoint.y / 100) * canvasHeight + 28}
                    stroke="#0284c7"
                    strokeWidth="1.5"
                  />

                  {/* Target Pin */}
                  <circle
                    cx={(pendingPoint.x / 100) * canvasWidth}
                    cy={(pendingPoint.y / 100) * canvasHeight}
                    r="11"
                    fill="#0284c7"
                    stroke="#ffffff"
                    strokeWidth="2.5"
                    filter="url(#markerShadow)"
                  />

                  {/* Target Center Dot */}
                  <circle
                    cx={(pendingPoint.x / 100) * canvasWidth}
                    cy={(pendingPoint.y / 100) * canvasHeight}
                    r="3.5"
                    fill="#ffffff"
                  />

                  {/* Positioning Label Badge */}
                  <g transform={`translate(${(pendingPoint.x / 100) * canvasWidth}, ${(pendingPoint.y / 100) * canvasHeight + 17})`}>
                    <rect
                      x="-38"
                      y="0"
                      width="76"
                      height="15"
                      rx="7.5"
                      fill="#0284c7"
                      stroke="#ffffff"
                      strokeWidth="1"
                    />
                    <text
                      x="0"
                      y="11"
                      textAnchor="middle"
                      fill="#ffffff"
                      fontSize="8.5"
                      fontWeight="700"
                    >
                      NEW LOCATION
                    </text>
                  </g>
                </g>
              )}
            </svg>
          </div>
        )}

        {/* Floating Controls Overlay */}
        <div
          className="position-absolute bg-white bg-opacity-95 shadow-sm rounded-3 p-1 px-2 border d-flex align-items-center gap-1"
          style={{ bottom: '16px', left: '16px', zIndex: 10, fontSize: '0.75rem' }}
        >
          <span className="material-symbols-outlined fs-6 text-muted">
            {isPositioningMode ? 'ads_click' : 'pan_tool'}
          </span>
          <span className="text-muted small">
            {isPositioningMode
              ? 'Positioning Mode: Click to set marker'
              : 'Drag to Pan &bull; Scroll to Zoom &bull; Click Asset to inspect'}
          </span>
        </div>
      </div>

      {/* Bottom Legend & Asset Status Strip */}
      <div className="bg-light border-top p-2 px-3 d-flex flex-wrap align-items-center justify-content-between gap-2">
        <div className="d-flex align-items-center gap-3 flex-wrap" style={{ fontSize: '0.72rem' }}>
          <span className="text-muted fw-bold">ASSET STATUS:</span>
          <div className="d-flex align-items-center gap-1">
            <span style={{ width: '9px', height: '9px', borderRadius: '50%', background: '#10b981', display: 'inline-block' }} />
            <span className="text-dark">Available</span>
          </div>
          <div className="d-flex align-items-center gap-1">
            <span style={{ width: '9px', height: '9px', borderRadius: '50%', background: '#0ea5e9', display: 'inline-block' }} />
            <span className="text-dark">In Use</span>
          </div>
          <div className="d-flex align-items-center gap-1">
            <span style={{ width: '9px', height: '9px', borderRadius: '50%', background: '#f59e0b', display: 'inline-block' }} />
            <span className="text-dark">Maintenance</span>
          </div>
          <div className="d-flex align-items-center gap-1">
            <span style={{ width: '9px', height: '9px', borderRadius: '50%', background: '#8b5cf6', display: 'inline-block' }} />
            <span className="text-dark">Assigned</span>
          </div>

          <span className="text-muted mx-1">|</span>

          <span className="text-muted fw-bold">DEPARTMENTS:</span>
          <div className="d-flex align-items-center gap-1">
            <span style={{ width: '10px', height: '10px', borderRadius: '2px', background: '#dc2626', display: 'inline-block' }} />
            <span className="text-dark">ER</span>
          </div>
          <div className="d-flex align-items-center gap-1">
            <span style={{ width: '10px', height: '10px', borderRadius: '2px', background: '#d97706', display: 'inline-block' }} />
            <span className="text-dark">ICU</span>
          </div>
          <div className="d-flex align-items-center gap-1">
            <span style={{ width: '10px', height: '10px', borderRadius: '2px', background: '#2563eb', display: 'inline-block' }} />
            <span className="text-dark">Cardiology</span>
          </div>
          <div className="d-flex align-items-center gap-1">
            <span style={{ width: '10px', height: '10px', borderRadius: '2px', background: '#64748b', display: 'inline-block' }} />
            <span className="text-dark">Storage/Gen</span>
          </div>
        </div>

        <div className="d-flex align-items-center gap-3 text-muted small" style={{ fontSize: '0.72rem' }}>
          <span>
            <strong>Rooms:</strong> {rooms.length} configured
          </span>
          <span>
            <strong>Floor Assets:</strong> {assets.length} mapped
          </span>
          <span>
            <strong>Selected:</strong> {selectedAsset ? selectedAsset.assetCode : selectedRoom ? selectedRoom.name : 'None'}
          </span>
        </div>
      </div>
    </div>
  );
}
