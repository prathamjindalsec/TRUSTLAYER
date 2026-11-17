import axios from 'axios';

let accessToken = null;
let refreshAccessTokenHandler = null;
let sessionExpiredHandler = null;
let refreshInFlight = null;
let csrfTokenInMemory = null;

export const api = axios.create({
  baseURL: import.meta.env.VITE_API_BASE_URL || '/api',
  withCredentials: true,
  headers: {
    Accept: 'application/json',
  },
});

function csrfTokenFromCookie() {
  if (typeof document === 'undefined' || typeof document.cookie !== 'string') {
    return null;
  }
  return document.cookie
    .split('; ')
    .find((entry) => entry.startsWith('XSRF-TOKEN='))
    ?.split('=')
    .slice(1)
    .join('=');
}

function isUnsafeMethod(method) {
  return !['get', 'head', 'options'].includes((method || 'get').toLowerCase());
}

api.interceptors.request.use((config) => {
  const headers = config.headers ?? {};
  if (accessToken && !config.skipAuthorization) {
    headers.Authorization = `Bearer ${accessToken}`;
  }
  if (isUnsafeMethod(config.method) && !config.skipCsrf) {
    // The cookie is the normal same-origin source. Retaining the matching token issued by
    // /auth/csrf covers privacy-restricted browser contexts where document.cookie cannot be
    // read, while the backend cookie remains the authority for validation.
    const csrfToken = csrfTokenFromCookie() || csrfTokenInMemory;
    if (csrfToken) {
      headers['X-XSRF-TOKEN'] = csrfToken;
    }
  }
  config.headers = headers;
  return config;
});

api.interceptors.response.use(
  (response) => response,
  async (error) => {
    const request = error.config;
    const shouldRefresh = error.response?.status === 401
      && accessToken
      && !request?.skipAutoRefresh
      && !request?._trustlayerRetried
      && refreshAccessTokenHandler;

    if (!shouldRefresh) {
      return Promise.reject(error);
    }

    request._trustlayerRetried = true;
    try {
      refreshInFlight ??= refreshAccessTokenHandler().finally(() => {
        refreshInFlight = null;
      });
      const refreshed = await refreshInFlight;
      if (!refreshed) {
        throw error;
      }
      return api(request);
    } catch {
      sessionExpiredHandler?.();
      return Promise.reject(error);
    }
  },
);

export function setAccessToken(token) {
  accessToken = token || null;
}

export function clearAccessToken() {
  accessToken = null;
}

export function configureSessionRecovery({ refresh, onExpired }) {
  refreshAccessTokenHandler = refresh;
  sessionExpiredHandler = onExpired;
}

export function clearSessionRecovery() {
  refreshAccessTokenHandler = null;
  sessionExpiredHandler = null;
  refreshInFlight = null;
}

export async function ensureCsrfToken() {
  const response = await api.get('/auth/csrf', {
    skipAuthorization: true,
    skipAutoRefresh: true,
  });
  csrfTokenInMemory = response.data.data.token;
  return response.data.data;
}

/**
 * Every cookie-authenticated mutation asks the backend for a current CSRF pair immediately
 * beforehand. CookieCsrfTokenRepository can rotate or clear the readable cookie after a request;
 * relying on a value obtained only at login made later scanner submissions intermittently fail.
 */
export async function withFreshCsrf(request) {
  await ensureCsrfToken();
  return request();
}
