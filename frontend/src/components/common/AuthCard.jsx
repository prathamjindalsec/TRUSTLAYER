export function AuthCard({ title, description, children, footer }) {
  return (
    <section className="w-full max-w-md">
      <div className="surface overflow-hidden">
        <div className="border-b bg-slate-50 px-6 py-6 dark:border-slate-800 dark:bg-ink-850 sm:px-8">
          <h1 className="text-2xl font-bold tracking-tight text-ink-950 dark:text-white">{title}</h1>
          {description && <p className="mt-2 text-sm leading-6 text-slate-600 dark:text-slate-300">{description}</p>}
        </div>
        <div className="px-6 py-6 sm:px-8">{children}</div>
        {footer && <div className="border-t bg-slate-50 px-6 py-5 text-center text-sm text-slate-600 dark:border-slate-800 dark:bg-ink-850 dark:text-slate-300 sm:px-8">{footer}</div>}
      </div>
    </section>
  );
}
