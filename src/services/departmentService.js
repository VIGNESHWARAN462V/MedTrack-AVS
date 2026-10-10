import axios from 'axios';
import { TOKEN_STORAGE_KEY } from '../context/AuthContext';

const API_BASE_URL = '/api/departments';

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

// Safe client-side reference cache (5-minute TTL) for static hospital department list (Phase 20)
let cachedDepartments = null;
let lastFetchTime = 0;
const CACHE_TTL_MS = 5 * 60 * 1000;
let inFlightPromise = null;

export const departmentService = {
  async getDepartments(forceRefresh = false) {
    const now = Date.now();
    if (!forceRefresh && cachedDepartments && now - lastFetchTime < CACHE_TTL_MS) {
      return cachedDepartments;
    }

    if (inFlightPromise) {
      return inFlightPromise;
    }

    inFlightPromise = api
      .get('')
      .then((res) => {
        cachedDepartments = res.data;
        lastFetchTime = Date.now();
        inFlightPromise = null;
        return res.data;
      })
      .catch((err) => {
        inFlightPromise = null;
        throw err;
      });

    return inFlightPromise;
  },

  invalidateCache() {
    cachedDepartments = null;
    lastFetchTime = 0;
    inFlightPromise = null;
  },
};

export default departmentService;
