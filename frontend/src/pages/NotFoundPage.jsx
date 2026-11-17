import { ArrowLeft, SearchX } from 'lucide-react';
import { Link } from 'react-router-dom';

import { usePageTitle } from '../hooks/usePageTitle';

export function NotFoundPage() {
  usePageTitle('Page not found');
  return <div className="flex min-h-[65vh] items-center justify-center px-4"><div className="max-w-md text-center"><SearchX className="mx-auto h-10 w-10 text-trust-600 dark:text-trust-300" aria-hidden="true" /><p className="mt-6 text-sm font-bold uppercase tracking-[0.14em] text-trust-700 dark:text-trust-300">404</p><h1 className="mt-2 text-3xl font-bold text-ink-950 dark:text-white">That page is not here.</h1><p className="mt-3 text-sm leading-6 text-slate-600 dark:text-slate-300">The address may be incorrect, or the page may no longer be available.</p><Link to="/" className="btn-primary mt-7"><ArrowLeft className="h-4 w-4" aria-hidden="true" />Back to TRUSTLAYER</Link></div></div>;
}
