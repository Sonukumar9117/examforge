import clsx from 'clsx';
import { useId } from 'react';

/** ExamForge mark: red shield with a white checkmark. */
export function LogoMark({ className }: { className?: string }) {
  const id = useId();
  return (
    <svg viewBox="0 0 64 64" className={clsx('h-9 w-9 shrink-0', className)} aria-hidden="true">
      <defs>
        <linearGradient id={id} x1="0" y1="0" x2="1" y2="1">
          <stop offset="0" stopColor="#f43f5e" />
          <stop offset="1" stopColor="#991b1b" />
        </linearGradient>
      </defs>
      <path d="M32 3 8 11.5v17.3C8 45 18.3 56.6 32 61c13.7-4.4 24-16 24-32.2V11.5L32 3Z" fill={`url(#${id})`} />
      <path d="m20.5 32.5 8 8 15-16" fill="none" stroke="#fff" strokeWidth="6" strokeLinecap="round" strokeLinejoin="round" />
    </svg>
  );
}

export function Logo({ className, size = 'md' }: { className?: string; size?: 'sm' | 'md' | 'lg' }) {
  const mark = { sm: 'h-7 w-7', md: 'h-9 w-9', lg: 'h-12 w-12' }[size];
  const text = { sm: 'text-lg', md: 'text-xl', lg: 'text-3xl' }[size];
  return (
    <span className={clsx('inline-flex items-center gap-2.5', className)}>
      <LogoMark className={mark} />
      <span className={clsx('font-display font-extrabold tracking-tight', text)}>
        <span className="text-slate-900 dark:text-white">Exam</span>
        <span className="text-brand-600 dark:text-brand-500">Forge</span>
      </span>
    </span>
  );
}
