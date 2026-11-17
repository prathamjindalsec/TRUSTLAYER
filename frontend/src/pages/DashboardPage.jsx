import { ArrowLeftRight, ExternalLink, QrCode, ScanSearch, ShieldAlert, TriangleAlert } from 'lucide-react';
import { useCallback, useEffect, useState } from 'react';
import { Link } from 'react-router-dom';

import { dashboardStatistics } from '../api/dashboard';
import { ErrorState } from '../components/common/ErrorState';
import { PageHeader } from '../components/common/PageHeader';
import { ActivityTimeline } from '../components/dashboard/ActivityTimeline';
import { MetricCard } from '../components/dashboard/MetricCard';
import { RiskDistributionChart } from '../components/dashboard/RiskDistributionChart';
import { usePageTitle } from '../hooks/usePageTitle';
import { getApiErrorMessage } from '../utils/errors';

export function DashboardPage() {
  usePageTitle('Security overview');
  const [data, setData] = useState(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');
  const load = useCallback(async () => {
    setLoading(true); setError('');
    try { setData(await dashboardStatistics()); } catch (requestError) { setError(getApiErrorMessage(requestError, 'Could not load the latest dashboard data.')); } finally { setLoading(false); }
  }, []);
  useEffect(() => { load(); }, [load]);

  return <div><PageHeader eyebrow="Your security overview" title="Understand your latest risk signals" description="Every metric is calculated from current backend records. Your personal scans and simulations stay account-scoped; community counts are clearly labeled." actions={<><Link className="btn-secondary" to="/app/scan-url"><ScanSearch className="h-4 w-4" aria-hidden="true" />Scan URL</Link><Link className="btn-primary" to="/app/scan-qr"><QrCode className="h-4 w-4" aria-hidden="true" />Scan QR</Link></>} />{error ? <ErrorState message={error} retry={load} /> : <><div className="grid gap-4 sm:grid-cols-2 xl:grid-cols-4"><MetricCard loading={loading} icon={ScanSearch} label="URL assessments" value={data?.totalUrlScans} supportingText={`${data?.reviewRecommendedUrlScans ?? '—'} need review`} to="/app/scan-url" tone="trust" /><MetricCard loading={loading} icon={QrCode} label="QR assessments" value={data?.totalQrScans} supportingText={`${data?.reviewRecommendedQrScans ?? '—'} need review`} to="/app/scan-qr" tone="violet" /><MetricCard loading={loading} icon={ArrowLeftRight} label="Transaction simulations" value={data?.totalSimulatedTransactions} supportingText={`${data?.reviewRecommendedTransactions ?? '—'} need review`} to="/app/transactions" tone="amber" /><MetricCard loading={loading} icon={ShieldAlert} label="Community reports" value={data?.totalCommunityReports} supportingText={`${data?.totalCommunityThreats ?? '—'} canonical indicators`} to="/app/threats" tone="slate" /></div><div className="mt-6 grid gap-6 xl:grid-cols-[1.1fr_0.9fr]"><section className="surface p-5 sm:p-6"><div className="flex items-start justify-between gap-4"><div><h2 className="text-lg font-bold text-ink-950 dark:text-white">Risk distribution</h2><p className="mt-1 text-sm leading-6 text-slate-600 dark:text-slate-300">Your URL, QR, and simulation assessments grouped by classification.</p></div></div><div className="mt-5">{loading ? <div className="h-72 animate-pulse rounded-xl bg-slate-100 dark:bg-ink-800" /> : <RiskDistributionChart data={data?.riskDistribution} />}</div></section><section className="surface p-5 sm:p-6"><div className="flex items-start justify-between gap-3"><div><h2 className="text-lg font-bold text-ink-950 dark:text-white">Recent security activity</h2><p className="mt-1 text-sm leading-6 text-slate-600 dark:text-slate-300">Your latest stored scans, simulations, and reports.</p></div><Link to="/app/threats" className="text-sm font-semibold text-trust-700 hover:underline dark:text-trust-300">Explore</Link></div><div className="mt-5">{loading ? <div className="space-y-4"><div className="h-12 animate-pulse rounded bg-slate-100 dark:bg-ink-800" /><div className="h-12 animate-pulse rounded bg-slate-100 dark:bg-ink-800" /><div className="h-12 animate-pulse rounded bg-slate-100 dark:bg-ink-800" /></div> : <ActivityTimeline activity={data?.recentActivity} />}</div></section></div>{!loading && <section className="mt-6 grid gap-4 lg:grid-cols-2"><div className="rounded-xl border border-amber-200 bg-amber-50 p-4 text-sm leading-6 text-amber-950 dark:border-amber-500/25 dark:bg-amber-950/30 dark:text-amber-100"><div className="flex gap-2"><TriangleAlert className="mt-0.5 h-5 w-5 shrink-0" aria-hidden="true" /><div><p className="font-bold">What this dashboard does not say</p><p className="mt-1">{data?.limitation}</p></div></div></div><div className="surface-muted p-4 text-sm leading-6 text-slate-600 dark:text-slate-300"><p className="font-bold text-slate-900 dark:text-white">Data scope</p><p className="mt-1">{data?.personalDataScope}</p><Link to="/privacy" className="mt-2 inline-flex items-center gap-1 font-semibold text-trust-700 hover:underline dark:text-trust-300">Review privacy details <ExternalLink className="h-3.5 w-3.5" aria-hidden="true" /></Link></div></section>}</>}</div>;
}
