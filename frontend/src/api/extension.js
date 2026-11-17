import { api, withFreshCsrf } from './http';

/**
 * The web application is the consent surface for the installed extension. Authorization remains
 * a normal authenticated browser mutation, so it uses the same fresh CSRF discipline as profile
 * changes and scans. The raw one-time code is immediately handed to Chrome's callback URL and is
 * never put into browser storage by this page.
 */
export async function authorizeExtension(request) {
  const response = await withFreshCsrf(() => api.post('/extension/authorizations', request));
  return response.data.data;
}
