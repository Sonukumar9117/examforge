import { keepPreviousData, useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { CheckCheck, ChevronLeft, ChevronRight, FileQuestion, Pencil, Plus, Search, Upload } from 'lucide-react';
import { useEffect, useState } from 'react';
import { errorMessage } from '@/api/client';
import { adminCatalogApi, adminQuestionApi } from '@/api/endpoints';
import type { Question, QuestionFilters } from '@/api/types';
import { DeleteQuestionButton, QuestionEditor } from '@/components/admin/QuestionEditor';
import { ImportQuestionsDialog } from '@/components/admin/ImportQuestionsDialog';
import { PageHeader } from '@/components/PageHeader';
import { Button } from '@/components/ui/Button';
import { Alert, Badge, EmptyState, Spinner } from '@/components/ui/Feedback';

const selectClass =
  'h-10 rounded-xl border border-slate-300 bg-white px-3 text-sm dark:border-gray-600 dark:bg-gray-800';

export const typeLabel = { MCQ: 'Single', MULTI_SELECT: 'Multiple', TRUE_FALSE: 'True/False' } as const;

export default function QuestionsPage() {
  const queryClient = useQueryClient();
  const [search, setSearch] = useState('');
  const [filters, setFilters] = useState<QuestionFilters>({ page: 0, size: 20 });
  const [editing, setEditing] = useState<Question | 'new' | null>(null);
  const [importing, setImporting] = useState(false);
  const [selected, setSelected] = useState<Set<number>>(new Set());
  const [notice, setNotice] = useState<{ tone: 'success' | 'error'; text: string } | null>(null);

  useEffect(() => {
    const t = setTimeout(() => setFilters((f) => ({ ...f, q: search.trim(), page: 0 })), 300);
    return () => clearTimeout(t);
  }, [search]);

  const subjects = useQuery({ queryKey: ['admin', 'subjects'], queryFn: adminCatalogApi.subjects });
  const questions = useQuery({
    queryKey: ['admin', 'questions', filters],
    queryFn: () => adminQuestionApi.search(filters),
    placeholderData: keepPreviousData,
  });

  const setFilter = (patch: Partial<QuestionFilters>) => {
    setSelected(new Set());
    setFilters((f) => ({ ...f, ...patch, page: 0 }));
  };

  const approve = useMutation({
    mutationFn: () => adminQuestionApi.bulkStatus([...selected], 'APPROVED'),
    onSuccess: (r) => {
      setNotice({ tone: 'success', text: r.message });
      setSelected(new Set());
      queryClient.invalidateQueries({ queryKey: ['admin', 'questions'] });
    },
    onError: (e) => setNotice({ tone: 'error', text: errorMessage(e) }),
  });

  const page = questions.data;
  const allOnPage = page?.content.map((q) => q.id) ?? [];
  const allSelected = allOnPage.length > 0 && allOnPage.every((id) => selected.has(id));
  const toggle = (id: number) =>
    setSelected((s) => {
      const n = new Set(s);
      if (n.has(id)) n.delete(id);
      else n.add(id);
      return n;
    });

  return (
    <div>
      <PageHeader
        title="Question bank"
        description="Reusable questions for all exams. Only approved questions can go into a published exam."
        actions={
          <>
            <Button variant="secondary" onClick={() => setImporting(true)}>
              <Upload className="h-4 w-4" /> Import
            </Button>
            <Button onClick={() => setEditing('new')}>
              <Plus className="h-4 w-4" /> New question
            </Button>
          </>
        }
      />

      <div className="mb-4 grid gap-3 sm:grid-cols-2 lg:grid-cols-[1fr_auto_auto_auto_auto]">
        <label className="relative">
          <span className="sr-only">Search questions</span>
          <Search className="pointer-events-none absolute left-3 top-1/2 h-4 w-4 -translate-y-1/2 text-slate-400" />
          <input
            value={search}
            onChange={(e) => setSearch(e.target.value)}
            placeholder="Search question text or topic"
            className="h-10 w-full rounded-xl border border-slate-300 bg-white pl-9 pr-3 text-sm focus:border-brand-500 focus:outline-none focus:ring-2 focus:ring-brand-500/30 dark:border-gray-600 dark:bg-gray-800"
          />
        </label>
        <select aria-label="Subject" className={selectClass} value={filters.subjectId ?? ''} onChange={(e) => setFilter({ subjectId: e.target.value ? Number(e.target.value) : '' })}>
          <option value="">All subjects</option>
          {subjects.data?.map((s) => (
            <option key={s.id} value={s.id}>
              {s.name}
            </option>
          ))}
        </select>
        <select aria-label="Difficulty" className={selectClass} value={filters.difficulty ?? ''} onChange={(e) => setFilter({ difficulty: e.target.value as QuestionFilters['difficulty'] })}>
          <option value="">Any difficulty</option>
          <option value="EASY">Easy</option>
          <option value="MEDIUM">Medium</option>
          <option value="HARD">Hard</option>
        </select>
        <select aria-label="Type" className={selectClass} value={filters.type ?? ''} onChange={(e) => setFilter({ type: e.target.value as QuestionFilters['type'] })}>
          <option value="">Any type</option>
          <option value="MCQ">Single answer</option>
          <option value="MULTI_SELECT">Multiple answers</option>
          <option value="TRUE_FALSE">True / False</option>
        </select>
        <select aria-label="Status" className={selectClass} value={filters.status ?? ''} onChange={(e) => setFilter({ status: e.target.value as QuestionFilters['status'] })}>
          <option value="">Any status</option>
          <option value="DRAFT">Draft</option>
          <option value="APPROVED">Approved</option>
        </select>
      </div>

      {notice && <Alert tone={notice.tone} className="mb-4">{notice.text}</Alert>}
      {questions.isError && <Alert tone="error" className="mb-4">{errorMessage(questions.error)}</Alert>}

      {selected.size > 0 && (
        <div className="card mb-3 flex flex-wrap items-center gap-3 px-4 py-3 text-sm">
          <span className="font-semibold">{selected.size} selected</span>
          <Button size="sm" loading={approve.isPending} onClick={() => approve.mutate()}>
            <CheckCheck className="h-4 w-4" /> Approve
          </Button>
          <Button size="sm" variant="ghost" onClick={() => setSelected(new Set())}>
            Clear
          </Button>
        </div>
      )}

      {questions.isPending ? (
        <Spinner />
      ) : page && page.totalElements === 0 ? (
        <EmptyState icon={<FileQuestion className="h-6 w-6" />} title="No questions found">
          Add a question or import a CSV/JSON file to fill the bank.
        </EmptyState>
      ) : (
        <div className="card overflow-hidden">
          <div className="overflow-x-auto">
            <table className="w-full min-w-[820px] text-left text-sm">
              <thead className="border-b border-slate-200 bg-slate-50 text-xs uppercase tracking-wider text-slate-500 dark:border-gray-700 dark:bg-gray-800 dark:text-slate-400">
                <tr>
                  <th className="w-10 px-4 py-3">
                    <input
                      type="checkbox"
                      aria-label="Select all on this page"
                      className="h-4 w-4 accent-brand-600"
                      checked={allSelected}
                      onChange={() => setSelected(allSelected ? new Set() : new Set(allOnPage))}
                    />
                  </th>
                  <th className="px-4 py-3 font-semibold">Question</th>
                  <th className="px-4 py-3 font-semibold">Subject</th>
                  <th className="px-4 py-3 font-semibold">Type</th>
                  <th className="px-4 py-3 font-semibold">Marks</th>
                  <th className="px-4 py-3 font-semibold">Status</th>
                  <th className="px-4 py-3 text-right font-semibold">Actions</th>
                </tr>
              </thead>
              <tbody className="divide-y divide-slate-100 dark:divide-gray-700/70">
                {page?.content.map((q) => (
                  <tr key={q.id} className="align-top hover:bg-slate-50/70 dark:hover:bg-gray-800/40">
                    <td className="px-4 py-3">
                      <input type="checkbox" aria-label={`Select question ${q.id}`} className="h-4 w-4 accent-brand-600" checked={selected.has(q.id)} onChange={() => toggle(q.id)} />
                    </td>
                    <td className="max-w-md px-4 py-3">
                      <p className="line-clamp-2 font-medium">{q.questionText}</p>
                      <p className="mt-1 text-xs muted">
                        #{q.id}
                        {q.topic && ` · ${q.topic}`}
                        {q.tags.length > 0 && ` · ${q.tags.join(', ')}`}
                      </p>
                    </td>
                    <td className="px-4 py-3">{q.subject.name}</td>
                    <td className="px-4 py-3">
                      <div>{typeLabel[q.type]}</div>
                      <div className="text-xs muted">{q.difficulty.toLowerCase()}</div>
                    </td>
                    <td className="whitespace-nowrap px-4 py-3 font-mono text-xs">
                      +{q.marks} / −{q.negativeMarks}
                    </td>
                    <td className="px-4 py-3">
                      {q.status === 'APPROVED' ? <Badge tone="success">Approved</Badge> : <Badge tone="warning">Draft</Badge>}
                    </td>
                    <td className="px-4 py-3">
                      <div className="flex justify-end gap-1">
                        <Button size="sm" variant="ghost" onClick={() => setEditing(q)} aria-label={`Edit question ${q.id}`}>
                          <Pencil className="h-4 w-4" />
                        </Button>
                        <DeleteQuestionButton id={q.id} onDone={() => queryClient.invalidateQueries({ queryKey: ['admin', 'questions'] })} />
                      </div>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
          {page && page.totalPages > 1 && (
            <div className="flex items-center justify-between border-t border-slate-200 px-4 py-3 text-sm dark:border-gray-700">
              <span className="muted">
                Page {page.page + 1} of {page.totalPages} · {page.totalElements} questions
              </span>
              <div className="flex gap-2">
                <Button size="sm" variant="secondary" disabled={page.page === 0} onClick={() => setFilters((f) => ({ ...f, page: (f.page ?? 0) - 1 }))} aria-label="Previous page">
                  <ChevronLeft className="h-4 w-4" />
                </Button>
                <Button size="sm" variant="secondary" disabled={page.page + 1 >= page.totalPages} onClick={() => setFilters((f) => ({ ...f, page: (f.page ?? 0) + 1 }))} aria-label="Next page">
                  <ChevronRight className="h-4 w-4" />
                </Button>
              </div>
            </div>
          )}
        </div>
      )}

      <QuestionEditor
        key={editing === 'new' ? 'new' : editing?.id ?? 'none'}
        question={editing}
        subjects={subjects.data ?? []}
        onClose={() => setEditing(null)}
      />
      <ImportQuestionsDialog open={importing} onClose={() => setImporting(false)} />
    </div>
  );
}
