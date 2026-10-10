import axios from 'axios';
import { TOKEN_STORAGE_KEY } from '../context/AuthContext';

const API_BASE_URL = '/api/tracking';

// Axios instance with interceptor to attach JWT token
const api = axios.create({
  baseURL: API_BASE_URL,
});

api.interceptors.request.use((config) => {
  const token = localStorage.getItem(TOKEN_STORAGE_KEY);
  if (token) {
    config.headers.Authorization = `Bearer ${token}`;
  }
  return config;
});

export const trackingService = {
  /**
   * Ingest and persist real mobile GPS telemetry.
   * @param {Object} data - { assetId, latitude, longitude, accuracy, speed, heading, recordedAt }
   */
  async recordLocation(data) {
    const response = await api.post('/location', data);
    return response.data;
  },

  /**
   * Start a new GPS tracking session.
   * @param {number|string} assetId
   */
  async startSession(assetId) {
    const response = await api.post('/sessions/start', { assetId });
    return response.data;
  },

  /**
   * Stop an active GPS tracking session by session ID.
   * @param {number|string} sessionId
   */
  async stopSession(sessionId) {
    const response = await api.post(`/sessions/${sessionId}/stop`);
    return response.data;
  },

  /**
   * Stop an active GPS tracking session by asset ID.
   * @param {number|string} assetId
   */
  async stopSessionByAssetId(assetId) {
    const response = await api.post(`/sessions/stop?assetId=${assetId}`);
    return response.data;
  },

  /**
   * Get location history for an asset.
   * @param {number|string} assetId
   * @param {number} limit
   */
  async getHistory(assetId, limit = 50) {
    const response = await api.get(`/assets/${assetId}/history`, {
      params: { limit },
    });
    return response.data;
  },

  /**
   * Get latest known GPS location for an asset.
   * @param {number|string} assetId
   */
  async getLatestLocation(assetId) {
    const response = await api.get(`/assets/${assetId}/latest`);
    return response.data;
  },
};
