import { keepPreviousData, useQuery } from '@tanstack/react-query';
import { ChevronLeft, ChevronRight } from 'lucide-react';
import { useEffect, useState } from 'react';
import { errorMessage } from '@/api/client';
import { adminApi } from '@/api/endpoints';
import { PageHeader } from '@/components/PageHeader';
import { Button } from '@/components/ui/Button';
import { Alert, Badge, Spinner } from '@/components/ui/Feedback';

export default function AuditLogsPage() {
  const [input, setInput] = useState('');
  const [action, setAction] = useState('');
  const [page, setPage] = useState(0);

  useEffect(() => {
    const t = setTimeout(() => {
      setAction(input.trim());
      setPage(0);
    }, 300);
    return () => clearTimeout(t);
  }, [input]);

  const logs = useQuery({
    queryKey: ['admin', 'audit', action, page],
    queryFn: () => adminApi.auditLogs({ action, page, size: 50 }),
    placeholderData: keepPreviousData,
  });

  return (
    <div>
      <PageHeader title="Audit logs" description="Security-relevant and administrative actions, newest first." />
      <input
        value={input}
        onChange={(e) => setInput(e.target.value)}
        placeholder="Filter by action, e.g. ROLES or LOCKED"
        aria-label="Filter by action"
        className="mb-4 h-10 w-full max-w-sm rounded-xl border border-slate-300 bg-white px-3 text-sm focus:border-brand-500 focus:outline-none focus:ring-2 focus:ring-brand-500/30 dark:border-gray-600 dark:bg-gray-800"
      />
      {logs.isError && <Alert tone="error">{errorMessage(logs.error)}</Alert>}
      <div className="card overflow-hidden">
        <div className="overflow-x-auto">
          <table className="w-full min-w-[720px] text-left text-sm">
            <thead className="border-b border-slate-200 bg-slate-50 text-xs uppercase tracking-wider text-slate-500 dark:border-gray-700 dark:bg-gray-800 dark:text-slate-400">
              <tr>
                <th className="px-4 py-3 font-semibold">When</th>
                <th className="px-4 py-3 font-semibold">Action</th>
                <th className="px-4 py-3 font-semibold">Actor</th>
                <th className="px-4 py-3 font-semibold">Target</th>
                <th className="px-4 py-3 font-semibold">Details</th>
              </tr>
            </thead>
            <tbody className="divide-y divide-slate-100 dark:divide-gray-700/70">
              {logs.isPending && (
                <tr><td colSpan={5} className="px-4 py-8"><Spinner className="mx-auto" /></td></tr>
              )}
              {logs.data?.content.length === 0 && (
                <tr><td colSpan={5} className="px-4 py-8 text-center muted">No entries.</td></tr>
              )}
              {logs.data?.content.map((l) => (
                <tr key={l.id}>
                  <td className="whitespace-nowrap px-4 py-3 text-xs muted">{new Date(l.createdAt).toLocaleString()}</td>
                  <td className="px-4 py-3"><Badge tone={l.action.includes('LOCK') || l.action.includes('DISABLED') ? 'warning' : 'neutral'}>{l.action}</Badge></td>
                  <td className="px-4 py-3">{l.actorUsername ? `@${l.actorUsername}` : <span className="muted">system</span>}</td>
                  <td className="px-4 py-3 text-xs">{l.entityType ? `${l.entityType} #${l.entityId}` : '—'}</td>
                  <td className="px-4 py-3 text-xs muted">{l.details ?? '—'}</td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
        {logs.data && logs.data.totalPages > 1 && (
          <div className="flex items-center justify-between border-t border-slate-200 px-4 py-3 text-sm dark:border-gray-700">
            <span className="muted">Page {logs.data.page + 1} of {logs.data.totalPages}</span>
            <div className="flex gap-2">
              <Button size="sm" variant="secondary" disabled={page === 0} onClick={() => setPage((p) => p - 1)} aria-label="Previous page"><ChevronLeft className="h-4 w-4" /></Button>
              <Button size="sm" variant="secondary" disabled={page + 1 >= logs.data.totalPages} onClick={() => setPage((p) => p + 1)} aria-label="Next page"><ChevronRight className="h-4 w-4" /></Button>
            </div>
          </div>
        )}
      </div>
    </div>
  );
}
