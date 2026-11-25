import clsx from 'clsx';
import type { AttemptQuestion } from '@/api/types';
import { statusOf, type LocalAnswer, type QuestionStatus } from './useExamSession';

export const STATUS_META: Record<QuestionStatus, { label: string; className: string }> = {
  'not-visited': {
    label: 'Not visited',
    className: 'border-slate-300 bg-white text-slate-700 dark:border-gray-600 dark:bg-gray-800 dark:text-slate-200',
  },
  'not-answered': { label: 'Not answered', className: 'border-red-600 bg-red-600 text-white' },
  answered: { label: 'Answered', className: 'border-emerald-600 bg-emerald-600 text-white' },
  marked: { label: 'Marked for review', className: 'border-violet-600 bg-violet-600 text-white' },
  'answered-marked': {
    label: 'Answered & marked for review',
    className: 'border-violet-600 bg-violet-600 text-white ring-2 ring-inset ring-emerald-400',
  },
};

export function PaletteLegend({ counts }: { counts: Record<QuestionStatus, number> }) {
  return (
    <ul className="grid grid-cols-2 gap-x-3 gap-y-2 text-xs">
      {(Object.keys(STATUS_META) as QuestionStatus[]).map((s) => (
        <li key={s} className="flex items-center gap-2">
          <span className={clsx('flex h-6 w-6 shrink-0 items-center justify-center rounded-md border text-[11px] font-bold', STATUS_META[s].className)}>
            {counts[s]}
          </span>
          <span className="leading-tight">{STATUS_META[s].label}</span>
        </li>
      ))}
    </ul>
  );
}

export function QuestionPalette({
  questions,
  answers,
  current,
  onJump,
  locked,
}: {
  questions: AttemptQuestion[];
  answers: Record<number, LocalAnswer>;
  current: number;
  onJump: (index: number) => void;
  /** sequential exams: only the current and earlier-visited questions are reachable */
  locked?: boolean;
}) {
  return (
    <ol className="grid grid-cols-5 gap-2 sm:grid-cols-6 lg:grid-cols-5" aria-label="Question palette">
      {questions.map((q, i) => {
        const status = statusOf(answers[q.questionId]);
        return (
          <li key={q.questionId}>
            <button
              type="button"
              onClick={() => onJump(i)}
              disabled={locked && i !== current}
              aria-current={i === current ? 'step' : undefined}
              aria-label={`Question ${q.number}: ${STATUS_META[status].label}`}
              className={clsx(
                'flex h-10 w-full items-center justify-center rounded-lg border text-sm font-bold transition-transform disabled:cursor-not-allowed disabled:opacity-60',
                STATUS_META[status].className,
                i === current && 'scale-105 outline outline-2 outline-offset-2 outline-brand-500',
              )}
            >
              {q.number}
            </button>
          </li>
        );
      })}
    </ol>
  );
}
