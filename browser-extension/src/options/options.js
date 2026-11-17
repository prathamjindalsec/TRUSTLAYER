import { getExtensionRedirectUri, getSettings, saveSettings } from '../shared/storage.js';

const form = document.getElementById('settings-form');
const apiBaseUrl = document.getElementById('api-base-url');
const webAppBaseUrl = document.getElementById('web-app-base-url');
const clientId = document.getElementById('client-id');
const redirectUri = document.getElementById('redirect-uri');
const extensionOrigin = document.getElementById('extension-origin');
const automaticScanning = document.getElementById('automatic-scanning');
const notificationsEnabled = document.getElementById('notifications-enabled');
const feedback = document.getElementById('feedback');

let originalSettings = null;

form.addEventListener('submit', async (event) => {
  event.preventDefault();
  setFeedback('');
  try {
    const candidate = {
      apiBaseUrl: apiBaseUrl.value.trim(),
      webAppBaseUrl: webAppBaseUrl.value.trim(),
      clientId: clientId.value.trim(),
      automaticScanning: automaticScanning.checked,
      notificationsEnabled: notificationsEnabled.checked,
    };

    await requestBackendPermission(candidate.apiBaseUrl);
    if (candidate.automaticScanning) {
      const granted = await chrome.permissions.request({ origins: ['https://*/*'] });
      if (!granted) {
        throw new Error('Automatic scanning stays off because Chrome site access was not granted.');
      }
    }

    const settings = await saveSettings(candidate);
    const invalidateAccess = !originalSettings
      || originalSettings.apiBaseUrl !== settings.apiBaseUrl
      || originalSettings.clientId !== settings.clientId;
    await sendMessage({ type: 'SETTINGS_CHANGED', invalidateAccess });
    originalSettings = settings;
    setFeedback(invalidateAccess
      ? 'Settings saved. Reconnect the extension because its authorization audience changed.'
      : 'Settings saved.');
  } catch (error) {
    setFeedback(error.message || 'Settings could not be saved.', true);
  }
});

async function initialize() {
  const [settings, callback] = await Promise.all([getSettings(), getExtensionRedirectUri()]);
  originalSettings = settings;
  apiBaseUrl.value = settings.apiBaseUrl;
  webAppBaseUrl.value = settings.webAppBaseUrl;
  clientId.value = settings.clientId;
  automaticScanning.checked = settings.automaticScanning;
  notificationsEnabled.checked = settings.notificationsEnabled;
  redirectUri.value = callback;
  const parsed = new URL(callback);
  extensionOrigin.textContent = `Required extension origin: chrome-extension://${parsed.hostname.replace(/\.chromiumapp\.org$/, '')}`;
}

async function requestBackendPermission(value) {
  const url = new URL(value);
  const hostPattern = `${url.protocol}//${url.hostname}/*`;
  const alreadyGranted = await chrome.permissions.contains({ origins: [hostPattern] });
  if (alreadyGranted) return;
  const granted = await chrome.permissions.request({ origins: [hostPattern] });
  if (!granted) {
    throw new Error('Chrome backend access was not granted. TRUSTLAYER cannot contact the configured API.');
  }
}

function sendMessage(message) {
  return new Promise((resolve, reject) => {
    chrome.runtime.sendMessage(message, (response) => {
      const lastError = chrome.runtime.lastError;
      if (lastError) reject(new Error(lastError.message || 'Extension background service is unavailable.'));
      else if (!response?.ok) reject(new Error(response?.error?.message || 'TRUSTLAYER could not save the settings.'));
      else resolve(response.data);
    });
  });
}

function setFeedback(message, isError = false) {
  feedback.textContent = message;
  feedback.classList.toggle('error', isError);
}

initialize().catch((error) => setFeedback(error.message, true));
