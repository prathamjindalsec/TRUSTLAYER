import { beforeEach, describe, expect, it, vi } from 'vitest';

const mocks = vi.hoisted(() => ({
  api: {
    get: vi.fn(),
    post: vi.fn(),
  },
  withFreshCsrf: vi.fn(),
}));

vi.mock('./http', () => ({
  api: mocks.api,
  withFreshCsrf: mocks.withFreshCsrf,
}));

import { scanQr, scanUrl } from './scanner';

describe('scanner API client', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    mocks.withFreshCsrf.mockImplementation((request) => request());
  });

  it('obtains a fresh CSRF pair before a URL scan mutation', async () => {
    mocks.api.post.mockResolvedValue({ data: { data: { scanId: 42 } } });

    await expect(scanUrl('https://example.test/login')).resolves.toEqual({ scanId: 42 });

    expect(mocks.withFreshCsrf).toHaveBeenCalledTimes(1);
    expect(mocks.api.post).toHaveBeenCalledWith('/scanner/url', {
      url: 'https://example.test/login',
    });
  });

  it('obtains a fresh CSRF pair before a QR upload mutation', async () => {
    mocks.api.post.mockResolvedValue({ data: { data: { scanId: 43 } } });
    const image = new File(['not-a-real-qr'], 'scan.png', { type: 'image/png' });

    await expect(scanQr(image)).resolves.toEqual({ scanId: 43 });

    expect(mocks.withFreshCsrf).toHaveBeenCalledTimes(1);
    expect(mocks.api.post).toHaveBeenCalledWith(
      '/scanner/qr',
      expect.any(FormData),
      { headers: { 'Content-Type': 'multipart/form-data' } },
    );
  });
});
