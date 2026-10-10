import React, { useState, useEffect, useCallback } from 'react';
import {
  ResponsiveContainer,
  AreaChart,
  Area,
  BarChart,
  Bar,
  XAxis,
  YAxis,
  CartesianGrid,
  Tooltip,
  Legend,
} from 'recharts';
import { analyticsService } from '../services/analyticsService';
import { departmentService } from '../services/departmentService';
import { assetService } from '../services/assetService';

export default function AnalyticsPage() {
  // Filter States
  const [datePreset, setDatePreset] = useState('7d');
  const [fromDate, setFromDate] = useState(() => {
    const d = new Date();
    d.setDate(d.getDate() - 7);
    return d.toISOString().slice(0, 16);
  });
  const [toDate, setToDate] = useState(() => {
    return new Date().toISOString().slice(0, 16);
  });
  const [selectedDeptId, setSelectedDeptId] = useState('');
  const [selectedAssetId, setSelectedAssetId] = useState('');

  // Dropdown Options
  const [departments, setDepartments] = useState([]);
  const [assets, setAssets] = useState([]);

  // Analytics API Data States
  const [overview, setOverview] = useState(null);
  const [amcData, setAmcData] = useState(null);
  const [hfiData, setHfiData] = useState(null);
  const [gubData, setGubData] = useState(null);

  // Loading & Error States
  const [loading, setLoading] = useState(true);
  const [refreshing, setRefreshing] = useState(false);
  const [error, setError] = useState(null);

  // Active Tab
  const [activeTab, setActiveTab] = useState('overview');

  // Load Dropdowns
  useEffect(() => {
    const loadDropdowns = async () => {
      try {
        const [deptList, assetList] = await Promise.all([
          departmentService.getDepartments().catch(() => []),
          assetService.getAssets().catch(() => []),
        ]);
        setDepartments(deptList || []);
        setAssets(assetList || []);
      } catch (err) {
        console.error('Failed to load filter options:', err);
      }
    };
    loadDropdowns();
  }, []);

  // Handle Preset Changes
  const handlePresetChange = (preset) => {
    setDatePreset(preset);
    const now = new Date();
    let past = new Date();
    if (preset === '24h') {
      past.setHours(past.getHours() - 24);
    } else if (preset === '7d') {
      past.setDate(past.getDate() - 7);
    } else if (preset === '30d') {
      past.setDate(past.getDate() - 30);
    }
    if (preset !== 'custom') {
      setFromDate(past.toISOString().slice(0, 16));
      setToDate(now.toISOString().slice(0, 16));
    }
  };

  // Fetch Analytics Data
  const fetchAnalytics = useCallback(async (isRefresh = false) => {
    if (isRefresh) {
      setRefreshing(true);
    } else {
      setLoading(true);
    }
    setError(null);

    try {
      const fromIso = fromDate ? new Date(fromDate).toISOString() : undefined;
      const toIso = toDate ? new Date(toDate).toISOString() : undefined;

      const params = {};
      if (fromIso) params.from = fromIso;
      if (toIso) params.to = toIso;
      if (selectedDeptId) params.departmentId = selectedDeptId;
      if (selectedAssetId) params.assetId = selectedAssetId;

      const [overviewRes, amcRes, hfiRes, gubRes] = await Promise.all([
        analyticsService.getOverview(params),
        analyticsService.getAmc(params),
        analyticsService.getHfi(params),
        analyticsService.getGub({
          from: fromIso,
          to: toIso,
          departmentId: selectedDeptId || undefined,
        }),
      ]);

      setOverview(overviewRes);
      setAmcData(amcRes);
      setHfiData(hfiRes);
      setGubData(gubRes);
    } catch (err) {
      console.error('Failed to fetch analytics:', err);
      setError(
        err.response?.data?.message ||
        err.message ||
        'Failed to fetch database analytics. Please verify your connection.'
      );
    } finally {
      setLoading(false);
      setRefreshing(false);
    }
  }, [fromDate, toDate, selectedDeptId, selectedAssetId]);

  // Trigger on filter changes
  useEffect(() => {
    fetchAnalytics();
  }, [fetchAnalytics]);

  // Helper formatting functions
  const formatSeconds = (sec) => {
    if (sec == null || isNaN(sec)) return '0s';
    if (sec < 60) return `${Math.round(sec)}s`;
    if (sec < 3600) return `${Math.round(sec / 60)}m ${Math.round(sec % 60)}s`;
    const hrs = Math.floor(sec / 3600);
    const mins = Math.round((sec % 3600) / 60);
    return `${hrs}h ${mins}m`;
  };

  return (
    <div className="d-flex flex-column gap-4 pb-5">
      {/* Header */}
      <div className="d-flex flex-column flex-md-row justify-content-between align-items-md-center gap-3">
        <div>
          <h1 className="fs-3 fw-bold text-dark mb-1">
            Clinical Asset Tracking &amp; Utilization Analytics
          </h1>
          <p className="text-muted mb-0" style={{ fontSize: '0.875rem' }}>
            Authoritative, database-backed intelligence: Asset Movement Continuity (AMC), Handoff Friction Index (HFI), and Geo-Utilization Balance (GUB).
          </p>
        </div>
        <div className="d-flex align-items-center gap-2">
          <button
            type="button"
            className="btn btn-outline-secondary d-flex align-items-center gap-2"
            onClick={() => fetchAnalytics(true)}
            disabled={loading || refreshing}
          >
            <span
              className={`material-symbols-outlined fs-5 ${refreshing ? 'spin' : ''}`}
            >
              refresh
            </span>
            <span>{refreshing ? 'Updating...' : 'Refresh'}</span>
          </button>
        </div>
      </div>

      {/* Filter Controls Bar */}
      <div className="stitch-card p-3 p-md-4">
        <div className="row g-3 align-items-end">
          {/* Preset Buttons */}
          <div className="col-12 col-xl-4">
            <label className="form-label text-muted fw-semibold mb-1" style={{ fontSize: '0.75rem', textTransform: 'uppercase' }}>
              Time Range Preset
            </label>
            <div className="btn-group w-100" role="group">
              <button
                type="button"
                className={`btn btn-sm ${datePreset === '24h' ? 'btn-primary' : 'btn-outline-secondary'}`}
                onClick={() => handlePresetChange('24h')}
              >
                24 Hours
              </button>
              <button
                type="button"
                className={`btn btn-sm ${datePreset === '7d' ? 'btn-primary' : 'btn-outline-secondary'}`}
                onClick={() => handlePresetChange('7d')}
              >
                Last 7 Days
              </button>
              <button
                type="button"
                className={`btn btn-sm ${datePreset === '30d' ? 'btn-primary' : 'btn-outline-secondary'}`}
                onClick={() => handlePresetChange('30d')}
              >
                Last 30 Days
              </button>
              <button
                type="button"
                className={`btn btn-sm ${datePreset === 'custom' ? 'btn-primary' : 'btn-outline-secondary'}`}
                onClick={() => setDatePreset('custom')}
              >
                Custom
              </button>
            </div>
          </div>

          {/* Date Pickers */}
          <div className="col-6 col-sm-6 col-xl-2">
            <label className="form-label text-muted fw-semibold mb-1" style={{ fontSize: '0.75rem', textTransform: 'uppercase' }}>
              From (UTC)
            </label>
            <input
              type="datetime-local"
              className="form-control form-control-sm"
              value={fromDate}
              onChange={(e) => {
                setFromDate(e.target.value);
                setDatePreset('custom');
              }}
            />
          </div>

          <div className="col-6 col-sm-6 col-xl-2">
            <label className="form-label text-muted fw-semibold mb-1" style={{ fontSize: '0.75rem', textTransform: 'uppercase' }}>
              To (UTC)
            </label>
            <input
              type="datetime-local"
              className="form-control form-control-sm"
              value={toDate}
              onChange={(e) => {
                setToDate(e.target.value);
                setDatePreset('custom');
              }}
            />
          </div>

          {/* Department Filter */}
          <div className="col-6 col-sm-6 col-xl-2">
            <label className="form-label text-muted fw-semibold mb-1" style={{ fontSize: '0.75rem', textTransform: 'uppercase' }}>
              Department
            </label>
            <select
              className="form-select form-select-sm"
              value={selectedDeptId}
              onChange={(e) => setSelectedDeptId(e.target.value)}
            >
              <option value="">All Departments</option>
              {departments.map((d) => (
                <option key={d.id} value={d.id}>
                  {d.name} ({d.code})
                </option>
              ))}
            </select>
          </div>

          {/* Asset Filter */}
          <div className="col-6 col-sm-6 col-xl-2">
            <label className="form-label text-muted fw-semibold mb-1" style={{ fontSize: '0.75rem', textTransform: 'uppercase' }}>
              Asset
            </label>
            <select
              className="form-select form-select-sm"
              value={selectedAssetId}
              onChange={(e) => setSelectedAssetId(e.target.value)}
            >
              <option value="">All Assets</option>
              {assets.map((a) => (
                <option key={a.id} value={a.id}>
                  {a.assetCode} - {a.assetName || a.name}
                </option>
              ))}
            </select>
          </div>
        </div>
      </div>

      {/* Error Banner */}
      {error && (
        <div className="alert alert-danger d-flex align-items-center gap-2 mb-0" role="alert">
          <span className="material-symbols-outlined fs-5">error</span>
          <div>{error}</div>
        </div>
      )}

      {/* Three Unique Metrics: KPI Summary Cards */}
      <div className="row g-3 g-lg-4">
        {/* AMC Card */}
        <div className="col-12 col-md-4">
          <div className="stitch-card p-4 h-100 position-relative overflow-hidden">
            <div className="d-flex justify-content-between align-items-start mb-2">
              <span
                className="text-uppercase fw-semibold"
                style={{ fontSize: '0.75rem', color: 'var(--outline)', letterSpacing: '0.05em' }}
              >
                Asset Movement Continuity
              </span>
              <span className="badge rounded-pill bg-teal text-white" style={{ fontSize: '0.7rem' }}>
                AMC
              </span>
            </div>

            <div className="d-flex align-items-baseline gap-2 mt-1">
              <span className="fs-2 fw-bold text-dark" style={{ letterSpacing: '-0.02em' }}>
                {amcData?.continuityPercentage != null ? `${amcData.continuityPercentage}%` : 'N/A'}
              </span>
              <span
                className="badge"
                style={{
                  backgroundColor:
                    amcData?.status === 'MEASURED'
                      ? 'var(--success-container)'
                      : 'var(--surface-container-high)',
                  color:
                    amcData?.status === 'MEASURED'
                      ? 'var(--on-success-container)'
                      : 'var(--outline)',
                  fontSize: '0.75rem',
                }}
              >
                {amcData?.status || 'NO_DATA'}
              </span>
            </div>

            <div className="progress my-2" style={{ height: '6px', borderRadius: '3px' }}>
              <div
                className="progress-bar"
                role="progressbar"
                style={{
                  width: `${amcData?.continuityPercentage || 0}%`,
                  backgroundColor:
                    (amcData?.continuityPercentage || 0) >= 90
                      ? 'var(--success)'
                      : (amcData?.continuityPercentage || 0) >= 70
                      ? 'var(--primary)'
                      : 'var(--error)',
                }}
              />
            </div>

            <div className="d-flex justify-content-between text-muted mt-2" style={{ fontSize: '0.75rem' }}>
              <span>
                Updates: <strong>{amcData?.successfulGpsUpdates || 0}</strong> / {amcData?.expectedGpsUpdates || 0}
              </span>
              <span>
                Gaps: <strong>{amcData?.trackingGapsCount || 0}</strong>
              </span>
            </div>

            <p className="mt-2 mb-0 text-muted" style={{ fontSize: '0.725rem', lineHeight: '1.4' }}>
              <strong>Formula:</strong> (Successful GPS Updates / Expected Updates) × 100 based on active {amcData?.telemetryIntervalSeconds || 5}s telemetry transmission cadence.
            </p>
          </div>
        </div>

        {/* HFI Card */}
        <div className="col-12 col-md-4">
          <div className="stitch-card p-4 h-100 position-relative overflow-hidden">
            <div className="d-flex justify-content-between align-items-start mb-2">
              <span
                className="text-uppercase fw-semibold"
                style={{ fontSize: '0.75rem', color: 'var(--outline)', letterSpacing: '0.05em' }}
              >
                Handoff Friction Index
              </span>
              <span className="badge rounded-pill bg-primary text-white" style={{ fontSize: '0.7rem' }}>
                HFI
              </span>
            </div>

            <div className="d-flex align-items-baseline gap-2 mt-1">
              <span className="fs-2 fw-bold text-dark" style={{ letterSpacing: '-0.02em' }}>
                {hfiData?.overallHfi != null ? Number(hfiData.overallHfi).toFixed(1) : 'N/A'}
              </span>
              <span
                className="badge"
                style={{
                  backgroundColor:
                    hfiData?.overallHfi != null && hfiData.overallHfi < 30
                      ? 'var(--success-container)'
                      : hfiData?.overallHfi != null && hfiData.overallHfi < 60
                      ? 'var(--tertiary-container)'
                      : 'var(--surface-container-high)',
                  color:
                    hfiData?.overallHfi != null && hfiData.overallHfi < 30
                      ? 'var(--on-success-container)'
                      : hfiData?.overallHfi != null && hfiData.overallHfi < 60
                      ? 'var(--on-tertiary-container)'
                      : 'var(--outline)',
                  fontSize: '0.75rem',
                }}
              >
                {hfiData?.overallHfi != null
                  ? hfiData.overallHfi < 25
                    ? 'OPTIMAL'
                    : hfiData.overallHfi < 50
                    ? 'MODERATE'
                    : 'HIGH FRICTION'
                  : 'NO DATA'}
              </span>
            </div>

            <div className="progress my-2" style={{ height: '6px', borderRadius: '3px' }}>
              <div
                className="progress-bar"
                role="progressbar"
                style={{
                  width: `${Math.min(100, hfiData?.overallHfi || 0)}%`,
                  backgroundColor:
                    (hfiData?.overallHfi || 0) > 50 ? 'var(--error)' : 'var(--primary)',
                }}
              />
            </div>

            <div className="d-flex justify-content-between text-muted mt-2" style={{ fontSize: '0.75rem' }}>
              <span>
                Transfers: <strong>{hfiData?.completedTransferCount || 0}</strong> completed
              </span>
              <span>
                Avg Delay: <strong>{formatSeconds(hfiData?.averageHandoffDelaySeconds)}</strong>
              </span>
            </div>

            <p className="mt-2 mb-0 text-muted" style={{ fontSize: '0.725rem', lineHeight: '1.4' }}>
              <strong>Formula:</strong> 0.40·S_dur + 0.35·S_delay + 0.25·S_gap. Scale 0 (frictionless) to 100 (maximum friction).
            </p>
          </div>
        </div>

        {/* GUB Card */}
        <div className="col-12 col-md-4">
          <div className="stitch-card p-4 h-100 position-relative overflow-hidden">
            <div className="d-flex justify-content-between align-items-start mb-2">
              <span
                className="text-uppercase fw-semibold"
                style={{ fontSize: '0.75rem', color: 'var(--outline)', letterSpacing: '0.05em' }}
              >
                Geo-Utilization Balance
              </span>
              <span className="badge rounded-pill bg-dark text-white" style={{ fontSize: '0.7rem' }}>
                GUB
              </span>
            </div>

            <div className="d-flex align-items-baseline gap-2 mt-1">
              <span className="fs-2 fw-bold text-dark" style={{ letterSpacing: '-0.02em' }}>
                {gubData?.hospitalEquilibriumScore != null
                  ? `${gubData.hospitalEquilibriumScore}%`
                  : 'N/A'}
              </span>
              <span
                className="badge"
                style={{
                  backgroundColor: 'var(--secondary-container)',
                  color: 'var(--on-secondary-container)',
                  fontSize: '0.75rem',
                }}
              >
                {gubData?.status || 'BALANCED'}
              </span>
            </div>

            <div className="progress my-2" style={{ height: '6px', borderRadius: '3px' }}>
              <div
                className="progress-bar"
                role="progressbar"
                style={{
                  width: `${gubData?.hospitalEquilibriumScore || 0}%`,
                  backgroundColor: 'var(--secondary)',
                }}
              />
            </div>

            <div className="d-flex justify-content-between text-muted mt-2" style={{ fontSize: '0.75rem' }}>
              <span>
                Total Usable Assets: <strong>{gubData?.totalHospitalUsableAssets || 0}</strong>
              </span>
              <span>
                Total Demand: <strong>{gubData?.totalHospitalDemand || 0}</strong>
              </span>
            </div>

            <p className="mt-2 mb-0 text-muted" style={{ fontSize: '0.725rem', lineHeight: '1.4' }}>
              <strong>Formula:</strong> ((Supply - Demand) / (Supply + Demand)) × 100. Equilibrium score reflects inter-departmental distribution balance.
            </p>
          </div>
        </div>
      </div>

      {/* Tabs Navigation */}
      <ul className="nav nav-pills border-bottom pb-2 gap-2">
        <li className="nav-item">
          <button
            type="button"
            className={`btn btn-sm ${activeTab === 'overview' ? 'btn-primary' : 'btn-light text-muted'}`}
            onClick={() => setActiveTab('overview')}
          >
            Overview &amp; Visualizations
          </button>
        </li>
        <li className="nav-item">
          <button
            type="button"
            className={`btn btn-sm ${activeTab === 'amc' ? 'btn-primary' : 'btn-light text-muted'}`}
            onClick={() => setActiveTab('amc')}
          >
            AMC Movement Continuity
          </button>
        </li>
        <li className="nav-item">
          <button
            type="button"
            className={`btn btn-sm ${activeTab === 'hfi' ? 'btn-primary' : 'btn-light text-muted'}`}
            onClick={() => setActiveTab('hfi')}
          >
            HFI Handoff Friction
          </button>
        </li>
        <li className="nav-item">
          <button
            type="button"
            className={`btn btn-sm ${activeTab === 'gub' ? 'btn-primary' : 'btn-light text-muted'}`}
            onClick={() => setActiveTab('gub')}
          >
            GUB Geo-Utilization Balance
          </button>
        </li>
      </ul>

      {/* TAB 1: OVERVIEW & VISUALIZATIONS */}
      {activeTab === 'overview' && (
        <div className="d-flex flex-column gap-4">
          {/* AMC & GUB Charts Row */}
          <div className="row g-4">
            {/* Chart 1: AMC Continuity Over Time */}
            <div className="col-12 col-lg-6">
              <div className="stitch-card p-4 h-100">
                <div className="d-flex justify-content-between align-items-center mb-3">
                  <div>
                    <h2 className="fs-6 fw-bold mb-0">AMC: Tracking Continuity Over Time</h2>
                    <span className="text-muted" style={{ fontSize: '0.75rem' }}>
                      Telemetry packet success rate across active GPS sessions
                    </span>
                  </div>
                  <span className="badge bg-light text-dark">Continuity %</span>
                </div>

                {amcData?.timeSeries && amcData.timeSeries.length > 0 ? (
                  <div style={{ width: '100%', height: 260 }}>
                    <ResponsiveContainer>
                      <AreaChart data={amcData.timeSeries}>
                        <defs>
                          <linearGradient id="amcGradient" x1="0" y1="0" x2="0" y2="1">
                            <stop offset="5%" stopColor="var(--primary)" stopOpacity={0.8} />
                            <stop offset="95%" stopColor="var(--primary)" stopOpacity={0.0} />
                          </linearGradient>
                        </defs>
                        <CartesianGrid strokeDasharray="3 3" vertical={false} stroke="var(--outline-variant)" />
                        <XAxis dataKey="dateLabel" tick={{ fontSize: 11 }} />
                        <YAxis domain={[0, 100]} tick={{ fontSize: 11 }} unit="%" />
                        <Tooltip
                          formatter={(val) => [`${val}%`, 'Continuity']}
                          labelStyle={{ fontWeight: 'bold' }}
                        />
                        <Area
                          type="monotone"
                          dataKey="continuityPercentage"
                          stroke="var(--primary)"
                          strokeWidth={2}
                          fillOpacity={1}
                          fill="url(#amcGradient)"
                          name="Continuity %"
                        />
                      </AreaChart>
                    </ResponsiveContainer>
                  </div>
                ) : (
                  <div
                    className="p-5 text-center text-muted d-flex flex-column align-items-center justify-content-center"
                    style={{ minHeight: '260px', backgroundColor: 'var(--surface-container-low)', borderRadius: '12px' }}
                  >
                    <span className="material-symbols-outlined fs-2 text-muted mb-2">signal_cellular_off</span>
                    <span className="fw-medium">No telemetry samples in selected window</span>
                    <small className="text-muted">Start a physical GPS session or widen the date filter.</small>
                  </div>
                )}
              </div>
            </div>

            {/* Chart 2: GUB Department Supply vs Demand */}
            <div className="col-12 col-lg-6">
              <div className="stitch-card p-4 h-100">
                <div className="d-flex justify-content-between align-items-center mb-3">
                  <div>
                    <h2 className="fs-6 fw-bold mb-0">GUB: Department Supply vs Demand</h2>
                    <span className="text-muted" style={{ fontSize: '0.75rem' }}>
                      Usable equipment units vs transfer demand &amp; assignments
                    </span>
                  </div>
                  <span className="badge bg-light text-dark">Units</span>
                </div>

                {gubData?.departments && gubData.departments.length > 0 ? (
                  <div style={{ width: '100%', height: 260 }}>
                    <ResponsiveContainer>
                      <BarChart data={gubData.departments}>
                        <CartesianGrid strokeDasharray="3 3" vertical={false} stroke="var(--outline-variant)" />
                        <XAxis dataKey="departmentCode" tick={{ fontSize: 11 }} />
                        <YAxis tick={{ fontSize: 11 }} />
                        <Tooltip />
                        <Legend wrapperStyle={{ fontSize: 11 }} />
                        <Bar dataKey="usableAssetCount" fill="var(--primary)" name="Usable Supply" radius={[4, 4, 0, 0]} />
                        <Bar dataKey="demandCount" fill="#e11d48" name="Department Demand" radius={[4, 4, 0, 0]} />
                      </BarChart>
                    </ResponsiveContainer>
                  </div>
                ) : (
                  <div
                    className="p-5 text-center text-muted d-flex flex-column align-items-center justify-content-center"
                    style={{ minHeight: '260px', backgroundColor: 'var(--surface-container-low)', borderRadius: '12px' }}
                  >
                    <span className="material-symbols-outlined fs-2 text-muted mb-2">balance</span>
                    <span className="fw-medium">No departmental equipment records available</span>
                  </div>
                )}
              </div>
            </div>
          </div>

          {/* Chart 3: HFI Friction by Department */}
          <div className="stitch-card p-4">
            <div className="d-flex justify-content-between align-items-center mb-3">
              <div>
                <h2 className="fs-6 fw-bold mb-0">HFI: Transfer Handoff Friction by Department</h2>
                <span className="text-muted" style={{ fontSize: '0.75rem' }}>
                  Average composite friction score across transfer source and destination hubs
                </span>
              </div>
              <span className="badge bg-light text-dark">Friction Score (0-100)</span>
            </div>

            {hfiData?.departmentBreakdown && hfiData.departmentBreakdown.length > 0 ? (
              <div style={{ width: '100%', height: 260 }}>
                <ResponsiveContainer>
                  <BarChart data={hfiData.departmentBreakdown}>
                    <CartesianGrid strokeDasharray="3 3" vertical={false} stroke="var(--outline-variant)" />
                    <XAxis dataKey="departmentName" tick={{ fontSize: 11 }} />
                    <YAxis domain={[0, 100]} tick={{ fontSize: 11 }} />
                    <Tooltip />
                    <Bar
                      dataKey="averageHfi"
                      fill="var(--tertiary)"
                      name="Avg HFI Score"
                      radius={[4, 4, 0, 0]}
                    />
                  </BarChart>
                </ResponsiveContainer>
              </div>
            ) : (
              <div
                className="p-5 text-center text-muted d-flex flex-column align-items-center justify-content-center"
                style={{ minHeight: '200px', backgroundColor: 'var(--surface-container-low)', borderRadius: '12px' }}
              >
                <span className="material-symbols-outlined fs-2 text-muted mb-2">sync_problem</span>
                <span className="fw-medium">No completed transfers recorded in this period</span>
                <small className="text-muted">Complete an asset transfer to generate friction metrics.</small>
              </div>
            )}
          </div>
        </div>
      )}

      {/* TAB 2: AMC MOVEMENT CONTINUITY DEEP DIVE */}
      {activeTab === 'amc' && (
        <div className="d-flex flex-column gap-4">
          <div className="row g-3">
            <div className="col-6 col-md-3">
              <div className="stitch-card p-3">
                <span className="text-muted fw-semibold" style={{ fontSize: '0.75rem' }}>SUCCESSFUL UPDATES</span>
                <div className="fs-3 fw-bold text-dark mt-1">{amcData?.successfulGpsUpdates || 0}</div>
                <small className="text-muted">Accepted LocationHistory</small>
              </div>
            </div>
            <div className="col-6 col-md-3">
              <div className="stitch-card p-3">
                <span className="text-muted fw-semibold" style={{ fontSize: '0.75rem' }}>EXPECTED UPDATES</span>
                <div className="fs-3 fw-bold text-dark mt-1">{amcData?.expectedGpsUpdates || 0}</div>
                <small className="text-muted">Based on active sessions &amp; 5s interval</small>
              </div>
            </div>
            <div className="col-6 col-md-3">
              <div className="stitch-card p-3">
                <span className="text-muted fw-semibold" style={{ fontSize: '0.75rem' }}>ACTIVE DURATION</span>
                <div className="fs-3 fw-bold text-dark mt-1">{formatSeconds(amcData?.activeTrackingDurationSeconds)}</div>
                <small className="text-muted">Across {amcData?.sessionCount || 0} GPS sessions</small>
              </div>
            </div>
            <div className="col-6 col-md-3">
              <div className="stitch-card p-3">
                <span className="text-muted fw-semibold" style={{ fontSize: '0.75rem' }}>TRACKED ASSETS</span>
                <div className="fs-3 fw-bold text-dark mt-1">{amcData?.assetsTrackedCount || 0}</div>
                <small className="text-muted">Distinct clinical assets</small>
              </div>
            </div>
          </div>

          {/* GPS Accuracy & Gaps */}
          <div className="stitch-card p-4">
            <h2 className="fs-6 fw-bold mb-3">GPS Telemetry Quality &amp; Accuracy Spectrum</h2>
            <div className="row g-3">
              <div className="col-12 col-md-4">
                <div className="p-3 rounded-3" style={{ backgroundColor: 'var(--surface-container-low)' }}>
                  <div className="text-muted" style={{ fontSize: '0.75rem' }}>BEST ACCURACY (MIN ERROR)</div>
                  <div className="fs-4 fw-bold text-success mt-1">
                    {amcData?.bestAccuracyMeters != null ? `${amcData.bestAccuracyMeters} m` : 'N/A'}
                  </div>
                  <small className="text-muted">High-precision satellite lock</small>
                </div>
              </div>
              <div className="col-12 col-md-4">
                <div className="p-3 rounded-3" style={{ backgroundColor: 'var(--surface-container-low)' }}>
                  <div className="text-muted" style={{ fontSize: '0.75rem' }}>AVERAGE ACCURACY</div>
                  <div className="fs-4 fw-bold text-primary mt-1">
                    {amcData?.averageAccuracyMeters != null ? `${amcData.averageAccuracyMeters} m` : 'N/A'}
                  </div>
                  <small className="text-muted">Mean radius across updates</small>
                </div>
              </div>
              <div className="col-12 col-md-4">
                <div className="p-3 rounded-3" style={{ backgroundColor: 'var(--surface-container-low)' }}>
                  <div className="text-muted" style={{ fontSize: '0.75rem' }}>WORST ACCURACY (MAX ERROR)</div>
                  <div className="fs-4 fw-bold text-warning mt-1">
                    {amcData?.worstAccuracyMeters != null ? `${amcData.worstAccuracyMeters} m` : 'N/A'}
                  </div>
                  <small className="text-muted">Signal degradation / urban canyon</small>
                </div>
              </div>
            </div>

            <div className="mt-4 pt-3 border-top d-flex flex-column flex-md-row justify-content-between align-items-md-center gap-2">
              <div>
                <span className="fw-semibold text-dark">Telemetry Gaps (&gt;15s without update):</span>{' '}
                <span className="badge bg-secondary ms-1">{amcData?.trackingGapsCount || 0} gap events detected</span>
              </div>
              <div className="text-muted" style={{ fontSize: '0.85rem' }}>
                Cumulative Gap Duration: <strong>{formatSeconds(amcData?.totalGapDurationSeconds)}</strong>
              </div>
            </div>
          </div>
        </div>
      )}

      {/* TAB 3: HFI HANDOFF FRICTION DEEP DIVE */}
      {activeTab === 'hfi' && (
        <div className="d-flex flex-column gap-4">
          {/* Weights & Formula Definition */}
          <div className="stitch-card p-4">
            <h2 className="fs-6 fw-bold mb-1">HFI Deterministic Lifecycle Formulation</h2>
            <p className="text-muted mb-3" style={{ fontSize: '0.8rem' }}>
              Composite metric quantifying friction across handoff dispatch, transit execution, and telemetry continuity.
            </p>

            <div className="row g-3 text-center">
              <div className="col-12 col-md-4">
                <div className="p-3 rounded-3 border">
                  <div className="fw-bold text-primary" style={{ fontSize: '1.25rem' }}>40%</div>
                  <div className="fw-semibold text-dark" style={{ fontSize: '0.85rem' }}>Transit Duration Score (S_dur)</div>
                  <small className="text-muted">Normalized vs 60m MedTrack project-configured reference threshold. Actual score: {hfiData?.durationComponentScore?.toFixed(1) || 0}</small>
                </div>
              </div>
              <div className="col-12 col-md-4">
                <div className="p-3 rounded-3 border">
                  <div className="fw-bold text-primary" style={{ fontSize: '1.25rem' }}>35%</div>
                  <div className="fw-semibold text-dark" style={{ fontSize: '0.85rem' }}>Handoff Delay Score (S_delay)</div>
                  <small className="text-muted">Acceptance-to-start vs 45m MedTrack project-configured reference threshold. Actual: {hfiData?.delayComponentScore?.toFixed(1) || 0}</small>
                </div>
              </div>
              <div className="col-12 col-md-4">
                <div className="p-3 rounded-3 border">
                  <div className="fw-bold text-primary" style={{ fontSize: '1.25rem' }}>25%</div>
                  <div className="fw-semibold text-dark" style={{ fontSize: '0.85rem' }}>Tracking Gap Score (S_gap)</div>
                  <small className="text-muted">Telemetry outage ratio during transit. Actual: {hfiData?.gapComponentScore?.toFixed(1) || 0}</small>
                </div>
              </div>
            </div>
          </div>

          {/* Transfer Breakdown Table */}
          <div className="stitch-card p-4">
            <h2 className="fs-6 fw-bold mb-3">Individual Transfer Friction Audits</h2>
            {hfiData?.transfers && hfiData.transfers.length > 0 ? (
              <div className="table-responsive">
                <table className="table table-hover align-middle mb-0" style={{ fontSize: '0.85rem' }}>
                  <thead className="table-light">
                    <tr>
                      <th>Transfer ID</th>
                      <th>Asset</th>
                      <th>Source Dept</th>
                      <th>Destination Dept</th>
                      <th>Transit Time</th>
                      <th>Handoff Delay</th>
                      <th>Telemetry Gap %</th>
                      <th className="text-end">HFI Score</th>
                    </tr>
                  </thead>
                  <tbody>
                    {hfiData.transfers.map((t) => (
                      <tr key={t.transferId}>
                        <td className="fw-bold">#{t.transferId}</td>
                        <td>
                          <span className="badge bg-light text-dark border me-1">{t.assetCode}</span>
                          <span>{t.assetName}</span>
                        </td>
                        <td>{t.sourceDepartmentName || 'N/A'}</td>
                        <td>{t.destinationDepartmentName || 'N/A'}</td>
                        <td>{formatSeconds(t.transferDurationSeconds)}</td>
                        <td>{formatSeconds(t.handoffDelaySeconds)}</td>
                        <td>{((t.trackingGapRatio || 0) * 100).toFixed(1)}%</td>
                        <td className="text-end">
                          <span
                            className="badge"
                            style={{
                              backgroundColor:
                                t.hfiScore > 50 ? 'var(--error-container)' : 'var(--success-container)',
                              color:
                                t.hfiScore > 50 ? 'var(--on-error-container)' : 'var(--on-success-container)',
                              fontSize: '0.8rem',
                            }}
                          >
                            {t.hfiScore.toFixed(1)}
                          </span>
                        </td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              </div>
            ) : (
              <div className="p-4 text-center text-muted">
                No completed transfer lifecycle records in selected filter range.
              </div>
            )}
          </div>
        </div>
      )}

      {/* TAB 4: GUB GEO-UTILIZATION BALANCE DEEP DIVE */}
      {activeTab === 'gub' && (
        <div className="d-flex flex-column gap-4">
          {/* Department Balance Table */}
          <div className="stitch-card p-4">
            <div className="d-flex justify-content-between align-items-center mb-3">
              <div>
                <h2 className="fs-6 fw-bold mb-0">Department Supply vs Demand Balances</h2>
                <span className="text-muted" style={{ fontSize: '0.75rem' }}>
                  Surplus (&gt;+10), Balanced (-10 to +10), or Deficit (&lt;-10)
                </span>
              </div>
            </div>

            {gubData?.departments && gubData.departments.length > 0 ? (
              <div className="table-responsive">
                <table className="table table-hover align-middle mb-0" style={{ fontSize: '0.85rem' }}>
                  <thead className="table-light">
                    <tr>
                      <th>Department</th>
                      <th>Usable Supply</th>
                      <th>Available</th>
                      <th>Assigned</th>
                      <th>In Use</th>
                      <th>Inbound Transfers</th>
                      <th>Outbound Transfers</th>
                      <th>Total Demand</th>
                      <th>Balance Score</th>
                      <th className="text-end">Status</th>
                    </tr>
                  </thead>
                  <tbody>
                    {gubData.departments.map((d) => (
                      <tr key={d.departmentId}>
                        <td>
                          <strong>{d.departmentName}</strong>{' '}
                          <span className="text-muted">({d.departmentCode})</span>
                        </td>
                        <td className="fw-bold text-primary">{d.usableAssetCount}</td>
                        <td>{d.availableAssetCount}</td>
                        <td>{d.assignedAssetCount}</td>
                        <td>{d.inUseAssetCount}</td>
                        <td>
                          <span className="badge bg-light text-dark border">+{d.inboundTransferCount}</span>
                        </td>
                        <td>
                          <span className="badge bg-light text-dark border">-{d.outboundTransferCount}</span>
                        </td>
                        <td className="fw-bold text-dark">{d.demandCount}</td>
                        <td>
                          <span className="fw-semibold">
                            {d.balanceScore > 0 ? `+${d.balanceScore}` : d.balanceScore}
                          </span>
                        </td>
                        <td className="text-end">
                          <span
                            className="badge"
                            style={{
                              backgroundColor:
                                d.status === 'SURPLUS'
                                  ? 'var(--secondary-container)'
                                  : d.status === 'DEFICIT'
                                  ? 'var(--error-container)'
                                  : 'var(--success-container)',
                              color:
                                d.status === 'SURPLUS'
                                  ? 'var(--on-secondary-container)'
                                  : d.status === 'DEFICIT'
                                  ? 'var(--on-error-container)'
                                  : 'var(--on-success-container)',
                            }}
                          >
                            {d.status}
                          </span>
                        </td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              </div>
            ) : (
              <div className="p-4 text-center text-muted">
                No departmental data found.
              </div>
            )}
          </div>
        </div>
      )}
    </div>
  );
}
