import { keepPreviousData, useQuery } from '@tanstack/react-query';
import { ClipboardList } from 'lucide-react';
import { useState } from 'react';
import { Link } from 'react-router-dom';
import { errorMessage } from '@/api/client';
import { attemptApi } from '@/api/endpoints';
import { AttemptRow } from '@/components/AttemptRow';
import { PageHeader } from '@/components/PageHeader';
import { Button } from '@/components/ui/Button';
import { Alert, EmptyState, Spinner } from '@/components/ui/Feedback';

/** Every attempt: continue the ones in progress, open results of finished ones. */
export default function MyTestsPage() {
  const [page, setPage] = useState(0);
  const attempts = useQuery({
    queryKey: ['student', 'attempts', page],
    queryFn: () => attemptApi.mine(page, 20),
    placeholderData: keepPreviousData,
  });
  const data = attempts.data;
  const inProgress = data?.content.filter((a) => a.status === 'IN_PROGRESS' && new Date(a.deadlineAt) > new Date()) ?? [];
  const rest = data?.content.filter((a) => !inProgress.includes(a)) ?? [];

  return (
    <div className="mx-auto max-w-4xl">
      <PageHeader title="My tests" description="Continue a test in progress or look back at the ones you finished." />
      {attempts.isError && <Alert tone="error">{errorMessage(attempts.error)}</Alert>}
      {attempts.isPending ? (
        <Spinner />
      ) : data && data.totalElements === 0 ? (
        <EmptyState icon={<ClipboardList className="h-6 w-6" />} title="No tests yet">
          <Link to="/exams" className="link">Explore exams</Link> and take your first mock test.
        </EmptyState>
      ) : (
        <div className="space-y-6">
          {inProgress.length > 0 && (
            <section className="card border-brand-200 p-5 dark:border-brand-900">
              <h2 className="text-lg font-bold">In progress</h2>
              <p className="text-sm muted">The timer keeps running — finish before it ends.</p>
              <ul className="divide-y divide-slate-100 dark:divide-gray-700/70">
                {inProgress.map((a) => (
                  <AttemptRow key={a.attemptId} a={a} />
                ))}
              </ul>
            </section>
          )}
          {rest.length > 0 && (
            <section className="card p-5">
              <h2 className="text-lg font-bold">History</h2>
              <ul className="divide-y divide-slate-100 dark:divide-gray-700/70">
                {rest.map((a) => (
                  <AttemptRow key={a.attemptId} a={a} />
                ))}
              </ul>
            </section>
          )}
          {data && data.totalPages > 1 && (
            <div className="flex items-center justify-center gap-3 text-sm">
              <Button size="sm" variant="secondary" disabled={page === 0} onClick={() => setPage((p) => p - 1)}>
                Previous
              </Button>
              <span className="muted">
                Page {page + 1} of {data.totalPages}
              </span>
              <Button size="sm" variant="secondary" disabled={page + 1 >= data.totalPages} onClick={() => setPage((p) => p + 1)}>
                Next
              </Button>
            </div>
          )}
        </div>
      )}
    </div>
  );
}
