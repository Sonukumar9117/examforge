import { Clock3, PlayCircle } from 'lucide-react';
import { Link } from 'react-router-dom';
import type { AttemptSummary } from '@/api/types';
import { CategoryIcon } from '@/components/CategoryIcon';
import { Badge } from '@/components/ui/Feedback';

function minutesLeft(deadline: string) {
  return Math.max(0, Math.round((new Date(deadline).getTime() - Date.now()) / 60000));
}

/** One attempt in a list: continue if in progress, otherwise score + link to the result. */
export function AttemptRow({ a }: { a: AttemptSummary }) {
  const inProgress = a.status === 'IN_PROGRESS';
  const expired = inProgress && new Date(a.deadlineAt).getTime() < Date.now();
  return (
    <li className="flex flex-col gap-3 py-3 sm:flex-row sm:items-center">
      <span className="flex h-10 w-10 shrink-0 items-center justify-center rounded-xl bg-brand-50 text-brand-600 dark:bg-brand-950/50 dark:text-brand-400">
        <CategoryIcon name={a.exam.categoryIcon} />
      </span>
      <div className="min-w-0 flex-1">
        <p className="truncate font-semibold">{a.exam.title}</p>
        <p className="text-xs muted">
          {a.exam.categoryName} · attempt {a.attemptNo} · {new Date(a.startedAt).toLocaleString(undefined, { dateStyle: 'medium', timeStyle: 'short' })}
        </p>
      </div>
      {inProgress && !expired ? (
        <Link
          to={`/attempts/${a.attemptId}`}
          className="inline-flex h-9 items-center gap-1.5 self-start rounded-xl bg-brand-600 px-3.5 text-sm font-semibold text-white hover:bg-brand-700 sm:self-auto"
        >
          <PlayCircle className="h-4 w-4" /> Continue · {minutesLeft(a.deadlineAt)} min left
        </Link>
      ) : inProgress ? (
        <Link to={`/results/${a.attemptId}`} className="link self-start text-sm sm:self-auto">
          <Clock3 className="mr-1 inline h-4 w-4" /> Time is up — see result
        </Link>
      ) : (
        <div className="flex items-center gap-3 self-start sm:self-auto">
          <div className="text-right">
            <p className="font-bold">
              {a.score} <span className="font-normal muted">/ {a.maxScore}</span>
            </p>
            <p className="text-xs muted">{a.percentage}%</p>
          </div>
          {a.passed === true && <Badge tone="success">Passed</Badge>}
          {a.passed === false && <Badge tone="warning">Not passed</Badge>}
          <Link to={`/results/${a.attemptId}`} className="link text-sm">
            View
          </Link>
        </div>
      )}
    </li>
  );
}
