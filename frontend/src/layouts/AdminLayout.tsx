import clsx from 'clsx';
import {
  BarChart3,
  BookCopy,
  FileQuestion,
  FolderTree,
  GraduationCap,
  LayoutDashboard,
  Menu,
  Radio,
  ScrollText,
  Users,
  X,
} from 'lucide-react';
import { useEffect, useState } from 'react';
import { Link, NavLink, Outlet, useLocation } from 'react-router-dom';
import { isStudent, useAuth } from '@/auth/AuthProvider';
import { Logo } from '@/components/brand/Logo';
import { ThemeToggle } from '@/components/ThemeToggle';
import { UserMenu } from '@/components/UserMenu';

const nav = [
  { to: '/admin', label: 'Dashboard', Icon: LayoutDashboard, end: true },
  { to: '/admin/exams', label: 'Exams', Icon: BookCopy },
  { to: '/admin/questions', label: 'Question bank', Icon: FileQuestion },
  { to: '/admin/categories', label: 'Categories & subjects', Icon: FolderTree },
  { to: '/admin/live-tests', label: 'Live tests', Icon: Radio },
  { to: '/admin/reports', label: 'Reports', Icon: BarChart3 },
  { to: '/admin/users', label: 'Users', Icon: Users },
  { to: '/admin/audit-logs', label: 'Audit logs', Icon: ScrollText },
];

const linkClass = ({ isActive }: { isActive: boolean }) =>
  clsx(
    'flex items-center gap-3 rounded-xl px-3 py-2.5 text-sm font-semibold transition-colors',
    isActive
      ? 'bg-brand-600 text-white shadow-sm'
      : 'text-slate-600 hover:bg-slate-100 hover:text-slate-900 dark:text-slate-300 dark:hover:bg-gray-800 dark:hover:text-white',
  );

function Sidebar() {
  const { user } = useAuth();
  return (
    <div className="flex h-full flex-col gap-6 p-4">
      <Link to="/admin" className="px-2 pt-1">
        <Logo size="sm" />
        <span className="ml-[2.375rem] block text-[11px] font-bold uppercase tracking-widest text-brand-600 dark:text-brand-400">
          Admin console
        </span>
      </Link>
      <nav aria-label="Admin" className="grid gap-1">
        {nav.map(({ to, label, Icon, end }) => (
          <NavLink key={to} to={to} end={end} className={linkClass}>
            <Icon className="h-4 w-4" /> {label}
          </NavLink>
        ))}
      </nav>
      {isStudent(user) && (
        <Link
          to="/dashboard"
          className="mt-auto flex items-center gap-3 rounded-xl border border-slate-200 px-3 py-2.5 text-sm font-semibold text-slate-600 hover:bg-slate-50 dark:border-gray-700 dark:text-slate-300 dark:hover:bg-gray-800"
        >
          <GraduationCap className="h-4 w-4" /> Student view
        </Link>
      )}
    </div>
  );
}

export function AdminLayout() {
  const [open, setOpen] = useState(false);
  const location = useLocation();
  useEffect(() => setOpen(false), [location.pathname]);

  return (
    <div className="min-h-screen lg:pl-72">
      <aside className="fixed inset-y-0 left-0 z-40 hidden w-72 border-r border-slate-200 bg-white dark:border-gray-800 dark:bg-gray-900 lg:block">
        <Sidebar />
      </aside>

      {open && (
        <div className="fixed inset-0 z-50 lg:hidden" role="dialog" aria-modal="true" aria-label="Admin navigation">
          <div className="absolute inset-0 bg-black/40" onClick={() => setOpen(false)} />
          <aside className="absolute inset-y-0 left-0 w-72 max-w-[85%] bg-white shadow-xl dark:bg-gray-900">
            <button type="button" onClick={() => setOpen(false)} className="absolute right-3 top-3 rounded-lg p-2 hover:bg-slate-100 dark:hover:bg-gray-800" aria-label="Close menu">
              <X className="h-5 w-5" />
            </button>
            <Sidebar />
          </aside>
        </div>
      )}

      <header className="sticky top-0 z-30 flex h-16 items-center gap-3 border-b border-slate-200 bg-white/90 px-4 backdrop-blur dark:border-gray-800 dark:bg-gray-900/90 sm:px-6">
        <button type="button" className="rounded-lg p-2 hover:bg-slate-100 dark:hover:bg-gray-800 lg:hidden" onClick={() => setOpen(true)} aria-label="Open menu">
          <Menu className="h-5 w-5" />
        </button>
        <span className="lg:hidden">
          <Logo size="sm" />
        </span>
        <div className="ml-auto flex items-center gap-2">
          <ThemeToggle />
          <UserMenu profilePath="/admin/account" />
        </div>
      </header>

      <main id="main" className="mx-auto w-full max-w-7xl px-4 py-6 sm:px-6 sm:py-8">
        <Outlet />
      </main>
    </div>
  );
}
