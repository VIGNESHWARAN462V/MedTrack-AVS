import React, { useState, useEffect, useRef, useCallback } from 'react';
import facilityService from '../../../services/facilityService';

export default function FloorPlanConfigTab({
  selectedHospital,
  selectedBuilding,
  selectedFloor,
  onFloorUpdated,
  onNavigateToRooms,
}) {
  const [uploading, setUploading] = useState(false);
  const [uploadError, setUploadError] = useState(null);
  const [uploadSuccess, setUploadSuccess] = useState(null);
  const [selectedFile, setSelectedFile] = useState(null);
  const [filePreview, setFilePreview] = useState(null);
  const [dragActive, setDragActive] = useState(false);

  // Dimensions state
  const [dimensions, setDimensions] = useState({
    width: selectedFloor?.floorPlanWidth || 1000,
    height: selectedFloor?.floorPlanHeight || 650,
  });
  const [savingDimensions, setSavingDimensions] = useState(false);

  // Floor Rooms for preview overlay
  const [rooms, setRooms] = useState([]);
  const [loadingRooms, setLoadingRooms] = useState(false);
  const [imageError, setImageError] = useState(false);
  const [imageLoading, setImageLoading] = useState(false);
  const [authenticatedBlobUrl, setAuthenticatedBlobUrl] = useState(null);
  const [imageTimestamp, setImageTimestamp] = useState(Date.now());
  const activeBlobUrlRef = useRef(null);

  // Confirm delete modal
  const [confirmDeleteModal, setConfirmDeleteModal] = useState(false);
  const [deleting, setDeleting] = useState(false);

  const fileInputRef = useRef(null);

  useEffect(() => {
    let isCurrent = true;

    if (selectedFloor) {
      setDimensions({
        width: selectedFloor.floorPlanWidth || 1000,
        height: selectedFloor.floorPlanHeight || 650,
      });
      fetchRooms();

      if (selectedFloor.floorPlanImageUrl) {
        setImageLoading(true);
        setImageError(false);

        facilityService
          .fetchFloorPlanImageBlob(selectedFloor.id)
          .then((blob) => {
            if (!isCurrent) return;
            if (activeBlobUrlRef.current) {
              URL.revokeObjectURL(activeBlobUrlRef.current);
            }
            const newObjUrl = URL.createObjectURL(blob);
            activeBlobUrlRef.current = newObjUrl;
            setAuthenticatedBlobUrl(newObjUrl);
            setImageLoading(false);
          })
          .catch((err) => {
            if (!isCurrent) return;
            console.error('Failed to load authenticated floor plan blob:', err);
            setImageError(true);
            setImageLoading(false);
          });
      } else {
        if (activeBlobUrlRef.current) {
          URL.revokeObjectURL(activeBlobUrlRef.current);
          activeBlobUrlRef.current = null;
        }
        setAuthenticatedBlobUrl(null);
        setImageLoading(false);
        setImageError(false);
      }
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
  }, [selectedFloor?.id, selectedFloor?.floorPlanImageUrl, imageTimestamp]);

  const fetchRooms = async () => {
    if (!selectedFloor) return;
    try {
      setLoadingRooms(true);
      const data = await facilityService.getRoomsByFloor(selectedFloor.id);
      setRooms(data || []);
    } catch (err) {
      console.error('Failed to fetch rooms for preview:', err);
    } finally {
      setLoadingRooms(false);
    }
  };

  const handleFileSelect = (file) => {
    if (!file) return;
    setUploadError(null);
    setUploadSuccess(null);

    const ext = file.name.split('.').pop()?.toLowerCase();
    if (ext === 'svg') {
      setUploadError(
        'SVG format is not supported for floor plans due to vector security policies. Please upload an architectural blueprint in PNG or JPEG (.jpg, .jpeg) format.'
      );
      return;
    }

    const validExts = ['png', 'jpg', 'jpeg'];
    if (!validExts.includes(ext)) {
      setUploadError(
        `Unsupported file type ".${ext}". Please upload an architectural blueprint in PNG or JPEG (.jpg, .jpeg) format.`
      );
      return;
    }

    if (file.size > 15 * 1024 * 1024) {
      setUploadError(
        `File size (${(file.size / (1024 * 1024)).toFixed(1)} MB) exceeds maximum allowed limit of 15 MB.`
      );
      return;
    }

    setSelectedFile(file);
    if (file.type.startsWith('image/')) {
      const url = URL.createObjectURL(file);
      setFilePreview(url);
    } else {
      setFilePreview(null);
    }
  };

  const handleDrop = (e) => {
    e.preventDefault();
    e.stopPropagation();
    setDragActive(false);
    if (e.dataTransfer?.files?.[0]) {
      handleFileSelect(e.dataTransfer.files[0]);
    }
  };

  const handleDragOver = (e) => {
    e.preventDefault();
    e.stopPropagation();
    setDragActive(true);
  };

  const handleDragLeave = (e) => {
    e.preventDefault();
    e.stopPropagation();
    setDragActive(false);
  };

  const handleUpload = async () => {
    if (!selectedFile || !selectedFloor) return;

    try {
      setUploading(true);
      setUploadError(null);
      setUploadSuccess(null);

      const response = await facilityService.uploadFloorPlanImage(selectedFloor.id, selectedFile);
      setUploadSuccess(
        `Blueprint uploaded successfully (${response.width} × ${response.height} px, ${(response.fileSize / 1024).toFixed(1)} KB).`
      );

      setSelectedFile(null);
      if (filePreview) URL.revokeObjectURL(filePreview);
      setFilePreview(null);
      setImageError(false);
      setImageTimestamp(Date.now());

      // Refresh floor
      const refreshed = await facilityService.getFloorById(selectedFloor.id);
      if (onFloorUpdated) onFloorUpdated(refreshed);
      setDimensions({
        width: refreshed.floorPlanWidth || 1000,
        height: refreshed.floorPlanHeight || 650,
      });
    } catch (err) {
      console.error('Failed to upload floor plan:', err);
      const msg = err.response?.data?.message || err.message || 'Failed to upload floor plan image.';
      setUploadError(msg);
    } finally {
      setUploading(false);
    }
  };

  const handleSaveDimensions = async (e) => {
    e.preventDefault();
    if (!selectedFloor) return;

    const w = Number(dimensions.width);
    const h = Number(dimensions.height);
    if (!w || w < 200 || !h || h < 150) {
      setUploadError('Width must be >= 200px and Height must be >= 150px.');
      return;
    }

    try {
      setSavingDimensions(true);
      setUploadError(null);
      const updated = await facilityService.updateFloor(selectedFloor.id, {
        displayName: selectedFloor.displayName,
        floorPlanWidth: w,
        floorPlanHeight: h,
        active: selectedFloor.active,
      });
      setUploadSuccess(`Canvas dimensions updated to ${w} × ${h} px.`);
      if (onFloorUpdated) onFloorUpdated(updated);
    } catch (err) {
      console.error('Failed to update dimensions:', err);
      setUploadError(err.response?.data?.message || 'Failed to update canvas dimensions.');
    } finally {
      setSavingDimensions(false);
    }
  };

  const handleDeleteBlueprint = async () => {
    if (!selectedFloor) return;
    try {
      setDeleting(true);
      await facilityService.deleteFloorPlanImage(selectedFloor.id);
      setUploadSuccess('Architectural floor plan blueprint removed. Schematic grid fallback restored.');
      setConfirmDeleteModal(false);
      setImageError(false);
      const refreshed = await facilityService.getFloorById(selectedFloor.id);
      if (onFloorUpdated) onFloorUpdated(refreshed);
    } catch (err) {
      console.error('Failed to remove blueprint:', err);
      setUploadError(err.response?.data?.message || 'Failed to remove floor plan blueprint.');
    } finally {
      setDeleting(false);
    }
  };

  const canvasWidth = dimensions.width || 1000;
  const canvasHeight = dimensions.height || 650;
  const hasConfiguredImage = Boolean(authenticatedBlobUrl && !imageError);

  if (!selectedFloor) {
    return (
      <div className="text-center py-5 border rounded-3 bg-light">
        <span className="material-symbols-outlined fs-1 text-muted mb-2">layers</span>
        <h3 className="fs-6 fw-bold text-dark mb-1">No Floor Selected</h3>
        <p className="text-muted small mb-0">Please select a building floor from the Floors tab first.</p>
      </div>
    );
  }

  return (
    <div className="d-flex flex-column gap-4">
      {/* Top Header & Context */}
      <div className="d-flex flex-wrap align-items-center justify-content-between gap-3 border-bottom pb-3">
        <div>
          <h2 className="fs-5 fw-bold text-dark mb-1 d-flex align-items-center gap-2">
            <span className="material-symbols-outlined text-teal" style={{ color: '#005c55' }}>
              draw
            </span>
            <span>Architectural Floor Plan Blueprint Configuration</span>
          </h2>
          <div className="text-muted small d-flex align-items-center gap-2 flex-wrap">
            <span>
              Hospital: <strong>{selectedHospital?.name}</strong>
            </span>
            <span>&bull;</span>
            <span>
              Building: <strong>{selectedBuilding?.name}</strong>
            </span>
            <span>&bull;</span>
            <span className="badge bg-teal-subtle text-teal border border-teal-subtle px-2 py-1" style={{ color: '#005c55' }}>
              {selectedFloor.displayName} (Level {selectedFloor.floorNumber})
            </span>
          </div>
        </div>

        <button
          type="button"
          className="btn btn-sm btn-outline-primary d-inline-flex align-items-center gap-1"
          onClick={onNavigateToRooms}
        >
          <span className="material-symbols-outlined fs-6">meeting_room</span>
          <span>Manage Rooms ({rooms.length}) &rarr;</span>
        </button>
      </div>

      {/* Alerts */}
      {uploadError && (
        <div className="alert alert-danger d-flex align-items-center gap-2 py-2 px-3 rounded-3 mb-0" role="alert">
          <span className="material-symbols-outlined fs-5">error</span>
          <span className="small">{uploadError}</span>
        </div>
      )}

      {uploadSuccess && (
        <div className="alert alert-success d-flex align-items-center gap-2 py-2 px-3 rounded-3 mb-0" role="alert">
          <span className="material-symbols-outlined fs-5">check_circle</span>
          <span className="small">{uploadSuccess}</span>
        </div>
      )}

      {/* Upper Grid: Upload Dropzone & Canvas Dimension Adjuster */}
      <div className="row g-3">
        {/* Dropzone & Upload Panel */}
        <div className="col-12 col-lg-7">
          <div className="stitch-card p-3 h-100 d-flex flex-column">
            <div className="d-flex align-items-center justify-content-between mb-2">
              <span className="text-uppercase fw-semibold text-muted small">Upload Architectural Blueprint</span>
              <span className="badge bg-light text-dark border small">PNG, JPEG &bull; Max 15MB</span>
            </div>

            <div
              className={`border border-2 border-dashed rounded-3 p-4 text-center d-flex flex-column align-items-center justify-content-center flex-grow-1 ${
                dragActive ? 'border-primary bg-primary-subtle' : 'bg-light'
              }`}
              style={{ cursor: 'pointer', transition: 'all 0.2s ease', minHeight: '170px' }}
              onDragOver={handleDragOver}
              onDragLeave={handleDragLeave}
              onDrop={handleDrop}
              onClick={() => fileInputRef.current?.click()}
            >
              <input
                ref={fileInputRef}
                type="file"
                className="d-none"
                accept=".png,.jpg,.jpeg,image/png,image/jpeg"
                onChange={(e) => handleFileSelect(e.target.files?.[0])}
              />

              <span className="material-symbols-outlined fs-1 text-teal mb-1" style={{ color: '#005c55' }}>
                cloud_upload
              </span>
              <div className="fw-bold text-dark small">Click to browse or drag and drop blueprint image</div>
              <p className="text-muted small mb-0 mt-1" style={{ fontSize: '0.78rem' }}>
                Architectural floor plans are securely stored in persistent database storage.
              </p>
            </div>

            {selectedFile && (
              <div className="mt-3 p-2 rounded border bg-white d-flex align-items-center justify-content-between">
                <div className="d-flex align-items-center gap-2 text-truncate me-2">
                  <span className="material-symbols-outlined fs-5 text-primary">image</span>
                  <div className="small text-truncate">
                    <span className="fw-semibold text-dark">{selectedFile.name}</span>{' '}
                    <span className="text-muted">({(selectedFile.size / 1024).toFixed(1)} KB)</span>
                  </div>
                </div>

                <div className="d-flex align-items-center gap-1">
                  <button
                    type="button"
                    className="btn btn-sm btn-outline-secondary py-1 px-2"
                    onClick={() => {
                      setSelectedFile(null);
                      setFilePreview(null);
                    }}
                    disabled={uploading}
                  >
                    Cancel
                  </button>
                  <button
                    type="button"
                    className="btn btn-sm btn-teal text-white py-1 px-3 d-inline-flex align-items-center gap-1"
                    style={{ backgroundColor: '#005c55' }}
                    onClick={handleUpload}
                    disabled={uploading}
                  >
                    {uploading ? (
                      <>
                        <span className="spinner-border spinner-border-sm" role="status"></span>
                        <span>Uploading...</span>
                      </>
                    ) : (
                      <>
                        <span className="material-symbols-outlined fs-6">upload</span>
                        <span>Upload Blueprint</span>
                      </>
                    )}
                  </button>
                </div>
              </div>
            )}

            {/* Current Blueprint Metadata */}
            {selectedFloor.floorPlanImageUrl && (
              <div className="mt-3 pt-2 border-top d-flex align-items-center justify-content-between flex-wrap gap-2">
                <div className="small">
                  <span className="text-muted">Active Image Reference:</span>{' '}
                  <code className="text-primary fw-semibold">{selectedFloor.floorPlanImageUrl}</code>
                </div>

                <button
                  type="button"
                  className="btn btn-sm btn-outline-danger py-1 px-2 d-inline-flex align-items-center gap-1"
                  onClick={() => setConfirmDeleteModal(true)}
                  disabled={uploading}
                >
                  <span className="material-symbols-outlined fs-6">delete</span>
                  <span>Remove Blueprint</span>
                </button>
              </div>
            )}
          </div>
        </div>

        {/* Canvas Proportions & Coordinate Tuning Panel */}
        <div className="col-12 col-lg-5">
          <form onSubmit={handleSaveDimensions} className="stitch-card p-3 h-100 d-flex flex-column justify-content-between">
            <div>
              <span className="text-uppercase fw-semibold text-muted small d-block mb-2">
                Canvas Proportions &amp; Resolution
              </span>
              <p className="text-muted small mb-3" style={{ fontSize: '0.8rem' }}>
                Set the virtual coordinate plane resolution (in pixels). All room percentages scale proportionally.
              </p>

              <div className="row g-2 mb-3">
                <div className="col-6">
                  <label className="form-label text-uppercase fw-semibold text-muted small" style={{ fontSize: '0.72rem' }}>
                    Canvas Width (px)
                  </label>
                  <div className="input-group input-group-sm">
                    <input
                      type="number"
                      min="200"
                      max="8000"
                      className="form-control font-monospace"
                      value={dimensions.width}
                      onChange={(e) => setDimensions({ ...dimensions, width: e.target.value })}
                      required
                    />
                    <span className="input-group-text">px</span>
                  </div>
                </div>

                <div className="col-6">
                  <label className="form-label text-uppercase fw-semibold text-muted small" style={{ fontSize: '0.72rem' }}>
                    Canvas Height (px)
                  </label>
                  <div className="input-group input-group-sm">
                    <input
                      type="number"
                      min="150"
                      max="8000"
                      className="form-control font-monospace"
                      value={dimensions.height}
                      onChange={(e) => setDimensions({ ...dimensions, height: e.target.value })}
                      required
                    />
                    <span className="input-group-text">px</span>
                  </div>
                </div>
              </div>

              <div className="p-2 rounded bg-light border small text-muted">
                <div className="fw-semibold text-dark mb-1 d-flex align-items-center gap-1">
                  <span className="material-symbols-outlined fs-6 text-teal" style={{ color: '#005c55' }}>
                    info
                  </span>
                  <span>Aspect Ratio Preservation:</span>
                </div>
                Current ratio:{' '}
                <strong>
                  {dimensions.width && dimensions.height
                    ? (dimensions.width / dimensions.height).toFixed(2)
                    : '1.54'}
                  :1
                </strong>
                . When uploading PNG or JPEG, dimensions are automatically detected from the image header.
              </div>
            </div>

            <div className="d-flex justify-content-end mt-3">
              <button
                type="submit"
                className="btn btn-sm btn-outline-teal d-inline-flex align-items-center gap-1"
                style={{ borderColor: '#005c55', color: '#005c55' }}
                disabled={savingDimensions}
              >
                {savingDimensions ? (
                  <>
                    <span className="spinner-border spinner-border-sm" role="status"></span>
                    <span>Saving...</span>
                  </>
                ) : (
                  <>
                    <span className="material-symbols-outlined fs-6">aspect_ratio</span>
                    <span>Update Dimensions</span>
                  </>
                )}
              </button>
            </div>
          </form>
        </div>
      </div>

      {/* Blueprint Live Preview Canvas */}
      <div className="stitch-card p-3">
        <div className="d-flex align-items-center justify-content-between mb-2 flex-wrap gap-2">
          <div className="d-flex align-items-center gap-2">
            <span className="material-symbols-outlined text-teal fs-5" style={{ color: '#005c55' }}>
              preview
            </span>
            <span className="fw-bold text-dark fs-6">Live Architectural Canvas Preview</span>
            {hasConfiguredImage ? (
              <span className="badge bg-success-subtle text-success border border-success-subtle small">
                Architectural Blueprint Active
              </span>
            ) : (
              <span className="badge bg-secondary-subtle text-secondary border border-secondary-subtle small">
                Schematic Grid Mode (No blueprint image)
              </span>
            )}
          </div>

          <div className="text-muted small">
            Overlaying <strong>{rooms.length}</strong> configured {rooms.length === 1 ? 'room' : 'rooms'}
          </div>
        </div>

        {/* Blueprint Error Alert */}
        {imageError && (
          <div className="alert alert-warning py-2 px-3 small d-flex align-items-center gap-2 mb-2" role="alert">
            <span className="material-symbols-outlined fs-6">warning</span>
            <span>
              Could not load architectural image from <code>{selectedFloor.floorPlanImageUrl}</code>. Falling back to
              schematic coordinate grid.
            </span>
          </div>
        )}

        {/* Interactive SVG Preview Canvas */}
        <div
          className="border rounded-3 overflow-hidden bg-white d-flex align-items-center justify-content-center p-2"
          style={{ minHeight: '440px', backgroundColor: '#0f172a' }}
        >
          <svg
            viewBox={`0 0 ${canvasWidth} ${canvasHeight}`}
            style={{
              width: '100%',
              maxHeight: '520px',
              backgroundColor: '#f8fafc',
              boxShadow: '0 4px 6px -1px rgba(0, 0, 0, 0.1)',
              borderRadius: '6px',
            }}
          >
            <defs>
              {/* Schematic Grid Pattern */}
              <pattern id="previewGrid" width="40" height="40" patternUnits="userSpaceOnUse">
                <path d="M 40 0 L 0 0 0 40" fill="none" stroke="#e2e8f0" strokeWidth="1" />
                <path d="M 200 0 L 0 0 0 200" fill="none" stroke="#cbd5e1" strokeWidth="1.5" />
              </pattern>
            </defs>

            {/* Base Grid */}
            <rect width={canvasWidth} height={canvasHeight} fill="url(#previewGrid)" />

            {/* Architectural Blueprint Image (if configured) */}
            {authenticatedBlobUrl && !imageError && (
              <image
                href={authenticatedBlobUrl}
                width={canvasWidth}
                height={canvasHeight}
                preserveAspectRatio="xMidYMid meet"
                onError={() => setImageError(true)}
                opacity="0.9"
              />
            )}

            {/* Floor Boundary Guide */}
            <rect
              x="15"
              y="15"
              width={canvasWidth - 30}
              height={canvasHeight - 30}
              fill="none"
              stroke="#94a3b8"
              strokeWidth="2"
              strokeDasharray="6, 6"
              rx="4"
            />

            {/* Corner Badge */}
            <g transform="translate(25, 35)">
              <text fill="#64748b" fontSize="12" fontWeight="700" letterSpacing="1">
                {selectedBuilding?.code || 'BLD'} &bull; {selectedFloor.displayName.toUpperCase()}
              </text>
            </g>

            {/* Overlay Configured Rooms */}
            {rooms.map((room) => {
              const rx = (room.xPosition / 100) * canvasWidth;
              const ry = (room.yPosition / 100) * canvasHeight;
              const rw = Math.max((room.width / 100) * canvasWidth, 50);
              const rh = Math.max((room.height / 100) * canvasHeight, 40);

              return (
                <g key={room.id}>
                  <rect
                    x={rx}
                    y={ry}
                    width={rw}
                    height={rh}
                    rx="4"
                    fill="rgba(0, 92, 85, 0.16)"
                    stroke="#005c55"
                    strokeWidth="1.5"
                  />
                  <text
                    x={rx + 6}
                    y={ry + 16}
                    fill="#005c55"
                    fontSize="10"
                    fontWeight="700"
                    fontFamily="monospace"
                  >
                    {room.roomNumber}
                  </text>
                  {rh > 32 && (
                    <text
                      x={rx + 6}
                      y={ry + 30}
                      fill="#0f172a"
                      fontSize="9.5"
                      fontWeight="600"
                    >
                      {room.name.length > 14 ? room.name.slice(0, 14) + '…' : room.name}
                    </text>
                  )}
                </g>
              );
            })}
          </svg>
        </div>

        <div className="mt-2 text-muted small d-flex align-items-center justify-content-between">
          <span>
            Schematic canvas scaled to <strong>{canvasWidth} &times; {canvasHeight}</strong> resolution.
          </span>
          <span className="text-success d-inline-flex align-items-center gap-1">
            <span className="material-symbols-outlined fs-6">lock</span>
            <span>Database-backed persistence enabled</span>
          </span>
        </div>
      </div>

      {/* Delete Confirmation Modal */}
      {confirmDeleteModal && (
        <div className="modal show d-block" style={{ backgroundColor: 'rgba(0,0,0,0.5)' }} tabIndex="-1">
          <div className="modal-dialog modal-dialog-centered modal-sm">
            <div className="modal-content border-0 shadow">
              <div className="modal-header border-bottom bg-danger-subtle text-danger py-2">
                <h3 className="modal-title fs-6 fw-bold d-flex align-items-center gap-1">
                  <span className="material-symbols-outlined fs-5">warning</span>
                  <span>Remove Blueprint</span>
                </h3>
                <button
                  type="button"
                  className="btn-close"
                  onClick={() => setConfirmDeleteModal(false)}
                  disabled={deleting}
                ></button>
              </div>
              <div className="modal-body small">
                Are you sure you want to remove the architectural blueprint for{' '}
                <strong>{selectedFloor.displayName}</strong>?
                <p className="text-muted mt-2 mb-0" style={{ fontSize: '0.78rem' }}>
                  The floor will revert to the schematic coordinate grid fallback. Room geometries and asset positions
                  will remain intact.
                </p>
              </div>
              <div className="modal-footer border-top py-2">
                <button
                  type="button"
                  className="btn btn-sm btn-outline-secondary"
                  onClick={() => setConfirmDeleteModal(false)}
                  disabled={deleting}
                >
                  Cancel
                </button>
                <button
                  type="button"
                  className="btn btn-sm btn-danger"
                  onClick={handleDeleteBlueprint}
                  disabled={deleting}
                >
                  {deleting ? 'Removing...' : 'Remove Blueprint'}
                </button>
              </div>
            </div>
          </div>
        </div>
      )}
    </div>
  );
}
