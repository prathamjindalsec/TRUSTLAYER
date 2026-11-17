import { api, withFreshCsrf } from './http';

export async function getProfile() {
  const response = await api.get('/users/profile');
  return response.data.data;
}

export async function updateProfile(payload) {
  const response = await withFreshCsrf(() => api.put('/users/profile', payload));
  return response.data.data;
}

export async function getSessions(params = {}) {
  const response = await api.get('/users/sessions', { params });
  return response.data.data;
}

export async function revokeSession(sessionId) {
  await withFreshCsrf(() => api.delete(`/users/sessions/${encodeURIComponent(sessionId)}`));
}

export async function getLoginHistory(params = {}) {
  const response = await api.get('/users/login-history', { params });
  return response.data.data;
}

export async function deleteAccount(password) {
  await withFreshCsrf(() => api.delete('/users/account', { data: { password } }));
}
