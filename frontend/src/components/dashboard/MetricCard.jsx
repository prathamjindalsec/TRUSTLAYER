import { ArrowUpRight } from 'lucide-react';
import { Link } from 'react-router-dom';

import { Skeleton } from '../common/Skeleton';
import { formatNumber } from '../../utils/formatters';

export function MetricCard({ icon: Icon, label, value, supportingText, to, tone = 'trust', loading = false }) {
  const toneClasses = {
    trust: 'bg-trust-50 text-trust-700 dark:bg-trust-500/15 dark:text-trust-300',
    amber: 'bg-amber-100 text-amber-800 dark:bg-amber-500/15 dark:text-amber-200',
    violet: 'bg-violet-100 text-violet-800 dark:bg-violet-500/15 dark:text-violet-200',
    slate: 'bg-slate-100 text-slate-700 dark:bg-slate-800 dark:text-slate-300',
  }[tone];
  const content = <div className="surface h-full p-5 transition hover:-translate-y-0.5 hover:border-trust-200 hover:shadow-panel dark:hover:border-trust-500/30"><div className="flex items-start justify-between gap-3"><span className={`inline-flex rounded-xl p-2.5 ${toneClasses}`}><Icon className="h-5 w-5" aria-hidden="true" /></span>{to && <ArrowUpRight className="h-4 w-4 text-slate-400" aria-hidden="true" />}</div><p className="mt-5 text-sm font-semibold text-slate-600 dark:text-slate-300">{label}</p>{loading ? <Skeleton className="mt-2 h-9 w-16" /> : <p className="mt-1 text-3xl font-bold tracking-tight text-ink-950 dark:text-white">{formatNumber(value)}</p>}<p className="mt-2 min-h-5 text-xs leading-5 text-slate-500 dark:text-slate-400">{supportingText}</p></div>;
  return to ? <Link to={to} className="block focus:outline-none">{content}</Link> : content;
}
