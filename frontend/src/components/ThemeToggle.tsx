import clsx from 'clsx';
import { Monitor, Moon, Sun } from 'lucide-react';
import { meApi } from '@/api/endpoints';
import type { ThemePreference } from '@/api/types';
import { useAuth } from '@/auth/AuthProvider';
import { useTheme, type ThemeMode } from '@/theme/ThemeProvider';

const options: { mode: ThemeMode; label: string; Icon: typeof Sun }[] = [
  { mode: 'light', label: 'Light', Icon: Sun },
  { mode: 'dark', label: 'Dark', Icon: Moon },
  { mode: 'system', label: 'System', Icon: Monitor },
];

/** Light / dark / system switch. Saved locally and, when signed in, on the account (shared with mobile). */
export function ThemeToggle({ className }: { className?: string }) {
  const { mode, setMode } = useTheme();
  const { user, setUser } = useAuth();

  const choose = (m: ThemeMode) => {
    setMode(m);
    if (user) {
      meApi
        .preferences(m.toUpperCase() as ThemePreference)
        .then(setUser)
        .catch(() => {
          /* local preference still applies */
        });
    }
  };

  return (
    <div
      role="radiogroup"
      aria-label="Color theme"
      className={clsx('inline-flex rounded-xl border border-slate-200 bg-white p-0.5 dark:border-gray-700 dark:bg-gray-800', className)}
    >
      {options.map(({ mode: m, label, Icon }) => (
        <button
          key={m}
          type="button"
          role="radio"
          aria-checked={mode === m}
          title={label}
          onClick={() => choose(m)}
          className={clsx(
            'flex h-8 w-8 items-center justify-center rounded-lg transition-colors',
            mode === m
              ? 'bg-brand-50 text-brand-700 dark:bg-gray-700 dark:text-white'
              : 'text-slate-500 hover:text-slate-800 dark:text-slate-400 dark:hover:text-white',
          )}
        >
          <Icon className="h-4 w-4" />
          <span className="sr-only">{label}</span>
        </button>
      ))}
    </div>
  );
}
