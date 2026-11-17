import { api, withFreshCsrf } from './http';

export async function scanUrl(url) {
  const response = await withFreshCsrf(() => api.post('/scanner/url', { url }));
  return response.data.data;
}

export async function urlScanHistory(params = {}) {
  const response = await api.get('/scanner/history', { params });
  return response.data.data;
}

export async function scanQr(image) {
  const body = new FormData();
  body.append('image', image);
  const response = await withFreshCsrf(() => api.post('/scanner/qr', body, {
    headers: { 'Content-Type': 'multipart/form-data' },
  }));
  return response.data.data;
}

export async function qrScanHistory(params = {}) {
  const response = await api.get('/scanner/qr/history', { params });
  return response.data.data;
}
