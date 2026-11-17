import { normalizeApiBaseUrl } from './url-policy.js';

export class ExtensionApiError extends Error {
  constructor(message, { status = 0, code = 'EXTENSION_REQUEST_FAILED' } = {}) {
    super(message);
    this.name = 'ExtensionApiError';
    this.status = status;
    this.code = code;
  }
}

async function requestJson(baseUrl, path, { accessToken, body } = {}) {
  const response = await fetch(`${normalizeApiBaseUrl(baseUrl)}${path}`, {
    method: 'POST',
    credentials: 'omit',
    cache: 'no-store',
    referrerPolicy: 'no-referrer',
    headers: {
      'Content-Type': 'application/json',
      ...(accessToken ? { Authorization: `Bearer ${accessToken}` } : {}),
    },
    body: JSON.stringify(body),
  });
  const payload = await response.json().catch(() => null);
  if (!response.ok || !payload?.success) {
    throw new ExtensionApiError(
      payload?.message || `TRUSTLAYER request failed with HTTP ${response.status}.`,
      { status: response.status, code: payload?.code },
    );
  }
  return payload.data;
}

export function exchangeAuthorizationCode(settings, { code, verifier, redirectUri }) {
  return requestJson(settings.apiBaseUrl, '/api/extension/token', {
    body: {
      clientId: settings.clientId,
      redirectUri,
      code,
      codeVerifier: verifier,
    },
  });
}

export function scanUrl(settings, accessToken, url) {
  return requestJson(settings.apiBaseUrl, '/api/extension/scan-url', {
    accessToken,
    body: { url },
  });
}
