import { Menu, WifiOff } from 'lucide-react';
import { useRef, useState } from 'react';
import { Outlet } from 'react-router-dom';

import { SkipLink } from '../common/SkipLink';
import { useFocusTrap } from '../../hooks/useFocusTrap';
import { useOnlineStatus } from '../../hooks/useOnlineStatus';
import { Sidebar } from './Sidebar';
import { Topbar } from './Topbar';

export function AppShell() {
  const [sidebarOpen, setSidebarOpen] = useState(false);
  const sidebarRef = useRef(null);
  const menuButtonRef = useRef(null);
  const online = useOnlineStatus();
  const closeSidebar = () => setSidebarOpen(false);

  useFocusTrap(sidebarOpen, sidebarRef, closeSidebar);

  return (
    <div className="min-h-screen bg-slate-50 dark:bg-ink-950">
      <SkipLink />
      <Sidebar panelRef={sidebarRef} open={sidebarOpen} onClose={closeSidebar} />
      <div className="lg:pl-72">
        <Topbar menuButtonRef={menuButtonRef} navigationOpen={sidebarOpen} onToggleNavigation={() => setSidebarOpen((current) => !current)} />
        {!online && (
          <div role="status" className="flex items-center justify-center gap-2 border-y border-amber-300 bg-amber-50 px-4 py-2 text-center text-sm font-semibold text-amber-900 dark:border-amber-500/30 dark:bg-amber-950/40 dark:text-amber-100">
            <WifiOff className="h-4 w-4" aria-hidden="true" />
            You are offline. New scans and account changes will wait until your connection returns.
          </div>
        )}
        <main id="main-content" tabIndex="-1" className="mx-auto w-full max-w-[1600px] px-4 py-6 focus:outline-none sm:px-6 sm:py-8 lg:px-8">
          <Outlet />
        </main>
      </div>
      {sidebarOpen && (
        <button
          type="button"
          aria-label="Close navigation"
          onClick={closeSidebar}
          className="fixed inset-0 z-30 bg-ink-950/55 lg:hidden"
        />
      )}
    </div>
  );
}

export function MobileMenuButton({ onClick, buttonRef, expanded = false }) {
  return (
    <button ref={buttonRef} type="button" onClick={onClick} className="inline-flex h-11 w-11 items-center justify-center rounded-xl text-slate-600 hover:bg-slate-100 dark:text-slate-300 dark:hover:bg-ink-800 lg:hidden" aria-controls="app-navigation" aria-label={expanded ? 'Close navigation' : 'Open navigation'} aria-expanded={expanded}>
      <Menu className="h-5 w-5" aria-hidden="true" />
    </button>
  );
}
