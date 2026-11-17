import { api, withFreshCsrf } from './http';

export async function reportThreat(payload) {
  const response = await withFreshCsrf(() => api.post('/threats/report', payload));
  return response.data.data;
}

export async function searchThreats(params = {}) {
  const response = await api.get('/threats', { params });
  return response.data.data;
}

export async function threatStatistics() {
  const response = await api.get('/threats/statistics');
  return response.data.data;
}

export async function repeatedCampaigns(minReports = 3) {
  const response = await api.get('/threats/campaigns', { params: { minReports } });
  return response.data.data;
}
