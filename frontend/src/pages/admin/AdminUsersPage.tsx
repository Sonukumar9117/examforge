import { keepPreviousData, useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { ChevronLeft, ChevronRight, Search } from 'lucide-react';
import { useEffect, useState } from 'react';
import { errorMessage } from '@/api/client';
import { adminApi } from '@/api/endpoints';
import type { AdminUser, RoleName } from '@/api/types';
import { useAuth } from '@/auth/AuthProvider';
import { PageHeader } from '@/components/PageHeader';
import { Button } from '@/components/ui/Button';
import { Alert, Badge, Spinner } from '@/components/ui/Feedback';

type PendingAction = { user: AdminUser; kind: 'toggle-admin' | 'toggle-enabled' };

export default function AdminUsersPage() {
  const { user: me } = useAuth();
  const queryClient = useQueryClient();
  const [search, setSearch] = useState('');
  const [q, setQ] = useState('');
  const [role, setRole] = useState('');
  const [page, setPage] = useState(0);
  const [pending, setPending] = useState<PendingAction | null>(null);
  const [actionError, setActionError] = useState<string | null>(null);

  useEffect(() => {
    const t = setTimeout(() => {
      setQ(search.trim());
      setPage(0);
    }, 300);
    return () => clearTimeout(t);
  }, [search]);

  const users = useQuery({
    queryKey: ['admin', 'users', q, role, page],
    queryFn: () => adminApi.users({ q, role, page, size: 20 }),
    placeholderData: keepPreviousData,
  });

  const mutation = useMutation({
    mutationFn: async ({ user, kind }: PendingAction) => {
      if (kind === 'toggle-enabled') return adminApi.updateStatus(user.id, !user.enabled);
      const isAdminNow = user.roles.includes('ROLE_ADMIN');
      const roles: RoleName[] = isAdminNow ? ['ROLE_STUDENT'] : ['ROLE_STUDENT', 'ROLE_ADMIN'];
      return adminApi.updateRoles(user.id, roles);
    },
    onSuccess: () => {
      setPending(null);
      queryClient.invalidateQueries({ queryKey: ['admin'] });
    },
    onError: (e) => setActionError(errorMessage(e)),
  });

  const data = users.data;

  return (
    <div>
      <PageHeader title="Users" description="Search accounts, grant or remove admin access, and disable accounts. Every change is audit-logged." />

      <div className="mb-4 flex flex-col gap-3 sm:flex-row">
        <label className="relative flex-1">
          <span className="sr-only">Search users</span>
          <Search className="pointer-events-none absolute left-3 top-1/2 h-4 w-4 -translate-y-1/2 text-slate-400" />
          <input
            value={search}
            onChange={(e) => setSearch(e.target.value)}
            placeholder="Search by name, username or email"
            className="h-10 w-full rounded-xl border border-slate-300 bg-white pl-9 pr-3 text-sm focus:border-brand-500 focus:outline-none focus:ring-2 focus:ring-brand-500/30 dark:border-gray-600 dark:bg-gray-800"
          />
        </label>
        <select
          aria-label="Filter by role"
          value={role}
          onChange={(e) => {
            setRole(e.target.value);
            setPage(0);
          }}
          className="h-10 rounded-xl border border-slate-300 bg-white px-3 text-sm dark:border-gray-600 dark:bg-gray-800"
        >
          <option value="">All roles</option>
          <option value="ROLE_STUDENT">Students</option>
          <option value="ROLE_ADMIN">Admins</option>
        </select>
      </div>

      {users.isError && <Alert tone="error">{errorMessage(users.error)}</Alert>}

      {pending && (
        <div className="card mb-4 border-brand-200 p-4 dark:border-brand-900">
          <p className="text-sm">
            {pending.kind === 'toggle-admin'
              ? pending.user.roles.includes('ROLE_ADMIN')
                ? <>Remove admin access from <strong>@{pending.user.username}</strong>?</>
                : <>Give <strong>@{pending.user.username}</strong> full admin access?</>
              : pending.user.enabled
                ? <>Disable <strong>@{pending.user.username}</strong>? They will be signed out and unable to sign in.</>
                : <>Re-enable <strong>@{pending.user.username}</strong>?</>}
          </p>
          {actionError && <Alert tone="error" className="mt-3">{actionError}</Alert>}
          <div className="mt-3 flex gap-2">
            <Button size="sm" variant={pending.kind === 'toggle-enabled' && pending.user.enabled ? 'danger' : 'primary'} loading={mutation.isPending} onClick={() => mutation.mutate(pending)}>
              Confirm
            </Button>
            <Button size="sm" variant="secondary" onClick={() => { setPending(null); setActionError(null); }}>
              Cancel
            </Button>
          </div>
        </div>
      )}

      <div className="card overflow-hidden">
        <div className="overflow-x-auto">
          <table className="w-full min-w-[720px] text-left text-sm">
            <thead className="border-b border-slate-200 bg-slate-50 text-xs uppercase tracking-wider text-slate-500 dark:border-gray-700 dark:bg-gray-800 dark:text-slate-400">
              <tr>
                <th className="px-4 py-3 font-semibold">User</th>
                <th className="px-4 py-3 font-semibold">Roles</th>
                <th className="px-4 py-3 font-semibold">Status</th>
                <th className="px-4 py-3 font-semibold">Joined</th>
                <th className="px-4 py-3 text-right font-semibold">Actions</th>
              </tr>
            </thead>
            <tbody className="divide-y divide-slate-100 dark:divide-gray-700/70">
              {users.isPending && (
                <tr>
                  <td colSpan={5} className="px-4 py-8 text-center"><Spinner className="mx-auto" /></td>
                </tr>
              )}
              {data?.content.length === 0 && (
                <tr>
                  <td colSpan={5} className="px-4 py-8 text-center muted">No users match your search.</td>
                </tr>
              )}
              {data?.content.map((u) => {
                const self = u.id === me?.id;
                return (
                  <tr key={u.id} className="hover:bg-slate-50/70 dark:hover:bg-gray-800/40">
                    <td className="px-4 py-3">
                      <div className="font-semibold">{u.fullName}{self && <span className="ml-1.5 text-xs muted">(you)</span>}</div>
                      <div className="text-xs muted">@{u.username} · {u.email}</div>
                    </td>
                    <td className="px-4 py-3">
                      <div className="flex flex-wrap gap-1">
                        {u.roles.map((r) => (
                          <Badge key={r} tone={r === 'ROLE_ADMIN' ? 'brand' : 'neutral'}>{r.replace('ROLE_', '').toLowerCase()}</Badge>
                        ))}
                      </div>
                    </td>
                    <td className="px-4 py-3">
                      {u.enabled ? <Badge tone="success">Active</Badge> : <Badge tone="warning">Disabled</Badge>}
                      {!u.emailVerified && <span className="ml-1.5 text-xs muted">unverified</span>}
                    </td>
                    <td className="px-4 py-3 text-xs muted">{new Date(u.createdAt).toLocaleDateString()}</td>
                    <td className="px-4 py-3">
                      <div className="flex justify-end gap-2">
                        <Button size="sm" variant="secondary" disabled={self} onClick={() => { setActionError(null); setPending({ user: u, kind: 'toggle-admin' }); }}>
                          {u.roles.includes('ROLE_ADMIN') ? 'Remove admin' : 'Make admin'}
                        </Button>
                        <Button size="sm" variant="ghost" disabled={self} onClick={() => { setActionError(null); setPending({ user: u, kind: 'toggle-enabled' }); }}>
                          {u.enabled ? 'Disable' : 'Enable'}
                        </Button>
                      </div>
                    </td>
                  </tr>
                );
              })}
            </tbody>
          </table>
        </div>
        {data && data.totalPages > 1 && (
          <div className="flex items-center justify-between border-t border-slate-200 px-4 py-3 text-sm dark:border-gray-700">
            <span className="muted">
              Page {data.page + 1} of {data.totalPages} · {data.totalElements} users
            </span>
            <div className="flex gap-2">
              <Button size="sm" variant="secondary" disabled={page === 0} onClick={() => setPage((p) => p - 1)} aria-label="Previous page">
                <ChevronLeft className="h-4 w-4" />
              </Button>
              <Button size="sm" variant="secondary" disabled={page + 1 >= data.totalPages} onClick={() => setPage((p) => p + 1)} aria-label="Next page">
                <ChevronRight className="h-4 w-4" />
              </Button>
            </div>
          </div>
        )}
      </div>
    </div>
  );
}
