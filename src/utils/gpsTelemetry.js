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

  const accuracy = parseValidAccuracy(asset.gpsAccuracy);
  const accCat = getGpsAccuracyCategory(accuracy);

  if (diffMins <= 5) {
    if (accCat.category === 'LOW') {
      return {
        status: 'ACTIVE_COARSE',
        label: accCat.label,
        badgeClass: accCat.badgeClass,
        hasFix: true,
        isFresh: true,
        isLowPrecision: true,
        accuracyCategory: 'LOW',
        accuracy,
        lastUpdateText: `Updated ${relative}`,
        relativeTime: relative,
        formattedTime: date.toLocaleString(),
      };
    }
    if (accCat.category === 'MEDIUM') {
      return {
        status: 'ACTIVE_MEDIUM',
        label: accCat.label,
        badgeClass: accCat.badgeClass,
        hasFix: true,
        isFresh: true,
        isLowPrecision: false,
        accuracyCategory: 'MEDIUM',
        accuracy,
        lastUpdateText: `Updated ${relative}`,
        relativeTime: relative,
        formattedTime: date.toLocaleString(),
      };
    }
    if (accCat.category === 'HIGH') {
      return {
        status: 'ACTIVE_HIGH',
        label: accCat.label,
        badgeClass: accCat.badgeClass,
        hasFix: true,
        isFresh: true,
        isLowPrecision: false,
        accuracyCategory: 'HIGH',
        accuracy,
        lastUpdateText: `Updated ${relative}`,
        relativeTime: relative,
        formattedTime: date.toLocaleString(),
      };
    }
    // UNKNOWN accuracy: fresh coordinates exist, but accuracy is absent, negative, or non-finite
    return {
      status: 'ACTIVE_UNKNOWN_ACCURACY',
      label: accCat.label,
      badgeClass: accCat.badgeClass,
      hasFix: true,
      isFresh: true,
      isLowPrecision: false,
      accuracyCategory: 'UNKNOWN',
      accuracy: null,
      lastUpdateText: `Updated ${relative}`,
      relativeTime: relative,
      formattedTime: date.toLocaleString(),
    };
  }

  if (diffMins <= 60) {
    if (accCat.category === 'LOW') {
      return {
        status: 'RECENT_COARSE',
        label: `RECENT COARSE (±${accuracy.toFixed(1)}m)`,
        badgeClass: 'bg-warning-subtle text-warning-emphasis border border-warning-subtle',
        hasFix: true,
        isFresh: true,
        isLowPrecision: true,
        accuracyCategory: 'LOW',
        accuracy,
        lastUpdateText: `Updated ${relative}`,
        relativeTime: relative,
        formattedTime: date.toLocaleString(),
      };
    }
    if (accCat.category === 'MEDIUM' || accCat.category === 'HIGH') {
      return {
        status: accCat.category === 'MEDIUM' ? 'RECENT_MEDIUM' : 'RECENT_HIGH',
        label: `RECENT (±${accuracy.toFixed(1)}m)`,
        badgeClass: 'bg-info-subtle text-info-emphasis border border-info-subtle',
        hasFix: true,
        isFresh: true,
        isLowPrecision: false,
        accuracyCategory: accCat.category,
        accuracy,
        lastUpdateText: `Updated ${relative}`,
        relativeTime: relative,
        formattedTime: date.toLocaleString(),
      };
    }
    return {
      status: 'RECENT',
      label: 'RECENT FIX',
      badgeClass: 'bg-info-subtle text-info-emphasis border border-info-subtle',
      hasFix: true,
      isFresh: true,
      isLowPrecision: false,
      accuracyCategory: 'UNKNOWN',
      accuracy: null,
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
    isLowPrecision: accCat.isLowPrecision,
    accuracyCategory: accCat.category,
    accuracy,
    lastUpdateText: `Stale (${relative})`,
    relativeTime: relative,
    formattedTime: date.toLocaleString(),
  };
}

/**
 * Normalizes and validates raw GPS accuracy values.
 * Rejects null, undefined, empty strings, whitespace-only strings,
 * non-numbers, negative numbers, NaN, and non-finite values (Infinity).
 * Returns null if invalid, or the finite non-negative number if valid.
 */
export function parseValidAccuracy(accuracy) {
  if (accuracy == null) return null;
  if (typeof accuracy !== 'number' && typeof accuracy !== 'string') return null;
  if (typeof accuracy === 'string' && accuracy.trim() === '') return null;
  const num = Number(accuracy);
  if (!Number.isFinite(num) || num < 0) return null;
  return num;
}

/**
 * Evaluates GPS location precision based on standard accuracy thresholds:
 * - High: <= 10m
 * - Medium: > 10m and <= 30m
 * - Low / Coarse: > 30m
 * - Unknown: absent, empty/whitespace, negative, non-numeric, or non-finite
 */
export function getGpsAccuracyCategory(accuracy) {
  const acc = parseValidAccuracy(accuracy);
  if (acc == null) {
    return {
      category: 'UNKNOWN',
      label: 'LOCATION FIX — ACCURACY UNKNOWN',
      badgeClass: 'bg-secondary-subtle text-secondary border border-secondary-subtle',
      isLowPrecision: false,
    };
  }
  const formattedAcc = acc.toFixed(1);
  if (acc <= 10) {
    return {
      category: 'HIGH',
      label: `HIGH PRECISION (±${formattedAcc}m)`,
      badgeClass: 'bg-success-subtle text-success border border-success-subtle',
      isLowPrecision: false,
    };
  }
  if (acc <= 30) {
    return {
      category: 'MEDIUM',
      label: `MEDIUM PRECISION (±${formattedAcc}m)`,
      badgeClass: 'bg-info-subtle text-info-emphasis border border-info-subtle',
      isLowPrecision: false,
    };
  }
  return {
    category: 'LOW',
    label: `COARSE FIX (±${formattedAcc}m)`,
    badgeClass: 'bg-warning-subtle text-warning-emphasis border border-warning-subtle',
    isLowPrecision: true,
  };
}
