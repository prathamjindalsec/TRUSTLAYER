import { api, withFreshCsrf } from './http';

export async function analyzeTransaction(payload) {
  const response = await withFreshCsrf(() => api.post('/transactions/analyze', payload));
  return response.data.data;
}

export async function transactionHistory(params = {}) {
  const response = await api.get('/transactions/history', { params });
  return response.data.data;
}
