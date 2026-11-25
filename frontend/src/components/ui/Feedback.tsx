import clsx from 'clsx';
import { AlertCircle, CheckCircle2, Info, Loader2 } from 'lucide-react';
import type { ReactNode } from 'react';
import { LogoMark } from '@/components/brand/Logo';

type Tone = 'error' | 'success' | 'info' | 'warning';

const tones: Record<Tone, string> = {
  error: 'border-red-200 bg-red-50 text-red-800 dark:border-red-900/60 dark:bg-red-950/40 dark:text-red-200',
  success:
    'border-emerald-200 bg-emerald-50 text-emerald-800 dark:border-emerald-900/60 dark:bg-emerald-950/40 dark:text-emerald-200',
  info: 'border-sky-200 bg-sky-50 text-sky-800 dark:border-sky-900/60 dark:bg-sky-950/40 dark:text-sky-200',
  warning: 'border-amber-200 bg-amber-50 text-amber-900 dark:border-amber-900/60 dark:bg-amber-950/40 dark:text-amber-200',
};

const icons: Record<Tone, ReactNode> = {
  error: <AlertCircle className="h-4 w-4 shrink-0" />,
  success: <CheckCircle2 className="h-4 w-4 shrink-0" />,
  info: <Info className="h-4 w-4 shrink-0" />,
  warning: <AlertCircle className="h-4 w-4 shrink-0" />,
};

export function Alert({ tone = 'info', children, className }: { tone?: Tone; children: ReactNode; className?: string }) {
  return (
    <div
      role={tone === 'error' ? 'alert' : 'status'}
      className={clsx('flex items-start gap-2.5 rounded-xl border px-3.5 py-3 text-sm', tones[tone], className)}
    >
      <span className="mt-0.5">{icons[tone]}</span>
      <div className="min-w-0 flex-1">{children}</div>
    </div>
  );
}

export function Spinner({ className }: { className?: string }) {
  return <Loader2 className={clsx('h-5 w-5 animate-spin text-brand-600', className)} aria-label="Loading" />;
}

export function FullScreenLoader() {
  return (
    <div className="flex min-h-screen flex-col items-center justify-center gap-4">
      <LogoMark className="h-12 w-12 animate-pulse" />
      <span className="sr-only">Loading</span>
    </div>
  );
}

export function Badge({ children, tone = 'neutral' }: { children: ReactNode; tone?: 'neutral' | 'brand' | 'success' | 'warning' }) {
  const styles = {
    neutral: 'bg-slate-100 text-slate-700 dark:bg-gray-700 dark:text-slate-200',
    brand: 'bg-brand-50 text-brand-700 dark:bg-brand-950/60 dark:text-brand-300',
    success: 'bg-emerald-50 text-emerald-700 dark:bg-emerald-950/50 dark:text-emerald-300',
    warning: 'bg-amber-50 text-amber-800 dark:bg-amber-950/50 dark:text-amber-300',
  }[tone];
  return <span className={clsx('inline-flex items-center rounded-full px-2.5 py-0.5 text-xs font-semibold', styles)}>{children}</span>;
}

export function EmptyState({ icon, title, children }: { icon: ReactNode; title: string; children?: ReactNode }) {
  return (
    <div className="card flex flex-col items-center px-6 py-14 text-center">
      <div className="mb-4 flex h-12 w-12 items-center justify-center rounded-2xl bg-brand-50 text-brand-600 dark:bg-brand-950/50 dark:text-brand-400">
        {icon}
      </div>
      <h3 className="text-lg font-bold">{title}</h3>
      {children && <div className="mt-1.5 max-w-md text-sm muted">{children}</div>}
    </div>
  );
}
