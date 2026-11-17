import { X } from 'lucide-react';
import { useEffect, useRef } from 'react';

export function Modal({ open, onClose, title, children, footer, labelledBy = 'dialog-title' }) {
  const dialogRef = useRef(null);
  const lastActiveElement = useRef(null);
  const onCloseRef = useRef(onClose);

  useEffect(() => {
    onCloseRef.current = onClose;
  }, [onClose]);

  useEffect(() => {
    if (!open) return undefined;
    lastActiveElement.current = document.activeElement;
    const previousOverflow = document.body.style.overflow;
    document.body.style.overflow = 'hidden';
    const focusableSelector = 'button:not([disabled]), [href], input:not([disabled]), select:not([disabled]), textarea:not([disabled]), [tabindex]:not([tabindex="-1"])';
    const focusDialog = () => {
      const firstFocusable = dialogRef.current?.querySelector(focusableSelector);
      (firstFocusable ?? dialogRef.current)?.focus();
    };
    const animationFrame = window.requestAnimationFrame(focusDialog);
    const onKeyDown = (event) => {
      if (event.key === 'Escape') onCloseRef.current?.();
      if (event.key !== 'Tab' || !dialogRef.current) return;
      const focusable = [...dialogRef.current.querySelectorAll(focusableSelector)];
      if (!focusable.length) {
        event.preventDefault();
        dialogRef.current.focus();
        return;
      }
      const first = focusable[0];
      const last = focusable[focusable.length - 1];
      if (event.shiftKey && document.activeElement === first) {
        event.preventDefault();
        last.focus();
      } else if (!event.shiftKey && document.activeElement === last) {
        event.preventDefault();
        first.focus();
      }
    };
    window.addEventListener('keydown', onKeyDown);
    return () => {
      window.cancelAnimationFrame(animationFrame);
      window.removeEventListener('keydown', onKeyDown);
      document.body.style.overflow = previousOverflow;
      lastActiveElement.current?.focus?.();
    };
  }, [open]);

  if (!open) return null;
  return (
    <div className="fixed inset-0 z-50 flex items-end justify-center bg-ink-950/55 p-4 sm:items-center" role="presentation" onPointerDown={onClose}>
      <section
        role="dialog"
        ref={dialogRef}
        tabIndex="-1"
        aria-modal="true"
        aria-labelledby={labelledBy}
        className="w-full max-w-lg rounded-2xl bg-white shadow-2xl dark:bg-ink-900"
        onPointerDown={(event) => event.stopPropagation()}
      >
        <header className="flex items-center justify-between border-b px-5 py-4 dark:border-slate-800">
          <h2 id={labelledBy} className="text-lg font-bold text-ink-950 dark:text-white">{title}</h2>
          <button type="button" onClick={onClose} className="inline-flex h-11 w-11 items-center justify-center rounded-lg text-slate-500 hover:bg-slate-100 hover:text-slate-900 dark:hover:bg-ink-800 dark:hover:text-white" aria-label="Close dialog">
            <X className="h-5 w-5" aria-hidden="true" />
          </button>
        </header>
        <div className="p-5">{children}</div>
        {footer && <footer className="flex flex-wrap justify-end gap-2 border-t px-5 py-4 dark:border-slate-800">{footer}</footer>}
      </section>
    </div>
  );
}
