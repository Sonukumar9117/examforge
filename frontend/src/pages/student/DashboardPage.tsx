import { useQuery } from '@tanstack/react-query';
import { CalendarClock, Flame, Gauge, PlayCircle, Sparkles, Target, Timer } from 'lucide-react';
import { Link } from 'react-router-dom';
import { catalogApi, examApi, studentApi } from '@/api/endpoints';
import type { StudentStats } from '@/api/types';
import { useAuth } from '@/auth/AuthProvider';
import { AttemptRow } from '@/components/AttemptRow';
import { TrendChart } from '@/components/charts/TrendChart';
import { LiveTestsSection } from '@/components/LiveTestsSection';
import { ExamCard } from '@/components/ExamCard';
import { StatCard } from '@/components/PageHeader';
import { EmptyState, Spinner } from '@/components/ui/Feedback';

function greeting() {
  const h = new Date().getHours();
  return h < 12 ? 'Good morning' : h < 17 ? 'Good afternoon' : 'Good evening';
}

export default function DashboardPage() {
  const { user } = useAuth();
  const stats = useQuery({ queryKey: ['student', 'stats'], queryFn: studentApi.stats });
  if (!user) return null;
  const s = stats.data;
  const streak = s?.currentStreak ?? user.profile?.currentStreak ?? 0;
  const resume = s?.inProgress.find((a) => new Date(a.deadlineAt) > new Date());

  return (
    <div className="space-y-8">
      <section className="relative overflow-hidden rounded-3xl bg-ink p-6 text-white sm:p-8">
        <div className="pointer-events-none absolute -right-16 -top-20 h-72 w-72 rounded-full bg-brand-600/30 blur-3xl" />
        <p className="relative text-sm text-slate-300">{greeting()},</p>
        <h1 className="relative mt-1 text-2xl font-extrabold sm:text-3xl">
          {user.fullName.split(' ')[0]} <span className="text-slate-400">@{user.username}</span>
        </h1>
        <p className="relative mt-2 max-w-xl text-sm text-slate-300">
          {streak > 1 ? `You're on a ${streak}-day streak — keep it going!` : "Ready for today's practice? Pick a mock test and start a streak."}
        </p>
        <div className="relative mt-5 flex flex-wrap gap-3">
          {resume ? (
            <Link to={`/attempts/${resume.attemptId}`} className="inline-flex h-10 items-center gap-2 rounded-xl bg-brand-600 px-4 text-sm font-semibold hover:bg-brand-700">
              <PlayCircle className="h-4 w-4" /> Continue {resume.exam.title}
            </Link>
          ) : (
            <Link to="/exams" className="inline-flex h-10 items-center rounded-xl bg-brand-600 px-4 text-sm font-semibold hover:bg-brand-700">
              Explore exams
            </Link>
          )}
          <Link to="/results?tab=saved" className="inline-flex h-10 items-center rounded-xl border border-white/20 px-4 text-sm font-semibold hover:bg-white/10">
            Saved questions
          </Link>
        </div>
      </section>

      <section aria-label="Your statistics" className="grid grid-cols-2 gap-4 lg:grid-cols-4">
        <StatCard label="Tests taken" value={s?.testsTaken ?? '—'} icon={<Timer className="h-4 w-4" />} hint="Finished attempts" />
        <StatCard
          label="Average score"
          value={s?.averagePercentage != null ? `${s.averagePercentage}%` : '—'}
          icon={<Gauge className="h-4 w-4" />}
          hint={s?.bestPercentage != null ? `Best ${s.bestPercentage}%` : 'Across all tests'}
        />
        <StatCard label="Accuracy" value={s?.accuracy != null ? `${s.accuracy}%` : '—'} icon={<Target className="h-4 w-4" />} hint="Correct ÷ attempted" />
        <StatCard
          label="Daily streak"
          value={`${streak} day${streak === 1 ? '' : 's'}`}
          icon={<Flame className="h-4 w-4" />}
          hint={s ? `Longest ${s.longestStreak} days` : 'Practice every day'}
        />
      </section>

      <LiveTestsSection />

      {stats.isPending ? (
        <Spinner />
      ) : s && s.testsTaken > 0 ? (
        <section className="grid gap-6 lg:grid-cols-5">
          <div className="card p-5 lg:col-span-3">
            <h2 className="text-lg font-bold">Score trend</h2>
            <p className="mb-3 text-xs muted">Score % in your last {s.trend.length} tests</p>
            <TrendChart points={s.trend.map((t) => ({ date: t.date, label: t.examTitle, value: t.percentage }))} />
          </div>
          <WeakSubjects stats={s} />
        </section>
      ) : null}

      <section className="grid gap-6 lg:grid-cols-2">
        <div>
          <div className="mb-3 flex items-baseline justify-between">
            <h2 className="text-lg font-bold">Recent tests</h2>
            <Link to="/my-tests" className="link text-sm">
              All tests
            </Link>
          </div>
          {s && s.recent.length > 0 ? (
            <ul className="card divide-y divide-slate-100 px-5 dark:divide-gray-700/70">
              {s.recent.map((a) => (
                <AttemptRow key={a.attemptId} a={a} />
              ))}
            </ul>
          ) : (
            <EmptyState icon={<CalendarClock className="h-6 w-6" />} title="No tests yet">
              Your attempts and scores will appear here.
            </EmptyState>
          )}
        </div>
        <Recommended targetCategoryId={user.profile?.targetCategoryId ?? null} />
      </section>
    </div>
  );
}

function WeakSubjects({ stats }: { stats: StudentStats }) {
  const subjects = stats.subjects.filter((x) => x.correct + x.incorrect + x.unanswered > 0).slice(0, 5);
  return (
    <div className="card p-5 lg:col-span-2">
      <h2 className="text-lg font-bold">Subjects to practise</h2>
      <p className="mb-4 text-xs muted">Lowest accuracy first</p>
      {subjects.length === 0 ? (
        <p className="text-sm muted">Not enough data yet.</p>
      ) : (
        <ul className="space-y-3">
          {subjects.map((x) => (
            <li key={x.subjectId}>
              <div className="flex justify-between text-sm">
                <Link to={`/exams?subject=${x.subjectId}`} className="font-semibold hover:text-brand-700 dark:hover:text-brand-300">
                  {x.subject}
                </Link>
                <span className="muted">{x.correct + x.incorrect > 0 ? `${x.accuracy}%` : 'not attempted'}</span>
              </div>
              <div className="mt-1 h-2 overflow-hidden rounded-full bg-slate-100 dark:bg-gray-700">
                <div className="h-full rounded-full bg-brand-600 dark:bg-brand-400" style={{ width: `${x.accuracy}%` }} />
              </div>
              <p className="mt-0.5 text-xs muted">
                {x.correct} correct · {x.incorrect} wrong · {x.unanswered} skipped
              </p>
            </li>
          ))}
        </ul>
      )}
    </div>
  );
}

/** Latest published tests for the student's target exam (or from all exams if none is set). */
function Recommended({ targetCategoryId }: { targetCategoryId: number | null }) {
  const categories = useQuery({ queryKey: ['catalog', 'categories'], queryFn: catalogApi.categories });
  const target = categories.data?.find((c) => c.id === targetCategoryId);
  const exams = useQuery({
    queryKey: ['exams', 'recommended', target?.slug ?? 'all'],
    queryFn: () => examApi.browse({ category: target?.slug, size: 3 }),
    enabled: !categories.isPending,
  });

  return (
    <div>
      <div className="mb-3 flex items-baseline justify-between gap-2">
        <h2 className="text-lg font-bold">Recommended for you</h2>
        <Link to={target ? `/exams?category=${target.slug}` : '/exams'} className="link text-sm">
          See all
        </Link>
      </div>
      {!target && targetCategoryId === null && (
        <p className="mb-3 text-sm muted">
          <Link to="/profile" className="link">Set your target exam</Link> to get tests picked for you.
        </p>
      )}
      {exams.isPending ? (
        <Spinner />
      ) : exams.data && exams.data.content.length > 0 ? (
        <div className="grid gap-3">
          {exams.data.content.map((e) => (
            <ExamCard key={e.id} exam={e} />
          ))}
        </div>
      ) : (
        <EmptyState icon={<Sparkles className="h-6 w-6" />} title="No mock tests yet">
          {target ? `No ${target.name} tests are published yet.` : 'Published tests will show up here.'}
        </EmptyState>
      )}
    </div>
  );
}
