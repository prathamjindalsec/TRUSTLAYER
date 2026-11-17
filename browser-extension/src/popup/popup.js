import { canScanTabUrl, displayUrl } from '../shared/url-policy.js';

const elements = {
  connect: document.getElementById('connect'),
  connectionStatus: document.getElementById('connection-status'),
  currentUrl: document.getElementById('current-url'),
  privacyNote: document.getElementById('privacy-note'),
  scan: document.getElementById('scan'),
  error: document.getElementById('error'),
  result: document.getElementById('result'),
  resultScore: document.getElementById('result-score'),
  resultRisk: document.getElementById('result-risk'),
  resultTarget: document.getElementById('result-target'),
  indicators: document.getElementById('indicators'),
  recommendation: document.getElementById('recommendation'),
  disconnect: document.getElementById('disconnect'),
  openOptions: document.getElementById('open-options'),
};

let activeTab = null;
let status = null;

elements.connect.addEventListener('click', async () => {
  setError('');
  setBusy(elements.connect, true, 'Connecting…');
  try {
    status = await sendMessage({ type: 'CONNECT' });
    renderStatus();
  } catch (error) {
    setError(error.message);
  } finally {
    setBusy(elements.connect, false, status?.connected ? 'Reconnect' : 'Connect securely');
  }
});

elements.disconnect.addEventListener('click', async () => {
  await sendMessage({ type: 'DISCONNECT' });
  status = await sendMessage({ type: 'GET_STATUS' });
  renderStatus();
  elements.result.hidden = true;
});

elements.scan.addEventListener('click', async () => {
  if (!activeTab?.url) return;
  setError('');
  setBusy(elements.scan, true, 'Checking safely…');
  try {
    const response = await sendMessage({ type: 'SCAN_TAB_URL', tabId: activeTab.id, url: activeTab.url });
    renderResult(response);
  } catch (error) {
    setError(error.message);
  } finally {
    setBusy(elements.scan, false, 'Check this destination');
  }
});

elements.openOptions.addEventListener('click', () => chrome.runtime.openOptionsPage());

async function initialize() {
  [status, activeTab] = await Promise.all([
    sendMessage({ type: 'GET_STATUS' }),
    getActiveTab(),
  ]);
  renderStatus();
  renderTab();
}

function renderStatus() {
  const connected = Boolean(status?.connected);
  elements.connectionStatus.textContent = connected
    ? `Connected for URL scans until ${new Date(status.expiresAt).toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' })}.`
    : 'Not connected. Sign in through TRUSTLAYER to request a short-lived scan token.';
  elements.connect.hidden = connected;
  elements.disconnect.hidden = !connected;
}

function renderTab() {
  const scannable = canScanTabUrl(activeTab?.url || '');
  elements.currentUrl.textContent = activeTab?.url ? displayUrl(activeTab.url) : 'No active browser tab is available.';
  elements.privacyNote.textContent = scannable
    ? 'Credentials, query values, and fragments are removed before analysis.'
    : 'Only public HTTP(S) pages can be checked. Local, private-network, browser-internal, and file pages are not sent to TRUSTLAYER.';
  elements.scan.disabled = !scannable;
}

function renderResult(response) {
  const { result, analyzedTarget } = response;
  elements.result.hidden = false;
  elements.resultScore.textContent = `${result.riskScore}/100`;
  elements.resultRisk.textContent = result.riskClassification || 'Unknown';
  elements.resultTarget.textContent = `Analyzed: ${analyzedTarget}`;
  elements.indicators.replaceChildren();
  const indicators = result.indicators?.slice(0, 4) || [];
  if (indicators.length === 0) {
    const item = document.createElement('li');
    item.textContent = 'No configured static indicator was observed. This is not a safety guarantee.';
    elements.indicators.append(item);
  } else {
    indicators.forEach((indicator) => {
      const item = document.createElement('li');
      item.textContent = `${indicator.name} (+${indicator.weight})`;
      elements.indicators.append(item);
    });
  }
  elements.recommendation.textContent = result.recommendations?.[0]
    || 'Use a known official destination independently before signing in or approving a payment.';
}

function setBusy(button, busy, label) {
  button.disabled = busy;
  button.textContent = label;
}

function setError(message) {
  elements.error.hidden = !message;
  elements.error.textContent = message || '';
}

async function getActiveTab() {
  const tabs = await chrome.tabs.query({ active: true, lastFocusedWindow: true });
  return tabs[0] || null;
}

function sendMessage(message) {
  return new Promise((resolve, reject) => {
    chrome.runtime.sendMessage(message, (response) => {
      const lastError = chrome.runtime.lastError;
      if (lastError) {
        reject(new Error(lastError.message || 'Extension background service is unavailable.'));
      } else if (!response?.ok) {
        reject(new Error(response?.error?.message || 'TRUSTLAYER could not complete this request.'));
      } else {
        resolve(response.data);
      }
    });
  });
}

initialize().catch((error) => setError(error.message));
