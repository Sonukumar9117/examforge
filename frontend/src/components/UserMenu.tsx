import { LogOut, Settings } from 'lucide-react';
import { useEffect, useRef, useState } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import { useAuth } from '@/auth/AuthProvider';

export function Avatar({ name, url, size = 36 }: { name: string; url?: string | null; size?: number }) {
  const initials = name
    .split(/\s+/)
    .filter(Boolean)
    .slice(0, 2)
    .map((p) => p[0]?.toUpperCase())
    .join('');
  return url ? (
    <img src={url} alt="" referrerPolicy="no-referrer" className="rounded-full object-cover" style={{ width: size, height: size }} />
  ) : (
    <span
      className="flex items-center justify-center rounded-full bg-brand-600 text-sm font-bold text-white"
      style={{ width: size, height: size }}
      aria-hidden="true"
    >
      {initials || '?'}
    </span>
  );
}

export function UserMenu({ profilePath }: { profilePath: string }) {
  const { user, logout } = useAuth();
  const navigate = useNavigate();
  const [open, setOpen] = useState(false);
  const ref = useRef<HTMLDivElement>(null);

  useEffect(() => {
    if (!open) return;
    const close = (e: MouseEvent | KeyboardEvent) => {
      if (e instanceof KeyboardEvent ? e.key === 'Escape' : !ref.current?.contains(e.target as Node)) setOpen(false);
    };
    document.addEventListener('mousedown', close);
    document.addEventListener('keydown', close);
    return () => {
      document.removeEventListener('mousedown', close);
      document.removeEventListener('keydown', close);
    };
  }, [open]);

  if (!user) return null;

  const signOut = async () => {
    setOpen(false);
    await logout();
    navigate('/login', { replace: true });
  };

  return (
    <div className="relative" ref={ref}>
      <button
        type="button"
        onClick={() => setOpen((o) => !o)}
        aria-haspopup="menu"
        aria-expanded={open}
        className="flex items-center gap-2 rounded-full p-0.5 pr-2 hover:bg-slate-100 dark:hover:bg-gray-800"
      >
        <Avatar name={user.fullName} url={user.avatarUrl} size={32} />
        <span className="hidden max-w-[10rem] truncate text-sm font-semibold sm:block">{user.username}</span>
      </button>
      {open && (
        <div role="menu" className="card absolute right-0 z-50 mt-2 w-60 overflow-hidden p-1.5">
          <div className="px-3 py-2">
            <p className="truncate text-sm font-semibold">{user.fullName}</p>
            <p className="truncate text-xs muted">{user.email}</p>
          </div>
          <div className="my-1 h-px bg-slate-100 dark:bg-gray-700" />
          <Link
            role="menuitem"
            to={profilePath}
            onClick={() => setOpen(false)}
            className="flex items-center gap-2.5 rounded-lg px-3 py-2 text-sm hover:bg-slate-100 dark:hover:bg-gray-700"
          >
            <Settings className="h-4 w-4" /> Account settings
          </Link>
          <button
            role="menuitem"
            type="button"
            onClick={signOut}
            className="flex w-full items-center gap-2.5 rounded-lg px-3 py-2 text-left text-sm text-red-600 hover:bg-red-50 dark:text-red-400 dark:hover:bg-red-950/40"
          >
            <LogOut className="h-4 w-4" /> Sign out
          </button>
        </div>
      )}
    </div>
  );
}
