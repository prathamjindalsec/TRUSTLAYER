import { ExtensionApiError, exchangeAuthorizationCode, scanUrl } from '../shared/api-client.js';
import { createPkcePair, randomUrlSafeValue } from '../shared/pkce.js';
import {
  clearAccessRecord,
  getAccessRecord,
  getExtensionRedirectUri,
  getSettings,
  saveAccessRecord,
} from '../shared/storage.js';
import { canScanTabUrl, displayUrl, sanitizeUrlForAnalysis } from '../shared/url-policy.js';

const AUTO_SCAN_COOLDOWN_MS = 5 * 60 * 1000;
const recentAutomaticScans = new Map();

chrome.runtime.onInstalled.addListener(() => {
  chrome.action.setBadgeText({ text: '' });
});

chrome.runtime.onMessage.addListener((message, _sender, sendResponse) => {
  handleMessage(message)
    .then((data) => sendResponse({ ok: true, data }))
    .catch((error) => sendResponse({
      ok: false,
      error: {
        code: error.code || 'EXTENSION_OPERATION_FAILED',
        message: safeErrorMessage(error),
      },
    }));
  return true;
});

chrome.tabs.onUpdated.addListener((tabId, changeInfo, tab) => {
  if (changeInfo.status !== 'complete' || !tab.url || tab.incognito) return;
  automaticallyScan(tabId, tab.url).catch(() => {
    // Automatic checks are best effort. The popup remains the visible, retryable control.
  });
});

async function handleMessage(message) {
  switch (message?.type) {
    case 'GET_STATUS':
      return connectionStatus();
    case 'CONNECT':
      return connect();
    case 'DISCONNECT':
      await clearAccessRecord();
      await chrome.action.setBadgeText({ text: '' });
      return { connected: false };
    case 'SCAN_TAB_URL':
      return scanForTab({ tabId: message.tabId, url: message.url, automatic: false });
    case 'SETTINGS_CHANGED':
      if (message.invalidateAccess) await clearAccessRecord();
      return connectionStatus();
    default:
      throw new ExtensionApiError('Unsupported extension request.', { code: 'UNSUPPORTED_MESSAGE' });
  }
}

async function connectionStatus() {
  const [settings, access] = await Promise.all([getSettings(), getAccessRecord()]);
  return {
    connected: Boolean(access),
    expiresAt: access?.expiresAt || null,
    scope: access?.scope || null,
    automaticScanning: settings.automaticScanning,
  };
}

async function connect() {
  const settings = await getSettings();
  const redirectUri = await getExtensionRedirectUri();
  const { verifier, challenge } = await createPkcePair();
  const state = randomUrlSafeValue(32);
  const authorizationUrl = new URL('/extension/connect', settings.webAppBaseUrl);
  authorizationUrl.searchParams.set('client_id', settings.clientId);
  authorizationUrl.searchParams.set('redirect_uri', redirectUri);
  authorizationUrl.searchParams.set('code_challenge', challenge);
  authorizationUrl.searchParams.set('code_challenge_method', 'S256');
  authorizationUrl.searchParams.set('state', state);

  const callbackUrl = await launchWebAuthFlow(authorizationUrl.toString());
  const callback = new URL(callbackUrl);
  if (`${callback.origin}${callback.pathname}` !== redirectUri || callback.searchParams.get('state') !== state) {
    throw new ExtensionApiError('Extension authorization callback did not match the original request.', {
      code: 'AUTH_CALLBACK_REJECTED',
    });
  }
  const code = callback.searchParams.get('code');
  if (!code) {
    throw new ExtensionApiError('Extension authorization did not return a code.', { code: 'AUTH_CODE_MISSING' });
  }

  const token = await exchangeAuthorizationCode(settings, { code, verifier, redirectUri });
  await saveAccessRecord({
    accessToken: token.accessToken,
    expiresAt: token.expiresAt,
    scope: token.scope,
  });
  return connectionStatus();
}

function launchWebAuthFlow(url) {
  return new Promise((resolve, reject) => {
    chrome.identity.launchWebAuthFlow({ url, interactive: true }, (callbackUrl) => {
      const lastError = chrome.runtime.lastError;
      if (lastError) {
        reject(new ExtensionApiError(lastError.message || 'Browser authorization was cancelled.', {
          code: 'AUTH_WINDOW_CLOSED',
        }));
        return;
      }
      resolve(callbackUrl);
    });
  });
}

async function automaticallyScan(tabId, url) {
  const settings = await getSettings();
  if (!settings.automaticScanning || !canScanTabUrl(url)) return;

  const automaticPermission = await chrome.permissions.contains({ origins: ['https://*/*'] });
  if (!automaticPermission) return;

  const key = `${tabId}:${sanitizeUrlForAnalysis(url)}`;
  const previous = recentAutomaticScans.get(key);
  if (previous && Date.now() - previous < AUTO_SCAN_COOLDOWN_MS) return;
  recentAutomaticScans.set(key, Date.now());
  await scanForTab({ tabId, url, automatic: true });
}

async function scanForTab({ tabId, url, automatic }) {
  if (!canScanTabUrl(url)) {
    throw new ExtensionApiError('This is not a public HTTP(S) page that TRUSTLAYER can check.', {
      code: 'UNSUPPORTED_TAB_URL',
    });
  }
  const [settings, access] = await Promise.all([getSettings(), getAccessRecord()]);
  if (!access) {
    throw new ExtensionApiError('Connect the extension before requesting an account-scoped scan.', {
      code: 'EXTENSION_NOT_CONNECTED',
    });
  }

  try {
    const result = await scanUrl(settings, access.accessToken, sanitizeUrlForAnalysis(url));
    await presentRisk(tabId, result, settings, automatic);
    return {
      result,
      analyzedTarget: displayUrl(url),
      automatic,
    };
  } catch (error) {
    if (error instanceof ExtensionApiError && error.status === 401) {
      await clearAccessRecord();
    }
    throw error;
  }
}

async function presentRisk(tabId, result, settings, automatic) {
  const classification = result.riskClassification || 'UNKNOWN';
  const style = classification === 'CRITICAL' || classification === 'HIGH'
    ? { text: '!', color: '#dc2626' }
    : classification === 'MEDIUM'
      ? { text: '?', color: '#d97706' }
      : { text: '✓', color: '#15803d' };
  await chrome.action.setBadgeText({ tabId, text: style.text });
  await chrome.action.setBadgeBackgroundColor({ tabId, color: style.color });
  await chrome.action.setTitle({ tabId, title: `TRUSTLAYER: ${classification} risk (${result.riskScore}/100)` });

  if (classification !== 'HIGH' && classification !== 'CRITICAL') return;

  const reasons = (result.indicators || []).slice(0, 3).map((item) => item.name).filter(Boolean);
  try {
    await chrome.scripting.executeScript({
      target: { tabId },
      func: injectRiskWarning,
      args: [{ classification, score: result.riskScore, reasons }],
    });
  } catch {
    // Injection needs activeTab (manual) or the optional site permission (automatic). A failed
    // banner must never change navigation or conceal the scan result in the popup.
  }

  if (automatic && settings.notificationsEnabled) {
    try {
      await chrome.notifications.create({
        type: 'basic',
        iconUrl: chrome.runtime.getURL('icons/shield.svg'),
        title: `TRUSTLAYER ${classification.toLowerCase()}-risk warning`,
        message: `Static URL assessment: ${result.riskScore}/100. Review the destination before entering credentials or approving a payment.`,
        priority: 1,
      });
    } catch {
      // A notification failure must not turn a completed risk assessment into a client error.
    }
  }
}

/** Runs in the inspected tab. It deliberately uses textContent, not HTML from the API. */
function injectRiskWarning({ classification, score, reasons }) {
  const existing = document.getElementById('trustlayer-risk-warning');
  if (existing) existing.remove();
  const banner = document.createElement('section');
  banner.id = 'trustlayer-risk-warning';
  banner.setAttribute('role', 'alert');
  banner.setAttribute('aria-live', 'assertive');
  banner.style.cssText = [
    'position:fixed', 'z-index:2147483647', 'top:16px', 'left:50%', 'transform:translateX(-50%)',
    'max-width:min(680px,calc(100vw - 32px))', 'box-sizing:border-box', 'padding:16px 46px 16px 18px',
    'border:1px solid #fecaca', 'border-radius:14px', 'background:#450a0a', 'color:#fff',
    'box-shadow:0 20px 45px rgba(0,0,0,.35)', 'font:14px/1.45 system-ui,sans-serif'
  ].join(';');
  const heading = document.createElement('strong');
  heading.textContent = `TRUSTLAYER warning: ${classification} risk (${score}/100)`;
  const detail = document.createElement('p');
  detail.style.margin = '6px 0 0';
  detail.textContent = reasons.length
    ? `Observed signals: ${reasons.join('; ')}.`
    : 'Review this destination before entering credentials or approving a payment.';
  const close = document.createElement('button');
  close.type = 'button';
  close.textContent = 'Dismiss';
  close.setAttribute('aria-label', 'Dismiss TRUSTLAYER warning');
  close.style.cssText = 'position:absolute;right:10px;top:10px;border:0;border-radius:7px;padding:6px 8px;background:#fff;color:#7f1d1d;font:600 12px system-ui;cursor:pointer';
  close.addEventListener('click', () => banner.remove());
  banner.append(heading, detail, close);
  document.documentElement.append(banner);
}

function safeErrorMessage(error) {
  if (error instanceof ExtensionApiError) return error.message;
  return 'TRUSTLAYER could not complete this request. Try again from the popup.';
}
