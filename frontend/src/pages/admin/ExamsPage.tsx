import { keepPreviousData, useQuery } from '@tanstack/react-query';
import { BookCopy, ChevronLeft, ChevronRight, Plus, Search } from 'lucide-react';
import { useEffect, useState } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import { errorMessage } from '@/api/client';
import { adminCatalogApi, adminExamApi } from '@/api/endpoints';
import type { ExamStatus } from '@/api/types';
import { ExamDetailsForm } from '@/components/admin/ExamDetailsForm';
import { PageHeader } from '@/components/PageHeader';
import { Button } from '@/components/ui/Button';
import { Alert, Badge, EmptyState, Spinner } from '@/components/ui/Feedback';
import { Modal } from '@/components/ui/Modal';

export function ExamStatusBadge({ status }: { status: ExamStatus }) {
  if (status === 'PUBLISHED') return <Badge tone="success">Published</Badge>;
  if (status === 'ARCHIVED') return <Badge>Archived</Badge>;
  return <Badge tone="warning">Draft</Badge>;
}

const selectClass = 'h-10 rounded-xl border border-slate-300 bg-white px-3 text-sm dark:border-gray-600 dark:bg-gray-800';

export default function ExamsPage() {
  const navigate = useNavigate();
  const [search, setSearch] = useState('');
  const [q, setQ] = useState('');
  const [categoryId, setCategoryId] = useState<number | ''>('');
  const [status, setStatus] = useState('');
  const [page, setPage] = useState(0);
  const [creating, setCreating] = useState(false);

  useEffect(() => {
    const t = setTimeout(() => {
      setQ(search.trim());
      setPage(0);
    }, 300);
    return () => clearTimeout(t);
  }, [search]);

  const categories = useQuery({ queryKey: ['admin', 'categories'], queryFn: adminCatalogApi.categories });
  const exams = useQuery({
    queryKey: ['admin', 'exams', q, categoryId, status, page],
    queryFn: () => adminExamApi.search({ q, categoryId, status, page, size: 20 }),
    placeholderData: keepPreviousData,
  });
  const data = exams.data;

  return (
    <div>
      <PageHeader
        title="Exams"
        description="Build mock tests from the question bank, schedule them, and publish when ready."
        actions={
          <Button onClick={() => setCreating(true)}>
            <Plus className="h-4 w-4" /> New exam
          </Button>
        }
      />

      <div className="mb-4 grid gap-3 sm:grid-cols-[1fr_auto_auto]">
        <label className="relative">
          <span className="sr-only">Search exams</span>
          <Search className="pointer-events-none absolute left-3 top-1/2 h-4 w-4 -translate-y-1/2 text-slate-400" />
          <input
            value={search}
            onChange={(e) => setSearch(e.target.value)}
            placeholder="Search by title"
            className="h-10 w-full rounded-xl border border-slate-300 bg-white pl-9 pr-3 text-sm focus:border-brand-500 focus:outline-none focus:ring-2 focus:ring-brand-500/30 dark:border-gray-600 dark:bg-gray-800"
          />
        </label>
        <select aria-label="Category" className={selectClass} value={categoryId} onChange={(e) => { setCategoryId(e.target.value ? Number(e.target.value) : ''); setPage(0); }}>
          <option value="">All categories</option>
          {categories.data?.map((c) => (
            <option key={c.id} value={c.id}>
              {c.name}
            </option>
          ))}
        </select>
        <select aria-label="Status" className={selectClass} value={status} onChange={(e) => { setStatus(e.target.value); setPage(0); }}>
          <option value="">Any status</option>
          <option value="DRAFT">Draft</option>
          <option value="PUBLISHED">Published</option>
        </select>
      </div>

      {exams.isError && <Alert tone="error">{errorMessage(exams.error)}</Alert>}
      {exams.isPending ? (
        <Spinner />
      ) : data && data.totalElements === 0 ? (
        <EmptyState icon={<BookCopy className="h-6 w-6" />} title="No exams yet">
          Create an exam, add approved questions from the question bank, then publish it.
        </EmptyState>
      ) : (
        <div className="card overflow-hidden">
          <div className="overflow-x-auto">
            <table className="w-full min-w-[760px] text-left text-sm">
              <thead className="border-b border-slate-200 bg-slate-50 text-xs uppercase tracking-wider text-slate-500 dark:border-gray-700 dark:bg-gray-800 dark:text-slate-400">
                <tr>
                  <th className="px-4 py-3 font-semibold">Exam</th>
                  <th className="px-4 py-3 font-semibold">Type</th>
                  <th className="px-4 py-3 font-semibold">Questions</th>
                  <th className="px-4 py-3 font-semibold">Duration</th>
                  <th className="px-4 py-3 font-semibold">Status</th>
                  <th className="px-4 py-3 font-semibold">Updated</th>
                </tr>
              </thead>
              <tbody className="divide-y divide-slate-100 dark:divide-gray-700/70">
                {data?.content.map((e) => (
                  <tr key={e.id} className="hover:bg-slate-50/70 dark:hover:bg-gray-800/40">
                    <td className="px-4 py-3">
                      <Link to={`/admin/exams/${e.id}`} className="font-semibold hover:text-brand-700 dark:hover:text-brand-300">
                        {e.title}
                      </Link>
                      <div className="text-xs muted">{e.category.name}</div>
                    </td>
                    <td className="px-4 py-3 capitalize">{e.type.toLowerCase()}</td>
                    <td className="px-4 py-3">
                      {e.questionCount} · {e.totalMarks} marks
                    </td>
                    <td className="px-4 py-3">{e.durationMinutes} min</td>
                    <td className="px-4 py-3">
                      <ExamStatusBadge status={e.status} />
                    </td>
                    <td className="px-4 py-3 text-xs muted">{new Date(e.updatedAt).toLocaleDateString()}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
          {data && data.totalPages > 1 && (
            <div className="flex items-center justify-between border-t border-slate-200 px-4 py-3 text-sm dark:border-gray-700">
              <span className="muted">
                Page {data.page + 1} of {data.totalPages}
              </span>
              <div className="flex gap-2">
                <Button size="sm" variant="secondary" disabled={page === 0} onClick={() => setPage((p) => p - 1)} aria-label="Previous page">
                  <ChevronLeft className="h-4 w-4" />
                </Button>
                <Button size="sm" variant="secondary" disabled={page + 1 >= data.totalPages} onClick={() => setPage((p) => p + 1)} aria-label="Next page">
                  <ChevronRight className="h-4 w-4" />
                </Button>
              </div>
            </div>
          )}
        </div>
      )}

      <Modal open={creating} title="New exam" onClose={() => setCreating(false)} size="xl">
        <ExamDetailsForm
          exam={null}
          categories={categories.data ?? []}
          submitLabel="Create & add questions"
          onSubmit={async (input) => {
            const created = await adminExamApi.create(input);
            navigate(`/admin/exams/${created.summary.id}`);
          }}
        />
      </Modal>
    </div>
  );
}
