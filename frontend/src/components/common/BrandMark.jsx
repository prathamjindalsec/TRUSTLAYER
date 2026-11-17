import { ShieldCheck } from 'lucide-react';
import { Link } from 'react-router-dom';

export function BrandMark({ compact = false, to = '/' }) {
  const content = (
    <>
      <span className="flex h-9 w-9 items-center justify-center rounded-xl bg-trust-500 text-white shadow-sm" aria-hidden="true">
        <ShieldCheck className="h-5 w-5" strokeWidth={2.5} />
      </span>
      {!compact && (
        <span className="leading-none">
          <span className="block text-sm font-extrabold tracking-[0.14em] text-ink-950 dark:text-white">TRUSTLAYER</span>
          <span className="mt-1 block text-[10px] font-semibold uppercase tracking-[0.13em] text-slate-500 dark:text-slate-400">Financial defense</span>
        </span>
      )}
    </>
  );

  return to ? (
    <Link to={to} className="inline-flex items-center gap-2.5 rounded-lg" aria-label="TRUSTLAYER home">
      {content}
    </Link>
  ) : (
    <span className="inline-flex items-center gap-2.5">{content}</span>
  );
}
