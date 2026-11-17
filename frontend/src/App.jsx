import { lazy, Suspense } from 'react';
import { Navigate, Outlet, Route, Routes, useLocation } from 'react-router-dom';

import { CookieConsent } from './components/common/CookieConsent';
import { AppShell } from './components/layout/AppShell';
import { AuthLayout } from './components/layout/AuthLayout';
import { PublicLayout } from './components/layout/PublicLayout';
import { useAuth } from './context/AuthContext';

const DashboardPage = lazy(() => import('./pages/DashboardPage').then((module) => ({ default: module.DashboardPage })));
const ExtensionConnectPage = lazy(() => import('./pages/ExtensionConnectPage').then((module) => ({ default: module.ExtensionConnectPage })));
const ForgotPasswordPage = lazy(() => import('./pages/AuthPages').then((module) => ({ default: module.ForgotPasswordPage })));
const LandingPage = lazy(() => import('./pages/LandingPage').then((module) => ({ default: module.LandingPage })));
const LoginPage = lazy(() => import('./pages/AuthPages').then((module) => ({ default: module.LoginPage })));
const NotFoundPage = lazy(() => import('./pages/NotFoundPage').then((module) => ({ default: module.NotFoundPage })));
const CookiePolicyPage = lazy(() => import('./pages/PolicyPages').then((module) => ({ default: module.CookiePolicyPage })));
const PrivacyPage = lazy(() => import('./pages/PolicyPages').then((module) => ({ default: module.PrivacyPage })));
const ProfilePage = lazy(() => import('./pages/ProfilePage').then((module) => ({ default: module.ProfilePage })));
const QrScannerPage = lazy(() => import('./pages/ScanPages').then((module) => ({ default: module.QrScannerPage })));
const RegisterPage = lazy(() => import('./pages/AuthPages').then((module) => ({ default: module.RegisterPage })));
const ResetPasswordPage = lazy(() => import('./pages/AuthPages').then((module) => ({ default: module.ResetPasswordPage })));
const TermsPage = lazy(() => import('./pages/PolicyPages').then((module) => ({ default: module.TermsPage })));
const ThreatIntelligencePage = lazy(() => import('./pages/ThreatIntelligencePage').then((module) => ({ default: module.ThreatIntelligencePage })));
const TransactionPage = lazy(() => import('./pages/TransactionPage').then((module) => ({ default: module.TransactionPage })));
const UrlScannerPage = lazy(() => import('./pages/ScanPages').then((module) => ({ default: module.UrlScannerPage })));
const VerifyEmailPage = lazy(() => import('./pages/AuthPages').then((module) => ({ default: module.VerifyEmailPage })));

function AuthBootScreen() {
  return <div className="flex min-h-screen items-center justify-center bg-slate-50 dark:bg-ink-950"><div className="flex items-center gap-3 text-sm font-semibold text-slate-600 dark:text-slate-300"><span className="h-5 w-5 animate-spin rounded-full border-2 border-slate-300 border-t-trust-600 dark:border-slate-700 dark:border-t-trust-300" aria-hidden="true" />Restoring secure session…</div></div>;
}

function RouteLoadingFallback() {
  return <div className="flex min-h-[40vh] items-center justify-center" role="status"><div className="flex items-center gap-3 text-sm font-semibold text-slate-600 dark:text-slate-300"><span className="h-5 w-5 animate-spin rounded-full border-2 border-slate-300 border-t-trust-600 dark:border-slate-700 dark:border-t-trust-300" aria-hidden="true" />Loading securely…</div></div>;
}

function DeferredPage({ children }) {
  return <Suspense fallback={<RouteLoadingFallback />}>{children}</Suspense>;
}

function RequireAuth() {
  const { isAuthenticated, isBooting } = useAuth();
  if (isBooting) return <AuthBootScreen />;
  return isAuthenticated ? <Outlet /> : <Navigate to="/login" replace />;
}

function PublicOnly() {
  const { isAuthenticated, isBooting } = useAuth();
  const location = useLocation();
  if (isBooting) return <Outlet />;
  return isAuthenticated ? <Navigate to={safeLoginReturnPath(location.search) || '/app/dashboard'} replace /> : <Outlet />;
}

function safeLoginReturnPath(search) {
  const candidate = new URLSearchParams(search).get('returnTo');
  // The extension flow is the only cross-route return target accepted from a query parameter.
  // This prevents a login page from becoming an open redirect.
  return candidate?.startsWith('/extension/connect') && !candidate.startsWith('//') ? candidate : null;
}

export default function App() {
  return <><Routes><Route element={<PublicLayout />}><Route index element={<DeferredPage><LandingPage /></DeferredPage>} /><Route path="privacy" element={<DeferredPage><PrivacyPage /></DeferredPage>} /><Route path="terms" element={<DeferredPage><TermsPage /></DeferredPage>} /><Route path="cookies" element={<DeferredPage><CookiePolicyPage /></DeferredPage>} /></Route><Route element={<PublicOnly />}><Route element={<AuthLayout />}><Route path="login" element={<DeferredPage><LoginPage /></DeferredPage>} /><Route path="register" element={<DeferredPage><RegisterPage /></DeferredPage>} /><Route path="verify-email" element={<DeferredPage><VerifyEmailPage /></DeferredPage>} /><Route path="forgot-password" element={<DeferredPage><ForgotPasswordPage /></DeferredPage>} /><Route path="reset-password" element={<DeferredPage><ResetPasswordPage /></DeferredPage>} /></Route></Route><Route element={<AuthLayout />}><Route path="extension/connect" element={<DeferredPage><ExtensionConnectPage /></DeferredPage>} /></Route><Route element={<RequireAuth />}><Route path="app" element={<AppShell />}><Route index element={<Navigate to="dashboard" replace />} /><Route path="dashboard" element={<DeferredPage><DashboardPage /></DeferredPage>} /><Route path="scan-url" element={<DeferredPage><UrlScannerPage /></DeferredPage>} /><Route path="scan-qr" element={<DeferredPage><QrScannerPage /></DeferredPage>} /><Route path="transactions" element={<DeferredPage><TransactionPage /></DeferredPage>} /><Route path="threats" element={<DeferredPage><ThreatIntelligencePage /></DeferredPage>} /><Route path="profile" element={<DeferredPage><ProfilePage /></DeferredPage>} /></Route></Route><Route path="*" element={<DeferredPage><NotFoundPage /></DeferredPage>} /></Routes><CookieConsent /></>;
}
