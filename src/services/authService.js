import { buildApiUrl } from './apiConfig';

// MedTrack – AVS Authentication Service
const API_BASE_URL = buildApiUrl('/api/auth');

/**
 * Handle API responses consistently
 */
async function handleResponse(response) {
  const contentType = response.headers.get('content-type');
  const isJson = contentType && contentType.includes('application/json');
  const data = isJson ? await response.json() : null;

  if (!response.ok) {
    const errorMsg = data?.message || data?.error || `Request failed with status ${response.status}`;
    const error = new Error(errorMsg);
    error.status = response.status;
    error.data = data;
    throw error;
  }

  return data;
}

export const authService = {
  /**
   * Local email and password login
   */
  async login(email, password) {
    const response = await fetch(`${API_BASE_URL}/login`, {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
      },
      body: JSON.stringify({ email, password }),
    });
    return handleResponse(response);
  },

  /**
   * Local user registration (default STAFF role)
   */
  async register(name, email, password) {
    const response = await fetch(`${API_BASE_URL}/register`, {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
      },
      body: JSON.stringify({ name, email, password }),
    });
    return handleResponse(response);
  },

  /**
   * Retrieve current authenticated user profile
   */
  async getCurrentUser(token) {
    const response = await fetch(`${API_BASE_URL}/me`, {
      method: 'GET',
      headers: {
        'Content-Type': 'application/json',
        Authorization: `Bearer ${token}`,
      },
    });
    return handleResponse(response);
  },

  /**
   * Logout current user
   */
  async logout(token) {
    try {
      const response = await fetch(`${API_BASE_URL}/logout`, {
        method: 'POST',
        headers: {
          'Content-Type': 'application/json',
          Authorization: token ? `Bearer ${token}` : '',
        },
      });
      return handleResponse(response);
    } catch (err) {
      console.warn('Stateless logout completed on client:', err.message);
      return { status: 'SUCCESS' };
    }
  },

  /**
   * Exchange short-lived one-time code for JWT from Google OAuth2 redirect
   */
  async exchangeOAuth2Code(code) {
    const response = await fetch(`${API_BASE_URL}/oauth2/exchange`, {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
      },
      body: JSON.stringify({ code }),
    });
    return handleResponse(response);
  },

  /**
   * Get full URL to trigger Google OAuth2 flow on the backend
   */
  getGoogleOAuthUrl() {
    return buildApiUrl('/oauth2/authorization/google');
  },
};
