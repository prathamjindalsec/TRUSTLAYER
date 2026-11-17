import { Chrome, LoaderCircle, ShieldCheck, TriangleAlert } from 'lucide-react';
import { Link, Navigate, useLocation } from 'react-router-dom';
import { useMemo, useState } from 'react';

import { authorizeExtension } from '../api/extension';
import { AuthCard } from '../components/common/AuthCard';
import { useAuth } from '../context/AuthContext';
import { usePageTitle } from '../hooks/usePageTitle';
import { getApiErrorMessage } from '../utils/errors';

function readExtensionRequest(search) {
  const query = new URLSearchParams(search);
  const clientId = query.get('client_id') || '';
  const redirectUri = query.get('redirect_uri') || '';
  const codeChallenge = query.get('code_challenge') || '';
  const codeChallengeMethod = query.get('code_challenge_method') || '';
  const state = query.get('state') || '';

  try {
    const callback = new URL(redirectUri);
    if (callback.protocol !== 'https:' || !callback.hostname.endsWith('.chromiumapp.org')) {
      return { error: 'The requested extension callback is not a supported Chrome callback URL.' };
    }
  } catch {
    return { error: 'The extension connection request is malformed.' };
  }

  if (!clientId || !state || codeChallengeMethod !== 'S256' || !/^[A-Za-z0-9_-]{43,128}$/.test(codeChallenge)) {
    return { error: 'The extension connection request is incomplete or uses an unsupported authorization method.' };
  }

  return {
    request: { clientId, redirectUri, codeChallenge, codeChallengeMethod, state },
  };
}

export function ExtensionConnectPage() {
  usePageTitle('Connect browser extension');
  const location = useLocation();
  const { isAuthenticated, isBooting, user } = useAuth();
  const parsed = useMemo(() => readExtensionRequest(location.search), [location.search]);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState('');

  if (isBooting) {
    return <AuthCard title="Checking your secure session" description="Please wait while TRUSTLAYER restores your account session."><div className="flex items-center gap-3 text-sm text-slate-600 dark:text-slate-300"><LoaderCircle className="h-5 w-5 animate-spin" aria-hidden="true" />Restoring secure session…</div></AuthCard>;
  }

  if (parsed.error) {
    return <AuthCard title="Cannot connect this extension" description="The connection request did not pass the browser-side safety checks." footer={<Link to="/app/dashboard" className="link-inline">Return to dashboard</Link>}><div role="alert" className="rounded-xl border border-red-200 bg-red-50 p-4 text-sm leading-6 text-red-900 dark:border-red-500/25 dark:bg-red-950/30 dark:text-red-100"><TriangleAlert className="mb-2 h-5 w-5" aria-hidden="true" />{parsed.error}</div></AuthCard>;
  }

  if (!isAuthenticated) {
    const returnTo = `${location.pathname}${location.search}`;
    return <Navigate to={`/login?returnTo=${encodeURIComponent(returnTo)}`} replace />;
  }

  const approve = async () => {
    setBusy(true);
    setError('');
    try {
      const authorization = await authorizeExtension(parsed.request);
      const callback = new URL(authorization.redirectUri);
      callback.searchParams.set('code', authorization.authorizationCode);
      callback.searchParams.set('state', authorization.state);
      // Do not expose a one-time authorization code in the page body, history, or application
      // storage. Chrome identity receives it directly and exchanges it with the PKCE verifier.
      window.location.replace(callback.toString());
    } catch (requestError) {
      setError(getApiErrorMessage(requestError, 'TRUSTLAYER could not authorize this extension. Check its exact backend configuration and try again.'));
      setBusy(false);
    }
  };

  return <AuthCard title="Connect TRUSTLAYER browser protection" description="Review this request before granting the extension access." footer={<Link to="/app/dashboard" className="link-inline">Cancel and return to dashboard</Link>}><div className="space-y-5"><div className="rounded-xl border border-trust-200 bg-trust-50 p-4 text-sm leading-6 text-trust-950 dark:border-trust-500/25 dark:bg-trust-500/10 dark:text-trust-100"><div className="flex gap-3"><Chrome className="mt-0.5 h-5 w-5 shrink-0" aria-hidden="true" /><div><p className="font-bold">You are connecting as {user?.email}.</p><p className="mt-1">The extension receives a short-lived, URL-scan-only token. It cannot read your password, refresh cookie, profile, session history, transaction data, or community reports.</p></div></div></div><div className="rounded-xl bg-slate-50 p-4 text-sm leading-6 text-slate-700 dark:bg-ink-850 dark:text-slate-200"><p><strong>Requested capability:</strong> analyze a website URL with TRUSTLAYER’s static local rules.</p><p className="mt-2"><strong>Not a capability:</strong> opening links, reading form fields, blocking payments, or controlling your browser navigation.</p><p className="mt-2 break-all text-xs text-slate-500 dark:text-slate-400">Chrome callback: {parsed.request.redirectUri}</p></div>{error && <p role="alert" className="rounded-xl border border-red-200 bg-red-50 px-3.5 py-3 text-sm text-red-900 dark:border-red-500/25 dark:bg-red-950/30 dark:text-red-100">{error}</p>}<button type="button" onClick={approve} disabled={busy} className="btn-primary w-full">{busy ? <LoaderCircle className="h-4 w-4 animate-spin" aria-hidden="true" /> : <ShieldCheck className="h-4 w-4" aria-hidden="true" />}{busy ? 'Authorizing securely…' : 'Allow URL-scan protection'}</button><p className="text-center text-xs leading-5 text-slate-500 dark:text-slate-400">Authorization can be ended by disconnecting the extension, waiting for the short-lived token to expire, or revoking this browser session from your account.</p></div></AuthCard>;
}
