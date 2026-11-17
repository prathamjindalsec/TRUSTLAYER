import { ArrowLeftRight, Info, LoaderCircle, ShieldCheck } from 'lucide-react';
import { useCallback, useEffect, useState } from 'react';

import { analyzeTransaction, transactionHistory } from '../api/transactions';
import { EmptyState } from '../components/common/EmptyState';
import { ErrorState } from '../components/common/ErrorState';
import { RiskBadge } from '../components/common/RiskBadge';
import { RiskReport } from '../components/common/RiskReport';
import { SelectField, TextField } from '../components/common/FormField';
import { PageHeader } from '../components/common/PageHeader';
import { useToast } from '../context/ToastContext';
import { usePageTitle } from '../hooks/usePageTitle';
import { getApiErrorMessage } from '../utils/errors';
import { formatCurrency, formatDateTime } from '../utils/formatters';

function localOffsetDateTime(value) {
  const local = new Date(value);
  if (Number.isNaN(local.getTime())) return null;
  const offset = -local.getTimezoneOffset();
  const sign = offset >= 0 ? '+' : '-';
  const absolute = Math.abs(offset);
  const hours = String(Math.floor(absolute / 60)).padStart(2, '0');
  const minutes = String(absolute % 60).padStart(2, '0');
  return `${value.length === 16 ? `${value}:00` : value}${sign}${hours}:${minutes}`;
}

function defaultLocalTime() {
  const now = new Date();
  now.setMinutes(now.getMinutes() - now.getTimezoneOffset());
  return now.toISOString().slice(0, 16);
}

export function TransactionPage() {
  usePageTitle('Transaction lab');
  const toast = useToast();
  const [values, setValues] = useState({ amount: '', currency: 'INR', beneficiaryIdentifier: '', transactionTime: defaultLocalTime() });
  const [result, setResult] = useState(null); const [history, setHistory] = useState(null); const [busy, setBusy] = useState(false); const [error, setError] = useState(''); const [historyError, setHistoryError] = useState('');
  const loadHistory = useCallback(async () => { try { setHistoryError(''); setHistory(await transactionHistory({ page: 0, size: 10 })); } catch (requestError) { setHistoryError(getApiErrorMessage(requestError, 'Could not load simulation history.')); } }, []);
  useEffect(() => { loadHistory(); }, [loadHistory]);
  const submit = async (event) => { event.preventDefault(); const amount = Number(values.amount); if (!Number.isFinite(amount) || amount <= 0) return setError('Enter an amount greater than zero.'); const transactionTime = localOffsetDateTime(values.transactionTime); if (!transactionTime) return setError('Provide a valid local date and time.'); setBusy(true); setError(''); try { const response = await analyzeTransaction({ amount, currency: values.currency, beneficiaryIdentifier: values.beneficiaryIdentifier, transactionTime }); setResult(response); toast.success('Simulation analyzed. No real payment was sent or blocked.'); loadHistory(); } catch (requestError) { setError(getApiErrorMessage(requestError, 'Could not analyze this simulation.')); } finally { setBusy(false); } };
  return <div><PageHeader eyebrow="Simulation only" title="Explore transaction-risk signals" description="Model a hypothetical payment before you act. TRUSTLAYER uses only the simulations in this account; it never sees or controls a real bank, UPI, wallet, card, or transfer." /><div className="grid gap-6 xl:grid-cols-[0.9fr_1.1fr]"><section className="surface p-5 sm:p-6"><form noValidate onSubmit={submit} className="space-y-4"><div className="grid gap-4 sm:grid-cols-[1fr_130px]"><TextField label="Simulated amount" type="number" min="0.01" step="0.01" inputMode="decimal" placeholder="120000.00" value={values.amount} onChange={(event) => setValues((current) => ({ ...current, amount: event.target.value }))} required /><SelectField label="Currency" value={values.currency} onChange={(event) => setValues((current) => ({ ...current, currency: event.target.value }))}><option value="INR">INR</option><option value="USD">USD</option><option value="EUR">EUR</option><option value="GBP">GBP</option></SelectField></div><TextField label="Beneficiary identifier" value={values.beneficiaryIdentifier} onChange={(event) => setValues((current) => ({ ...current, beneficiaryIdentifier: event.target.value }))} hint="Used only to compare simulations within this account. It is redacted in stored results." placeholder="demo-beneficiary@bank" required /><TextField label="Local transaction time" type="datetime-local" max={defaultLocalTime()} value={values.transactionTime} onChange={(event) => setValues((current) => ({ ...current, transactionTime: event.target.value }))} hint="Your browser's time-zone offset is included for the timing rule." required />{error && <p role="alert" className="text-sm text-alert-red">{error}</p>}<button type="submit" className="btn-primary w-full" disabled={busy}>{busy ? <LoaderCircle className="h-4 w-4 animate-spin" aria-hidden="true" /> : <ArrowLeftRight className="h-4 w-4" aria-hidden="true" />}{busy ? 'Analyzing simulation…' : 'Analyze simulated transaction'}</button></form><div className="mt-6 rounded-xl border border-blue-200 bg-blue-50 p-4 text-sm leading-6 text-blue-950 dark:border-blue-500/25 dark:bg-blue-950/30 dark:text-blue-100"><Info className="mb-2 h-4 w-4" aria-hidden="true" /><strong>Strict boundary:</strong> this feature is a demo risk analyzer. It cannot approve, block, delay, reverse, or send real money.</div></section><div>{result ? <RiskReport title="Simulated transaction assessment" targetLabel="Beneficiary" target={result.beneficiary} result={result} /> : <EmptyState icon={ArrowLeftRight} title="Simulation result will appear here" description="Enter a hypothetical amount, beneficiary identifier, and time to see transparent behavioral rules and their limitations." />}</div></div><section className="surface mt-6 p-5 sm:p-6"><div className="mb-5"><h2 className="text-lg font-bold text-ink-950 dark:text-white">Your simulated transaction history</h2><p className="mt-1 text-sm text-slate-600 dark:text-slate-300">Only this account's redacted demonstration records are included.</p></div>{historyError ? <ErrorState title="History is temporarily unavailable" message={historyError} retry={loadHistory} /> : history ? history.items?.length ? <div className="overflow-x-auto"><table className="w-full min-w-[700px] text-left text-sm"><thead className="border-b text-xs uppercase tracking-wide text-slate-500 dark:border-slate-800 dark:text-slate-400"><tr><th className="px-1 pb-3 font-bold">Simulation</th><th className="px-3 pb-3 font-bold">Amount</th><th className="px-3 pb-3 font-bold">Beneficiary</th><th className="px-3 pb-3 font-bold">Risk</th><th className="px-1 pb-3 text-right font-bold">When</th></tr></thead><tbody className="divide-y dark:divide-slate-800">{history.items.map((item) => <tr key={item.transactionId}><td className="px-1 py-3.5 font-medium text-slate-800 dark:text-slate-100">#{item.transactionId}</td><td className="px-3 py-3.5 text-slate-700 dark:text-slate-200">{formatCurrency(item.amount, item.currency)}</td><td className="px-3 py-3.5 text-slate-600 dark:text-slate-300">{item.beneficiary}</td><td className="px-3 py-3.5"><RiskBadge risk={item.riskClassification} score={item.riskScore} compact /></td><td className="px-1 py-3.5 text-right text-slate-500 dark:text-slate-400">{formatDateTime(item.transactionTime)}</td></tr>)}</tbody></table></div> : <EmptyState icon={ShieldCheck} title="No simulations yet" description="Your redacted simulation history will appear here after analysis." /> : <div className="h-40 animate-pulse rounded-xl bg-slate-100 dark:bg-ink-800" />}</section></div>;
}
