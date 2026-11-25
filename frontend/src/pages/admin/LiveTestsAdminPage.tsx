import { useQuery } from '@tanstack/react-query';
import { Plus, Radio } from 'lucide-react';
import { Link } from 'react-router-dom';
import { errorMessage } from '@/api/client';
import { liveApi } from '@/api/endpoints';
import type { LiveTestItem } from '@/api/types';
import { Countdown } from '@/components/Countdown';
import { PageHeader } from '@/components/PageHeader';
import { Alert, Badge, EmptyState, Spinner } from '@/components/ui/Feedback';

const fmt = (iso: string | null) => (iso ? new Date(iso).toLocaleString(undefined, { dateStyle: 'medium', timeStyle: 'short' }) : '—');

/** Admin overview of live tests (exams of type LIVE with a time window). */
export default function LiveTestsAdminPage() {
  const live = useQuery({ queryKey: ['live'], queryFn: liveApi.list, refetchInterval: 60_000 });
  const d = live.data;
  const rows: { item: LiveTestItem; state: 'Open' | 'Upcoming' | 'Closed' }[] = d
    ? [
        ...d.open.map((item) => ({ item, state: 'Open' as const })),
        ...d.upcoming.map((item) => ({ item, state: 'Upcoming' as const })),
        ...d.ended.map((item) => ({ item, state: 'Closed' as const })),
      ]
    : [];

  return (
    <div>
      <PageHeader
        title="Live tests"
        description="A live test is an exam of type “Live” with a start and end time. Every student takes it once in that window; the leaderboard opens when it closes."
        actions={
          <Link to="/admin/exams" className="inline-flex h-10 items-center gap-2 rounded-xl bg-brand-600 px-4 text-sm font-semibold text-white hover:bg-brand-700">
            <Plus className="h-4 w-4" /> New live test
          </Link>
        }
      />
      <p className="mb-4 text-sm muted">
        To create one: <strong>Exams → New exam</strong>, choose type <strong>Live (scheduled)</strong>, set the start and end time, add questions and publish.
      </p>
      {live.isError && <Alert tone="error">{errorMessage(live.error)}</Alert>}
      {live.isPending ? (
        <Spinner />
      ) : rows.length === 0 ? (
        <EmptyState icon={<Radio className="h-6 w-6" />} title="No live tests">
          Published live tests that are open, upcoming or closed in the last 30 days appear here.
        </EmptyState>
      ) : (
        <div className="card overflow-hidden">
          <div className="overflow-x-auto">
            <table className="w-full min-w-[760px] text-left text-sm">
              <thead className="border-b border-slate-200 bg-slate-50 text-xs uppercase tracking-wider text-slate-500 dark:border-gray-700 dark:bg-gray-800 dark:text-slate-400">
                <tr>
                  <th className="px-4 py-3 font-semibold">Test</th>
                  <th className="px-4 py-3 font-semibold">Window</th>
                  <th className="px-4 py-3 font-semibold">State</th>
                  <th className="px-4 py-3 text-right font-semibold">Links</th>
                </tr>
              </thead>
              <tbody className="divide-y divide-slate-100 dark:divide-gray-700/70">
                {rows.map(({ item, state }) => (
                  <tr key={item.exam.id}>
                    <td className="px-4 py-3">
                      <div className="font-semibold">{item.exam.title}</div>
                      <div className="text-xs muted">
                        {item.exam.category.name} · {item.exam.questionCount} questions · {item.exam.durationMinutes} min
                      </div>
                    </td>
                    <td className="px-4 py-3 text-xs">
                      {fmt(item.exam.startsAt)} → {fmt(item.exam.endsAt)}
                    </td>
                    <td className="px-4 py-3">
                      {state === 'Open' && (
                        <span className="text-xs">
                          <Badge tone="brand">Open</Badge> closes in <Countdown to={item.exam.endsAt} serverNow={d!.serverNow} />
                        </span>
                      )}
                      {state === 'Upcoming' && (
                        <span className="text-xs">
                          <Badge>Upcoming</Badge> opens in <Countdown to={item.exam.startsAt} serverNow={d!.serverNow} />
                        </span>
                      )}
                      {state === 'Closed' && <Badge tone="success">Closed</Badge>}
                    </td>
                    <td className="px-4 py-3 text-right">
                      <div className="flex justify-end gap-3">
                        <Link to={`/admin/exams/${item.exam.id}`} className="link text-sm">
                          Exam
                        </Link>
                        <Link to={`/admin/reports?exam=${item.exam.id}`} className="link text-sm">
                          Report
                        </Link>
                        <Link to={`/leaderboard?exam=${item.exam.slug}`} className="link text-sm">
                          Leaderboard
                        </Link>
                      </div>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        </div>
      )}
    </div>
  );
}
