import clsx from 'clsx';
import { Eye, EyeOff } from 'lucide-react';
import {
  forwardRef,
  useId,
  useState,
  type InputHTMLAttributes,
  type ReactNode,
  type SelectHTMLAttributes,
  type TextareaHTMLAttributes,
} from 'react';

interface FieldProps extends InputHTMLAttributes<HTMLInputElement> {
  label: string;
  error?: string;
  hint?: ReactNode;
  /** Element shown inside the input on the right (e.g. a status icon). */
  trailing?: ReactNode;
}

const inputBase =
  'block w-full rounded-xl border bg-white px-3.5 py-2.5 text-sm text-slate-900 placeholder:text-slate-400 transition ' +
  'focus:outline-none focus:ring-2 focus:ring-brand-500/30 focus:border-brand-500 ' +
  'dark:bg-gray-900/60 dark:text-slate-100 dark:placeholder:text-slate-500';

export const TextField = forwardRef<HTMLInputElement, FieldProps>(function TextField(
  { label, error, hint, trailing, className, id, ...rest },
  ref,
) {
  const autoId = useId();
  const inputId = id ?? autoId;
  const describedBy = error ? `${inputId}-error` : hint ? `${inputId}-hint` : undefined;
  return (
    <div className={className}>
      <label htmlFor={inputId} className="mb-1.5 block text-sm font-medium text-slate-700 dark:text-slate-300">
        {label}
      </label>
      <div className="relative">
        <input
          ref={ref}
          id={inputId}
          aria-invalid={!!error || undefined}
          aria-describedby={describedBy}
          className={clsx(
            inputBase,
            trailing && 'pr-11',
            error ? 'border-red-500 dark:border-red-500' : 'border-slate-300 dark:border-gray-600',
          )}
          {...rest}
        />
        {trailing && <div className="absolute inset-y-0 right-0 flex items-center pr-3">{trailing}</div>}
      </div>
      {error ? (
        <p id={`${inputId}-error`} role="alert" className="mt-1.5 text-xs font-medium text-red-600 dark:text-red-400">
          {error}
        </p>
      ) : hint ? (
        <div id={`${inputId}-hint`} className="mt-1.5 text-xs muted">
          {hint}
        </div>
      ) : null}
    </div>
  );
});

export const PasswordField = forwardRef<HTMLInputElement, Omit<FieldProps, 'type' | 'trailing'>>(function PasswordField(
  props,
  ref,
) {
  const [visible, setVisible] = useState(false);
  return (
    <TextField
      ref={ref}
      type={visible ? 'text' : 'password'}
      trailing={
        <button
          type="button"
          onClick={() => setVisible((v) => !v)}
          className="rounded-md p-1 text-slate-400 hover:text-slate-600 dark:hover:text-slate-200"
          aria-label={visible ? 'Hide password' : 'Show password'}
          aria-pressed={visible}
        >
          {visible ? <EyeOff className="h-4 w-4" /> : <Eye className="h-4 w-4" />}
        </button>
      }
      {...props}
    />
  );
});

interface CheckboxProps extends Omit<InputHTMLAttributes<HTMLInputElement>, 'type'> {
  label: ReactNode;
  error?: string;
}

export const Checkbox = forwardRef<HTMLInputElement, CheckboxProps>(function Checkbox(
  { label, error, id, className, ...rest },
  ref,
) {
  const autoId = useId();
  const inputId = id ?? autoId;
  return (
    <div className={className}>
      <label htmlFor={inputId} className="flex cursor-pointer items-start gap-2.5 text-sm text-slate-700 dark:text-slate-300">
        <input
          ref={ref}
          id={inputId}
          type="checkbox"
          aria-invalid={!!error || undefined}
          className="mt-0.5 h-4 w-4 rounded border-slate-300 accent-brand-600 dark:border-gray-600"
          {...rest}
        />
        <span>{label}</span>
      </label>
      {error && (
        <p role="alert" className="mt-1 text-xs font-medium text-red-600 dark:text-red-400">
          {error}
        </p>
      )}
    </div>
  );
});

function FieldShell({ id, label, error, hint, className, children }: { id: string; label: string; error?: string; hint?: ReactNode; className?: string; children: ReactNode }) {
  return (
    <div className={className}>
      <label htmlFor={id} className="mb-1.5 block text-sm font-medium text-slate-700 dark:text-slate-300">
        {label}
      </label>
      {children}
      {error ? (
        <p id={`${id}-error`} role="alert" className="mt-1.5 text-xs font-medium text-red-600 dark:text-red-400">
          {error}
        </p>
      ) : hint ? (
        <div id={`${id}-hint`} className="mt-1.5 text-xs muted">
          {hint}
        </div>
      ) : null}
    </div>
  );
}

interface SelectFieldProps extends SelectHTMLAttributes<HTMLSelectElement> {
  label: string;
  error?: string;
  hint?: ReactNode;
}

export const SelectField = forwardRef<HTMLSelectElement, SelectFieldProps>(function SelectField(
  { label, error, hint, className, id, children, ...rest },
  ref,
) {
  const autoId = useId();
  const selectId = id ?? autoId;
  return (
    <FieldShell id={selectId} label={label} error={error} hint={hint} className={className}>
      <select
        ref={ref}
        id={selectId}
        aria-invalid={!!error || undefined}
        className={clsx(inputBase, 'pr-8', error ? 'border-red-500' : 'border-slate-300 dark:border-gray-600')}
        {...rest}
      >
        {children}
      </select>
    </FieldShell>
  );
});

interface TextAreaFieldProps extends TextareaHTMLAttributes<HTMLTextAreaElement> {
  label: string;
  error?: string;
  hint?: ReactNode;
}

export const TextAreaField = forwardRef<HTMLTextAreaElement, TextAreaFieldProps>(function TextAreaField(
  { label, error, hint, className, id, rows = 3, ...rest },
  ref,
) {
  const autoId = useId();
  const areaId = id ?? autoId;
  return (
    <FieldShell id={areaId} label={label} error={error} hint={hint} className={className}>
      <textarea
        ref={ref}
        id={areaId}
        rows={rows}
        aria-invalid={!!error || undefined}
        className={clsx(inputBase, error ? 'border-red-500' : 'border-slate-300 dark:border-gray-600')}
        {...rest}
      />
    </FieldShell>
  );
});
