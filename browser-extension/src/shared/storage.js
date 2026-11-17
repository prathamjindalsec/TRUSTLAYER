import { normalizeApiBaseUrl, normalizeWebAppBaseUrl } from './url-policy.js';

const SETTINGS_KEY = 'trustlayerSettings';
const ACCESS_TOKEN_KEY = 'trustlayerExtensionAccess';

export const DEFAULT_SETTINGS = Object.freeze({
  apiBaseUrl: 'http://localhost:8080',
  webAppBaseUrl: 'http://localhost:5173',
  clientId: 'trustlayer-chrome-extension',
  automaticScanning: false,
  notificationsEnabled: true,
});

export async function getSettings() {
  const stored = await chrome.storage.local.get(SETTINGS_KEY);
  return { ...DEFAULT_SETTINGS, ...(stored[SETTINGS_KEY] || {}) };
}

export async function saveSettings(nextSettings) {
  const settings = {
    ...DEFAULT_SETTINGS,
    ...nextSettings,
    apiBaseUrl: normalizeApiBaseUrl(nextSettings.apiBaseUrl),
    webAppBaseUrl: normalizeWebAppBaseUrl(nextSettings.webAppBaseUrl),
    clientId: String(nextSettings.clientId || '').trim(),
  };
  if (!/^[A-Za-z0-9._-]{3,100}$/.test(settings.clientId)) {
    throw new Error('Client ID must contain 3–100 letters, numbers, dots, underscores, or hyphens.');
  }
  await chrome.storage.local.set({ [SETTINGS_KEY]: settings });
  return settings;
}

export async function getExtensionRedirectUri() {
  return chrome.identity.getRedirectURL('trustlayer');
}

export async function getAccessRecord() {
  const stored = await chrome.storage.session.get(ACCESS_TOKEN_KEY);
  const record = stored[ACCESS_TOKEN_KEY] || null;
  if (!record || !record.accessToken || !record.expiresAt || Date.parse(record.expiresAt) <= Date.now() + 30_000) {
    if (record) await chrome.storage.session.remove(ACCESS_TOKEN_KEY);
    return null;
  }
  return record;
}

export async function saveAccessRecord(record) {
  await chrome.storage.session.set({ [ACCESS_TOKEN_KEY]: record });
}

export async function clearAccessRecord() {
  await chrome.storage.session.remove(ACCESS_TOKEN_KEY);
}
