import { CheckCircle2, Copy, LoaderCircle, MailCheck, ShieldCheck } from 'lucide-react';
import { Link, useLocation, useNavigate } from 'react-router-dom';
import { useState } from 'react';

import * as authApi from '../api/auth';
import { AuthCard } from '../components/common/AuthCard';
import { TextField } from '../components/common/FormField';
import { useAuth } from '../context/AuthContext';
import { useToast } from '../context/ToastContext';
import { usePageTitle } from '../hooks/usePageTitle';
import { getApiErrorMessage, getFieldErrors } from '../utils/errors';

function SubmitButton({ busy, children }) {
  return <button type="submit" className="btn-primary w-full" disabled={busy}>{busy && <LoaderCircle className="h-4 w-4 animate-spin" aria-hidden="true" />}{children}</button>;
}

function PasswordHint() {
  return <p className="mt-3 text-xs leading-5 text-slate-500 dark:text-slate-400">Use 12+ characters with upper- and lower-case letters, a number, and a symbol.</p>;
}

function passwordProblem(password) {
  if (password.length < 12) return 'Use at least 12 characters.';
  if (!/[A-Z]/.test(password) || !/[a-z]/.test(password) || !/\d/.test(password) || !/[^A-Za-z0-9]/.test(password)) {
    return 'Use upper- and lower-case letters, a number, and a symbol.';
  }
  return null;
}

export function LoginPage() {
  usePageTitle('Sign in');
  const { login } = useAuth();
  const toast = useToast();
  const navigate = useNavigate();
  const location = useLocation();
  const [values, setValues] = useState({ email: '', password: '' });
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState('');

  const submit = async (event) => {
    event.preventDefault();
    setError('');
    setBusy(true);
    try {
      await login(values);
      toast.success('Signed in securely.');
      navigate(safeExtensionReturnPath(location.search) || '/app/dashboard', { replace: true });
    } catch (requestError) {
      setError(getApiErrorMessage(requestError, 'Sign-in failed. Check your email and password.'));
    } finally {
      setBusy(false);
    }
  };

  return <AuthCard title="Welcome back" description="Sign in to your security workspace." footer={<>Need an account? <Link to="/register" className="link-inline">Create one</Link></>}><form noValidate onSubmit={submit} className="space-y-4">{error && <FormError message={error} />}<TextField label="Email address" type="email" autoComplete="email" value={values.email} onChange={(event) => setValues((current) => ({ ...current, email: event.target.value }))} required /><TextField label="Password" type="password" autoComplete="current-password" value={values.password} onChange={(event) => setValues((current) => ({ ...current, password: event.target.value }))} required /><div className="flex justify-end"><Link to="/forgot-password" className="link-inline text-sm">Forgot password?</Link></div><SubmitButton busy={busy}>Sign in</SubmitButton><p className="text-center text-xs text-slate-500 dark:text-slate-400">Sign-in is protected by session controls and abuse monitoring.</p></form></AuthCard>;
}

function safeExtensionReturnPath(search) {
  const candidate = new URLSearchParams(search).get('returnTo');
  return candidate?.startsWith('/extension/connect') && !candidate.startsWith('//') ? candidate : null;
}

export function RegisterPage() {
  usePageTitle('Create account');
  const [values, setValues] = useState({ firstName: '', lastName: '', email: '', password: '', confirmPassword: '' });
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState('');
  const [fieldErrors, setFieldErrors] = useState({});
  const [registration, setRegistration] = useState(null);
  const navigate = useNavigate();

  const submit = async (event) => {
    event.preventDefault();
    const problem = passwordProblem(values.password);
    if (problem) return setFieldErrors({ password: problem });
    if (values.password !== values.confirmPassword) return setFieldErrors({ confirmPassword: 'Passwords do not match.' });
    setBusy(true); setError(''); setFieldErrors({});
    try {
      const result = await authApi.registerAccount({
        email: values.email,
        password: values.password,
        firstName: values.firstName || null,
        lastName: values.lastName || null,
      });
      setRegistration(result);
    } catch (requestError) {
      setError(getApiErrorMessage(requestError, 'Could not create the account.'));
      setFieldErrors(getFieldErrors(requestError));
    } finally { setBusy(false); }
  };

  if (registration) {
    const developmentToken = registration.developmentActionToken;
    return <AuthCard title="Check your email" description="Your account has been created, but it cannot sign in until the email address is verified." footer={<><Link to="/login" className="link-inline">Back to sign in</Link></>}><div className="space-y-5"><div className="rounded-xl bg-trust-50 p-4 text-sm leading-6 text-trust-950 dark:bg-trust-500/10 dark:text-trust-100"><MailCheck className="mb-2 h-5 w-5 text-trust-700 dark:text-trust-300" aria-hidden="true" />A verification message should be delivered to <strong>{registration.user.email}</strong>.</div>{developmentToken ? <DevelopmentToken token={developmentToken} label="Development-only verification token" onContinue={() => navigate('/verify-email', { state: { token: developmentToken } })} /> : <><p className="text-sm leading-6 text-slate-600 dark:text-slate-300">If you do not receive it, request another verification message from the verification page.</p><Link to="/verify-email" className="btn-primary w-full">Verify email</Link></>}</div></AuthCard>;
  }

  return <AuthCard title="Create your workspace" description="Start with an email-verified account. Self-registration never grants an administrative role." footer={<>Already have an account? <Link to="/login" className="link-inline">Sign in</Link></>}><form noValidate onSubmit={submit} className="space-y-4">{error && <FormError message={error} />}<div className="grid gap-4 sm:grid-cols-2"><TextField label="First name" autoComplete="given-name" value={values.firstName} onChange={(event) => setValues((current) => ({ ...current, firstName: event.target.value }))} error={fieldErrors.firstName} /><TextField label="Last name" autoComplete="family-name" value={values.lastName} onChange={(event) => setValues((current) => ({ ...current, lastName: event.target.value }))} error={fieldErrors.lastName} /></div><TextField label="Email address" type="email" autoComplete="email" value={values.email} onChange={(event) => setValues((current) => ({ ...current, email: event.target.value }))} error={fieldErrors.email} required /><TextField label="Password" type="password" autoComplete="new-password" value={values.password} onChange={(event) => setValues((current) => ({ ...current, password: event.target.value }))} error={fieldErrors.password} required /><TextField label="Confirm password" type="password" autoComplete="new-password" value={values.confirmPassword} onChange={(event) => setValues((current) => ({ ...current, confirmPassword: event.target.value }))} error={fieldErrors.confirmPassword} required /><PasswordHint /><SubmitButton busy={busy}>Create account</SubmitButton></form></AuthCard>;
}

export function VerifyEmailPage() {
  usePageTitle('Verify email');
  const navigate = useNavigate();
  const location = useLocation();
  const toast = useToast();
  const [token, setToken] = useState(location.state?.token ?? '');
  const [email, setEmail] = useState('');
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState('');
  const [resentToken, setResentToken] = useState(null);

  const verify = async (event) => {
    event.preventDefault(); setBusy(true); setError('');
    try { await authApi.verifyEmail(token); toast.success('Email verified. You can now sign in.'); navigate('/login', { replace: true }); } catch (requestError) { setError(getApiErrorMessage(requestError, 'The verification token could not be used.')); } finally { setBusy(false); }
  };
  const resend = async (event) => {
    event.preventDefault(); setBusy(true); setError('');
    try { const result = await authApi.resendVerification(email); setResentToken(result?.developmentActionToken ?? null); toast.info('If verification is needed, a message has been queued.'); } catch (requestError) { setError(getApiErrorMessage(requestError, 'Could not request another verification message.')); } finally { setBusy(false); }
  };

  return <AuthCard title="Verify your email" description="Paste the one-time verification token from your message. Tokens are single-use." footer={<>Return to <Link to="/login" className="link-inline">sign in</Link></>}><form noValidate onSubmit={verify} className="space-y-4">{error && <FormError message={error} />}<TextField label="Verification token" value={token} onChange={(event) => setToken(event.target.value)} autoComplete="one-time-code" required /><SubmitButton busy={busy}>Verify email</SubmitButton></form><div className="my-7 border-t dark:border-slate-800" /><form onSubmit={resend} className="space-y-3"><h2 className="font-bold text-slate-900 dark:text-white">Need a new verification message?</h2><TextField label="Account email" type="email" value={email} onChange={(event) => setEmail(event.target.value)} required /><button type="submit" className="btn-secondary w-full" disabled={busy}>Request another message</button>{resentToken && <DevelopmentToken token={resentToken} label="Development-only verification token" onContinue={() => setToken(resentToken)} compact />}</form></AuthCard>;
}

export function ForgotPasswordPage() {
  usePageTitle('Reset password');
  const [email, setEmail] = useState(''); const [busy, setBusy] = useState(false); const [error, setError] = useState(''); const [token, setToken] = useState(null); const navigate = useNavigate();
  const submit = async (event) => { event.preventDefault(); setBusy(true); setError(''); try { const result = await authApi.requestPasswordReset(email); setToken(result?.developmentActionToken ?? null); } catch (requestError) { setError(getApiErrorMessage(requestError, 'Could not request a reset message.')); } finally { setBusy(false); } };
  return <AuthCard title="Reset your password" description="Enter your account email and we will initiate a one-time reset flow." footer={<>Remembered it? <Link to="/login" className="link-inline">Sign in</Link></>}><form noValidate onSubmit={submit} className="space-y-4">{error && <FormError message={error} />}<TextField label="Email address" type="email" autoComplete="email" value={email} onChange={(event) => setEmail(event.target.value)} required /><SubmitButton busy={busy}>Request password reset</SubmitButton></form>{token && <DevelopmentToken token={token} label="Development-only reset token" onContinue={() => navigate('/reset-password', { state: { token } })} />}</AuthCard>;
}

export function ResetPasswordPage() {
  usePageTitle('Choose new password');
  const location = useLocation(); const navigate = useNavigate(); const toast = useToast();
  const [values, setValues] = useState({ token: location.state?.token ?? '', newPassword: '', confirmPassword: '' }); const [busy, setBusy] = useState(false); const [error, setError] = useState(''); const [fieldErrors, setFieldErrors] = useState({});
  const submit = async (event) => { event.preventDefault(); const problem = passwordProblem(values.newPassword); if (problem) return setFieldErrors({ newPassword: problem }); if (values.newPassword !== values.confirmPassword) return setFieldErrors({ confirmPassword: 'Passwords do not match.' }); setBusy(true); setError(''); setFieldErrors({}); try { await authApi.resetPassword({ token: values.token, newPassword: values.newPassword }); toast.success('Password reset successful. Sign in with the new password.'); navigate('/login', { replace: true }); } catch (requestError) { setError(getApiErrorMessage(requestError, 'Could not reset the password.')); setFieldErrors(getFieldErrors(requestError)); } finally { setBusy(false); } };
  return <AuthCard title="Choose a new password" description="The reset token is single-use and signs out existing sessions after a successful reset." footer={<Link to="/login" className="link-inline">Back to sign in</Link>}><form noValidate onSubmit={submit} className="space-y-4">{error && <FormError message={error} />}<TextField label="Reset token" value={values.token} onChange={(event) => setValues((current) => ({ ...current, token: event.target.value }))} required /><TextField label="New password" type="password" autoComplete="new-password" value={values.newPassword} onChange={(event) => setValues((current) => ({ ...current, newPassword: event.target.value }))} error={fieldErrors.newPassword} required /><TextField label="Confirm new password" type="password" autoComplete="new-password" value={values.confirmPassword} onChange={(event) => setValues((current) => ({ ...current, confirmPassword: event.target.value }))} error={fieldErrors.confirmPassword} required /><PasswordHint /><SubmitButton busy={busy}>Reset password</SubmitButton></form></AuthCard>;
}

function FormError({ message }) {
  return <div role="alert" className="rounded-xl border border-red-200 bg-red-50 px-3.5 py-3 text-sm leading-5 text-red-900 dark:border-red-500/25 dark:bg-red-950/30 dark:text-red-100">{message}</div>;
}

function DevelopmentToken({ token, label, onContinue, compact = false }) {
  const toast = useToast();
  const copy = async () => { await navigator.clipboard?.writeText(token); toast.success('Development token copied.'); };
  return <div className={`rounded-xl border border-amber-200 bg-amber-50 p-4 text-amber-950 dark:border-amber-500/25 dark:bg-amber-950/30 dark:text-amber-100 ${compact ? 'mt-3' : 'mt-5'}`}><div className="flex gap-2"><ShieldCheck className="mt-0.5 h-4 w-4 shrink-0" aria-hidden="true" /><div><p className="text-sm font-bold">{label}</p><p className="mt-1 text-xs leading-5">This is exposed only because the backend is in local development mode. Never enable this behavior in deployment.</p></div></div><code className="mt-3 block break-all rounded-lg bg-white/70 p-2 text-xs dark:bg-ink-950/50">{token}</code><div className="mt-3 flex gap-2"><button type="button" onClick={copy} className="btn-secondary min-h-9 px-3 py-2 text-xs"><Copy className="h-3.5 w-3.5" aria-hidden="true" />Copy</button>{onContinue && <button type="button" onClick={onContinue} className="btn-primary min-h-9 px-3 py-2 text-xs"><CheckCircle2 className="h-3.5 w-3.5" aria-hidden="true" />Use token</button>}</div></div>;
}
