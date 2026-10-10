import React, { createContext, useContext, useState, useEffect } from 'react';
import { authService } from '../services/authService';

const AuthContext = createContext(null);

export const TOKEN_STORAGE_KEY = 'medtrack_access_token';

export function AuthProvider({ children }) {
  const [token, setToken] = useState(() => localStorage.getItem(TOKEN_STORAGE_KEY) || null);
  const [user, setUser] = useState(null);
  const [loading, setLoading] = useState(true);

  // Initialize and validate session on initial mount
  useEffect(() => {
    let isMounted = true;

    async function initializeAuth() {
      const storedToken = localStorage.getItem(TOKEN_STORAGE_KEY);
      if (!storedToken) {
        if (isMounted) {
          setLoading(false);
        }
        return;
      }

      try {
        const currentUser = await authService.getCurrentUser(storedToken);
        if (isMounted) {
          setUser(currentUser);
          setToken(storedToken);
        }
      } catch (err) {
        console.warn('Session expired or invalid JWT token. Clearing credentials.');
        localStorage.removeItem(TOKEN_STORAGE_KEY);
        if (isMounted) {
          setUser(null);
          setToken(null);
        }
      } finally {
        if (isMounted) {
          setLoading(false);
        }
      }
    }

    initializeAuth();

    return () => {
      isMounted = false;
    };
  }, []);

  const login = async (email, password) => {
    const authData = await authService.login(email, password);
    const receivedToken = authData.accessToken;
    localStorage.setItem(TOKEN_STORAGE_KEY, receivedToken);
    setToken(receivedToken);
    setUser(authData.user);
    return authData;
  };

  const register = async (name, email, password) => {
    return await authService.register(name, email, password);
  };

  const logout = async () => {
    try {
      if (token) {
        await authService.logout(token);
      }
    } finally {
      localStorage.removeItem(TOKEN_STORAGE_KEY);
      setToken(null);
      setUser(null);
    }
  };

  const handleOAuthCallback = async (code) => {
    const authData = await authService.exchangeOAuth2Code(code);
    const receivedToken = authData.accessToken;
    localStorage.setItem(TOKEN_STORAGE_KEY, receivedToken);
    setToken(receivedToken);
    setUser(authData.user);
    return authData;
  };

  const roles = user?.roles || (user?.role ? [user.role] : []);
  const isAuthenticated = !!token && !!user;

  const value = {
    token,
    user,
    roles,
    isAuthenticated,
    loading,
    login,
    register,
    logout,
    handleOAuthCallback,
  };

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>;
}

export function useAuth() {
  const context = useContext(AuthContext);
  if (!context) {
    throw new Error('useAuth must be used within an AuthProvider');
  }
  return context;
}
