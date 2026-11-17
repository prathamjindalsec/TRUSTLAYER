import { Menu, Moon, Sun } from 'lucide-react';
import { useEffect, useRef, useState } from 'react';
import { Link, NavLink, Outlet } from 'react-router-dom';

import { useTheme } from '../../context/ThemeContext';
import { BrandMark } from '../common/BrandMark';
import { SkipLink } from '../common/SkipLink';

export function PublicLayout() {
  const { resolvedTheme, setTheme } = useTheme();
  const [menuOpen, setMenuOpen] = useState(false);
  const menuButtonRef = useRef(null);

  useEffect(() => {
    if (!menuOpen) return undefined;
    const closeOnEscape = (event) => {
      if (event.key !== 'Escape') return;
      event.preventDefault();
      setMenuOpen(false);
      menuButtonRef.current?.focus();
    };
    document.addEventListener('keydown', closeOnEscape);
    return () => document.removeEventListener('keydown', closeOnEscape);
  }, [menuOpen]);

  return (
    <div className="min-h-screen bg-slate-50 dark:bg-ink-950">
      <SkipLink />
      <header className="border-b border-slate-200/80 bg-white/95 px-4 py-3 backdrop-blur dark:border-slate-800 dark:bg-ink-950/95 sm:px-6">
        <div className="mx-auto flex max-w-7xl items-center justify-between gap-4">
          <BrandMark />
          <nav className="hidden items-center gap-6 md:flex" aria-label="Public navigation">
            <NavLink to="/" className="text-sm font-semibold text-slate-600 hover:text-trust-700 dark:text-slate-300 dark:hover:text-trust-300">How it works</NavLink>
            <NavLink to="/privacy" className="text-sm font-semibold text-slate-600 hover:text-trust-700 dark:text-slate-300 dark:hover:text-trust-300">Privacy</NavLink>
            <NavLink to="/terms" className="text-sm font-semibold text-slate-600 hover:text-trust-700 dark:text-slate-300 dark:hover:text-trust-300">Terms</NavLink>
          </nav>
          <div className="flex items-center gap-2">
            <button type="button" onClick={() => setTheme(resolvedTheme === 'dark' ? 'light' : 'dark')} className="inline-flex h-11 w-11 items-center justify-center rounded-xl text-slate-600 hover:bg-slate-100 dark:text-slate-300 dark:hover:bg-ink-800" aria-label={`Switch to ${resolvedTheme === 'dark' ? 'light' : 'dark'} mode`}>
              {resolvedTheme === 'dark' ? <Sun className="h-4.5 w-4.5" aria-hidden="true" /> : <Moon className="h-4.5 w-4.5" aria-hidden="true" />}
            </button>
            <Link to="/login" className="hidden min-h-10 items-center rounded-xl px-3 text-sm font-bold text-slate-700 hover:bg-slate-100 dark:text-slate-100 dark:hover:bg-ink-800 sm:inline-flex">Sign in</Link>
            <Link to="/register" className="btn-primary hidden sm:inline-flex">Create account</Link>
            <button ref={menuButtonRef} type="button" onClick={() => setMenuOpen((current) => !current)} className="inline-flex h-11 w-11 items-center justify-center rounded-xl text-slate-600 hover:bg-slate-100 dark:text-slate-300 dark:hover:bg-ink-800 md:hidden" aria-controls="public-mobile-navigation" aria-expanded={menuOpen} aria-label="Toggle navigation"><Menu className="h-5 w-5" aria-hidden="true" /></button>
          </div>
        </div>
        {menuOpen && (
          <nav id="public-mobile-navigation" className="mx-auto mt-3 flex max-w-7xl flex-col gap-1 border-t pt-3 md:hidden dark:border-slate-800" aria-label="Mobile public navigation">
            {[
              ['/', 'How it works'],
              ['/privacy', 'Privacy'],
              ['/terms', 'Terms'],
              ['/login', 'Sign in'],
              ['/register', 'Create account'],
            ].map(([to, label]) => <Link key={to} onClick={() => setMenuOpen(false)} to={to} className="rounded-lg px-3 py-2.5 text-sm font-semibold text-slate-700 hover:bg-slate-100 dark:text-slate-200 dark:hover:bg-ink-800">{label}</Link>)}
          </nav>
        )}
      </header>
      <main id="main-content" tabIndex="-1" className="focus:outline-none"><Outlet /></main>
      <footer className="border-t border-slate-200 bg-white px-4 py-8 dark:border-slate-800 dark:bg-ink-900 sm:px-6">
        <div className="mx-auto flex max-w-7xl flex-col gap-4 text-sm text-slate-500 sm:flex-row sm:items-center sm:justify-between dark:text-slate-400">
          <p>© {new Date().getFullYear()} TRUSTLAYER. Decision support, not a guarantee of safety.</p>
          <div className="flex flex-wrap gap-x-5 gap-y-2"><Link to="/privacy" className="hover:text-trust-700 dark:hover:text-trust-300">Privacy</Link><Link to="/terms" className="hover:text-trust-700 dark:hover:text-trust-300">Terms</Link><button type="button" onClick={() => window.dispatchEvent(new Event('trustlayer:cookie-settings'))} className="hover:text-trust-700 dark:hover:text-trust-300">Cookie choices</button></div>
        </div>
      </footer>
    </div>
  );
}
