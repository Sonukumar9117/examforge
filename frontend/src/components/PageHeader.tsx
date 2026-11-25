import type { ReactNode } from 'react';

export function PageHeader({ title, description, actions }: { title: string; description?: ReactNode; actions?: ReactNode }) {
  return (
    <div className="mb-6 flex flex-col gap-3 sm:flex-row sm:items-end sm:justify-between">
      <div>
        <h1 className="text-2xl font-extrabold sm:text-3xl">{title}</h1>
        {description && <p className="mt-1 text-sm muted">{description}</p>}
      </div>
      {actions && <div className="flex flex-wrap gap-2">{actions}</div>}
    </div>
  );
}

export function StatCard({ label, value, hint, icon }: { label: string; value: ReactNode; hint?: ReactNode; icon: ReactNode }) {
  return (
    <div className="card p-5">
      <div className="flex items-center justify-between">
        <span className="text-xs font-semibold uppercase tracking-wider muted">{label}</span>
        <span className="text-brand-600 dark:text-brand-400">{icon}</span>
      </div>
      <div className="mt-2 font-display text-3xl font-extrabold">{value}</div>
      {hint && <div className="mt-1 text-xs muted">{hint}</div>}
    </div>
  );
}
