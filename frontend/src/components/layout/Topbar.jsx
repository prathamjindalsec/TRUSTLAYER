import { ChevronDown, LogOut, Moon, Sun, UserRound } from 'lucide-react';
import { useEffect, useRef, useState } from 'react';
import { Link, useNavigate } from 'react-router-dom';

import { useAuth } from '../../context/AuthContext';
import { useTheme } from '../../context/ThemeContext';
import { initials } from '../../utils/formatters';
import { MobileMenuButton } from './AppShell';

export function Topbar({ onToggleNavigation, menuButtonRef, navigationOpen }) {
  const { user, logout } = useAuth();
  const { resolvedTheme, setTheme } = useTheme();
  const [open, setOpen] = useState(false);
  const menuRef = useRef(null);
  const accountButtonRef = useRef(null);
  const navigate = useNavigate();

  useEffect(() => {
    const closeForOutsidePointer = (event) => {
      if (!menuRef.current?.contains(event.target)) setOpen(false);
    };
    document.addEventListener('pointerdown', closeForOutsidePointer);
    return () => document.removeEventListener('pointerdown', closeForOutsidePointer);
  }, []);

  useEffect(() => {
    if (!open) return undefined;
    const closeOnEscape = (event) => {
      if (event.key !== 'Escape') return;
      event.preventDefault();
      setOpen(false);
      accountButtonRef.current?.focus();
    };
    document.addEventListener('keydown', closeOnEscape);
    return () => document.removeEventListener('keydown', closeOnEscape);
  }, [open]);

  const signOut = async () => {
    setOpen(false);
    await logout();
    navigate('/login', { replace: true });
  };

  return (
    <header className="sticky top-0 z-20 flex h-16 items-center justify-between border-b border-slate-200/80 bg-slate-50/95 px-4 backdrop-blur dark:border-slate-800/80 dark:bg-ink-950/95 sm:px-6 lg:px-8">
      <div className="flex items-center gap-2">
        <MobileMenuButton buttonRef={menuButtonRef} expanded={navigationOpen} onClick={onToggleNavigation} />
        <p className="hidden text-sm font-medium text-slate-500 sm:block dark:text-slate-400">Security workspace</p>
      </div>
      <div className="flex items-center gap-2 sm:gap-3">
        <button
          type="button"
          onClick={() => setTheme(resolvedTheme === 'dark' ? 'light' : 'dark')}
          className="inline-flex h-11 w-11 items-center justify-center rounded-xl text-slate-600 transition hover:bg-slate-200/70 dark:text-slate-300 dark:hover:bg-ink-800"
          aria-label={`Switch to ${resolvedTheme === 'dark' ? 'light' : 'dark'} mode`}
        >
          {resolvedTheme === 'dark' ? <Sun className="h-4.5 w-4.5" aria-hidden="true" /> : <Moon className="h-4.5 w-4.5" aria-hidden="true" />}
        </button>
        <div className="relative" ref={menuRef}>
          <button ref={accountButtonRef} type="button" onClick={() => setOpen((current) => !current)} className="flex min-h-11 items-center gap-2 rounded-xl px-1.5 text-left transition hover:bg-slate-200/70 dark:hover:bg-ink-800" aria-expanded={open} aria-controls="account-actions">
            <span className="flex h-8 w-8 items-center justify-center rounded-full bg-ink-900 text-xs font-bold text-white dark:bg-trust-500">{initials(user)}</span>
            <span className="hidden max-w-36 truncate text-sm font-semibold text-slate-700 md:block dark:text-slate-100">{user?.firstName || user?.email}</span>
            <ChevronDown className="hidden h-4 w-4 text-slate-500 sm:block" aria-hidden="true" />
          </button>
          {open && (
            <div id="account-actions" aria-label="Account actions" className="absolute right-0 top-[calc(100%+0.5rem)] w-56 rounded-xl border bg-white p-1.5 shadow-panel dark:border-slate-700 dark:bg-ink-850 dark:shadow-panel-dark">
              <div className="border-b px-3 py-2.5 dark:border-slate-700">
                <p className="truncate text-sm font-bold text-slate-900 dark:text-white">{user?.firstName ? `${user.firstName} ${user.lastName || ''}`.trim() : 'Your account'}</p>
                <p className="mt-0.5 truncate text-xs text-slate-500 dark:text-slate-400">{user?.email}</p>
              </div>
              <Link onClick={() => setOpen(false)} to="/app/profile" className="mt-1 flex min-h-11 items-center gap-2 rounded-lg px-3 text-sm font-semibold text-slate-700 hover:bg-slate-100 dark:text-slate-200 dark:hover:bg-ink-800"><UserRound className="h-4 w-4" aria-hidden="true" />Account and sessions</Link>
              <button type="button" onClick={signOut} className="flex min-h-11 w-full items-center gap-2 rounded-lg px-3 text-sm font-semibold text-alert-red hover:bg-red-50 dark:hover:bg-red-950/30"><LogOut className="h-4 w-4" aria-hidden="true" />Sign out</button>
            </div>
          )}
        </div>
      </div>
    </header>
  );
}
