const riskRank = {
  LOW: 0,
  MEDIUM: 1,
  HIGH: 2,
  CRITICAL: 3,
};

export function formatNumber(value) {
  return new Intl.NumberFormat(undefined, { maximumFractionDigits: 0 }).format(value ?? 0);
}

export function formatCurrency(value, currency = 'INR') {
  if (value === null || value === undefined || Number.isNaN(Number(value))) {
    return '—';
  }
  try {
    return new Intl.NumberFormat(undefined, {
      style: 'currency',
      currency,
      maximumFractionDigits: 2,
    }).format(value);
  } catch {
    return `${value} ${currency}`;
  }
}

export function formatDateTime(value) {
  if (!value) return '—';
  const date = new Date(value);
  if (Number.isNaN(date.getTime())) return '—';
  return new Intl.DateTimeFormat(undefined, {
    dateStyle: 'medium',
    timeStyle: 'short',
  }).format(date);
}

export function formatRelativeTime(value) {
  if (!value) return '—';
  const deltaSeconds = Math.round((new Date(value).getTime() - Date.now()) / 1000);
  const units = [
    ['year', 31_536_000],
    ['month', 2_592_000],
    ['day', 86_400],
    ['hour', 3_600],
    ['minute', 60],
  ];
  const relative = new Intl.RelativeTimeFormat(undefined, { numeric: 'auto' });
  for (const [unit, seconds] of units) {
    if (Math.abs(deltaSeconds) >= seconds || unit === 'minute') {
      return relative.format(Math.round(deltaSeconds / seconds), unit);
    }
  }
  return 'just now';
}

export function riskTone(risk) {
  return {
    LOW: 'bg-trust-100 text-trust-700 dark:bg-trust-500/15 dark:text-trust-300',
    MEDIUM: 'bg-amber-100 text-amber-800 dark:bg-amber-500/15 dark:text-amber-200',
    HIGH: 'bg-orange-100 text-orange-800 dark:bg-orange-500/15 dark:text-orange-200',
    CRITICAL: 'bg-red-100 text-red-800 dark:bg-red-500/15 dark:text-red-200',
  }[risk] ?? 'bg-slate-100 text-slate-700 dark:bg-slate-800 dark:text-slate-300';
}

export function verificationTone(status) {
  return {
    VERIFIED: 'bg-trust-100 text-trust-700 dark:bg-trust-500/15 dark:text-trust-300',
    UNVERIFIED: 'bg-amber-100 text-amber-800 dark:bg-amber-500/15 dark:text-amber-200',
    REJECTED: 'bg-slate-200 text-slate-700 dark:bg-slate-800 dark:text-slate-300',
  }[status] ?? 'bg-slate-100 text-slate-700 dark:bg-slate-800 dark:text-slate-300';
}

export function compareRisk(left, right) {
  return (riskRank[left] ?? -1) - (riskRank[right] ?? -1);
}

export function initials(user) {
  const first = user?.firstName?.trim()?.[0] ?? '';
  const last = user?.lastName?.trim()?.[0] ?? '';
  return `${first}${last}`.toUpperCase() || user?.email?.[0]?.toUpperCase() || '?';
}

export function titleCase(value) {
  return String(value ?? '')
    .toLowerCase()
    .split('_')
    .filter(Boolean)
    .map((word) => word[0]?.toUpperCase() + word.slice(1))
    .join(' ');
}
