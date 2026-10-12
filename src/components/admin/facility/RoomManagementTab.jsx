import React, { useState, useEffect, useCallback, useMemo, useRef } from 'react';
import facilityService from '../../../services/facilityService';
import departmentService from '../../../services/departmentService';

export default function RoomManagementTab({
  selectedHospital,
  selectedBuilding,
  selectedFloor,
  onNavigateToFloorPlan,
}) {
  const [rooms, setRooms] = useState([]);
  const [departments, setDepartments] = useState([]);
  const [loading, setLoading] = useState(false);
  const [search, setSearch] = useState('');
  const [error, setError] = useState(null);
  const [success, setSuccess] = useState(null);

  // Selected room for inspection/editing
  const [selectedRoomId, setSelectedRoomId] = useState(null);

  // Modal / Editor State
  const [modalOpen, setModalOpen] = useState(false);
  const [editingRoom, setEditingRoom] = useState(null);
  const [saving, setSaving] = useState(false);
  const [formData, setFormData] = useState({
    departmentId: '',
    roomNumber: '',
    name: '',
    description: '',
    xPosition: 10.0,
    yPosition: 10.0,
    width: 25.0,
    height: 20.0,
    active: true,
  });

  // Visual Interactive Drag-to-Draw State
  const [isDrawingMode, setIsDrawingMode] = useState(false);
  const [drawStart, setDrawStart] = useState(null);
  const [currentDraw, setCurrentDraw] = useState(null);
  const [imageError, setImageError] = useState(false);

  // Deactivate confirm
  const [deactivateRoom, setDeactivateRoom] = useState(null);

  const canvasRef = useRef(null);

  const fetchRooms = useCallback(async () => {
    if (!selectedFloor) {
      setRooms([]);
      return;
    }
    try {
      setLoading(true);
      setError(null);
      const data = await facilityService.getRoomsByFloor(selectedFloor.id);
      setRooms(data || []);
      if (data && data.length > 0 && !selectedRoomId) {
        setSelectedRoomId(data[0].id);
      }
    } catch (err) {
      console.error('Failed to load rooms:', err);
      setError('Unable to load rooms for this floor.');
    } finally {
      setLoading(false);
    }
  }, [selectedFloor, selectedRoomId]);

  const fetchDepartments = useCallback(async () => {
    try {
      const depts = await departmentService.getDepartments();
      setDepartments(depts || []);
    } catch (err) {
      console.error('Failed to load departments:', err);
    }
  }, []);

  useEffect(() => {
    fetchRooms();
    fetchDepartments();
  }, [fetchRooms, fetchDepartments]);

  const filteredRooms = useMemo(() => {
    if (!search.trim()) return rooms;
    const q = search.toLowerCase();
    return rooms.filter(
      (r) =>
        r.name?.toLowerCase().includes(q) ||
        r.roomNumber?.toLowerCase().includes(q) ||
        r.departmentName?.toLowerCase().includes(q) ||
        r.departmentCode?.toLowerCase().includes(q)
    );
  }, [rooms, search]);

  const canvasWidth = selectedFloor?.floorPlanWidth || 1000;
  const canvasHeight = selectedFloor?.floorPlanHeight || 650;
  const [authenticatedBlobUrl, setAuthenticatedBlobUrl] = useState(null);
  const activeBlobUrlRef = useRef(null);

  useEffect(() => {
    let isCurrent = true;
    if (selectedFloor && selectedFloor.floorPlanImageUrl) {
      setImageError(false);
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
          console.error('Failed to load authenticated floor plan image blob in RoomManagementTab:', err);
          setImageError(true);
        });
    } else {
      if (activeBlobUrlRef.current) {
        URL.revokeObjectURL(activeBlobUrlRef.current);
        activeBlobUrlRef.current = null;
      }
      setAuthenticatedBlobUrl(null);
      setImageError(false);
    }

    return () => {
      isCurrent = false;
      if (activeBlobUrlRef.current) {
        URL.revokeObjectURL(activeBlobUrlRef.current);
        activeBlobUrlRef.current = null;
      }
    };
  }, [selectedFloor?.id, selectedFloor?.floorPlanImageUrl]);

  const handleOpenCreate = () => {
    setEditingRoom(null);
    setFormData({
      departmentId: departments[0]?.id || '',
      roomNumber: '',
      name: '',
      description: '',
      xPosition: 15.0,
      yPosition: 15.0,
      width: 25.0,
      height: 20.0,
      active: true,
    });
    setIsDrawingMode(false);
    setDrawStart(null);
    setCurrentDraw(null);
    setError(null);
    setSuccess(null);
    setModalOpen(true);
  };

  const handleOpenEdit = (room) => {
    setEditingRoom(room);
    setFormData({
      departmentId: room.departmentId || '',
      roomNumber: room.roomNumber || '',
      name: room.name || '',
      description: room.description || '',
      xPosition: room.xPosition ?? 10.0,
      yPosition: room.yPosition ?? 10.0,
      width: room.width ?? 20.0,
      height: room.height ?? 15.0,
      active: room.active ?? true,
    });
    setIsDrawingMode(false);
    setDrawStart(null);
    setCurrentDraw(null);
    setError(null);
    setSuccess(null);
    setModalOpen(true);
  };

  // Convert client SVG coordinates to percentage (0.0 to 100.0)
  const getCoordinatesFromEvent = (e) => {
    const svgEl = canvasRef.current;
    if (!svgEl) return null;
    const rect = svgEl.getBoundingClientRect();
    const clientX = e.clientX - rect.left;
    const clientY = e.clientY - rect.top;

    const pctX = Math.max(0, Math.min(100, (clientX / rect.width) * 100));
    const pctY = Math.max(0, Math.min(100, (clientY / rect.height) * 100));

    return {
      x: Math.round(pctX * 10) / 10,
      y: Math.round(pctY * 10) / 10,
    };
  };

  const handleMouseDownCanvas = (e) => {
    if (!isDrawingMode) return;
    const coords = getCoordinatesFromEvent(e);
    if (!coords) return;

    setDrawStart(coords);
    setCurrentDraw({
      x: coords.x,
      y: coords.y,
      w: 1,
      h: 1,
    });
  };

  const handleMouseMoveCanvas = (e) => {
    if (!isDrawingMode || !drawStart) return;
    const coords = getCoordinatesFromEvent(e);
    if (!coords) return;

    const minX = Math.min(drawStart.x, coords.x);
    const minY = Math.min(drawStart.y, coords.y);
    const width = Math.max(2, Math.abs(coords.x - drawStart.x));
    const height = Math.max(2, Math.abs(coords.y - drawStart.y));

    // Ensure within 100% bounds
    const safeW = Math.min(width, 100 - minX);
    const safeH = Math.min(height, 100 - minY);

    setCurrentDraw({
      x: Math.round(minX * 10) / 10,
      y: Math.round(minY * 10) / 10,
      w: Math.round(safeW * 10) / 10,
      h: Math.round(safeH * 10) / 10,
    });
  };

  const handleMouseUpCanvas = () => {
    if (!isDrawingMode || !currentDraw) return;
    setFormData((prev) => ({
      ...prev,
      xPosition: currentDraw.x,
      yPosition: currentDraw.y,
      width: currentDraw.w,
      height: currentDraw.h,
    }));
    setDrawStart(null);
    setCurrentDraw(null);
    setIsDrawingMode(false);
  };

  const handleSubmit = async (e) => {
    e.preventDefault();
    if (!formData.name?.trim()) {
      setError('Room name is required.');
      return;
    }
    if (!editingRoom && !formData.roomNumber?.trim()) {
      setError('Room number is required (e.g. ER-BAY-101, ICU-202).');
      return;
    }

    const x = Number(formData.xPosition);
    const y = Number(formData.yPosition);
    const w = Number(formData.width);
    const h = Number(formData.height);

    if (x < 0 || x > 100 || y < 0 || y > 100) {
      setError('Coordinates must be between 0.0% and 100.0%.');
      return;
    }
    if (w <= 0 || h <= 0) {
      setError('Width and height must be greater than 0%.');
      return;
    }
    if (x + w > 100.0001) {
      setError(`Room rectangle exceeds canvas horizontal boundary (X: ${x}% + Width: ${w}% = ${(x + w).toFixed(1)}% > 100%).`);
      return;
    }
    if (y + h > 100.0001) {
      setError(`Room rectangle exceeds canvas vertical boundary (Y: ${y}% + Height: ${h}% = ${(y + h).toFixed(1)}% > 100%).`);
      return;
    }

    const payload = {
      departmentId: formData.departmentId ? Number(formData.departmentId) : null,
      name: formData.name.trim(),
      description: formData.description?.trim() || null,
      xPosition: x,
      yPosition: y,
      width: w,
      height: h,
      active: formData.active,
    };

    try {
      setSaving(true);
      setError(null);

      if (editingRoom) {
        const updated = await facilityService.updateRoom(editingRoom.id, payload);
        setSuccess(`Room "${updated.name}" updated successfully.`);
      } else {
        payload.roomNumber = formData.roomNumber.trim().toUpperCase();
        const created = await facilityService.createRoom(selectedFloor.id, payload);
        setSuccess(`Room "${created.name}" created successfully.`);
        setSelectedRoomId(created.id);
      }

      setModalOpen(false);
      fetchRooms();
    } catch (err) {
      console.error('Failed to save room:', err);
      setError(err.response?.data?.message || err.message || 'Failed to save room.');
    } finally {
      setSaving(false);
    }
  };

  const handleDeactivate = async () => {
    if (!deactivateRoom) return;
    try {
      setSaving(true);
      await facilityService.deleteRoom(deactivateRoom.id);
      setSuccess(`Room "${deactivateRoom.name}" deactivated.`);
      setDeactivateRoom(null);
      fetchRooms();
    } catch (err) {
      console.error('Failed to deactivate room:', err);
      setError(err.response?.data?.message || 'Failed to deactivate room.');
    } finally {
      setSaving(false);
    }
  };

  if (!selectedFloor) {
    return (
      <div className="text-center py-5 border rounded-3 bg-light">
        <span className="material-symbols-outlined fs-1 text-muted mb-2">meeting_room</span>
        <h3 className="fs-6 fw-bold text-dark mb-1">No Floor Selected</h3>
        <p className="text-muted small mb-0">Please select a building floor from the Floors tab first.</p>
      </div>
    );
  }

  return (
    <div className="d-flex flex-column gap-3">
      {/* Context Bar */}
      <div className="d-flex flex-wrap align-items-center justify-content-between gap-3 border-bottom pb-3">
        <div>
          <h2 className="fs-5 fw-bold text-dark mb-1 d-flex align-items-center gap-2">
            <span className="material-symbols-outlined text-primary">meeting_room</span>
            <span>Room Management &amp; Floor-Plan Geometry</span>
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

        <div className="d-flex align-items-center gap-2">
          <button
            type="button"
            className="btn btn-sm btn-outline-secondary d-inline-flex align-items-center gap-1"
            onClick={onNavigateToFloorPlan}
            title="Inspect or upload architectural blueprint"
          >
            <span className="material-symbols-outlined fs-6">draw</span>
            <span>Configure Blueprint</span>
          </button>

          <button
            type="button"
            className="btn-stitch-primary px-3 py-1 btn-sm d-inline-flex align-items-center gap-1"
            onClick={handleOpenCreate}
          >
            <span className="material-symbols-outlined fs-6">add</span>
            <span>Add Room</span>
          </button>
        </div>
      </div>

      {/* Alerts */}
      {error && (
        <div className="alert alert-danger d-flex align-items-center gap-2 py-2 px-3 rounded-3 mb-0" role="alert">
          <span className="material-symbols-outlined fs-5">error</span>
          <span className="small">{error}</span>
        </div>
      )}

      {success && (
        <div className="alert alert-success d-flex align-items-center gap-2 py-2 px-3 rounded-3 mb-0" role="alert">
          <span className="material-symbols-outlined fs-5">check_circle</span>
          <span className="small">{success}</span>
        </div>
      )}

      {/* Split Layout: Interactive Blueprint Canvas (Left) & Room List (Right) */}
      <div className="row g-3">
        {/* Interactive Canvas Viewer */}
        <div className="col-12 col-xl-7">
          <div className="stitch-card p-3 h-100 d-flex flex-column">
            <div className="d-flex align-items-center justify-content-between mb-2">
              <span className="text-uppercase fw-semibold text-muted small">Floor Geometry Canvas</span>
              <span className="badge bg-light text-dark border small">
                {canvasWidth} &times; {canvasHeight} px &bull; Percentage Coordinate Grid
              </span>
            </div>

            <div
              className="border rounded-3 overflow-hidden bg-white d-flex align-items-center justify-content-center p-2 flex-grow-1"
              style={{ minHeight: '440px', backgroundColor: '#0f172a' }}
            >
              <svg
                ref={canvasRef}
                viewBox={`0 0 ${canvasWidth} ${canvasHeight}`}
                style={{
                  width: '100%',
                  height: '100%',
                  maxHeight: '520px',
                  backgroundColor: '#f8fafc',
                  borderRadius: '6px',
                  cursor: 'default',
                }}
              >
                <defs>
                  <pattern id="roomEditorGrid" width="40" height="40" patternUnits="userSpaceOnUse">
                    <path d="M 40 0 L 0 0 0 40" fill="none" stroke="#e2e8f0" strokeWidth="1" />
                    <path d="M 200 0 L 0 0 0 200" fill="none" stroke="#cbd5e1" strokeWidth="1.5" />
                  </pattern>
                </defs>

                {/* Base Grid */}
                <rect width={canvasWidth} height={canvasHeight} fill="url(#roomEditorGrid)" />

                {/* Architectural Blueprint Background */}
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

                {/* Perimeter Guide */}
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

                {/* Rooms Render */}
                {rooms.map((room) => {
                  const isSelected = selectedRoomId === room.id;
                  const rx = (room.xPosition / 100) * canvasWidth;
                  const ry = (room.yPosition / 100) * canvasHeight;
                  const rw = Math.max((room.width / 100) * canvasWidth, 50);
                  const rh = Math.max((room.height / 100) * canvasHeight, 40);

                  return (
                    <g
                      key={room.id}
                      style={{ cursor: 'pointer' }}
                      onClick={() => setSelectedRoomId(room.id)}
                    >
                      <rect
                        x={rx}
                        y={ry}
                        width={rw}
                        height={rh}
                        rx="4"
                        fill={isSelected ? 'rgba(0, 92, 85, 0.35)' : 'rgba(2, 132, 199, 0.16)'}
                        stroke={isSelected ? '#005c55' : '#0284c7'}
                        strokeWidth={isSelected ? '3' : '1.5'}
                      />
                      <text
                        x={rx + 6}
                        y={ry + 16}
                        fill={isSelected ? '#005c55' : '#0369a1'}
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
                          fontWeight={isSelected ? '700' : '600'}
                        >
                          {room.name.length > 14 ? room.name.slice(0, 14) + '…' : room.name}
                        </text>
                      )}
                    </g>
                  );
                })}
              </svg>
            </div>
          </div>
        </div>

        {/* Room List & Table */}
        <div className="col-12 col-xl-5">
          <div className="stitch-card p-3 h-100 d-flex flex-column">
            <div className="d-flex align-items-center justify-content-between mb-3 gap-2">
              <div className="input-group input-group-sm">
                <span className="input-group-text bg-white border-end-0">
                  <span className="material-symbols-outlined text-muted fs-6">search</span>
                </span>
                <input
                  type="text"
                  className="form-control border-start-0 ps-0"
                  placeholder="Search rooms..."
                  value={search}
                  onChange={(e) => setSearch(e.target.value)}
                />
              </div>
              <span className="badge bg-light text-muted border text-nowrap">
                {filteredRooms.length} {filteredRooms.length === 1 ? 'Room' : 'Rooms'}
              </span>
            </div>

            {loading ? (
              <div className="text-center py-5">
                <div className="spinner-border spinner-border-sm text-primary" role="status"></div>
                <p className="text-muted small mt-2 mb-0">Loading floor rooms...</p>
              </div>
            ) : filteredRooms.length === 0 ? (
              <div className="text-center py-5 border rounded-3 bg-light">
                <span className="material-symbols-outlined fs-1 text-muted mb-2">meeting_room</span>
                <h3 className="fs-6 fw-bold text-dark mb-1">No Rooms Configured</h3>
                <p className="text-muted small mb-3">
                  {search ? 'No rooms match your search query.' : 'No rooms configured on this floor.'}
                </p>
                <button type="button" className="btn btn-sm btn-outline-primary" onClick={handleOpenCreate}>
                  Add First Room
                </button>
              </div>
            ) : (
              <div className="table-responsive border rounded-3 bg-white flex-grow-1" style={{ maxHeight: '470px' }}>
                <table className="table table-hover align-middle mb-0">
                  <thead className="table-light sticky-top">
                    <tr style={{ fontSize: '0.78rem' }}>
                      <th className="text-uppercase text-muted ps-3">Room</th>
                      <th className="text-uppercase text-muted">Department</th>
                      <th className="text-uppercase text-muted">Geometry (X, Y, W, H)</th>
                      <th className="text-uppercase text-muted text-end pe-3">Actions</th>
                    </tr>
                  </thead>
                  <tbody style={{ fontSize: '0.85rem' }}>
                    {filteredRooms.map((room) => {
                      const isSelected = selectedRoomId === room.id;

                      return (
                        <tr
                          key={room.id}
                          className={isSelected ? 'table-active' : ''}
                          style={{ cursor: 'pointer' }}
                          onClick={() => setSelectedRoomId(room.id)}
                        >
                          <td className="ps-3 py-2">
                            <div className="fw-bold text-dark">{room.name}</div>
                            <span className="badge bg-light text-dark border font-monospace" style={{ fontSize: '0.7rem' }}>
                              {room.roomNumber}
                            </span>
                          </td>

                          <td>
                            {room.departmentCode || room.departmentName ? (
                              <span className="badge bg-teal-subtle text-teal border border-teal-subtle small" style={{ color: '#005c55' }}>
                                {room.departmentCode || room.departmentName}
                              </span>
                            ) : (
                              <span className="text-muted small">None</span>
                            )}
                          </td>

                          <td>
                            <div className="font-monospace text-muted small" style={{ fontSize: '0.75rem' }}>
                              ({room.xPosition}%, {room.yPosition}%)
                              <div className="text-dark fw-semibold">
                                {room.width}% &times; {room.height}%
                              </div>
                            </div>
                          </td>

                          <td className="text-end pe-3" onClick={(e) => e.stopPropagation()}>
                            <div className="btn-group btn-group-sm">
                              <button
                                type="button"
                                className="btn btn-outline-secondary"
                                onClick={() => handleOpenEdit(room)}
                                title="Edit room geometry"
                              >
                                <span className="material-symbols-outlined fs-6">edit</span>
                              </button>
                              {room.active && (
                                <button
                                  type="button"
                                  className="btn btn-outline-danger"
                                  onClick={() => setDeactivateRoom(room)}
                                  title="Deactivate room"
                                >
                                  <span className="material-symbols-outlined fs-6">block</span>
                                </button>
                              )}
                            </div>
                          </td>
                        </tr>
                      );
                    })}
                  </tbody>
                </table>
              </div>
            )}
          </div>
        </div>
      </div>

      {/* Create / Edit Room Modal with Visual Geometry Box */}
      {modalOpen && (
        <div className="modal show d-block" style={{ backgroundColor: 'rgba(0,0,0,0.5)' }} tabIndex="-1">
          <div className="modal-dialog modal-dialog-centered modal-lg">
            <div className="modal-content border-0 shadow">
              <form onSubmit={handleSubmit}>
                <div className="modal-header border-bottom">
                  <h3 className="modal-title fs-5 fw-bold d-flex align-items-center gap-2">
                    <span className="material-symbols-outlined text-primary">
                      {editingRoom ? 'edit' : 'add_location'}
                    </span>
                    <span>{editingRoom ? 'Edit Room & Geometry' : 'Create New Room on Floor'}</span>
                  </h3>
                  <button
                    type="button"
                    className="btn-close"
                    onClick={() => setModalOpen(false)}
                    disabled={saving}
                  ></button>
                </div>

                <div className="modal-body">
                  <div className="row g-3">
                    {/* Basic Room Info */}
                    <div className="col-12 col-md-4">
                      <label className="form-label text-uppercase fw-semibold small text-muted">Room Number *</label>
                      <input
                        type="text"
                        className="form-control font-monospace"
                        placeholder="e.g. ER-BAY-101"
                        required
                        disabled={Boolean(editingRoom)}
                        value={formData.roomNumber}
                        onChange={(e) => setFormData({ ...formData, roomNumber: e.target.value })}
                      />
                      {editingRoom && <small className="text-muted">Room number is unique on floor</small>}
                    </div>

                    <div className="col-12 col-md-5">
                      <label className="form-label text-uppercase fw-semibold small text-muted">Room Name *</label>
                      <input
                        type="text"
                        className="form-control"
                        placeholder="e.g. Acute Trauma Resuscitation Bay 1"
                        required
                        value={formData.name}
                        onChange={(e) => setFormData({ ...formData, name: e.target.value })}
                      />
                    </div>

                    <div className="col-12 col-md-3">
                      <label className="form-label text-uppercase fw-semibold small text-muted">Department</label>
                      <select
                        className="form-select"
                        value={formData.departmentId}
                        onChange={(e) => setFormData({ ...formData, departmentId: e.target.value })}
                      >
                        <option value="">None / Facility</option>
                        {departments.map((d) => (
                          <option key={d.id} value={d.id}>
                            {d.name} ({d.code})
                          </option>
                        ))}
                      </select>
                    </div>

                    <div className="col-12">
                      <label className="form-label text-uppercase fw-semibold small text-muted">Description</label>
                      <input
                        type="text"
                        className="form-control"
                        placeholder="e.g. High-acuity trauma and ventilator monitored space"
                        value={formData.description}
                        onChange={(e) => setFormData({ ...formData, description: e.target.value })}
                      />
                    </div>

                    {/* Geometry Coordinate Inputs */}
                    <div className="col-12 border-top pt-3">
                      <div className="d-flex align-items-center justify-content-between mb-2">
                        <span className="text-uppercase fw-semibold text-muted small">
                          Floor Plan Coordinates (0.0% to 100.0%)
                        </span>
                        <button
                          type="button"
                          className={`btn btn-xs btn-sm d-inline-flex align-items-center gap-1 ${
                            isDrawingMode ? 'btn-primary' : 'btn-outline-primary'
                          }`}
                          onClick={() => setIsDrawingMode(!isDrawingMode)}
                        >
                          <span className="material-symbols-outlined fs-6">draw</span>
                          <span>{isDrawingMode ? 'Drawing Active (Click & Drag)' : 'Draw on Canvas'}</span>
                        </button>
                      </div>

                      <div className="row g-2 mb-3">
                        <div className="col-3">
                          <label className="form-label text-muted small" style={{ fontSize: '0.72rem' }}>
                            X Position (%)
                          </label>
                          <div className="input-group input-group-sm">
                            <input
                              type="number"
                              step="0.1"
                              min="0"
                              max="100"
                              className="form-control font-monospace"
                              value={formData.xPosition}
                              onChange={(e) => setFormData({ ...formData, xPosition: Number(e.target.value) })}
                              required
                            />
                            <span className="input-group-text">%</span>
                          </div>
                        </div>

                        <div className="col-3">
                          <label className="form-label text-muted small" style={{ fontSize: '0.72rem' }}>
                            Y Position (%)
                          </label>
                          <div className="input-group input-group-sm">
                            <input
                              type="number"
                              step="0.1"
                              min="0"
                              max="100"
                              className="form-control font-monospace"
                              value={formData.yPosition}
                              onChange={(e) => setFormData({ ...formData, yPosition: Number(e.target.value) })}
                              required
                            />
                            <span className="input-group-text">%</span>
                          </div>
                        </div>

                        <div className="col-3">
                          <label className="form-label text-muted small" style={{ fontSize: '0.72rem' }}>
                            Width (%)
                          </label>
                          <div className="input-group input-group-sm">
                            <input
                              type="number"
                              step="0.1"
                              min="0.1"
                              max="100"
                              className="form-control font-monospace"
                              value={formData.width}
                              onChange={(e) => setFormData({ ...formData, width: Number(e.target.value) })}
                              required
                            />
                            <span className="input-group-text">%</span>
                          </div>
                        </div>

                        <div className="col-3">
                          <label className="form-label text-muted small" style={{ fontSize: '0.72rem' }}>
                            Height (%)
                          </label>
                          <div className="input-group input-group-sm">
                            <input
                              type="number"
                              step="0.1"
                              min="0.1"
                              max="100"
                              className="form-control font-monospace"
                              value={formData.height}
                              onChange={(e) => setFormData({ ...formData, height: Number(e.target.value) })}
                              required
                            />
                            <span className="input-group-text">%</span>
                          </div>
                        </div>
                      </div>

                      {/* Interactive Visual Canvas Box Inside Modal */}
                      <div
                        className="border rounded-3 overflow-hidden bg-light position-relative"
                        style={{ height: '220px', cursor: isDrawingMode ? 'crosshair' : 'default' }}
                        onMouseDown={handleMouseDownCanvas}
                        onMouseMove={handleMouseMoveCanvas}
                        onMouseUp={handleMouseUpCanvas}
                      >
                        <svg
                          ref={canvasRef}
                          viewBox={`0 0 ${canvasWidth} ${canvasHeight}`}
                          style={{ width: '100%', height: '100%', pointerEvents: 'all' }}
                        >
                          <defs>
                            <pattern id="modalGrid" width="40" height="40" patternUnits="userSpaceOnUse">
                              <path d="M 40 0 L 0 0 0 40" fill="none" stroke="#e2e8f0" strokeWidth="1" />
                            </pattern>
                          </defs>

                          <rect width={canvasWidth} height={canvasHeight} fill="url(#modalGrid)" />

                          {authenticatedBlobUrl && !imageError && (
                            <image
                              href={authenticatedBlobUrl}
                              width={canvasWidth}
                              height={canvasHeight}
                              preserveAspectRatio="xMidYMid meet"
                              opacity="0.8"
                            />
                          )}

                          {/* Existing other rooms in light gray */}
                          {rooms
                            .filter((r) => !editingRoom || r.id !== editingRoom.id)
                            .map((r) => (
                              <rect
                                key={r.id}
                                x={(r.xPosition / 100) * canvasWidth}
                                y={(r.yPosition / 100) * canvasHeight}
                                width={(r.width / 100) * canvasWidth}
                                height={(r.height / 100) * canvasHeight}
                                fill="rgba(100, 116, 139, 0.12)"
                                stroke="#94a3b8"
                                strokeWidth="1"
                                strokeDasharray="3, 2"
                              />
                            ))}

                          {/* Currently edited room rectangle (live preview) */}
                          <rect
                            x={
                              currentDraw
                                ? (currentDraw.x / 100) * canvasWidth
                                : (Number(formData.xPosition) / 100) * canvasWidth
                            }
                            y={
                              currentDraw
                                ? (currentDraw.y / 100) * canvasHeight
                                : (Number(formData.yPosition) / 100) * canvasHeight
                            }
                            width={
                              currentDraw
                                ? (currentDraw.w / 100) * canvasWidth
                                : (Number(formData.width) / 100) * canvasWidth
                            }
                            height={
                              currentDraw
                                ? (currentDraw.h / 100) * canvasHeight
                                : (Number(formData.height) / 100) * canvasHeight
                            }
                            rx="4"
                            fill="rgba(0, 92, 85, 0.35)"
                            stroke="#005c55"
                            strokeWidth="2.5"
                          />
                        </svg>

                        {isDrawingMode && (
                          <div
                            className="position-absolute top-0 start-50 translate-middle-x mt-2 badge bg-dark text-white px-3 py-1 shadow"
                            style={{ pointerEvents: 'none', zIndex: 10 }}
                          >
                            Click and drag anywhere on canvas to draw room rectangle
                          </div>
                        )}
                      </div>
                    </div>

                    <div className="col-12">
                      <div className="form-check form-switch mt-1">
                        <input
                          className="form-check-input"
                          type="checkbox"
                          role="switch"
                          id="roomActiveSwitch"
                          checked={formData.active}
                          onChange={(e) => setFormData({ ...formData, active: e.target.checked })}
                        />
                        <label className="form-check-label small fw-semibold" htmlFor="roomActiveSwitch">
                          Room Active
                        </label>
                      </div>
                    </div>
                  </div>
                </div>

                <div className="modal-footer border-top bg-light">
                  <button
                    type="button"
                    className="btn btn-outline-secondary"
                    onClick={() => setModalOpen(false)}
                    disabled={saving}
                  >
                    Cancel
                  </button>
                  <button type="submit" className="btn btn-primary" disabled={saving}>
                    {saving ? 'Saving...' : editingRoom ? 'Save Changes' : 'Create Room'}
                  </button>
                </div>
              </form>
            </div>
          </div>
        </div>
      )}

      {/* Deactivate Confirmation Modal */}
      {deactivateRoom && (
        <div className="modal show d-block" style={{ backgroundColor: 'rgba(0,0,0,0.5)' }} tabIndex="-1">
          <div className="modal-dialog modal-dialog-centered modal-sm">
            <div className="modal-content border-0 shadow">
              <div className="modal-header border-bottom bg-danger-subtle text-danger py-2">
                <h3 className="modal-title fs-6 fw-bold d-flex align-items-center gap-1">
                  <span className="material-symbols-outlined fs-5">warning</span>
                  <span>Confirm Deactivation</span>
                </h3>
                <button
                  type="button"
                  className="btn-close"
                  onClick={() => setDeactivateRoom(null)}
                  disabled={saving}
                ></button>
              </div>
              <div className="modal-body small">
                Are you sure you want to deactivate room <strong>{deactivateRoom.name}</strong> (
                {deactivateRoom.roomNumber})?
                <p className="text-muted mt-2 mb-0" style={{ fontSize: '0.78rem' }}>
                  The room rectangle will be archived. Assigned assets and historical telemetry are preserved safely.
                </p>
              </div>
              <div className="modal-footer border-top py-2">
                <button
                  type="button"
                  className="btn btn-sm btn-outline-secondary"
                  onClick={() => setDeactivateRoom(null)}
                  disabled={saving}
                >
                  Cancel
                </button>
                <button
                  type="button"
                  className="btn btn-sm btn-danger"
                  onClick={handleDeactivate}
                  disabled={saving}
                >
                  {saving ? 'Deactivating...' : 'Deactivate'}
                </button>
              </div>
            </div>
          </div>
        </div>
      )}
    </div>
  );
}
