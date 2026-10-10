/**
 * MedTrack – AVS API & WebSocket Configuration
 *
 * Resolves the backend API base URL and WebSocket URL:
 * - In production: reads VITE_API_BASE_URL (e.g. 'https://medtrack-api-production.up.railway.app').
 *   Logs an explicit warning if missing in production to prevent silent localhost failures.
 * - In development: uses VITE_API_BASE_URL if set, or empty string ''
 *   (which utilizes Vite's dev proxy to http://localhost:8080).
 */

export function getApiBaseUrl() {
  const configured = import.meta.env.VITE_API_BASE_URL;
  if (configured && typeof configured === 'string' && configured.trim()) {
    return configured.trim().replace(/\/+$/, '');
  }
  if (import.meta.env.PROD) {
    console.warn(
      '[MedTrack] WARNING: VITE_API_BASE_URL is not defined in this production build. ' +
      'Requests will target the current host origin.'
    );
  }
  return '';
}

/**
 * Builds an absolute or relative API URL for the given endpoint.
 * @param {string} endpoint - e.g. '/api/assets'
 * @returns {string} - e.g. 'https://medtrack-api.up.railway.app/api/assets' or '/api/assets'
 */
export function buildApiUrl(endpoint) {
  const base = getApiBaseUrl();
  const cleanEndpoint = endpoint.startsWith('/') ? endpoint : `/${endpoint}`;
  return base ? `${base}${cleanEndpoint}` : cleanEndpoint;
}

/**
 * Resolves the STOMP/WebSocket broker URL.
 * Automatically converts:
 *   https:// -> wss://
 *   http://  -> ws://
 * @returns {string} - e.g. 'wss://medtrack-api.up.railway.app/ws'
 */
export function getWebSocketBrokerUrl() {
  const base = getApiBaseUrl();
  if (base) {
    try {
      const url = new URL(base);
      const proto = url.protocol === 'https:' ? 'wss:' : 'ws:';
      return `${proto}//${url.host}/ws`;
    } catch (e) {
      console.error('[MedTrack] Error parsing VITE_API_BASE_URL for WebSocket:', e);
    }
  }

  // Fallback for local development or same-host deployment
  const isHttps = typeof window !== 'undefined' && window.location.protocol === 'https:';
  const host = typeof window !== 'undefined' ? window.location.host : 'localhost:5173';
  const proto = isHttps ? 'wss:' : 'ws:';
  return `${proto}//${host}/ws`;
}
