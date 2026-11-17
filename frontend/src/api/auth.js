import { api, ensureCsrfToken, withFreshCsrf } from './http';

export async function registerAccount(payload) {
  const response = await api.post('/auth/register', payload, {
    skipAuthorization: true,
    skipAutoRefresh: true,
    skipCsrf: true,
  });
  return response.data.data;
}

export async function verifyEmail(token) {
  const response = await api.post('/auth/verify-email', { token }, {
    skipAuthorization: true,
    skipAutoRefresh: true,
    skipCsrf: true,
  });
  return response.data;
}

export async function resendVerification(email) {
  const response = await api.post('/auth/resend-verification', { email }, {
    skipAuthorization: true,
    skipAutoRefresh: true,
    skipCsrf: true,
  });
  return response.data.data;
}

export async function requestPasswordReset(email) {
  const response = await api.post('/auth/forgot-password', { email }, {
    skipAuthorization: true,
    skipAutoRefresh: true,
    skipCsrf: true,
  });
  return response.data.data;
}

export async function resetPassword(payload) {
  const response = await api.post('/auth/reset-password', payload, {
    skipAuthorization: true,
    skipAutoRefresh: true,
    skipCsrf: true,
  });
  return response.data;
}

export async function login(credentials) {
  await ensureCsrfToken();
  const response = await api.post('/auth/login', credentials, {
    skipAuthorization: true,
    skipAutoRefresh: true,
    skipCsrf: true,
  });
  return response.data.data;
}

export async function refresh() {
  await ensureCsrfToken();
  const response = await api.post('/auth/refresh', null, {
    skipAuthorization: true,
    skipAutoRefresh: true,
  });
  return response.data.data;
}

export async function logout() {
  await withFreshCsrf(() => api.post('/auth/logout', null, {
    skipAuthorization: true,
    skipAutoRefresh: true,
  }));
}

export async function logoutAllDevices() {
  await withFreshCsrf(() => api.post('/auth/logout-all'));
}
