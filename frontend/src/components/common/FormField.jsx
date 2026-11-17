import { AlertCircle, Eye, EyeOff } from 'lucide-react';
import { useId, useState } from 'react';

export function TextField({ label, hint, error, type = 'text', className = '', ...props }) {
  const generatedId = useId();
  const id = props.id || generatedId;
  const isPassword = type === 'password';
  const [showPassword, setShowPassword] = useState(false);
  const describedBy = [hint && `${id}-hint`, error && `${id}-error`].filter(Boolean).join(' ') || undefined;

  return (
    <div className={className}>
      {label && <label className="field-label" htmlFor={id}>{label}</label>}
      <div className="relative">
        <input
          id={id}
          type={isPassword && showPassword ? 'text' : type}
          aria-invalid={Boolean(error)}
          aria-describedby={describedBy}
          className={`field-input ${isPassword ? 'pr-12' : ''}`}
          {...props}
        />
        {isPassword && (
          <button
            type="button"
            onClick={() => setShowPassword((visible) => !visible)}
            className="absolute inset-y-0 right-0 flex w-11 items-center justify-center rounded-r-xl text-slate-500 hover:text-slate-700 dark:text-slate-400 dark:hover:text-white"
            aria-label={showPassword ? 'Hide password' : 'Show password'}
          >
            {showPassword ? <EyeOff className="h-4 w-4" aria-hidden="true" /> : <Eye className="h-4 w-4" aria-hidden="true" />}
          </button>
        )}
      </div>
      {error ? (
        <p id={`${id}-error`} role="alert" className="mt-1.5 flex items-start gap-1.5 text-xs leading-5 text-alert-red">
          <AlertCircle className="mt-0.5 h-3.5 w-3.5 shrink-0" aria-hidden="true" />
          {error}
        </p>
      ) : hint ? <p id={`${id}-hint`} className="field-hint">{hint}</p> : null}
    </div>
  );
}

export function SelectField({ label, hint, error, children, className = '', ...props }) {
  const generatedId = useId();
  const id = props.id || generatedId;
  const describedBy = [hint && `${id}-hint`, error && `${id}-error`].filter(Boolean).join(' ') || undefined;
  return (
    <div className={className}>
      {label && <label className="field-label" htmlFor={id}>{label}</label>}
      <select id={id} aria-invalid={Boolean(error)} aria-describedby={describedBy} className="field-input" {...props}>{children}</select>
      {error ? <p id={`${id}-error`} role="alert" className="mt-1.5 text-xs text-alert-red">{error}</p> : hint && <p id={`${id}-hint`} className="field-hint">{hint}</p>}
    </div>
  );
}
