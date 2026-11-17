import { api } from './http';

export async function dashboardStatistics() {
  const response = await api.get('/dashboard/statistics');
  return response.data.data;
}
