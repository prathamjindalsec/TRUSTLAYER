import { ArrowLeftRight, QrCode, ScanSearch, ShieldAlert } from 'lucide-react';

import { EmptyState } from '../common/EmptyState';
import { RiskBadge } from '../common/RiskBadge';
import { formatRelativeTime } from '../../utils/formatters';

const activityIcons = {
  URL_SCAN: ScanSearch,
  QR_SCAN: QrCode,
  TRANSACTION_SIMULATION: ArrowLeftRight,
  COMMUNITY_REPORT: ShieldAlert,
};

export function ActivityTimeline({ activity }) {
  if (!activity?.length) return <EmptyState title="No security activity yet" description="Start with a URL or QR scan, a transaction simulation, or a community report." />;
  return <ol className="divide-y dark:divide-slate-800">{activity.map((item, index) => { const Icon = activityIcons[item.activityType] ?? ShieldAlert; return <li key={`${item.activityType}-${item.resourceId}-${index}`} className="flex gap-3 py-4 first:pt-0 last:pb-0"><span className="mt-0.5 flex h-9 w-9 shrink-0 items-center justify-center rounded-xl bg-slate-100 text-slate-600 dark:bg-ink-800 dark:text-slate-300"><Icon className="h-4 w-4" aria-hidden="true" /></span><div className="min-w-0 flex-1"><div className="flex flex-wrap items-center justify-between gap-2"><p className="font-semibold text-slate-900 dark:text-white">{item.title}</p><time className="text-xs text-slate-500 dark:text-slate-400" dateTime={item.occurredAt}>{formatRelativeTime(item.occurredAt)}</time></div><p className="mt-1 truncate text-sm text-slate-600 dark:text-slate-300" title={item.detail}>{item.detail}</p></div>{item.riskClassification && <RiskBadge risk={item.riskClassification} score={item.riskScore} compact />}</li>; })}</ol>;
}
