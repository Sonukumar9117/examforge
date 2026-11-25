import { keepPreviousData, useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { ArrowDown, ArrowLeft, ArrowUp, Eye, Plus, Rocket, Search, Trash2, Undo2, X } from 'lucide-react';
import { useEffect, useState } from 'react';
import { Link, useNavigate, useParams } from 'react-router-dom';
import { errorMessage } from '@/api/client';
import { adminCatalogApi, adminExamApi, adminQuestionApi, adminResultApi } from '@/api/endpoints';
import type { ExamDetail, Question } from '@/api/types';
import { ExamDetailsForm } from '@/components/admin/ExamDetailsForm';
import { Button } from '@/components/ui/Button';
import { Alert, Badge, Spinner } from '@/components/ui/Feedback';
import { Modal } from '@/components/ui/Modal';
import { ExamStatusBadge } from './ExamsPage';

type Row = { question: Question; marks: number; negativeMarks: number };

const rowsFrom = (exam: ExamDetail): Row[] =>
  exam.questions.map((q) => ({ question: q.question, marks: q.marks, negativeMarks: q.negativeMarks }));

export default function ExamEditorPage() {
  const id = Number(useParams().id);
  const navigate = useNavigate();
  const queryClient = useQueryClient();
  const exam = useQuery({ queryKey: ['admin', 'exam', id], queryFn: () => adminExamApi.get(id) });
  const categories = useQuery({ queryKey: ['admin', 'categories'], queryFn: adminCatalogApi.categories });

  const [rows, setRows] = useState<Row[]>([]);
  const [dirty, setDirty] = useState(false);
  const [picking, setPicking] = useState(false);
  const [message, setMessage] = useState<{ tone: 'success' | 'error'; text: string } | null>(null);
  const [confirmDelete, setConfirmDelete] = useState(false);

  useEffect(() => {
    if (exam.data && !dirty) setRows(rowsFrom(exam.data));
  }, [exam.data, dirty]);

  const refresh = (detail: ExamDetail) => {
    queryClient.setQueryData(['admin', 'exam', id], detail);
    queryClient.invalidateQueries({ queryKey: ['admin', 'exams'] });
    setRows(rowsFrom(detail));
    setDirty(false);
  };

  const saveQuestions = useMutation({
    mutationFn: () =>
      adminExamApi.setQuestions(
        id,
        rows.map((r) => ({ questionId: r.question.id, marks: r.marks, negativeMarks: r.negativeMarks })),
      ),
    onSuccess: (d) => {
      refresh(d);
      setMessage({ tone: 'success', text: 'Questions saved.' });
    },
    onError: (e) => setMessage({ tone: 'error', text: errorMessage(e) }),
  });
  const publish = useMutation({
    mutationFn: () => adminExamApi.publish(id),
    onSuccess: (d) => {
      refresh(d);
      setMessage({ tone: 'success', text: 'Exam published — students can now see it.' });
    },
    onError: (e) => setMessage({ tone: 'error', text: errorMessage(e) }),
  });
  const unpublish = useMutation({
    mutationFn: () => adminExamApi.unpublish(id),
    onSuccess: (d) => {
      refresh(d);
      setMessage({ tone: 'success', text: 'Exam moved back to draft. You can edit it now.' });
    },
    onError: (e) => setMessage({ tone: 'error', text: errorMessage(e) }),
  });
  const release = useMutation({
    mutationFn: () => adminResultApi.releaseResults(id),
    onSuccess: (d) => {
      refresh(d);
      setMessage({ tone: 'success', text: 'Answers released. Students can now review their results.' });
    },
    onError: (e) => setMessage({ tone: 'error', text: errorMessage(e) }),
  });
  const remove = useMutation({
    mutationFn: () => adminExamApi.remove(id),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['admin', 'exams'] });
      navigate('/admin/exams', { replace: true });
    },
    onError: (e) => {
      setConfirmDelete(false);
      setMessage({ tone: 'error', text: errorMessage(e) });
    },
  });

  if (exam.isPending) return <Spinner />;
  if (exam.isError) return <Alert tone="error">{errorMessage(exam.error)}</Alert>;
  const detail = exam.data;
  const s = detail.summary;
  const published = s.status === 'PUBLISHED';
  const total = rows.reduce((sum, r) => sum + Number(r.marks || 0), 0);
  const drafts = rows.filter((r) => r.question.status !== 'APPROVED').length;

  const update = (i: number, patch: Partial<Row>) => {
    setRows((rs) => rs.map((r, idx) => (idx === i ? { ...r, ...patch } : r)));
    setDirty(true);
  };
  const move = (i: number, d: -1 | 1) => {
    setRows((rs) => {
      const next = [...rs];
      [next[i], next[i + d]] = [next[i + d], next[i]];
      return next;
    });
    setDirty(true);
  };

  return (
    <div className="space-y-6">
      <div>
        <Link to="/admin/exams" className="inline-flex items-center gap-1 text-sm muted hover:text-slate-900 dark:hover:text-white">
          <ArrowLeft className="h-4 w-4" /> All exams
        </Link>
        <div className="mt-2 flex flex-col gap-3 sm:flex-row sm:items-center sm:justify-between">
          <div>
            <h1 className="text-2xl font-extrabold sm:text-3xl">{s.title}</h1>
            <div className="mt-1 flex flex-wrap items-center gap-2 text-sm muted">
              <ExamStatusBadge status={s.status} />
              <span>{s.category.name}</span>·<span>{s.questionCount} questions</span>·<span>{s.totalMarks} marks</span>·
              <span>{s.durationMinutes} min</span>
            </div>
          </div>
          <div className="flex flex-wrap gap-2">
            {detail.resultRelease === 'MANUAL' && (
              <Button
                variant="secondary"
                disabled={!!detail.resultsReleasedAt}
                loading={release.isPending}
                onClick={() => release.mutate()}
                title="Let students see correct answers and explanations"
              >
                <Eye className="h-4 w-4" /> {detail.resultsReleasedAt ? 'Answers released' : 'Release answers'}
              </Button>
            )}
            {published ? (
              <Button variant="secondary" loading={unpublish.isPending} onClick={() => unpublish.mutate()}>
                <Undo2 className="h-4 w-4" /> Unpublish
              </Button>
            ) : (
              <>
                {confirmDelete ? (
                  <Button variant="danger" loading={remove.isPending} onClick={() => remove.mutate()}>
                    Confirm delete
                  </Button>
                ) : (
                  <Button variant="ghost" onClick={() => setConfirmDelete(true)}>
                    <Trash2 className="h-4 w-4" /> Delete
                  </Button>
                )}
                <Button loading={publish.isPending} disabled={dirty} title={dirty ? 'Save questions first' : undefined} onClick={() => publish.mutate()}>
                  <Rocket className="h-4 w-4" /> Publish
                </Button>
              </>
            )}
          </div>
        </div>
      </div>

      {message && <Alert tone={message.tone}>{message.text}</Alert>}
      {published && <Alert tone="info">This exam is published, so it is read-only. Unpublish it to make changes.</Alert>}

      {/* Questions */}
      <section className="card p-5 sm:p-6">
        <div className="mb-4 flex flex-wrap items-center justify-between gap-3">
          <div>
            <h2 className="text-lg font-bold">Questions</h2>
            <p className="text-sm muted">
              {rows.length} questions · {total} total marks
              {drafts > 0 && <span className="text-amber-600 dark:text-amber-400"> · {drafts} not approved yet</span>}
            </p>
          </div>
          {!published && (
            <div className="flex gap-2">
              <Button variant="secondary" onClick={() => setPicking(true)}>
                <Plus className="h-4 w-4" /> Add questions
              </Button>
              <Button disabled={!dirty} loading={saveQuestions.isPending} onClick={() => saveQuestions.mutate()}>
                Save questions
              </Button>
            </div>
          )}
        </div>

        {rows.length === 0 ? (
          <p className="rounded-xl border border-dashed border-slate-300 p-6 text-center text-sm muted dark:border-gray-600">
            No questions yet. Use “Add questions” to pick from the question bank.
          </p>
        ) : (
          <ol className="divide-y divide-slate-100 dark:divide-gray-700/70">
            {rows.map((r, i) => (
              <li key={r.question.id} className="flex flex-col gap-3 py-3 sm:flex-row sm:items-center">
                <span className="flex h-8 w-8 shrink-0 items-center justify-center rounded-lg bg-slate-100 text-sm font-bold dark:bg-gray-700">{i + 1}</span>
                <div className="min-w-0 flex-1">
                  <p className="line-clamp-2 text-sm font-medium">{r.question.questionText}</p>
                  <p className="mt-0.5 text-xs muted">
                    {r.question.subject.name} · {r.question.difficulty.toLowerCase()}
                    {r.question.status !== 'APPROVED' && (
                      <>
                        {' '}
                        · <Badge tone="warning">Draft</Badge>
                      </>
                    )}
                  </p>
                </div>
                <div className="flex items-center gap-2">
                  <label className="text-xs muted">
                    +
                    <input
                      type="number"
                      step="0.25"
                      min={0}
                      disabled={published}
                      aria-label={`Marks for question ${i + 1}`}
                      value={r.marks}
                      onChange={(e) => update(i, { marks: Number(e.target.value) })}
                      className="ml-1 h-8 w-16 rounded-lg border border-slate-300 bg-white px-2 text-sm dark:border-gray-600 dark:bg-gray-900/60"
                    />
                  </label>
                  <label className="text-xs muted">
                    −
                    <input
                      type="number"
                      step="0.01"
                      min={0}
                      disabled={published}
                      aria-label={`Negative marks for question ${i + 1}`}
                      value={r.negativeMarks}
                      onChange={(e) => update(i, { negativeMarks: Number(e.target.value) })}
                      className="ml-1 h-8 w-16 rounded-lg border border-slate-300 bg-white px-2 text-sm dark:border-gray-600 dark:bg-gray-900/60"
                    />
                  </label>
                  {!published && (
                    <div className="flex">
                      <Button size="sm" variant="ghost" disabled={i === 0} onClick={() => move(i, -1)} aria-label="Move up">
                        <ArrowUp className="h-4 w-4" />
                      </Button>
                      <Button size="sm" variant="ghost" disabled={i === rows.length - 1} onClick={() => move(i, 1)} aria-label="Move down">
                        <ArrowDown className="h-4 w-4" />
                      </Button>
                      <Button
                        size="sm"
                        variant="ghost"
                        onClick={() => {
                          setRows((rs) => rs.filter((_, idx) => idx !== i));
                          setDirty(true);
                        }}
                        aria-label="Remove from exam"
                      >
                        <X className="h-4 w-4" />
                      </Button>
                    </div>
                  )}
                </div>
              </li>
            ))}
          </ol>
        )}
      </section>

      {/* Details */}
      <section className="card p-5 sm:p-6">
        <h2 className="mb-4 text-lg font-bold">Exam details</h2>
        <ExamDetailsForm
          key={detail.summary.updatedAt}
          exam={detail}
          categories={categories.data ?? []}
          locked={published}
          submitLabel="Save details"
          onSubmit={async (input) => refresh(await adminExamApi.update(id, input))}
        />
      </section>

      <QuestionPicker
        open={picking}
        excluded={new Set(rows.map((r) => r.question.id))}
        onClose={() => setPicking(false)}
        onAdd={(qs) => {
          setRows((rs) => [...rs, ...qs.map((q) => ({ question: q, marks: q.marks, negativeMarks: q.negativeMarks }))]);
          setDirty(true);
          setPicking(false);
        }}
      />
    </div>
  );
}

function QuestionPicker({
  open,
  excluded,
  onClose,
  onAdd,
}: {
  open: boolean;
  excluded: Set<number>;
  onClose: () => void;
  onAdd: (qs: Question[]) => void;
}) {
  const [search, setSearch] = useState('');
  const [q, setQ] = useState('');
  const [subjectId, setSubjectId] = useState<number | ''>('');
  const [status, setStatus] = useState<'APPROVED' | ''>('APPROVED');
  const [page, setPage] = useState(0);
  const [chosen, setChosen] = useState<Map<number, Question>>(new Map());

  useEffect(() => {
    const t = setTimeout(() => {
      setQ(search.trim());
      setPage(0);
    }, 300);
    return () => clearTimeout(t);
  }, [search]);

  const subjects = useQuery({ queryKey: ['admin', 'subjects'], queryFn: adminCatalogApi.subjects, enabled: open });
  const results = useQuery({
    queryKey: ['admin', 'questions', 'picker', q, subjectId, status, page],
    queryFn: () => adminQuestionApi.search({ q, subjectId, status, page, size: 15 }),
    enabled: open,
    placeholderData: keepPreviousData,
  });

  const toggle = (question: Question) =>
    setChosen((m) => {
      const n = new Map(m);
      if (n.has(question.id)) n.delete(question.id);
      else n.set(question.id, question);
      return n;
    });

  const close = () => {
    setChosen(new Map());
    onClose();
  };

  return (
    <Modal
      open={open}
      title="Add questions from the bank"
      onClose={close}
      size="xl"
      footer={
        <>
          <Button variant="secondary" onClick={close}>
            Cancel
          </Button>
          <Button
            disabled={chosen.size === 0}
            onClick={() => {
              onAdd([...chosen.values()]);
              setChosen(new Map());
            }}
          >
            Add {chosen.size || ''} question{chosen.size === 1 ? '' : 's'}
          </Button>
        </>
      }
    >
      <div className="mb-3 grid gap-2 sm:grid-cols-[1fr_auto_auto]">
        <label className="relative">
          <span className="sr-only">Search</span>
          <Search className="pointer-events-none absolute left-3 top-1/2 h-4 w-4 -translate-y-1/2 text-slate-400" />
          <input
            value={search}
            onChange={(e) => setSearch(e.target.value)}
            placeholder="Search questions"
            className="h-10 w-full rounded-xl border border-slate-300 bg-white pl-9 pr-3 text-sm dark:border-gray-600 dark:bg-gray-900/60"
          />
        </label>
        <select aria-label="Subject" value={subjectId} onChange={(e) => { setSubjectId(e.target.value ? Number(e.target.value) : ''); setPage(0); }} className="h-10 rounded-xl border border-slate-300 bg-white px-3 text-sm dark:border-gray-600 dark:bg-gray-900/60">
          <option value="">All subjects</option>
          {subjects.data?.map((s) => (
            <option key={s.id} value={s.id}>
              {s.name}
            </option>
          ))}
        </select>
        <select aria-label="Status" value={status} onChange={(e) => { setStatus(e.target.value as 'APPROVED' | ''); setPage(0); }} className="h-10 rounded-xl border border-slate-300 bg-white px-3 text-sm dark:border-gray-600 dark:bg-gray-900/60">
          <option value="APPROVED">Approved only</option>
          <option value="">Include drafts</option>
        </select>
      </div>

      {results.isPending ? (
        <Spinner />
      ) : (
        <ul className="divide-y divide-slate-100 rounded-xl border border-slate-200 dark:divide-gray-700 dark:border-gray-700">
          {results.data?.content.length === 0 && <li className="p-4 text-center text-sm muted">No matching questions.</li>}
          {results.data?.content.map((question) => {
            const already = excluded.has(question.id);
            return (
              <li key={question.id}>
                <label className={`flex items-start gap-3 p-3 ${already ? 'opacity-50' : 'cursor-pointer hover:bg-slate-50 dark:hover:bg-gray-700/40'}`}>
                  <input
                    type="checkbox"
                    className="mt-1 h-4 w-4 accent-brand-600"
                    disabled={already}
                    checked={already || chosen.has(question.id)}
                    onChange={() => toggle(question)}
                  />
                  <span className="min-w-0 flex-1">
                    <span className="line-clamp-2 text-sm font-medium">{question.questionText}</span>
                    <span className="mt-0.5 block text-xs muted">
                      #{question.id} · {question.subject.name} · {question.difficulty.toLowerCase()} · +{question.marks}/−{question.negativeMarks}
                      {already && ' · already in exam'}
                      {question.status !== 'APPROVED' && ' · draft'}
                    </span>
                  </span>
                </label>
              </li>
            );
          })}
        </ul>
      )}
      {results.data && results.data.totalPages > 1 && (
        <div className="mt-3 flex items-center justify-between text-sm">
          <span className="muted">
            Page {results.data.page + 1} of {results.data.totalPages}
          </span>
          <div className="flex gap-2">
            <Button size="sm" variant="secondary" disabled={page === 0} onClick={() => setPage((p) => p - 1)}>
              Previous
            </Button>
            <Button size="sm" variant="secondary" disabled={page + 1 >= results.data.totalPages} onClick={() => setPage((p) => p + 1)}>
              Next
            </Button>
          </div>
        </div>
      )}
    </Modal>
  );
}
