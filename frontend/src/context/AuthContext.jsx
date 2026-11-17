import { createContext, useCallback, useContext, useEffect, useMemo, useRef, useState } from 'react';

import * as authApi from '../api/auth';
import {
  clearAccessToken,
  clearSessionRecovery,
  configureSessionRecovery,
  setAccessToken,
} from '../api/http';

const AuthContext = createContext(null);

// React StrictMode intentionally re-runs effects in development. Refresh tokens rotate on every
// successful use, so two concurrent boot refreshes would make the second request look like a
// replay and the backend correctly revoke the session. This promise is module-scoped to cover
// StrictMode's temporary remount as well as concurrent 401 recovery.
let bootRefreshInFlight = null;

function refreshSessionOnce() {
  if (!bootRefreshInFlight) {
    bootRefreshInFlight = authApi.refresh().finally(() => {
      bootRefreshInFlight = null;
    });
  }
  return bootRefreshInFlight;
}

export function AuthProvider({ children }) {
  const [user, setUser] = useState(null);
  const [status, setStatus] = useState('booting');
  const sessionVersion = useRef(0);

  const endSession = useCallback(() => {
    sessionVersion.current += 1;
    clearAccessToken();
    setUser(null);
    setStatus('anonymous');
  }, []);

  const restoreSession = useCallback(async () => {
    const versionAtStart = sessionVersion.current;
    try {
      const session = await refreshSessionOnce();
      if (versionAtStart !== sessionVersion.current) {
        return false;
      }
      setAccessToken(session.accessToken);
      setUser(session.user);
      setStatus('authenticated');
      return true;
    } catch {
      if (versionAtStart !== sessionVersion.current) {
        return false;
      }
      clearAccessToken();
      setUser(null);
      setStatus('anonymous');
      return false;
    }
  }, []);

  useEffect(() => {
    configureSessionRecovery({ refresh: restoreSession, onExpired: endSession });
    restoreSession();
    return clearSessionRecovery;
  }, [endSession, restoreSession]);

  const login = useCallback(async (credentials) => {
    const versionAtStart = sessionVersion.current + 1;
    sessionVersion.current = versionAtStart;
    const session = await authApi.login(credentials);
    if (versionAtStart !== sessionVersion.current) {
      throw new Error('The sign-in state changed before the session could be established. Please try again.');
    }
    setAccessToken(session.accessToken);
    setUser(session.user);
    setStatus('authenticated');
    return session;
  }, []);

  const logout = useCallback(async () => {
    try {
      await authApi.logout();
    } finally {
      endSession();
    }
  }, [endSession]);

  const logoutAll = useCallback(async () => {
    try {
      await authApi.logoutAllDevices();
    } finally {
      endSession();
    }
  }, [endSession]);

  const value = useMemo(() => ({
    user,
    status,
    isAuthenticated: status === 'authenticated',
    isBooting: status === 'booting',
    login,
    logout,
    logoutAll,
    restoreSession,
    updateUser: setUser,
  }), [login, logout, logoutAll, restoreSession, status, user]);

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>;
}

export function useAuth() {
  const context = useContext(AuthContext);
  if (!context) {
    throw new Error('useAuth must be used inside AuthProvider.');
  }
  return context;
}
