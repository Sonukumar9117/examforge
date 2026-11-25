import { BarChart3, Clock3, ShieldCheck, Users } from 'lucide-react';
import type { ReactNode } from 'react';
import { Link } from 'react-router-dom';
import { Logo, LogoMark } from '@/components/brand/Logo';
import { ThemeToggle } from '@/components/ThemeToggle';

const features = [
  { Icon: Clock3, title: 'Real CBT experience', text: 'Server-timed tests, question palette, mark for review.' },
  { Icon: ShieldCheck, title: 'Fair & secure', text: 'Answers are scored on the server with exam-accurate negative marking.' },
  { Icon: Users, title: 'Live exam rooms', text: 'Take scheduled tests together and see where you stand.' },
  { Icon: BarChart3, title: 'Know your weak areas', text: 'Subject-wise analytics and focused practice suggestions.' },
];

export function AuthLayout({ title, subtitle, children }: { title: string; subtitle?: ReactNode; children: ReactNode }) {
  return (
    <div className="flex min-h-screen">
      {/* Brand panel (desktop) */}
      <aside className="relative hidden w-[44%] max-w-xl overflow-hidden bg-ink p-12 text-white lg:flex lg:flex-col">
        <div className="pointer-events-none absolute -right-24 -top-24 h-96 w-96 rounded-full bg-brand-600/25 blur-3xl" />
        <div className="pointer-events-none absolute -bottom-32 -left-20 h-96 w-96 rounded-full bg-crimson/30 blur-3xl" />
        <Link to="/" className="relative inline-flex items-center gap-2.5">
          <LogoMark className="h-10 w-10" />
          <span className="font-display text-2xl font-extrabold tracking-tight">
            Exam<span className="text-brand-500">Forge</span>
          </span>
        </Link>
        <div className="relative mt-auto">
          <h2 className="font-display text-4xl font-extrabold leading-tight">
            Practice like it&apos;s <span className="text-brand-500">exam day.</span>
          </h2>
          <p className="mt-3 max-w-md text-slate-300">
            Mock tests for UPSC, SSC, Banking, Railways, State PCS, GATE, JEE &amp; NEET — in a realistic computer-based test.
          </p>
          <ul className="mt-10 grid gap-5">
            {features.map(({ Icon, title, text }) => (
              <li key={title} className="flex gap-3.5">
                <span className="flex h-10 w-10 shrink-0 items-center justify-center rounded-xl bg-white/10 text-brand-400">
                  <Icon className="h-5 w-5" />
                </span>
                <span>
                  <span className="block font-semibold">{title}</span>
                  <span className="text-sm text-slate-400">{text}</span>
                </span>
              </li>
            ))}
          </ul>
        </div>
      </aside>

      {/* Form side */}
      <main className="flex flex-1 flex-col">
        <div className="flex items-center justify-between px-4 py-4 sm:px-8">
          <Link to="/" className="lg:invisible">
            <Logo size="sm" />
          </Link>
          <ThemeToggle />
        </div>
        <div className="flex flex-1 items-start justify-center px-4 pb-12 pt-4 sm:items-center sm:px-8">
          <div className="w-full max-w-md">
            <h1 className="text-2xl font-extrabold sm:text-3xl">{title}</h1>
            {subtitle && <p className="mt-1.5 text-sm muted">{subtitle}</p>}
            <div className="mt-7">{children}</div>
          </div>
        </div>
      </main>
    </div>
  );
}

export function OrDivider() {
  return (
    <div className="my-6 flex items-center gap-3 text-xs font-medium uppercase tracking-wider text-slate-400">
      <span className="h-px flex-1 bg-slate-200 dark:bg-gray-700" />
      or
      <span className="h-px flex-1 bg-slate-200 dark:bg-gray-700" />
    </div>
  );
}
