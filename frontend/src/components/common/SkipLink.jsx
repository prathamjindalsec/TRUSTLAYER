export function SkipLink({ targetId = 'main-content' }) {
  return (
    <a
      href={`#${targetId}`}
      className="fixed left-4 top-4 z-[200] -translate-y-24 rounded-lg bg-ink-950 px-4 py-2.5 text-sm font-bold text-white shadow-lg transition-transform focus:translate-y-0 dark:bg-white dark:text-ink-950"
    >
      Skip to main content
    </a>
  );
}
