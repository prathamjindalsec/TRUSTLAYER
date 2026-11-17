import { Inbox } from 'lucide-react';

export function EmptyState({ icon: Icon = Inbox, title, description, action }) {
  return (
    <div className="flex min-h-52 flex-col items-center justify-center rounded-xl border border-dashed border-slate-300 px-6 py-10 text-center dark:border-slate-700">
      <span className="mb-4 rounded-xl bg-slate-100 p-3 text-slate-500 dark:bg-ink-800 dark:text-slate-300">
        <Icon className="h-6 w-6" aria-hidden="true" />
      </span>
      <h2 className="text-base font-bold text-slate-900 dark:text-white">{title}</h2>
      {description && <p className="mt-1.5 max-w-md text-sm leading-6 text-slate-600 dark:text-slate-300">{description}</p>}
      {action && <div className="mt-5">{action}</div>}
    </div>
  );
}
