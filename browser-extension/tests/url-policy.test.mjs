import assert from 'node:assert/strict';
import test from 'node:test';

import {
  canScanTabUrl,
  normalizeApiBaseUrl,
  sanitizeUrlForAnalysis,
} from '../src/shared/url-policy.js';

test('extension scans public HTTP(S) URLs but rejects browser-local destinations', () => {
  assert.equal(canScanTabUrl('https://paypa1-demo.example.zip/login'), true);
  assert.equal(canScanTabUrl('http://192.0.2.1/login'), true);
  assert.equal(canScanTabUrl('https://localhost/admin'), false);
  assert.equal(canScanTabUrl('http://192.168.1.20/router'), false);
  assert.equal(canScanTabUrl('http://[::1]/router'), false);
  assert.equal(canScanTabUrl('file:///Users/test/statement.pdf'), false);
  assert.equal(canScanTabUrl('chrome://settings'), false);
});

test('extension removes credentials, query values, and fragments before analysis', () => {
  assert.equal(
    sanitizeUrlForAnalysis('https://user:secret@paypa1-demo.example.zip/login?token=private#payment'),
    'https://paypa1-demo.example.zip/login',
  );
});

test('extension backend configuration allows HTTPS and loopback HTTP only', () => {
  assert.equal(normalizeApiBaseUrl('https://api.trustlayer.example'), 'https://api.trustlayer.example');
  assert.equal(normalizeApiBaseUrl('http://localhost:8080'), 'http://localhost:8080');
  assert.throws(() => normalizeApiBaseUrl('http://api.trustlayer.example'));
  assert.throws(() => normalizeApiBaseUrl('https://api.trustlayer.example/api'));
});
