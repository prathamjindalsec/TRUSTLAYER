import { Moon, Sun } from 'lucide-react';
import { Link, Outlet } from 'react-router-dom';

import { useTheme } from '../../context/ThemeContext';
import { BrandMark } from '../common/BrandMark';
import { SkipLink } from '../common/SkipLink';

export function AuthLayout() {
  const { resolvedTheme, setTheme } = useTheme();
  return (
    <div className="min-h-screen bg-slate-50 px-4 py-5 dark:bg-ink-950 sm:px-6 sm:py-7">
      <SkipLink />
      <header className="mx-auto flex max-w-6xl items-center justify-between">
        <BrandMark />
        <button type="button" onClick={() => setTheme(resolvedTheme === 'dark' ? 'light' : 'dark')} className="inline-flex h-11 w-11 items-center justify-center rounded-xl text-slate-600 hover:bg-white dark:text-slate-300 dark:hover:bg-ink-900" aria-label={`Switch to ${resolvedTheme === 'dark' ? 'light' : 'dark'} mode`}>
          {resolvedTheme === 'dark' ? <Sun className="h-4.5 w-4.5" aria-hidden="true" /> : <Moon className="h-4.5 w-4.5" aria-hidden="true" />}
        </button>
      </header>
      <main id="main-content" tabIndex="-1" className="mx-auto flex min-h-[calc(100vh-6.5rem)] max-w-6xl items-center justify-center py-10 focus:outline-none">
        <Outlet />
      </main>
      <p className="mx-auto max-w-6xl text-center text-xs leading-5 text-slate-500 dark:text-slate-400">Your credentials are protected with secure authentication controls. <Link to="/privacy" className="link-inline">Read the privacy policy</Link>.</p>
    </div>
  );
}
