import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import clsx from 'clsx';
import {
  ArrowLeft,
  Bookmark,
  BookmarkCheck,
  CheckCircle2,
  CircleMinus,
  Printer,
  RotateCcw,
  Target,
  XCircle,
} from 'lucide-react';
import { useMemo, useState } from 'react';
import { Link, useLocation, useParams } from 'react-router-dom';
import { errorMessage } from '@/api/client';
import { attemptApi, studentApi } from '@/api/endpoints';
import type { ExamResult, ReviewItem } from '@/api/types';
import { ExamCard } from '@/components/ExamCard';
import { StatCard } from '@/components/PageHeader';
import { Button } from '@/components/ui/Button';
import { Alert, Badge, Spinner } from '@/components/ui/Feedback';

export function formatDuration(seconds: number) {
  const m = Math.floor(seconds / 60);
  const s = seconds % 60;
  return m >= 60 ? `${Math.floor(m / 60)}h ${m % 60}m` : `${m}m ${String(s).padStart(2, '0')}s`;
}

type Filter = 'ALL' | ReviewItem['outcome'];

export default function ResultPage() {
  const attemptId = Number(useParams().attemptId);
  const notice = (useLocation().state as { notice?: string } | null)?.notice;
  const result = useQuery({ queryKey: ['result', attemptId], queryFn: () => attemptApi.result(attemptId) });

  if (result.isPending) return <Spinner />;
  if (result.isError) return <Alert tone="error">{errorMessage(result.error)}</Alert>;
  return <ResultView r={result.data} notice={notice} />;
}

function ResultView({ r, notice }: { r: ExamResult; notice?: string }) {
  const [filter, setFilter] = useState<Filter>('ALL');
  const review = useMemo(() => (r.review ?? []).filter((i) => filter === 'ALL' || i.outcome === filter), [r.review, filter]);
  const pct = Math.max(0, Math.min(100, r.percentage));

  return (
    <div className="mx-auto max-w-5xl space-y-6 print:max-w-none">
      <div className="flex flex-wrap items-center justify-between gap-2 print:hidden">
        <Link to="/results" className="inline-flex items-center gap-1 text-sm muted hover:text-slate-900 dark:hover:text-white">
          <ArrowLeft className="h-4 w-4" /> All results
        </Link>
        <div className="flex gap-2">
          <Button variant="secondary" size="sm" onClick={() => window.print()}>
            <Printer className="h-4 w-4" /> Print / save PDF
          </Button>
          <Link to={`/exams/${r.exam.slug}`} className="inline-flex h-8 items-center gap-1.5 rounded-xl bg-brand-600 px-3 text-sm font-semibold text-white hover:bg-brand-700">
            <RotateCcw className="h-4 w-4" /> Try again
          </Link>
        </div>
      </div>

      {notice && <Alert tone="info">{notice}</Alert>}

      {/* Score */}
      <section className="card overflow-hidden">
        <div className="flex flex-col gap-6 p-6 sm:flex-row sm:items-center sm:p-8">
          <div className="relative mx-auto h-36 w-36 shrink-0 sm:mx-0">
            <svg viewBox="0 0 36 36" className="h-full w-full -rotate-90" aria-hidden="true">
              <circle cx="18" cy="18" r="15.9" fill="none" strokeWidth="3.2" className="stroke-slate-200 dark:stroke-gray-700" />
              <circle
                cx="18"
                cy="18"
                r="15.9"
                fill="none"
                strokeWidth="3.2"
                strokeLinecap="round"
                strokeDasharray={`${pct} 100`}
                className={r.passed === false ? 'stroke-red-500' : 'stroke-emerald-500'}
              />
            </svg>
            <div className="absolute inset-0 flex flex-col items-center justify-center">
              <span className="font-display text-3xl font-extrabold">{r.percentage}%</span>
              <span className="text-xs muted">score</span>
            </div>
          </div>
          <div className="flex-1 text-center sm:text-left">
            <p className="text-sm muted">{r.exam.categoryName} · attempt {r.attemptNo}</p>
            <h1 className="text-2xl font-extrabold">{r.exam.title}</h1>
            <p className="mt-2 text-lg">
              <span className="font-bold">{r.score}</span> <span className="muted">/ {r.maxScore} marks</span>
            </p>
            <div className="mt-2 flex flex-wrap justify-center gap-2 sm:justify-start">
              {r.passed === true && <Badge tone="success">Passed (pass mark {r.passMarks})</Badge>}
              {r.passed === false && <Badge tone="warning">Not passed (pass mark {r.passMarks})</Badge>}
              {r.status === 'AUTO_SUBMITTED' && <Badge>Submitted automatically</Badge>}
              {r.rank != null ? (
                <Badge tone="brand">
                  Rank {r.rank} of {r.candidates} · better than {r.percentile}%
                </Badge>
              ) : (
                r.rankNote && <span className="text-xs muted">{r.rankNote}</span>
              )}
              {r.leaderboardAvailable && (
                <Link to={`/leaderboard?exam=${r.exam.slug}`} className="link text-xs print:hidden">
                  View leaderboard
                </Link>
              )}
            </div>
          </div>
        </div>
      </section>

      <section className="grid grid-cols-2 gap-3 lg:grid-cols-4">
        <StatCard label="Correct" value={r.correct} icon={<CheckCircle2 className="h-4 w-4" />} hint={`of ${r.totalQuestions} questions`} />
        <StatCard label="Incorrect" value={r.incorrect} icon={<XCircle className="h-4 w-4" />} />
        <StatCard label="Unanswered" value={r.unanswered} icon={<CircleMinus className="h-4 w-4" />} />
        <StatCard label="Accuracy" value={`${r.accuracy}%`} icon={<Target className="h-4 w-4" />} hint={`Time ${formatDuration(r.timeTakenSeconds)}`} />
      </section>

      {/* Subjects */}
      {r.subjects.length > 0 && (
        <section className="card p-5 sm:p-6">
          <h2 className="mb-4 text-lg font-bold">Subject-wise performance</h2>
          <ul className="space-y-4">
            {r.subjects.map((s) => {
              const width = s.maxScore > 0 ? Math.max(0, (s.score / s.maxScore) * 100) : 0;
              return (
                <li key={s.subjectId}>
                  <div className="flex flex-wrap items-baseline justify-between gap-2 text-sm">
                    <span className="font-semibold">{s.subject}</span>
                    <span className="muted">
                      {s.score}/{s.maxScore} marks · {s.correct}✓ {s.incorrect}✗ {s.unanswered}– · {s.correct + s.incorrect > 0 ? `${s.accuracy}% accuracy` : 'not attempted'}
                    </span>
                  </div>
                  <div className="mt-1.5 h-2.5 overflow-hidden rounded-full bg-slate-100 dark:bg-gray-700">
                    <div
                      className={clsx('h-full rounded-full', s.accuracy >= 60 ? 'bg-emerald-500' : s.accuracy >= 40 ? 'bg-amber-500' : 'bg-red-500')}
                      style={{ width: `${width}%` }}
                    />
                  </div>
                </li>
              );
            })}
          </ul>
        </section>
      )}

      {/* Recommendations */}
      {r.recommendations.length > 0 && (
        <section className="print:hidden">
          <h2 className="mb-3 text-lg font-bold">Practise your weak areas</h2>
          <div className="grid gap-3 sm:grid-cols-2 lg:grid-cols-3">
            {r.recommendations.map((e) => (
              <ExamCard key={e.id} exam={e} />
            ))}
          </div>
        </section>
      )}

      {/* Review */}
      <section>
        <div className="mb-3 flex flex-wrap items-center justify-between gap-2">
          <h2 className="text-lg font-bold">Question review</h2>
          {r.review && (
            <div className="flex flex-wrap gap-1 print:hidden" role="tablist" aria-label="Filter questions">
              {(['ALL', 'CORRECT', 'INCORRECT', 'UNANSWERED'] as Filter[]).map((f) => (
                <button
                  key={f}
                  type="button"
                  role="tab"
                  aria-selected={filter === f}
                  onClick={() => setFilter(f)}
                  className={clsx(
                    'rounded-full px-3 py-1 text-xs font-semibold',
                    filter === f ? 'bg-brand-600 text-white' : 'bg-white text-slate-600 hover:bg-slate-100 dark:bg-gray-800 dark:text-slate-300',
                  )}
                >
                  {f === 'ALL' ? 'All' : f.charAt(0) + f.slice(1).toLowerCase()}
                </button>
              ))}
            </div>
          )}
        </div>
        {!r.reviewAvailable ? (
          <Alert tone="info">{r.reviewMessage ?? 'Answers are not available yet.'}</Alert>
        ) : (
          <ol className="space-y-4">
            {review.map((item) => (
              <ReviewCard key={item.questionId} item={item} attemptId={r.attemptId} />
            ))}
            {review.length === 0 && <p className="text-sm muted">No questions in this group.</p>}
          </ol>
        )}
      </section>
    </div>
  );
}

const outcomeMeta = {
  CORRECT: { label: 'Correct', className: 'text-emerald-700 bg-emerald-50 dark:bg-emerald-950/50 dark:text-emerald-300' },
  INCORRECT: { label: 'Incorrect', className: 'text-red-700 bg-red-50 dark:bg-red-950/50 dark:text-red-300' },
  UNANSWERED: { label: 'Not answered', className: 'text-slate-700 bg-slate-100 dark:bg-gray-700 dark:text-slate-200' },
};

function ReviewCard({ item, attemptId }: { item: ReviewItem; attemptId: number }) {
  const queryClient = useQueryClient();
  const toggleSave = useMutation({
    mutationFn: async () => {
      if (item.saved) await studentApi.unsaveQuestion(item.questionId);
      else await studentApi.saveQuestion(item.questionId);
    },
    onSuccess: () => {
      queryClient.setQueryData<ExamResult>(['result', attemptId], (old) =>
        old && old.review
          ? { ...old, review: old.review.map((i) => (i.questionId === item.questionId ? { ...i, saved: !i.saved } : i)) }
          : old,
      );
      queryClient.invalidateQueries({ queryKey: ['student', 'saved'] });
    },
  });
  const meta = outcomeMeta[item.outcome];

  return (
    <li className="card break-inside-avoid p-5">
      <div className="flex flex-wrap items-center gap-2 text-xs">
        <span className="font-bold">Q{item.number}</span>
        <span className={clsx('rounded-full px-2.5 py-0.5 font-semibold', meta.className)}>
          {meta.label} · {item.marksAwarded > 0 ? '+' : ''}
          {item.marksAwarded}
        </span>
        <span className="muted">
          {item.subject}
          {item.topic && ` · ${item.topic}`}
        </span>
        <button
          type="button"
          onClick={() => toggleSave.mutate()}
          disabled={toggleSave.isPending}
          className="ml-auto inline-flex items-center gap-1 rounded-lg px-2 py-1 font-semibold text-slate-600 hover:bg-slate-100 dark:text-slate-300 dark:hover:bg-gray-700 print:hidden"
          aria-pressed={item.saved}
        >
          {item.saved ? <BookmarkCheck className="h-4 w-4 text-brand-600" /> : <Bookmark className="h-4 w-4" />}
          {item.saved ? 'Saved' : 'Save'}
        </button>
      </div>
      <p className="mt-3 whitespace-pre-line font-medium">{item.text}</p>
      {item.imageUrl && <img src={item.imageUrl} alt="" className="mt-3 max-h-60 rounded-lg" />}
      <ul className="mt-3 space-y-2">
        {item.options.map((o) => (
          <li
            key={o.id}
            className={clsx(
              'flex items-start gap-2 rounded-lg border px-3 py-2 text-sm',
              o.correct
                ? 'border-emerald-500 bg-emerald-50 dark:bg-emerald-950/30'
                : o.selected
                  ? 'border-red-400 bg-red-50 dark:bg-red-950/30'
                  : 'border-slate-200 dark:border-gray-700',
            )}
          >
            <span className="font-bold">{o.label}.</span>
            <span className="flex-1">{o.text}</span>
            {o.correct && <span className="text-xs font-semibold text-emerald-700 dark:text-emerald-300">Correct answer</span>}
            {o.selected && <span className="text-xs font-semibold muted">Your answer</span>}
          </li>
        ))}
      </ul>
      {item.explanation && (
        <div className="mt-3 rounded-lg bg-slate-50 p-3 text-sm dark:bg-gray-900/60">
          <span className="font-semibold">Explanation: </span>
          {item.explanation}
        </div>
      )}
      {toggleSave.isError && <p className="mt-2 text-xs text-red-600">{errorMessage(toggleSave.error)}</p>}
    </li>
  );
}
