import { useQuery } from '@tanstack/react-query';
import { CalendarClock, Radio, Trophy } from 'lucide-react';
import type { ReactNode } from 'react';
import { Link } from 'react-router-dom';
import { liveApi } from '@/api/endpoints';
import type { LiveTestItem } from '@/api/types';
import { CategoryIcon } from '@/components/CategoryIcon';
import { Countdown } from '@/components/Countdown';
import { Badge, EmptyState, Spinner } from '@/components/ui/Feedback';

const fmt = (iso: string | null) =>
  iso ? new Date(iso).toLocaleString(undefined, { weekday: 'short', day: 'numeric', month: 'short', hour: 'numeric', minute: '2-digit' }) : '';

/**
 * Live tests: a fixed time window in which every student takes the test once.
 * When the window closes, the leaderboard ranks everyone.
 */
export function LiveTestsSection() {
  const live = useQuery({ queryKey: ['live'], queryFn: liveApi.list, refetchInterval: 60_000 });
  const d = live.data;

  return (
    <section>
      <div className="mb-3 flex items-baseline justify-between gap-2">
        <h2 className="flex items-center gap-2 text-lg font-bold">
          <Radio className="h-5 w-5 text-brand-600" /> Live tests
        </h2>
        <Link to="/leaderboard" className="link text-sm">
          Leaderboards
        </Link>
      </div>
      {live.isPending ? (
        <Spinner />
      ) : !d || d.open.length + d.upcoming.length + d.ended.length === 0 ? (
        <EmptyState icon={<CalendarClock className="h-6 w-6" />} title="No live tests right now">
          Live tests run for a fixed time window. Everyone takes the test once, and a leaderboard is published when it closes.
        </EmptyState>
      ) : (
        <div className="grid gap-4 lg:grid-cols-3">
          <Column title="Open now" empty="No live test is open right now.">
            {d.open.map((i) => (
              <Row key={i.exam.id} item={i}>
                {i.myStatus === 'IN_PROGRESS' ? (
                  <Link to={`/attempts/${i.myAttemptId}`} className="link text-sm">Continue</Link>
                ) : i.myStatus ? (
                  <Badge tone="success">Done · {i.myScore}</Badge>
                ) : (
                  <Link to={`/exams/${i.exam.slug}`} className="inline-flex h-8 items-center rounded-lg bg-brand-600 px-3 text-xs font-semibold text-white hover:bg-brand-700">
                    Take test
                  </Link>
                )}
                <p className="mt-1 text-xs muted">
                  Closes in <Countdown to={i.exam.endsAt} serverNow={d.serverNow} done="a moment" />
                </p>
              </Row>
            ))}
          </Column>
          <Column title="Coming up" empty="Nothing scheduled.">
            {d.upcoming.map((i) => (
              <Row key={i.exam.id} item={i}>
                <p className="text-xs muted">
                  Opens in <Countdown to={i.exam.startsAt} serverNow={d.serverNow} />
                </p>
                <p className="text-xs muted">{fmt(i.exam.startsAt)} – {fmt(i.exam.endsAt)}</p>
              </Row>
            ))}
          </Column>
          <Column title="Results out" empty="No closed live tests yet.">
            {d.ended.map((i) => (
              <Row key={i.exam.id} item={i}>
                <Link to={`/leaderboard?exam=${i.exam.slug}`} className="inline-flex items-center gap-1 text-sm font-semibold text-brand-700 hover:underline dark:text-brand-300">
                  <Trophy className="h-4 w-4" /> Leaderboard
                </Link>
                <p className="text-xs muted">{i.myStatus ? `Your score: ${i.myScore ?? '—'}` : 'You did not take part'}</p>
              </Row>
            ))}
          </Column>
        </div>
      )}
    </section>
  );
}

function Column({ title, empty, children }: { title: string; empty: string; children: ReactNode[] }) {
  return (
    <div className="card p-4">
      <h3 className="mb-2 text-xs font-bold uppercase tracking-wider muted">{title}</h3>
      {children.length === 0 ? <p className="py-3 text-sm muted">{empty}</p> : <ul className="divide-y divide-slate-100 dark:divide-gray-700/70">{children}</ul>}
    </div>
  );
}

function Row({ item, children }: { item: LiveTestItem; children: ReactNode }) {
  return (
    <li className="flex gap-3 py-3">
      <span className="flex h-9 w-9 shrink-0 items-center justify-center rounded-lg bg-brand-50 text-brand-600 dark:bg-brand-950/50 dark:text-brand-400">
        <CategoryIcon name={item.exam.category.icon} className="h-4 w-4" />
      </span>
      <div className="min-w-0 flex-1 space-y-1">
        <Link to={`/exams/${item.exam.slug}`} className="block truncate text-sm font-semibold hover:text-brand-700 dark:hover:text-brand-300">
          {item.exam.title}
        </Link>
        {children}
      </div>
    </li>
  );
}
