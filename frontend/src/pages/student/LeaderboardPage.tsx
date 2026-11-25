import { keepPreviousData, useQuery } from '@tanstack/react-query';
import clsx from 'clsx';
import { Lock, Medal, Trophy } from 'lucide-react';
import { useMemo } from 'react';
import { Link, useSearchParams } from 'react-router-dom';
import { errorMessage } from '@/api/client';
import { attemptApi, liveApi } from '@/api/endpoints';
import { useAuth } from '@/auth/AuthProvider';
import { Countdown } from '@/components/Countdown';
import { PageHeader } from '@/components/PageHeader';
import { Alert, EmptyState, Spinner } from '@/components/ui/Feedback';
import { formatDuration } from './ResultPage';

/** Leaderboards: closed live tests and any test the student has taken. */
export default function LeaderboardPage() {
  const { user } = useAuth();
  const [params, setParams] = useSearchParams();
  const live = useQuery({ queryKey: ['live'], queryFn: liveApi.list });
  const mine = useQuery({ queryKey: ['student', 'attempts', 0], queryFn: () => attemptApi.mine(0, 50) });

  // Exams to choose from: closed/open live tests first, then other tests the student took.
  const options = useMemo(() => {
    const map = new Map<string, string>();
    live.data?.ended.forEach((i) => map.set(i.exam.slug, `🏁 ${i.exam.title}`));
    live.data?.open.forEach((i) => map.set(i.exam.slug, `● ${i.exam.title} (live now)`));
    mine.data?.content.forEach((a) => !map.has(a.exam.slug) && map.set(a.exam.slug, a.exam.title));
    return [...map.entries()];
  }, [live.data, mine.data]);

  const slug = params.get('exam') ?? options[0]?.[0] ?? '';
  const board = useQuery({
    queryKey: ['leaderboard', slug],
    queryFn: () => liveApi.leaderboard(slug),
    enabled: !!slug,
    placeholderData: keepPreviousData,
  });
  const b = board.data;

  return (
    <div className="mx-auto max-w-4xl">
      <PageHeader title="Leaderboard" description="Live tests are ranked when their time window closes. Each student counts once." />

      {live.isPending || mine.isPending ? (
        <Spinner />
      ) : options.length === 0 ? (
        <EmptyState icon={<Trophy className="h-6 w-6" />} title="No leaderboards yet">
          Take a live test or a mock test — its leaderboard will show up here. <Link to="/dashboard" className="link">See live tests</Link>
        </EmptyState>
      ) : (
        <>
          <label className="mb-5 block max-w-xl">
            <span className="mb-1.5 block text-sm font-medium text-slate-700 dark:text-slate-300">Test</span>
            <select
              value={slug}
              onChange={(e) => setParams({ exam: e.target.value }, { replace: true })}
              className="h-10 w-full rounded-xl border border-slate-300 bg-white px-3 text-sm dark:border-gray-600 dark:bg-gray-800"
            >
              {options.map(([value, label]) => (
                <option key={value} value={value}>
                  {label}
                </option>
              ))}
            </select>
          </label>

          {board.isError && <Alert tone="error">{errorMessage(board.error)}</Alert>}
          {board.isPending && !b ? (
            <Spinner />
          ) : b && !b.available ? (
            <div className="card flex flex-col items-center p-10 text-center">
              <Lock className="h-8 w-8 text-brand-600" />
              <h2 className="mt-3 text-lg font-bold">Leaderboard opens when the test closes</h2>
              <p className="mt-1 text-sm muted">
                {b.exam.title} closes in{' '}
                <Countdown to={b.opensAt} serverNow={new Date().toISOString()} done="a moment — refresh to see ranks" />.
              </p>
            </div>
          ) : b ? (
            <>
              {b.me && (
                <div className="card mb-4 flex flex-wrap items-center gap-4 border-brand-200 p-5 dark:border-brand-900">
                  <Medal className="h-8 w-8 text-brand-600" />
                  <div>
                    <p className="text-sm muted">Your rank</p>
                    <p className="text-2xl font-extrabold">
                      #{b.me.rank} <span className="text-base font-normal muted">of {b.totalParticipants}</span>
                    </p>
                  </div>
                  <p className="text-sm">
                    Score <strong>{b.me.score}</strong>/{b.me.maxScore} · better than {b.me.betterThanPercent}% of students
                  </p>
                </div>
              )}
              {b.message && <Alert tone="info">{b.message}</Alert>}
              {b.entries.length > 0 && (
                <div className="card overflow-hidden">
                  <div className="overflow-x-auto">
                    <table className="w-full min-w-[560px] text-left text-sm">
                      <caption className="sr-only">Leaderboard for {b.exam.title}</caption>
                      <thead className="border-b border-slate-200 bg-slate-50 text-xs uppercase tracking-wider text-slate-500 dark:border-gray-700 dark:bg-gray-800 dark:text-slate-400">
                        <tr>
                          <th className="px-4 py-3 font-semibold">Rank</th>
                          <th className="px-4 py-3 font-semibold">Student</th>
                          <th className="px-4 py-3 text-right font-semibold">Score</th>
                          <th className="px-4 py-3 text-right font-semibold">Accuracy</th>
                          <th className="px-4 py-3 text-right font-semibold">Time</th>
                        </tr>
                      </thead>
                      <tbody className="divide-y divide-slate-100 dark:divide-gray-700/70">
                        {b.entries.map((e) => (
                          <tr key={e.rank} className={clsx(e.me && 'bg-brand-50 font-semibold dark:bg-brand-950/40')}>
                            <td className="px-4 py-3">
                              <span className="inline-flex items-center gap-1.5">
                                {e.rank <= 3 && <Trophy className={clsx('h-4 w-4', ['text-amber-500', 'text-slate-400', 'text-orange-700'][e.rank - 1])} aria-hidden="true" />}
                                {e.rank}
                              </span>
                            </td>
                            <td className="px-4 py-3">{e.me ? `You (${user?.fullName})` : e.name}</td>
                            <td className="px-4 py-3 text-right font-mono">
                              {e.score} <span className="muted">/ {e.maxScore}</span>
                            </td>
                            <td className="px-4 py-3 text-right">{e.accuracy}%</td>
                            <td className="px-4 py-3 text-right">{formatDuration(e.timeTakenSeconds)}</td>
                          </tr>
                        ))}
                      </tbody>
                    </table>
                  </div>
                </div>
              )}
              <p className="mt-3 text-xs muted">
                Ties are broken by the faster time, then the earlier submission. Don&apos;t want your name shown?{' '}
                <Link to="/profile" className="link">Change it in account settings</Link>.
              </p>
            </>
          ) : null}
        </>
      )}
    </div>
  );
}
