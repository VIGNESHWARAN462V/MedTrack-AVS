import axios from 'axios';
import { TOKEN_STORAGE_KEY } from '../context/AuthContext';

const API_BASE_URL = '/api/ai';

const api = axios.create({
  baseURL: API_BASE_URL,
  timeout: 20000, // 20s client timeout matching backend budget
});

// Attach authenticated user's JWT access token to every request
api.interceptors.request.use(
  (config) => {
    const token = localStorage.getItem(TOKEN_STORAGE_KEY);
    if (token) {
      config.headers.Authorization = `Bearer ${token}`;
    }
    return config;
  },
  (error) => Promise.reject(error)
);

export const aiService = {
  /**
   * Submit an operational question to the MedTrack AI Operations Assistant.
   * Grounded in authorized server-side data (Phase 20.1 - 20.3).
   *
   * @param {string} question - Validated operational question
   * @returns {Promise<{
   *   answer: string,
   *   provider: string,
   *   model: string,
   *   answeredAt: string,
   *   cached: boolean,
   *   sources: Array<{label: string, resourceType: string, identifier: string, timestamp: string, freshness: string}>,
   *   dataAsOf: string
   * }>}
   */
  async chat(question) {
    if (!question || !question.trim()) {
      throw new Error('Question is required');
    }

    const response = await api.post('/chat', {
      question: question.trim(),
    });

    return response.data;
  },
};

export default aiService;
