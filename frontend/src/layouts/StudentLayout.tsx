import clsx from 'clsx';
import { BarChart3, BookOpenCheck, ClipboardList, Compass, Home, Menu, Trophy, User, X } from 'lucide-react';
import { useEffect, useState } from 'react';
import { Link, NavLink, Outlet, useLocation } from 'react-router-dom';
import { isAdmin, useAuth } from '@/auth/AuthProvider';
import { Logo } from '@/components/brand/Logo';
import { ThemeToggle } from '@/components/ThemeToggle';
import { UserMenu } from '@/components/UserMenu';
import { VerifyEmailBanner } from '@/components/VerifyEmailBanner';

export const studentNav = [
  { to: '/dashboard', label: 'Home', Icon: Home },
  { to: '/exams', label: 'Explore Exams', Icon: Compass },
  { to: '/my-tests', label: 'My Tests', Icon: ClipboardList },
  { to: '/results', label: 'Results', Icon: BarChart3 },
  { to: '/leaderboard', label: 'Leaderboard', Icon: Trophy },
  { to: '/profile', label: 'Profile', Icon: User },
];

const linkClass = ({ isActive }: { isActive: boolean }) =>
  clsx(
    'flex items-center gap-2 rounded-lg px-3 py-2 text-sm font-semibold transition-colors',
    isActive
      ? 'bg-brand-50 text-brand-700 dark:bg-brand-950/50 dark:text-brand-300'
      : 'text-slate-600 hover:bg-slate-100 hover:text-slate-900 dark:text-slate-300 dark:hover:bg-gray-800 dark:hover:text-white',
  );

export function StudentLayout() {
  const { user } = useAuth();
  const [menuOpen, setMenuOpen] = useState(false);
  const location = useLocation();
  useEffect(() => setMenuOpen(false), [location.pathname]);

  return (
    <div className="flex min-h-screen flex-col">
      <a href="#main" className="sr-only focus:not-sr-only focus:absolute focus:left-4 focus:top-4 focus:z-[60] focus:rounded-lg focus:bg-white focus:px-3 focus:py-2">
        Skip to content
      </a>
      <header className="sticky top-0 z-40 border-b border-slate-200 bg-white/90 backdrop-blur dark:border-gray-800 dark:bg-gray-900/90 print:hidden">
        <div className="mx-auto flex h-16 max-w-7xl items-center gap-3 px-4 sm:px-6">
          <button
            type="button"
            className="rounded-lg p-2 text-slate-600 hover:bg-slate-100 dark:text-slate-300 dark:hover:bg-gray-800 lg:hidden"
            onClick={() => setMenuOpen((o) => !o)}
            aria-label={menuOpen ? 'Close menu' : 'Open menu'}
            aria-expanded={menuOpen}
          >
            {menuOpen ? <X className="h-5 w-5" /> : <Menu className="h-5 w-5" />}
          </button>
          <Link to="/dashboard" aria-label="ExamForge home">
            <Logo size="sm" />
          </Link>
          <nav aria-label="Main" className="ml-6 hidden items-center gap-1 lg:flex">
            {studentNav.map(({ to, label }) => (
              <NavLink key={to} to={to} className={linkClass}>
                {label}
              </NavLink>
            ))}
          </nav>
          <div className="ml-auto flex items-center gap-2">
            {isAdmin(user) && (
              <Link to="/admin" className="hidden items-center gap-1.5 rounded-lg px-3 py-2 text-sm font-semibold text-brand-700 hover:bg-brand-50 dark:text-brand-300 dark:hover:bg-brand-950/40 sm:flex">
                <BookOpenCheck className="h-4 w-4" /> Admin
              </Link>
            )}
            <ThemeToggle className="hidden sm:inline-flex" />
            <UserMenu profilePath="/profile" />
          </div>
        </div>
        {menuOpen && (
          <nav aria-label="Main" className="border-t border-slate-200 px-4 py-3 dark:border-gray-800 lg:hidden">
            <div className="grid gap-1">
              {studentNav.map(({ to, label, Icon }) => (
                <NavLink key={to} to={to} className={linkClass}>
                  <Icon className="h-4 w-4" /> {label}
                </NavLink>
              ))}
              {isAdmin(user) && (
                <NavLink to="/admin" className={linkClass}>
                  <BookOpenCheck className="h-4 w-4" /> Admin panel
                </NavLink>
              )}
            </div>
            <ThemeToggle className="mt-3 sm:hidden" />
          </nav>
        )}
      </header>
      <VerifyEmailBanner />
      <main id="main" className="mx-auto w-full max-w-7xl flex-1 px-4 py-6 sm:px-6 sm:py-8">
        <Outlet />
      </main>
    </div>
  );
}
