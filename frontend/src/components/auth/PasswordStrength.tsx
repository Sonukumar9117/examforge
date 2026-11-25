import clsx from 'clsx';

/** 0–4 score: length, mixed case, digits, symbols. Mirrors (and is stricter than) the server rule. */
export function passwordScore(pw: string): number {
  if (!pw) return 0;
  let score = 0;
  if (pw.length >= 8) score++;
  if (pw.length >= 12) score++;
  if (/[a-z]/.test(pw) && /[A-Z]/.test(pw)) score++;
  if (/\d/.test(pw) && /[^A-Za-z0-9]/.test(pw)) score++;
  if (pw.length < 8) score = Math.min(score, 1);
  return Math.min(score, 4);
}

const labels = ['Too short', 'Weak', 'Fair', 'Good', 'Strong'];
const colors = ['bg-slate-300 dark:bg-gray-600', 'bg-red-500', 'bg-amber-500', 'bg-lime-500', 'bg-emerald-500'];

export function PasswordStrength({ password }: { password: string }) {
  const score = passwordScore(password);
  if (!password) return null;
  return (
    <div className="mt-2" aria-live="polite">
      <div className="flex gap-1" aria-hidden="true">
        {[1, 2, 3, 4].map((i) => (
          <div key={i} className={clsx('h-1.5 flex-1 rounded-full transition-colors', i <= score ? colors[score] : 'bg-slate-200 dark:bg-gray-700')} />
        ))}
      </div>
      <p className="mt-1 text-xs muted">
        Password strength: <span className="font-semibold text-slate-700 dark:text-slate-200">{labels[score]}</span>
      </p>
    </div>
  );
}
