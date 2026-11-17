import {
  Activity,
  ArrowLeftRight,
  FileSearch,
  LayoutDashboard,
  QrCode,
  ScanSearch,
  ShieldAlert,
  UserRound,
  X,
} from 'lucide-react';
import { NavLink } from 'react-router-dom';

import { BrandMark } from '../common/BrandMark';

const navigation = [
  { to: '/app/dashboard', label: 'Overview', icon: LayoutDashboard, end: true },
  { to: '/app/scan-url', label: 'URL scanner', icon: ScanSearch },
  { to: '/app/scan-qr', label: 'QR scanner', icon: QrCode },
  { to: '/app/transactions', label: 'Transaction lab', icon: ArrowLeftRight },
  { to: '/app/threats', label: 'Threat intelligence', icon: ShieldAlert },
  { to: '/app/profile', label: 'Account & sessions', icon: UserRound },
];

export function Sidebar({ open, onClose, panelRef }) {
  return (
    <aside id="app-navigation" ref={panelRef} tabIndex={open ? '-1' : undefined} role={open ? 'dialog' : undefined} aria-modal={open || undefined} className={`fixed inset-y-0 left-0 z-40 flex w-72 flex-col border-r border-slate-200 bg-white px-4 py-5 shadow-xl transition-transform duration-200 dark:border-slate-800 dark:bg-ink-900 lg:translate-x-0 lg:shadow-none ${open ? 'translate-x-0' : '-translate-x-full'}`} aria-label={open ? 'Navigation menu' : 'Primary navigation'}>
      <div className="flex items-center justify-between px-2">
        <BrandMark to="/app/dashboard" />
        <button type="button" onClick={onClose} className="inline-flex h-11 w-11 items-center justify-center rounded-lg text-slate-500 hover:bg-slate-100 dark:hover:bg-ink-800 lg:hidden" aria-label="Close navigation">
          <X className="h-5 w-5" aria-hidden="true" />
        </button>
      </div>
      <nav className="mt-9 space-y-1" aria-label="Product navigation">
        {navigation.map(({ to, label, icon: Icon, end }) => (
          <NavLink
            key={to}
            to={to}
            end={end}
            onClick={onClose}
            className={({ isActive }) => `group flex min-h-11 items-center gap-3 rounded-xl px-3 text-sm font-semibold transition ${isActive ? 'bg-trust-50 text-trust-700 dark:bg-trust-500/15 dark:text-trust-300' : 'text-slate-600 hover:bg-slate-100 hover:text-slate-950 dark:text-slate-300 dark:hover:bg-ink-800 dark:hover:text-white'}`}
          >
            <Icon className="h-[18px] w-[18px]" aria-hidden="true" />
            {label}
          </NavLink>
        ))}
      </nav>
      <div className="mt-auto space-y-3 px-2">
        <div className="rounded-xl border border-trust-100 bg-trust-50 p-3 dark:border-trust-500/20 dark:bg-trust-500/10">
          <div className="flex items-center gap-2 text-sm font-bold text-trust-800 dark:text-trust-200"><Activity className="h-4 w-4" aria-hidden="true" />Protection boundary</div>
          <p className="mt-1.5 text-xs leading-5 text-trust-800/80 dark:text-trust-100/75">TRUSTLAYER explains risk. It does not open suspicious links or block real payments.</p>
        </div>
        <NavLink to="/privacy" className="flex items-center gap-2 px-2 text-xs font-semibold text-slate-500 hover:text-trust-700 dark:text-slate-400 dark:hover:text-trust-300"><FileSearch className="h-3.5 w-3.5" aria-hidden="true" />Privacy and terms</NavLink>
      </div>
    </aside>
  );
}
