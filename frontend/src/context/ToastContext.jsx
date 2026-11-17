import { createContext, useCallback, useContext, useMemo, useRef, useState } from 'react';
import { CheckCircle2, Info, TriangleAlert, X, XCircle } from 'lucide-react';

const ToastContext = createContext(null);
const icons = {
  success: CheckCircle2,
  error: XCircle,
  warning: TriangleAlert,
  info: Info,
};

export function ToastProvider({ children }) {
  const [toasts, setToasts] = useState([]);
  const nextId = useRef(0);

  const dismiss = useCallback((id) => {
    setToasts((current) => current.filter((toast) => toast.id !== id));
  }, []);

  const show = useCallback((message, tone = 'info', options = {}) => {
    const id = ++nextId.current;
    const toast = { id, message, tone, title: options.title };
    setToasts((current) => [...current, toast].slice(-4));
    window.setTimeout(() => dismiss(id), options.duration ?? 5_000);
    return id;
  }, [dismiss]);

  const value = useMemo(() => ({
    show,
    success: (message, options) => show(message, 'success', options),
    error: (message, options) => show(message, 'error', options),
    warning: (message, options) => show(message, 'warning', options),
    info: (message, options) => show(message, 'info', options),
  }), [show]);

  return (
    <ToastContext.Provider value={value}>
      {children}
      <div className="pointer-events-none fixed inset-x-4 bottom-4 z-[100] mx-auto flex max-w-md flex-col gap-2 sm:left-auto sm:right-5 sm:mx-0">
        {toasts.map((toast) => {
          const Icon = icons[toast.tone] ?? Info;
          const toneClass = {
            success: 'border-trust-200 text-trust-900 dark:border-trust-500/30 dark:text-trust-100',
            error: 'border-red-200 text-red-950 dark:border-red-500/30 dark:text-red-100',
            warning: 'border-amber-200 text-amber-950 dark:border-amber-500/30 dark:text-amber-100',
            info: 'border-blue-200 text-blue-950 dark:border-blue-500/30 dark:text-blue-100',
          }[toast.tone];
          return (
            <article
              key={toast.id}
              role={toast.tone === 'error' ? 'alert' : 'status'}
              aria-atomic="true"
              className={`pointer-events-auto flex w-full items-start gap-3 rounded-xl border bg-white p-4 text-left shadow-panel dark:bg-ink-850 ${toneClass}`}
            >
              <Icon className="mt-0.5 h-5 w-5 shrink-0" aria-hidden="true" />
              <div className="min-w-0 flex-1 text-sm leading-5">
                {toast.title && <p className="mb-0.5 font-semibold">{toast.title}</p>}
                {toast.message}
              </div>
              <button
                type="button"
                onClick={() => dismiss(toast.id)}
                className="-mr-1 -mt-1 inline-flex h-9 w-9 shrink-0 items-center justify-center rounded-lg hover:bg-black/5 focus-visible:ring-offset-white dark:hover:bg-white/10 dark:focus-visible:ring-offset-ink-850"
                aria-label={`Dismiss ${toast.title || `${toast.tone} notification`}`}
              >
                <X className="h-4 w-4" aria-hidden="true" />
              </button>
            </article>
          );
        })}
      </div>
    </ToastContext.Provider>
  );
}

export function useToast() {
  const context = useContext(ToastContext);
  if (!context) {
    throw new Error('useToast must be used inside ToastProvider.');
  }
  return context;
}
