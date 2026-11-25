import { keepPreviousData, useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import clsx from 'clsx';
import { BarChart3, Bookmark, Trash2 } from 'lucide-react';
import { useState } from 'react';
import { Link, useSearchParams } from 'react-router-dom';
import { errorMessage } from '@/api/client';
import { attemptApi, studentApi } from '@/api/endpoints';
import { AttemptRow } from '@/components/AttemptRow';
import { PageHeader } from '@/components/PageHeader';
import { Button } from '@/components/ui/Button';
import { Alert, EmptyState, Spinner } from '@/components/ui/Feedback';

export default function ResultsListPage() {
  const [params, setParams] = useSearchParams();
  const tab = params.get('tab') === 'saved' ? 'saved' : 'results';

  return (
    <div className="mx-auto max-w-4xl">
      <PageHeader title="Results" description="Your scores and the questions you saved for revision." />
      <div role="tablist" aria-label="Results sections" className="mb-5 inline-flex rounded-xl border border-slate-200 bg-white p-1 dark:border-gray-700 dark:bg-gray-800">
        {[
          ['results', 'Test results', BarChart3],
          ['saved', 'Saved questions', Bookmark],
        ].map(([key, label, Icon]) => {
          const I = Icon as typeof BarChart3;
          return (
            <button
              key={key as string}
              type="button"
              role="tab"
              aria-selected={tab === key}
              onClick={() => setParams(key === 'saved' ? { tab: 'saved' } : {}, { replace: true })}
              className={clsx(
                'flex items-center gap-1.5 rounded-lg px-3 py-1.5 text-sm font-semibold',
                tab === key ? 'bg-brand-600 text-white' : 'text-slate-600 hover:text-slate-900 dark:text-slate-300 dark:hover:text-white',
              )}
            >
              <I className="h-4 w-4" /> {label as string}
            </button>
          );
        })}
      </div>
      {tab === 'results' ? <FinishedResults /> : <SavedQuestions />}
    </div>
  );
}

function FinishedResults() {
  const [page, setPage] = useState(0);
  const attempts = useQuery({
    queryKey: ['student', 'attempts', page],
    queryFn: () => attemptApi.mine(page, 20),
    placeholderData: keepPreviousData,
  });
  const finished = attempts.data?.content.filter((a) => a.status !== 'IN_PROGRESS') ?? [];

  if (attempts.isPending) return <Spinner />;
  if (attempts.isError) return <Alert tone="error">{errorMessage(attempts.error)}</Alert>;
  if (attempts.data.totalElements === 0)
    return (
      <EmptyState icon={<BarChart3 className="h-6 w-6" />} title="No results yet">
        Finish a test to see your score here. <Link to="/exams" className="link">Explore exams</Link>
      </EmptyState>
    );
  return (
    <>
      <ul className="card divide-y divide-slate-100 px-5 dark:divide-gray-700/70">
        {finished.map((a) => (
          <AttemptRow key={a.attemptId} a={a} />
        ))}
        {finished.length === 0 && <li className="py-6 text-center text-sm muted">No finished tests on this page.</li>}
      </ul>
      {attempts.data.totalPages > 1 && (
        <div className="mt-4 flex items-center justify-center gap-3 text-sm">
          <Button size="sm" variant="secondary" disabled={page === 0} onClick={() => setPage((p) => p - 1)}>
            Previous
          </Button>
          <Button size="sm" variant="secondary" disabled={page + 1 >= attempts.data.totalPages} onClick={() => setPage((p) => p + 1)}>
            Next
          </Button>
        </div>
      )}
    </>
  );
}

function SavedQuestions() {
  const queryClient = useQueryClient();
  const [page, setPage] = useState(0);
  const saved = useQuery({
    queryKey: ['student', 'saved', page],
    queryFn: () => studentApi.savedQuestions(page, 20),
    placeholderData: keepPreviousData,
  });
  const remove = useMutation({
    mutationFn: (questionId: number) => studentApi.unsaveQuestion(questionId),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ['student', 'saved'] }),
  });

  if (saved.isPending) return <Spinner />;
  if (saved.isError) return <Alert tone="error">{errorMessage(saved.error)}</Alert>;
  if (saved.data.totalElements === 0)
    return (
      <EmptyState icon={<Bookmark className="h-6 w-6" />} title="No saved questions">
        Use “Save” on any question in a result review to collect it here for revision.
      </EmptyState>
    );

  return (
    <>
      <ol className="space-y-4">
        {saved.data.content.map((s) => (
          <li key={s.id} className="card p-5">
            <div className="flex items-center gap-2 text-xs muted">
              <span>
                {s.subject}
                {s.topic && ` · ${s.topic}`}
              </span>
              <Button
                size="sm"
                variant="ghost"
                className="ml-auto"
                loading={remove.isPending && remove.variables === s.questionId}
                onClick={() => remove.mutate(s.questionId)}
                aria-label="Remove from saved"
              >
                <Trash2 className="h-4 w-4" />
              </Button>
            </div>
            <p className="mt-1 whitespace-pre-line font-medium">{s.text}</p>
            <ul className="mt-3 space-y-1.5 text-sm">
              {s.options.map((o) => (
                <li
                  key={o.id}
                  className={clsx(
                    'rounded-lg border px-3 py-1.5',
                    o.correct ? 'border-emerald-500 bg-emerald-50 dark:bg-emerald-950/30' : 'border-slate-200 dark:border-gray-700',
                  )}
                >
                  <span className="font-bold">{o.label}.</span> {o.text}
                  {o.correct && <span className="ml-2 text-xs font-semibold text-emerald-700 dark:text-emerald-300">Correct</span>}
                </li>
              ))}
            </ul>
            {s.explanation && (
              <p className="mt-3 rounded-lg bg-slate-50 p-3 text-sm dark:bg-gray-900/60">
                <span className="font-semibold">Explanation: </span>
                {s.explanation}
              </p>
            )}
            {s.note && <p className="mt-2 text-sm italic muted">Your note: {s.note}</p>}
          </li>
        ))}
      </ol>
      {saved.data.totalPages > 1 && (
        <div className="mt-4 flex items-center justify-center gap-3 text-sm">
          <Button size="sm" variant="secondary" disabled={page === 0} onClick={() => setPage((p) => p - 1)}>
            Previous
          </Button>
          <Button size="sm" variant="secondary" disabled={page + 1 >= saved.data.totalPages} onClick={() => setPage((p) => p + 1)}>
            Next
          </Button>
        </div>
      )}
    </>
  );
}
