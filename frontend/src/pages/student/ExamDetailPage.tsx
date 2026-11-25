import { useMutation, useQuery } from '@tanstack/react-query';
import { ArrowLeft, CalendarClock, CircleAlert, Clock3, FileQuestion, Lock, Navigation, Play, Trophy } from 'lucide-react';
import { useState, type ReactNode } from 'react';
import { Link, useNavigate, useParams } from 'react-router-dom';
import { ApiError, errorMessage } from '@/api/client';
import { attemptApi, examApi } from '@/api/endpoints';
import { CategoryIcon } from '@/components/CategoryIcon';
import { examTypeLabel, formatWindow } from '@/components/ExamCard';
import { Button } from '@/components/ui/Button';
import { Alert, Badge, EmptyState, Spinner } from '@/components/ui/Feedback';
import { Checkbox } from '@/components/ui/Field';

function Fact({ icon, label, value }: { icon: ReactNode; label: string; value: ReactNode }) {
  return (
    <div className="card flex items-center gap-3 p-4">
      <span className="text-brand-600 dark:text-brand-400">{icon}</span>
      <div>
        <dt className="text-xs muted">{label}</dt>
        <dd className="font-semibold">{value}</dd>
      </div>
    </div>
  );
}

export default function ExamDetailPage() {
  const slug = useParams().slug ?? '';
  const exam = useQuery({ queryKey: ['exams', 'detail', slug], queryFn: () => examApi.get(slug) });

  if (exam.isPending) return <Spinner />;
  if (exam.isError) {
    return exam.error instanceof ApiError && exam.error.status === 404 ? (
      <EmptyState icon={<FileQuestion className="h-6 w-6" />} title="Exam not found">
        It may have been unpublished. <Link to="/exams" className="link">Browse other exams</Link>.
      </EmptyState>
    ) : (
      <Alert tone="error">{errorMessage(exam.error)}</Alert>
    );
  }

  const { summary: s, description, instructions, allowNavigation, negativeMarking, maxNegativeMarks, subjects } = exam.data;
  const window = formatWindow(s.startsAt, s.endsAt);
  const notStarted = s.startsAt && new Date(s.startsAt) > new Date();
  const closed = !!s.endsAt && new Date(s.endsAt) <= new Date();

  return (
    <div className="mx-auto max-w-4xl space-y-6">
      <Link to="/exams" className="inline-flex items-center gap-1 text-sm muted hover:text-slate-900 dark:hover:text-white">
        <ArrowLeft className="h-4 w-4" /> All exams
      </Link>

      <header className="flex flex-col gap-4 sm:flex-row sm:items-start">
        <span className="flex h-14 w-14 shrink-0 items-center justify-center rounded-2xl bg-brand-50 text-brand-600 dark:bg-brand-950/50 dark:text-brand-400">
          <CategoryIcon name={s.category.icon} className="h-7 w-7" />
        </span>
        <div className="flex-1">
          <p className="text-sm muted">{s.category.name}</p>
          <h1 className="text-2xl font-extrabold sm:text-3xl">{s.title}</h1>
          <div className="mt-2 flex flex-wrap gap-1.5">
            <Badge tone={s.type === 'LIVE' ? 'brand' : 'neutral'}>{examTypeLabel[s.type]}</Badge>
            <Badge>{s.difficulty.toLowerCase()}</Badge>
            {subjects.map((sub) => (
              <Badge key={sub.id}>{sub.name}</Badge>
            ))}
          </div>
          {description && <p className="mt-3 text-sm text-slate-600 dark:text-slate-300">{description}</p>}
        </div>
      </header>

      <dl className="grid grid-cols-2 gap-3 lg:grid-cols-4">
        <Fact icon={<FileQuestion className="h-5 w-5" />} label="Questions" value={s.questionCount} />
        <Fact icon={<Clock3 className="h-5 w-5" />} label="Duration" value={`${s.durationMinutes} minutes`} />
        <Fact icon={<Trophy className="h-5 w-5" />} label="Total marks" value={s.passMarks != null ? `${s.totalMarks} (pass ${s.passMarks})` : s.totalMarks} />
        <Fact
          icon={<CircleAlert className="h-5 w-5" />}
          label="Negative marking"
          value={negativeMarking ? `Up to −${maxNegativeMarks} per wrong answer` : 'None'}
        />
      </dl>

      <div className="grid gap-3 sm:grid-cols-2">
        {window && (
          <div className="flex items-center gap-2 text-sm">
            <CalendarClock className="h-4 w-4 text-brand-600" /> Available: {window}
          </div>
        )}
        <div className="flex items-center gap-2 text-sm">
          <Navigation className="h-4 w-4 text-brand-600" />
          {allowNavigation ? 'You can move between questions freely' : 'Questions must be answered in order'}
        </div>
        {s.maxAttempts && (
          <div className="flex items-center gap-2 text-sm">
            <Lock className="h-4 w-4 text-brand-600" /> {s.maxAttempts} attempt{s.maxAttempts === 1 ? '' : 's'} allowed
          </div>
        )}
      </div>

      {instructions && (
        <section className="card p-5 sm:p-6">
          <h2 className="mb-3 text-lg font-bold">Instructions</h2>
          <div className="whitespace-pre-line text-sm leading-relaxed text-slate-700 dark:text-slate-300">{instructions}</div>
        </section>
      )}

      {s.type === 'LIVE' && (
        <Alert tone="info">
          <strong>Live test:</strong> take it any time before it closes{s.endsAt ? ` (${new Date(s.endsAt).toLocaleString(undefined, { dateStyle: 'medium', timeStyle: 'short' })})` : ''}.
          You get <strong>one attempt</strong>. When the test closes, everyone is ranked on the{' '}
          <Link to={`/leaderboard?exam=${s.slug}`} className="link">leaderboard</Link>.
        </Alert>
      )}

      {closed ? (
        <div className="card flex flex-wrap items-center justify-between gap-3 p-5">
          <p className="text-sm">This test has closed.</p>
          <Link to={`/leaderboard?exam=${s.slug}`} className="link text-sm">
            See the leaderboard
          </Link>
        </div>
      ) : (
        <StartPanel slug={s.slug} notStarted={!!notStarted} durationMinutes={s.durationMinutes} />
      )}
    </div>
  );
}

/** Instructions must be confirmed before starting. The server records the start time. */
function StartPanel({ slug, notStarted, durationMinutes }: { slug: string; notStarted: boolean; durationMinutes: number }) {
  const navigate = useNavigate();
  const [agreed, setAgreed] = useState(false);
  const start = useMutation({
    mutationFn: () => attemptApi.start(slug),
    onSuccess: (attempt) => navigate(`/attempts/${attempt.attemptId}`),
  });

  return (
    <div className="card space-y-4 p-5 sm:p-6">
      {start.isError && <Alert tone="error">{errorMessage(start.error)}</Alert>}
      {notStarted ? (
        <p className="text-sm muted">This test has not opened yet. Come back at the start time.</p>
      ) : (
        <>
          <Checkbox
            label={`I have read the instructions. I understand the ${durationMinutes}-minute timer starts now and keeps running even if I close the page.`}
            checked={agreed}
            onChange={(e) => setAgreed(e.target.checked)}
          />
          <div className="flex flex-wrap items-center gap-3">
            <Button size="lg" disabled={!agreed} loading={start.isPending} onClick={() => start.mutate()}>
              <Play className="h-4 w-4" /> Start test
            </Button>
            <span className="text-xs muted">If you already started this test, you will continue where you left off.</span>
          </div>
        </>
      )}
    </div>
  );
}
