import { riskTone, titleCase, verificationTone } from '../../utils/formatters';

export function RiskBadge({ risk, score, compact = false }) {
  if (!risk) return <span className="text-sm text-slate-500 dark:text-slate-400">Pending</span>;
  return (
    <span className={`inline-flex items-center gap-1.5 rounded-full px-2.5 py-1 text-xs font-bold ${riskTone(risk)}`}>
      {titleCase(risk)}
      {!compact && score !== null && score !== undefined && <span aria-label={`Risk score ${score}`}>{score}/100</span>}
    </span>
  );
}

export function VerificationBadge({ status }) {
  if (!status) return null;
  return (
    <span className={`inline-flex items-center rounded-full px-2.5 py-1 text-xs font-bold ${verificationTone(status)}`}>
      {titleCase(status)}
    </span>
  );
}
