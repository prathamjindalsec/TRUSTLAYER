import { RotateCcw, TriangleAlert } from 'lucide-react';

export function ErrorState({ title = 'Could not load this data', message, retry }) {
  return (
    <div className="rounded-xl border border-red-200 bg-red-50 p-5 text-red-950 dark:border-red-500/25 dark:bg-red-950/30 dark:text-red-100">
      <div className="flex items-start gap-3">
        <TriangleAlert className="mt-0.5 h-5 w-5 shrink-0" aria-hidden="true" />
        <div>
          <h2 className="font-bold">{title}</h2>
          <p className="mt-1 text-sm leading-6 opacity-90">{message || 'Check your connection, then try again.'}</p>
          {retry && (
            <button type="button" onClick={retry} className="btn-secondary mt-4 border-red-200 bg-white text-red-900 hover:bg-red-100 dark:border-red-500/30 dark:bg-transparent dark:text-red-100">
              <RotateCcw className="h-4 w-4" aria-hidden="true" />
              Try again
            </button>
          )}
        </div>
      </div>
    </div>
  );
}
