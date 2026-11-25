import { keepPreviousData, useMutation, useQuery } from '@tanstack/react-query';
import { BarChart3, Clock3, Download, Percent, Target, Trophy, Users } from 'lucide-react';
import { useState } from 'react';
import { Link, useSearchParams } from 'react-router-dom';
import { errorMessage } from '@/api/client';
import { adminExamApi, adminReportApi } from '@/api/endpoints';
import { DistributionChart } from '@/components/charts/DistributionChart';
import { PageHeader, StatCard } from '@/components/PageHeader';
import { Button } from '@/components/ui/Button';
import { Alert, Badge, EmptyState, Spinner } from '@/components/ui/Feedback';
import { formatDuration } from '@/pages/student/ResultPage';

/** Per-exam analytics: averages, pass rate, score spread, hardest questions, subjects, attempts + CSV export. */
export default function ReportsPage() {
  const [params, setParams] = useSearchParams();
  const exams = useQuery({ queryKey: ['admin', 'exams', 'for-reports'], queryFn: () => adminExamApi.search({ size: 100 }) });
  const examId = Number(params.get('exam')) || exams.data?.content.find((e) => e.status === 'PUBLISHED')?.id || exams.data?.content[0]?.id;

  return (
    <div className="space-y-6">
      <PageHeader title="Reports" description="How students performed on each exam. Export every attempt as CSV." />
      {exams.isPending ? (
        <Spinner />
      ) : !examId ? (
        <EmptyState icon={<BarChart3 className="h-6 w-6" />} title="No exams yet">
          Create and publish an exam; its report appears here once students take it.
        </EmptyState>
      ) : (
        <>
          <label className="block max-w-xl">
            <span className="mb-1.5 block text-sm font-medium text-slate-700 dark:text-slate-300">Exam</span>
            <select
              value={examId}
              onChange={(e) => setParams({ exam: e.target.value }, { replace: true })}
              className="h-10 w-full rounded-xl border border-slate-300 bg-white px-3 text-sm dark:border-gray-600 dark:bg-gray-800"
            >
              {exams.data?.content.map((e) => (
                <option key={e.id} value={e.id}>
                  {e.title} {e.status !== 'PUBLISHED' ? `(${e.status.toLowerCase()})` : ''}
                </option>
              ))}
            </select>
          </label>
          <ExamReportView examId={examId} />
        </>
      )}
    </div>
  );
}

function ExamReportView({ examId }: { examId: number }) {
  const report = useQuery({ queryKey: ['admin', 'report', examId], queryFn: () => adminReportApi.exam(examId) });
  const [page, setPage] = useState(0);
  const attempts = useQuery({
    queryKey: ['admin', 'report', examId, 'attempts', page],
    queryFn: () => adminReportApi.attempts(examId, page),
    placeholderData: keepPreviousData,
  });
  const exportCsv = useMutation({ mutationFn: () => adminReportApi.exportCsv(examId) });

  if (report.isPending) return <Spinner />;
  if (report.isError) return <Alert tone="error">{errorMessage(report.error)}</Alert>;
  const r = report.data;
  const hardest = [...r.questions].filter((q) => q.correct + q.incorrect > 0).sort((a, b) => a.accuracy - b.accuracy);

  return (
    <div className="space-y-6">
      <div className="flex flex-wrap items-center justify-between gap-3">
        <p className="text-sm muted">
          {r.exam.category.name} · {r.exam.type.toLowerCase()} · {r.exam.questionCount} questions · {r.exam.totalMarks} marks ·{' '}
          <Link to={`/admin/exams/${r.exam.id}`} className="link">open exam</Link>
          {r.exam.status === 'PUBLISHED' && (
            <>
              {' · '}
              <Link to={`/leaderboard?exam=${r.exam.slug}`} className="link">leaderboard</Link>
            </>
          )}
        </p>
        <Button variant="secondary" loading={exportCsv.isPending} onClick={() => exportCsv.mutate()} disabled={r.totalAttempts === 0}>
          <Download className="h-4 w-4" /> Export CSV
        </Button>
      </div>
      {exportCsv.isError && <Alert tone="error">{errorMessage(exportCsv.error)}</Alert>}

      <section className="grid grid-cols-2 gap-4 lg:grid-cols-4">
        <StatCard label="Students" value={r.uniqueStudents} icon={<Users className="h-4 w-4" />} hint={`${r.finishedAttempts} finished · ${r.inProgressAttempts} in progress`} />
        <StatCard label="Average score" value={r.averageScore ?? '—'} icon={<Target className="h-4 w-4" />} hint={r.averagePercentage != null ? `${r.averagePercentage}% · high ${r.highestScore} · low ${r.lowestScore}` : 'No results yet'} />
        <StatCard label="Pass rate" value={r.passRate != null ? `${r.passRate}%` : '—'} icon={<Percent className="h-4 w-4" />} hint={r.exam.passMarks != null ? `Pass mark ${r.exam.passMarks}` : 'No pass mark set'} />
        <StatCard label="Average time" value={r.averageTimeSeconds != null ? formatDuration(r.averageTimeSeconds) : '—'} icon={<Clock3 className="h-4 w-4" />} hint={`Limit ${r.exam.durationMinutes} min`} />
      </section>

      {r.finishedAttempts === 0 ? (
        <EmptyState icon={<Trophy className="h-6 w-6" />} title="No finished attempts yet">
          Analytics appear once students submit this exam.
        </EmptyState>
      ) : (
        <>
          <div className="grid gap-6 lg:grid-cols-5">
            <section className="card p-5 lg:col-span-3">
              <h2 className="font-bold">Score distribution</h2>
              <p className="mb-3 text-xs muted">Students per 10% score band (negative scores count in 0–10%)</p>
              <DistributionChart buckets={r.scoreDistribution} />
            </section>
            <section className="card p-5 lg:col-span-2">
              <h2 className="font-bold">Subjects</h2>
              <p className="mb-3 text-xs muted">Accuracy = correct ÷ attempted</p>
              <ul className="space-y-3">
                {r.subjects.map((s) => (
                  <li key={s.subject}>
                    <div className="flex justify-between text-sm">
                      <span className="font-semibold">{s.subject}</span>
                      <span className="muted">{s.correct + s.incorrect > 0 ? `${s.accuracy}%` : 'not attempted'}</span>
                    </div>
                    <div className="mt-1 h-2 overflow-hidden rounded-full bg-slate-100 dark:bg-gray-700">
                      <div className="h-full rounded-full bg-brand-600 dark:bg-brand-400" style={{ width: `${s.accuracy}%` }} />
                    </div>
                    <p className="mt-0.5 text-xs muted">
                      {s.correct} correct · {s.incorrect} wrong · {s.unanswered} skipped
                    </p>
                  </li>
                ))}
              </ul>
            </section>
          </div>

          <section className="card overflow-hidden">
            <div className="p-5 pb-2">
              <h2 className="font-bold">Questions, hardest first</h2>
              <p className="text-xs muted">Low accuracy can mean a hard question — or a mistake in the answer key worth checking.</p>
            </div>
            <div className="overflow-x-auto">
              <table className="w-full min-w-[720px] text-left text-sm">
                <thead className="border-y border-slate-200 bg-slate-50 text-xs uppercase tracking-wider text-slate-500 dark:border-gray-700 dark:bg-gray-800 dark:text-slate-400">
                  <tr>
                    <th className="px-4 py-2 font-semibold">#</th>
                    <th className="px-4 py-2 font-semibold">Question</th>
                    <th className="px-4 py-2 font-semibold">Subject</th>
                    <th className="px-4 py-2 text-right font-semibold">Correct</th>
                    <th className="px-4 py-2 text-right font-semibold">Wrong</th>
                    <th className="px-4 py-2 text-right font-semibold">Skipped</th>
                    <th className="px-4 py-2 text-right font-semibold">Accuracy</th>
                  </tr>
                </thead>
                <tbody className="divide-y divide-slate-100 dark:divide-gray-700/70">
                  {[...hardest, ...r.questions.filter((q) => !hardest.includes(q))].map((q) => (
                    <tr key={q.questionId}>
                      <td className="px-4 py-2 muted">{q.number}</td>
                      <td className="max-w-md px-4 py-2">
                        <span className="line-clamp-2">{q.text}</span>
                      </td>
                      <td className="px-4 py-2 text-xs">
                        {q.subject} · {q.difficulty.toLowerCase()}
                      </td>
                      <td className="px-4 py-2 text-right">{q.correct}</td>
                      <td className="px-4 py-2 text-right">{q.incorrect}</td>
                      <td className="px-4 py-2 text-right">{q.unanswered}</td>
                      <td className="px-4 py-2 text-right font-semibold">{q.correct + q.incorrect > 0 ? `${q.accuracy}%` : '—'}</td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          </section>
        </>
      )}

      <section className="card overflow-hidden">
        <div className="p-5 pb-2">
          <h2 className="font-bold">Attempts</h2>
        </div>
        {attempts.isPending ? (
          <Spinner className="m-5" />
        ) : (
          <>
            <div className="overflow-x-auto">
              <table className="w-full min-w-[760px] text-left text-sm">
                <thead className="border-y border-slate-200 bg-slate-50 text-xs uppercase tracking-wider text-slate-500 dark:border-gray-700 dark:bg-gray-800 dark:text-slate-400">
                  <tr>
                    <th className="px-4 py-2 font-semibold">Student</th>
                    <th className="px-4 py-2 font-semibold">Started</th>
                    <th className="px-4 py-2 font-semibold">Status</th>
                    <th className="px-4 py-2 text-right font-semibold">Score</th>
                    <th className="px-4 py-2 text-right font-semibold">Time</th>
                    <th className="px-4 py-2 text-right font-semibold">Result</th>
                  </tr>
                </thead>
                <tbody className="divide-y divide-slate-100 dark:divide-gray-700/70">
                  {attempts.data?.content.length === 0 && (
                    <tr>
                      <td colSpan={6} className="px-4 py-6 text-center muted">No attempts yet.</td>
                    </tr>
                  )}
                  {attempts.data?.content.map((a) => (
                    <tr key={a.attemptId}>
                      <td className="px-4 py-2">
                        <div className="font-semibold">{a.fullName}</div>
                        <div className="text-xs muted">@{a.username} · attempt {a.attemptNo}</div>
                      </td>
                      <td className="px-4 py-2 text-xs">{new Date(a.startedAt).toLocaleString(undefined, { dateStyle: 'medium', timeStyle: 'short' })}</td>
                      <td className="px-4 py-2">
                        {a.status === 'IN_PROGRESS' ? <Badge>In progress</Badge> : a.passed === true ? <Badge tone="success">Passed</Badge> : a.passed === false ? <Badge tone="warning">Not passed</Badge> : <Badge>Submitted</Badge>}
                      </td>
                      <td className="px-4 py-2 text-right font-mono">{a.score != null ? `${a.score} / ${a.maxScore}` : '—'}</td>
                      <td className="px-4 py-2 text-right">{a.timeTakenSeconds != null ? formatDuration(a.timeTakenSeconds) : '—'}</td>
                      <td className="px-4 py-2 text-right">
                        {a.status !== 'IN_PROGRESS' && (
                          <Link to={`/results/${a.attemptId}`} className="link text-sm">
                            Open
                          </Link>
                        )}
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
            {attempts.data && attempts.data.totalPages > 1 && (
              <div className="flex justify-end gap-2 border-t border-slate-200 px-4 py-3 dark:border-gray-700">
                <Button size="sm" variant="secondary" disabled={page === 0} onClick={() => setPage((p) => p - 1)}>
                  Previous
                </Button>
                <Button size="sm" variant="secondary" disabled={page + 1 >= attempts.data.totalPages} onClick={() => setPage((p) => p + 1)}>
                  Next
                </Button>
              </div>
            )}
          </>
        )}
      </section>
    </div>
  );
}
