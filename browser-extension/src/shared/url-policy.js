const PRIVATE_IPV4_RANGES = [
  [10, 10],
  [127, 127],
  [169, 169],
  [172, 172],
  [192, 192],
];

function isPrivateIpv4(hostname) {
  const octets = hostname.split('.').map((item) => Number.parseInt(item, 10));
  if (octets.length !== 4 || octets.some((item) => !Number.isInteger(item) || item < 0 || item > 255)) {
    return false;
  }
  if (PRIVATE_IPV4_RANGES.some(([start, end]) => octets[0] >= start && octets[0] <= end)) {
    if (octets[0] === 172) return octets[1] >= 16 && octets[1] <= 31;
    if (octets[0] === 192) return octets[1] === 168;
    if (octets[0] === 169) return octets[1] === 254;
    return true;
  }
  return false;
}

function isPrivateIpv6(hostname) {
  const normalized = hostname.toLowerCase().replace(/^\[|\]$/g, '');
  return normalized === '::1'
    || normalized.startsWith('fc')
    || normalized.startsWith('fd')
    || normalized.startsWith('fe80:');
}

export function isPrivateOrLocalHost(hostname) {
  const normalized = hostname.toLowerCase().replace(/\.$/, '');
  return normalized === 'localhost'
    || normalized.endsWith('.localhost')
    || normalized.endsWith('.local')
    || isPrivateIpv4(normalized)
    || isPrivateIpv6(normalized);
}

/**
 * The extension never sends local-network destinations to the API. The backend already avoids
 * outbound URL access, but the client also minimizes accidental exposure of internal URLs.
 */
export function canScanTabUrl(value) {
  try {
    const url = new URL(value);
    return (url.protocol === 'https:' || url.protocol === 'http:')
      && !isPrivateOrLocalHost(url.hostname);
  } catch {
    return false;
  }
}

/**
 * Credentials, query values, and fragments can contain tokens or personal data. The backend
 * redacts them before storage as a second line of defense, but the extension does not transmit
 * them in the first place. Host and path structure still give static rules useful evidence.
 */
export function sanitizeUrlForAnalysis(value) {
  if (!canScanTabUrl(value)) {
    throw new Error('Only public HTTP(S) pages can be checked by the extension.');
  }
  const url = new URL(value);
  url.username = '';
  url.password = '';
  url.search = '';
  url.hash = '';
  return url.toString();
}

export function displayUrl(value) {
  try {
    const url = new URL(value);
    const path = url.pathname && url.pathname !== '/' ? '/[path-redacted]' : '';
    const query = url.search ? '?[query-redacted]' : '';
    return `${url.protocol}//${url.hostname}${url.port ? `:${url.port}` : ''}${path}${query}`;
  } catch {
    return 'Unsupported page URL';
  }
}

export function normalizeApiBaseUrl(value) {
  const url = new URL(value);
  const loopbackHttp = url.protocol === 'http:' && ['localhost', '127.0.0.1', '[::1]'].includes(url.hostname);
  if (url.protocol !== 'https:' && !loopbackHttp) {
    throw new Error('Use HTTPS for a deployed backend. Plain HTTP is allowed only for localhost development.');
  }
  if (url.username || url.password || url.search || url.hash || (url.pathname !== '/' && url.pathname !== '')) {
    throw new Error('Backend URL must be an origin without credentials, a path, query, or fragment.');
  }
  return url.origin;
}

export function normalizeWebAppBaseUrl(value) {
  const url = new URL(value);
  const loopbackHttp = url.protocol === 'http:' && ['localhost', '127.0.0.1', '[::1]'].includes(url.hostname);
  if (url.protocol !== 'https:' && !loopbackHttp) {
    throw new Error('Use HTTPS for a deployed web application. Plain HTTP is allowed only for localhost development.');
  }
  if (url.username || url.password || url.search || url.hash || (url.pathname !== '/' && url.pathname !== '')) {
    throw new Error('Web app URL must be an origin without credentials, a path, query, or fragment.');
  }
  return url.origin;
}
