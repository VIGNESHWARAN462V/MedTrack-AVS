/**
 * GPS Telemetry and Freshness Evaluation Utility.
 *
 * Provides authoritative classification of outdoor GPS telemetry state:
 * - NO GPS FIX: When coordinates are absent, null, or invalid.
 * - GPS ACTIVE: Valid coordinates with fresh telemetry within 5 minutes.
 * - RECENT FIX: Valid coordinates with telemetry within 60 minutes.
 * - STALE GPS: Valid coordinates with telemetry older than 60 minutes or missing timestamp.
 */

export function getGpsTelemetryStatus(asset) {
  if (!asset) {
    return {
      status: 'NO_FIX',
      label: 'NO GPS FIX',
      badgeClass: 'bg-secondary-subtle text-secondary border border-secondary-subtle',
      hasFix: false,
      isFresh: false,
      lastUpdateText: 'No GPS fix recorded',
      relativeTime: null,
      formattedTime: null,
    };
  }

  const lat = Number(asset.latitude);
  const lon = Number(asset.longitude);

  // Validate finite and geographic coordinate bounds (-90..+90, -180..+180)
  if (
    asset.latitude == null ||
    asset.longitude == null ||
    !Number.isFinite(lat) ||
    !Number.isFinite(lon) ||
    lat < -90 ||
    lat > 90 ||
    lon < -180 ||
    lon > 180
  ) {
    return {
      status: 'NO_FIX',
      label: 'NO GPS FIX',
      badgeClass: 'bg-secondary-subtle text-secondary border border-secondary-subtle',
      hasFix: false,
      isFresh: false,
      lastUpdateText: 'No GPS fix recorded',
      relativeTime: null,
      formattedTime: null,
    };
  }

  // Telemetry timestamp check
  const timestamp = asset.lastSeen || asset.recordedAt;
  if (!timestamp) {
    return {
      status: 'STALE',
      label: 'STALE GPS',
      badgeClass: 'bg-warning-subtle text-warning-emphasis border border-warning-subtle',
      hasFix: true,
      isFresh: false,
      lastUpdateText: 'Timestamp unavailable',
      relativeTime: null,
      formattedTime: null,
    };
  }

  const date = new Date(timestamp);
  const time = date.getTime();

  if (!Number.isFinite(time)) {
    return {
      status: 'STALE',
      label: 'STALE GPS',
      badgeClass: 'bg-warning-subtle text-warning-emphasis border border-warning-subtle',
      hasFix: true,
      isFresh: false,
      lastUpdateText: 'Invalid timestamp',
      relativeTime: null,
      formattedTime: null,
    };
  }

  const now = Date.now();
  const diffMs = now - time;

  // Handle future timestamps (device clock skew > 1 min)
  if (diffMs < -60000) {
    return {
      status: 'STALE',
      label: 'STALE GPS',
      badgeClass: 'bg-warning-subtle text-warning-emphasis border border-warning-subtle',
      hasFix: true,
      isFresh: false,
      lastUpdateText: 'Clock skewed (future)',
      relativeTime: null,
      formattedTime: date.toLocaleString(),
    };
  }

  // Slight negative diff (< 1 min) is normalized to 0 (just now)
  const safeDiffMs = Math.max(0, diffMs);
  const diffMins = Math.floor(safeDiffMs / (1000 * 60));

  let relative = '';
  if (diffMins < 1) relative = 'Just now';
  else if (diffMins < 60) relative = `${diffMins}m ago`;
  else {
    const diffHours = Math.floor(diffMins / 60);
    if (diffHours < 24) relative = `${diffHours}h ago`;
    else {
      const diffDays = Math.floor(diffHours / 24);
      relative = `${diffDays}d ago`;
    }
  }

  if (diffMins <= 5) {
    return {
      status: 'ACTIVE',
      label: 'GPS ACTIVE',
      badgeClass: 'bg-success-subtle text-success border border-success-subtle',
      hasFix: true,
      isFresh: true,
      lastUpdateText: `Updated ${relative}`,
      relativeTime: relative,
      formattedTime: date.toLocaleString(),
    };
  }

  if (diffMins <= 60) {
    return {
      status: 'RECENT',
      label: 'RECENT FIX',
      badgeClass: 'bg-info-subtle text-info-emphasis border border-info-subtle',
      hasFix: true,
      isFresh: true,
      lastUpdateText: `Updated ${relative}`,
      relativeTime: relative,
      formattedTime: date.toLocaleString(),
    };
  }

  return {
    status: 'STALE',
    label: 'STALE GPS',
    badgeClass: 'bg-warning-subtle text-warning-emphasis border border-warning-subtle',
    hasFix: true,
    isFresh: false,
    lastUpdateText: `Stale (${relative})`,
    relativeTime: relative,
    formattedTime: date.toLocaleString(),
  };
}
