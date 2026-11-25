import { CalendarClock, Clock3, FileQuestion, Trophy } from 'lucide-react';
import { Link } from 'react-router-dom';
import type { ExamSummary } from '@/api/types';
import { CategoryIcon } from '@/components/CategoryIcon';
import { Badge } from '@/components/ui/Feedback';

const difficultyTone = { EASY: 'success', MEDIUM: 'neutral', HARD: 'warning' } as const;
export const examTypeLabel = { PRACTICE: 'Practice', MOCK: 'Mock test', LIVE: 'Live test' } as const;

export function formatWindow(startsAt: string | null, endsAt: string | null) {
  const fmt = (iso: string) =>
    new Date(iso).toLocaleString(undefined, { day: 'numeric', month: 'short', hour: 'numeric', minute: '2-digit' });
  if (startsAt && endsAt) return `${fmt(startsAt)} – ${fmt(endsAt)}`;
  if (startsAt) return `From ${fmt(startsAt)}`;
  if (endsAt) return `Until ${fmt(endsAt)}`;
  return null;
}

export function ExamCard({ exam }: { exam: ExamSummary }) {
  const window = formatWindow(exam.startsAt, exam.endsAt);
  return (
    <Link
      to={`/exams/${exam.slug}`}
      className="card group flex flex-col p-5 transition hover:-translate-y-0.5 hover:border-brand-300 dark:hover:border-brand-800"
    >
      <div className="flex items-start justify-between gap-3">
        <span className="flex h-10 w-10 shrink-0 items-center justify-center rounded-xl bg-brand-50 text-brand-600 dark:bg-brand-950/50 dark:text-brand-400">
          <CategoryIcon name={exam.category.icon} />
        </span>
        <div className="flex flex-wrap justify-end gap-1">
          <Badge tone={exam.type === 'LIVE' ? 'brand' : 'neutral'}>{examTypeLabel[exam.type]}</Badge>
          <Badge tone={difficultyTone[exam.difficulty]}>{exam.difficulty.toLowerCase()}</Badge>
        </div>
      </div>
      <h3 className="mt-3 font-bold leading-snug group-hover:text-brand-700 dark:group-hover:text-brand-300">{exam.title}</h3>
      <p className="mt-0.5 text-xs muted">{exam.category.name}</p>
      <dl className="mt-4 grid grid-cols-3 gap-2 text-xs">
        <div className="flex items-center gap-1.5">
          <FileQuestion className="h-3.5 w-3.5 text-slate-400" />
          <dt className="sr-only">Questions</dt>
          <dd>{exam.questionCount} Qs</dd>
        </div>
        <div className="flex items-center gap-1.5">
          <Clock3 className="h-3.5 w-3.5 text-slate-400" />
          <dt className="sr-only">Duration</dt>
          <dd>{exam.durationMinutes} min</dd>
        </div>
        <div className="flex items-center gap-1.5">
          <Trophy className="h-3.5 w-3.5 text-slate-400" />
          <dt className="sr-only">Total marks</dt>
          <dd>{exam.totalMarks} marks</dd>
        </div>
      </dl>
      {window && (
        <p className="mt-3 flex items-center gap-1.5 text-xs text-brand-700 dark:text-brand-300">
          <CalendarClock className="h-3.5 w-3.5" /> {window}
        </p>
      )}
    </Link>
  );
}
