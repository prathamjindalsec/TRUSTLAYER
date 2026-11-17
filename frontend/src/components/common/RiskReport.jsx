import { CheckCircle2, CircleHelp, ShieldAlert, Sparkles } from 'lucide-react';

import { RiskBadge } from './RiskBadge';
import { titleCase } from '../../utils/formatters';

export function RiskReport({ title = 'Assessment result', targetLabel, target, result }) {
  if (!result) return null;
  return (
    <section className="surface overflow-hidden">
      <div className="border-b bg-slate-50 px-5 py-5 dark:border-slate-800 dark:bg-ink-850 sm:px-6">
        <div className="flex flex-col gap-4 sm:flex-row sm:items-start sm:justify-between"><div className="min-w-0"><p className="text-xs font-bold uppercase tracking-[0.13em] text-trust-700 dark:text-trust-300">{title}</p><p className="mt-2 break-all text-sm font-semibold text-slate-800 dark:text-slate-100"><span className="mr-1 text-slate-500 dark:text-slate-400">{targetLabel}:</span>{target}</p></div><div className="flex items-center gap-3"><div className="text-right"><p className="text-3xl font-black tracking-tight text-ink-950 dark:text-white">{result.riskScore ?? '—'}<span className="text-base text-slate-400">/100</span></p><p className="text-xs text-slate-500 dark:text-slate-400">Explainable score</p></div><RiskBadge risk={result.riskClassification} score={result.riskScore} compact /></div></div>
      </div>
      <div className="grid gap-6 p-5 sm:p-6 lg:grid-cols-[1.05fr_0.95fr]">
        <div><h2 className="flex items-center gap-2 font-bold text-slate-900 dark:text-white"><ShieldAlert className="h-4 w-4 text-trust-700 dark:text-trust-300" aria-hidden="true" />Observed indicators</h2><div className="mt-4 space-y-3">{result.indicators?.length ? result.indicators.map((indicator) => <article key={indicator.code} className="rounded-xl border p-3.5 dark:border-slate-800"><div className="flex flex-wrap items-start justify-between gap-2"><div><p className="text-sm font-bold text-slate-900 dark:text-white">{indicator.name}</p><p className="mt-0.5 text-xs font-semibold uppercase tracking-wide text-slate-500 dark:text-slate-400">{titleCase(indicator.type)} · {titleCase(indicator.severity)}</p></div><span className="rounded-full bg-slate-100 px-2 py-1 text-xs font-bold text-slate-700 dark:bg-ink-800 dark:text-slate-300">+{indicator.weight}</span></div>{Object.keys(indicator.evidence || {}).length > 0 && <dl className="mt-3 flex flex-wrap gap-x-4 gap-y-1 text-xs leading-5 text-slate-600 dark:text-slate-300">{Object.entries(indicator.evidence).map(([key, value]) => <div key={key}><dt className="inline text-slate-500 dark:text-slate-400">{titleCase(key)}: </dt><dd className="inline font-semibold">{Array.isArray(value) ? value.join(', ') : String(value)}</dd></div>)}</dl>}</article>) : <p className="rounded-xl bg-trust-50 p-4 text-sm leading-6 text-trust-950 dark:bg-trust-500/10 dark:text-trust-100">No risk-raising rule was triggered by this analysis. That is not a guarantee that the target is safe.</p>}</div></div>
        <div className="space-y-5"><RiskList icon={CheckCircle2} title="Recommended next steps" items={result.recommendations} tone="trust" empty="No additional recommendation was provided by the analyzer." /><RiskList icon={CircleHelp} title="Evidence limitations" items={result.limitations} tone="slate" empty="No limitation was supplied." /><div className="rounded-xl border border-amber-200 bg-amber-50 p-4 text-sm leading-6 text-amber-950 dark:border-amber-500/25 dark:bg-amber-950/30 dark:text-amber-100"><Sparkles className="mb-2 h-4 w-4" aria-hidden="true" />Confidence: <strong>{titleCase(result.confidenceLevel || 'unknown')}</strong>. The score is generated from visible rules and evidence, not an opaque AI prediction.</div></div>
      </div>
    </section>
  );
}

function RiskList({ icon: Icon, title, items, tone, empty }) {
  const color = tone === 'trust' ? 'text-trust-700 dark:text-trust-300' : 'text-slate-600 dark:text-slate-300';
  return <section><h2 className={`flex items-center gap-2 font-bold ${color}`}><Icon className="h-4 w-4" aria-hidden="true" />{title}</h2><ul className="mt-2 space-y-2">{items?.length ? items.map((item) => <li key={item} className="flex gap-2 text-sm leading-6 text-slate-600 dark:text-slate-300"><span className="mt-2 h-1.5 w-1.5 shrink-0 rounded-full bg-current" aria-hidden="true" />{item}</li>) : <li className="text-sm leading-6 text-slate-500 dark:text-slate-400">{empty}</li>}</ul></section>;
}
