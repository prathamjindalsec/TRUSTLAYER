import {
  ArrowRight,
  Banknote,
  CheckCircle2,
  Eye,
  QrCode,
  ScanSearch,
  ShieldAlert,
  ShieldCheck,
  Sparkles,
} from 'lucide-react';
import { Link } from 'react-router-dom';

import { usePageTitle } from '../hooks/usePageTitle';

const capabilities = [
  { icon: ScanSearch, title: 'Explain suspicious URLs', text: 'Static structural analysis flags phishing patterns without opening the destination.' },
  { icon: QrCode, title: 'Inspect QR destinations', text: 'Decode local QR images, classify their content, and do not initiate a payment or navigation.' },
  { icon: Banknote, title: 'Practice transaction review', text: 'Compare simulated payment scenarios against your own simulation history. No banking rails are connected.' },
  { icon: ShieldAlert, title: 'Share community signals', text: 'Report suspicious indicators with redacted storage and clear unverified status.' },
];

const principles = [
  'Every score includes the signals and weights behind it.',
  'A secure-looking HTTPS lock is never treated as proof of safety.',
  'TRUSTLAYER does not fetch submitted links or execute QR payment instructions.',
  'Community reports stay unverified until an authorized human reviewer changes their status.',
];

export function LandingPage() {
  usePageTitle();
  return (
    <div>
      <section className="border-b border-slate-200 bg-white px-4 py-16 dark:border-slate-800 dark:bg-ink-950 sm:px-6 sm:py-24">
        <div className="mx-auto grid max-w-7xl gap-12 lg:grid-cols-[1.15fr_0.85fr] lg:items-center">
          <div className="max-w-3xl">
            <p className="inline-flex items-center gap-2 rounded-full border border-trust-200 bg-trust-50 px-3 py-1.5 text-xs font-bold uppercase tracking-[0.12em] text-trust-700 dark:border-trust-500/25 dark:bg-trust-500/10 dark:text-trust-300"><ShieldCheck className="h-3.5 w-3.5" aria-hidden="true" />Financial cyber-risk workspace</p>
            <h1 className="mt-6 text-4xl font-black tracking-[-0.045em] text-ink-950 sm:text-5xl lg:text-6xl dark:text-white">Stop financial fraud before money moves.</h1>
            <p className="mt-6 max-w-2xl text-lg leading-8 text-slate-600 dark:text-slate-300">TRUSTLAYER turns suspicious links, QR codes, community signals, and simulated payment patterns into clear, explainable security decisions.</p>
            <div className="mt-8 flex flex-col gap-3 sm:flex-row">
              <Link to="/register" className="btn-primary px-5">Create a secure workspace <ArrowRight className="h-4 w-4" aria-hidden="true" /></Link>
              <Link to="/login" className="btn-secondary px-5">Sign in</Link>
            </div>
            <p className="mt-5 text-xs leading-5 text-slate-500 dark:text-slate-400">Decision support only. TRUSTLAYER cannot block real financial transactions without an authorized provider integration.</p>
          </div>
          <div className="surface relative overflow-hidden p-5 sm:p-7">
            <div className="absolute right-0 top-0 h-32 w-32 rounded-bl-full bg-trust-50 dark:bg-trust-500/10" aria-hidden="true" />
            <div className="relative">
              <div className="flex items-center justify-between"><span className="text-sm font-bold text-slate-700 dark:text-slate-200">Explainable assessment</span><span className="rounded-full bg-amber-100 px-2.5 py-1 text-xs font-bold text-amber-800 dark:bg-amber-500/15 dark:text-amber-200">Review advised</span></div>
              <div className="mt-8 flex items-end justify-between gap-4"><div><p className="text-5xl font-black tracking-tight text-ink-950 dark:text-white">58<span className="text-xl text-slate-400">/100</span></p><p className="mt-1 text-sm font-semibold text-slate-600 dark:text-slate-300">Illustrative risk score</p></div><div className="flex h-16 w-16 items-center justify-center rounded-2xl bg-amber-100 text-amber-700 dark:bg-amber-500/15 dark:text-amber-200"><Eye className="h-7 w-7" aria-hidden="true" /></div></div>
              <div className="mt-8 space-y-3 border-t pt-5 dark:border-slate-800"><Signal label="Direct IP-address destination" weight="+30" tone="high" /><Signal label="Brand lookalike pattern" weight="+30" tone="high" /><Signal label="Static analysis only" weight="Unknown" tone="neutral" /></div>
              <p className="mt-6 rounded-xl bg-slate-50 p-3 text-xs leading-5 text-slate-600 dark:bg-ink-850 dark:text-slate-300">Scores describe observed evidence—not certainty. The real product view shows the exact rules, limitations, and recommendations from the backend.</p>
            </div>
          </div>
        </div>
      </section>

      <section className="px-4 py-16 sm:px-6 sm:py-20">
        <div className="mx-auto max-w-7xl">
          <div className="max-w-2xl"><p className="text-xs font-bold uppercase tracking-[0.14em] text-trust-700 dark:text-trust-300">Built for the moment before action</p><h2 className="mt-3 text-3xl font-bold tracking-tight text-ink-950 dark:text-white">One security workspace. Four useful checks.</h2><p className="mt-3 leading-7 text-slate-600 dark:text-slate-300">The platform favors transparent evidence, privacy minimization, and safe analysis boundaries over decorative “AI” claims.</p></div>
          <div className="mt-10 grid gap-4 sm:grid-cols-2 lg:grid-cols-4">
            {capabilities.map(({ icon: Icon, title, text }) => <article key={title} className="surface p-5"><span className="inline-flex rounded-xl bg-trust-50 p-2.5 text-trust-700 dark:bg-trust-500/15 dark:text-trust-300"><Icon className="h-5 w-5" aria-hidden="true" /></span><h3 className="mt-5 font-bold text-ink-950 dark:text-white">{title}</h3><p className="mt-2 text-sm leading-6 text-slate-600 dark:text-slate-300">{text}</p></article>)}
          </div>
        </div>
      </section>

      <section className="border-y border-slate-200 bg-white px-4 py-16 dark:border-slate-800 dark:bg-ink-900 sm:px-6 sm:py-20">
        <div className="mx-auto grid max-w-7xl gap-10 lg:grid-cols-2 lg:items-start">
          <div><p className="text-xs font-bold uppercase tracking-[0.14em] text-trust-700 dark:text-trust-300">Security principles</p><h2 className="mt-3 text-3xl font-bold tracking-tight text-ink-950 dark:text-white">Useful warnings should also be honest warnings.</h2><p className="mt-4 max-w-xl leading-7 text-slate-600 dark:text-slate-300">A cybersecurity dashboard that claims certainty it does not have creates a new form of risk. TRUSTLAYER shows its limits along with its findings.</p></div>
          <ul className="space-y-4">{principles.map((principle) => <li key={principle} className="flex gap-3 rounded-xl border bg-slate-50 p-4 text-sm leading-6 text-slate-700 dark:border-slate-800 dark:bg-ink-850 dark:text-slate-200"><CheckCircle2 className="mt-0.5 h-5 w-5 shrink-0 text-trust-600 dark:text-trust-300" aria-hidden="true" />{principle}</li>)}</ul>
        </div>
      </section>

      <section className="px-4 py-16 sm:px-6 sm:py-20"><div className="mx-auto max-w-4xl rounded-2xl border border-trust-200 bg-trust-50 p-8 text-center dark:border-trust-500/20 dark:bg-trust-500/10 sm:p-12"><Sparkles className="mx-auto h-7 w-7 text-trust-700 dark:text-trust-300" aria-hidden="true" /><h2 className="mt-4 text-3xl font-bold tracking-tight text-ink-950 dark:text-white">Make the next decision with more context.</h2><p className="mx-auto mt-3 max-w-2xl text-sm leading-7 text-slate-700 dark:text-slate-200">Set up an account to scan a suspicious URL, inspect a QR image, or model a transaction-risk scenario without sending money.</p><Link to="/register" className="btn-primary mt-7 px-5">Get started safely <ArrowRight className="h-4 w-4" aria-hidden="true" /></Link></div></section>
    </div>
  );
}

function Signal({ label, weight, tone }) {
  const colors = { high: 'bg-red-500', neutral: 'bg-slate-400' };
  return <div className="flex items-center justify-between gap-3 text-sm"><span className="flex items-center gap-2 text-slate-700 dark:text-slate-200"><span className={`h-2 w-2 rounded-full ${colors[tone]}`} aria-hidden="true" />{label}</span><span className="font-bold text-slate-900 dark:text-white">{weight}</span></div>;
}
