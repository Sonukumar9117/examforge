import { useQuery, useQueryClient } from '@tanstack/react-query';
import clsx from 'clsx';
import {
  AlertTriangle,
  Bookmark,
  ChevronLeft,
  ChevronRight,
  Clock3,
  CloudOff,
  Eraser,
  LayoutGrid,
  Loader2,
  Maximize,
  Minimize,
  Send,
  Check,
} from 'lucide-react';
import { useCallback, useEffect, useMemo, useRef, useState } from 'react';
import { Navigate, useBlocker, useNavigate, useParams } from 'react-router-dom';
import { ApiError, errorMessage } from '@/api/client';
import { attemptApi } from '@/api/endpoints';
import type { Attempt } from '@/api/types';
import { LogoMark } from '@/components/brand/Logo';
import { ThemeToggle } from '@/components/ThemeToggle';
import { Button } from '@/components/ui/Button';
import { Alert, FullScreenLoader } from '@/components/ui/Feedback';
import { Modal } from '@/components/ui/Modal';
import { PaletteLegend, QuestionPalette } from '@/exam/QuestionPalette';
import { clearLocalAttempt, statusOf, useExamSession, type QuestionStatus, type SaveState } from '@/exam/useExamSession';

export default function ExamHallPage() {
  const attemptId = Number(useParams().attemptId);
  const attempt = useQuery({
    queryKey: ['attempt', attemptId],
    queryFn: () => attemptApi.get(attemptId),
    staleTime: Infinity,
    refetchOnWindowFocus: false,
    retry: 2,
  });

  if (attempt.isPending) return <FullScreenLoader />;
  if (attempt.isError) {
    return (
      <div className="mx-auto max-w-lg px-4 py-20">
        <Alert tone="error">{errorMessage(attempt.error)}</Alert>
      </div>
    );
  }
  if (attempt.data.status !== 'IN_PROGRESS') return <Navigate to={`/results/${attemptId}`} replace />;
  return <ExamHall attempt={attempt.data} />;
}

function formatClock(ms: number) {
  const total = Math.max(0, Math.ceil(ms / 1000));
  const h = Math.floor(total / 3600);
  const m = Math.floor((total % 3600) / 60);
  const s = total % 60;
  const pad = (n: number) => String(n).padStart(2, '0');
  return h > 0 ? `${h}:${pad(m)}:${pad(s)}` : `${pad(m)}:${pad(s)}`;
}

function ExamHall({ attempt }: { attempt: Attempt }) {
  const navigate = useNavigate();
  const queryClient = useQueryClient();
  const { questions, exam } = attempt;
  const [index, setIndex] = useState(0);
  const [showSubmit, setShowSubmit] = useState(false);
  const [showPalette, setShowPalette] = useState(false);
  const [submitting, setSubmitting] = useState(false);
  const [submitError, setSubmitError] = useState<string | null>(null);
  const [finished, setFinished] = useState(false);
  const [notice, setNotice] = useState<string | null>(null);
  const [fullscreen, setFullscreen] = useState(false);
  const submitStarted = useRef(false);
  // A ref (not state) so the navigation guard sees it immediately when we redirect to the result.
  const finishedRef = useRef(false);

  const goToResult = useCallback(
    (reason?: string) => {
      clearLocalAttempt(attempt.attemptId);
      finishedRef.current = true;
      setFinished(true);
      queryClient.removeQueries({ queryKey: ['attempt', attempt.attemptId] });
      queryClient.invalidateQueries({ queryKey: ['student'] });
      navigate(`/results/${attempt.attemptId}`, { replace: true, state: reason ? { notice: reason } : undefined });
    },
    [attempt.attemptId, navigate, queryClient],
  );

  const onClosed = useCallback(
    (code: string) => goToResult(code === 'ATTEMPT_EXPIRED' ? 'Time was up, so your test was submitted automatically.' : undefined),
    [goToResult],
  );
  const session = useExamSession(attempt, onClosed);
  const { answers, update } = session;

  const q = questions[index];
  const current = answers[q.questionId];
  const isMulti = q.type === 'MULTI_SELECT';

  // Mark the question as visited when it is opened.
  useEffect(() => {
    if (!answers[q.questionId]?.visited) update(q.questionId, { visited: true });
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [q.questionId]);

  // ---------------------------------------------------------------- timer (server clock)
  const deadline = new Date(attempt.deadlineAt).getTime();
  const remainingNow = () => deadline - (Date.now() + session.serverOffsetMs);
  const [remaining, setRemaining] = useState(remainingNow);
  useEffect(() => {
    const t = window.setInterval(() => setRemaining(remainingNow()), 250);
    return () => window.clearInterval(t);
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [deadline, session.serverOffsetMs]);

  const submit = useCallback(
    async (auto: boolean) => {
      if (submitStarted.current && !auto) return; // no double submissions
      submitStarted.current = true;
      setSubmitting(true);
      setSubmitError(null);
      try {
        session.close();
        await attemptApi.submit(attempt.attemptId, session.pendingItems());
        goToResult(auto ? 'Time is up — your test was submitted automatically.' : undefined);
      } catch (e) {
        if (e instanceof ApiError && e.status === 404) return goToResult();
        submitStarted.current = false;
        setSubmitting(false);
        setSubmitError(
          `${errorMessage(e)} Your answers are kept on this device. ${auto ? 'Retrying…' : 'Please try again.'}`,
        );
        if (auto) window.setTimeout(() => void submit(true), 5000);
      }
    },
    [attempt.attemptId, goToResult, session],
  );

  useEffect(() => {
    if (remaining <= 0 && !submitStarted.current) void submit(true);
    if (remaining <= 5 * 60_000 && remaining > 5 * 60_000 - 1000) setNotice('5 minutes left.');
    if (remaining <= 60_000 && remaining > 59_000) setNotice('1 minute left — your test will be submitted automatically.');
  }, [remaining, submit]);

  // ---------------------------------------------------------------- leaving the page
  useEffect(() => {
    const warn = (e: BeforeUnloadEvent) => {
      if (finished) return;
      void session.flush();
      e.preventDefault();
      e.returnValue = '';
    };
    window.addEventListener('beforeunload', warn);
    return () => window.removeEventListener('beforeunload', warn);
  }, [finished, session]);
  const blocker = useBlocker(
    ({ currentLocation, nextLocation }) => !finishedRef.current && currentLocation.pathname !== nextLocation.pathname,
  );

  useEffect(() => {
    const onChange = () => setFullscreen(!!document.fullscreenElement);
    document.addEventListener('fullscreenchange', onChange);
    return () => document.removeEventListener('fullscreenchange', onChange);
  }, []);
  const toggleFullscreen = () => {
    if (document.fullscreenElement) void document.exitFullscreen();
    else void document.documentElement.requestFullscreen?.().catch(() => setNotice('Full screen is not available in this browser.'));
  };

  // ---------------------------------------------------------------- actions
  const counts = useMemo(() => {
    const c: Record<QuestionStatus, number> = { 'not-visited': 0, 'not-answered': 0, answered: 0, marked: 0, 'answered-marked': 0 };
    questions.forEach((qq) => (c[statusOf(answers[qq.questionId])] += 1));
    return c;
  }, [answers, questions]);
  const answeredCount = counts.answered + counts['answered-marked'];

  const choose = (optionId: number) => {
    const selected = current?.selected ?? [];
    const next = isMulti
      ? selected.includes(optionId)
        ? selected.filter((id) => id !== optionId)
        : [...selected, optionId]
      : [optionId];
    update(q.questionId, { selected: next, visited: true });
  };

  const go = (i: number) => {
    if (i < 0 || i >= questions.length) return;
    if (!exam.allowNavigation && i < index) return;
    void session.flush();
    setIndex(i);
    setShowPalette(false);
  };

  const saveAndNext = () => (index < questions.length - 1 ? go(index + 1) : setShowSubmit(true));
  const markAndNext = () => {
    update(q.questionId, { marked: !current?.marked, visited: true });
    if (!current?.marked) saveAndNext();
  };

  // Keyboard: 1–8 choose options, ←/→ move (when allowed).
  useEffect(() => {
    const onKey = (e: KeyboardEvent) => {
      const target = e.target as HTMLElement;
      if (showSubmit || target.closest('input, textarea, [role="dialog"]')) return;
      const n = Number(e.key);
      if (n >= 1 && n <= q.options.length) choose(q.options[n - 1].id);
      if (e.key === 'ArrowRight') go(index + 1);
      if (e.key === 'ArrowLeft' && exam.allowNavigation) go(index - 1);
    };
    window.addEventListener('keydown', onKey);
    return () => window.removeEventListener('keydown', onKey);
  });

  const warning = remaining <= 5 * 60_000;

  return (
    <div className="flex min-h-screen flex-col bg-slate-100 dark:bg-gray-950">
      {/* Top bar */}
      <header className="sticky top-0 z-30 border-b border-slate-200 bg-white dark:border-gray-800 dark:bg-gray-900">
        <div className="mx-auto flex h-14 max-w-7xl items-center gap-3 px-3 sm:px-5">
          <LogoMark className="h-8 w-8" />
          <div className="min-w-0 flex-1">
            <h1 className="truncate text-sm font-bold sm:text-base">{exam.title}</h1>
            <p className="hidden truncate text-xs muted sm:block">{exam.categoryName}</p>
          </div>
          <SaveIndicator state={session.saveState} pending={session.pendingCount} />
          <div
            role="timer"
            aria-live="off"
            aria-label={`Time left ${formatClock(remaining)}`}
            className={clsx(
              'flex items-center gap-1.5 rounded-xl px-3 py-1.5 font-mono text-base font-bold tabular-nums sm:text-lg',
              warning ? 'bg-red-600 text-white' : 'bg-slate-900 text-white dark:bg-gray-800',
              remaining <= 60_000 && 'animate-pulse',
            )}
          >
            <Clock3 className="h-4 w-4" />
            {formatClock(remaining)}
          </div>
          <button type="button" onClick={toggleFullscreen} className="hidden rounded-lg p-2 hover:bg-slate-100 dark:hover:bg-gray-800 sm:block" aria-label={fullscreen ? 'Exit full screen' : 'Full screen'}>
            {fullscreen ? <Minimize className="h-5 w-5" /> : <Maximize className="h-5 w-5" />}
          </button>
          <ThemeToggle className="hidden md:inline-flex" />
          <Button size="sm" onClick={() => setShowSubmit(true)} className="hidden sm:inline-flex">
            <Send className="h-4 w-4" /> Submit
          </Button>
        </div>
      </header>

      {notice && (
        <div role="status" className="bg-amber-100 px-4 py-2 text-center text-sm font-medium text-amber-900 dark:bg-amber-900/40 dark:text-amber-100">
          {notice}{' '}
          <button type="button" className="ml-2 underline" onClick={() => setNotice(null)}>
            Dismiss
          </button>
        </div>
      )}

      <div className="mx-auto flex w-full max-w-7xl flex-1 gap-5 px-3 py-4 sm:px-5 sm:py-6">
        {/* Question */}
        <main className="flex min-w-0 flex-1 flex-col">
          <article className="card flex flex-1 flex-col p-5 sm:p-7" aria-labelledby="question-title">
            <div className="flex flex-wrap items-center justify-between gap-2 border-b border-slate-100 pb-3 dark:border-gray-700">
              <h2 id="question-title" className="text-base font-bold">
                Question {q.number} <span className="font-normal muted">of {questions.length}</span>
              </h2>
              <div className="flex flex-wrap items-center gap-2 text-xs">
                <span className="rounded-full bg-slate-100 px-2.5 py-1 dark:bg-gray-700">{q.subject}</span>
                <span className="rounded-full bg-emerald-50 px-2.5 py-1 font-semibold text-emerald-700 dark:bg-emerald-950/50 dark:text-emerald-300">+{q.marks}</span>
                {q.negativeMarks > 0 && (
                  <span className="rounded-full bg-red-50 px-2.5 py-1 font-semibold text-red-700 dark:bg-red-950/50 dark:text-red-300">−{q.negativeMarks}</span>
                )}
              </div>
            </div>

            <div className="mt-5 flex-1">
              <p className="whitespace-pre-line text-base leading-relaxed sm:text-lg">{q.text}</p>
              {q.imageUrl && <img src={q.imageUrl} alt="" className="mt-4 max-h-72 rounded-xl border border-slate-200 dark:border-gray-700" />}
              {isMulti && <p className="mt-3 text-sm font-medium text-brand-700 dark:text-brand-300">Select all correct answers.</p>}

              <fieldset className="mt-5 space-y-3">
                <legend className="sr-only">Options</legend>
                {q.options.map((o, i) => {
                  const selected = current?.selected.includes(o.id) ?? false;
                  return (
                    <label
                      key={o.id}
                      className={clsx(
                        'flex cursor-pointer items-start gap-3 rounded-xl border-2 p-3.5 transition-colors sm:p-4',
                        selected
                          ? 'border-brand-600 bg-brand-50 dark:border-brand-500 dark:bg-brand-950/40'
                          : 'border-slate-200 hover:border-slate-300 dark:border-gray-700 dark:hover:border-gray-600',
                      )}
                    >
                      <input
                        type={isMulti ? 'checkbox' : 'radio'}
                        name={`q-${q.questionId}`}
                        checked={selected}
                        onChange={() => choose(o.id)}
                        className="sr-only"
                      />
                      <span
                        className={clsx(
                          'flex h-7 w-7 shrink-0 items-center justify-center rounded-lg border text-sm font-bold',
                          selected ? 'border-brand-600 bg-brand-600 text-white' : 'border-slate-300 dark:border-gray-600',
                        )}
                        aria-hidden="true"
                      >
                        {selected && isMulti ? <Check className="h-4 w-4" /> : o.label}
                      </span>
                      <span className="pt-0.5">
                        <span className="sr-only">Option {o.label}: </span>
                        {o.text}
                      </span>
                      <kbd className="ml-auto hidden self-center rounded border border-slate-200 px-1.5 text-[10px] muted dark:border-gray-700 lg:inline">{i + 1}</kbd>
                    </label>
                  );
                })}
              </fieldset>
            </div>
          </article>

          {/* Actions */}
          <div className="sticky bottom-0 z-20 -mx-3 mt-4 border-t border-slate-200 bg-slate-100/95 px-3 py-3 backdrop-blur dark:border-gray-800 dark:bg-gray-950/95 sm:static sm:mx-0 sm:border-0 sm:bg-transparent sm:p-0">
            <div className="flex flex-wrap items-center gap-2">
              <Button variant="secondary" size="sm" onClick={() => update(q.questionId, { selected: [] })} disabled={!current?.selected.length}>
                <Eraser className="h-4 w-4" /> Clear
              </Button>
              <Button variant="secondary" size="sm" onClick={markAndNext} className={clsx(current?.marked && 'border-violet-500 text-violet-700 dark:text-violet-300')}>
                <Bookmark className="h-4 w-4" /> {current?.marked ? 'Unmark' : 'Mark for review & next'}
              </Button>
              <div className="ml-auto flex gap-2">
                <Button variant="secondary" size="sm" onClick={() => go(index - 1)} disabled={index === 0 || !exam.allowNavigation}>
                  <ChevronLeft className="h-4 w-4" /> Previous
                </Button>
                <Button size="sm" onClick={saveAndNext}>
                  {index < questions.length - 1 ? (
                    <>
                      Save & next <ChevronRight className="h-4 w-4" />
                    </>
                  ) : (
                    <>
                      Save & finish <Send className="h-4 w-4" />
                    </>
                  )}
                </Button>
              </div>
            </div>
            <div className="mt-2 flex items-center justify-between gap-2 lg:hidden">
              <Button variant="ghost" size="sm" onClick={() => setShowPalette(true)}>
                <LayoutGrid className="h-4 w-4" /> Questions ({answeredCount}/{questions.length})
              </Button>
              <Button size="sm" variant="danger" className="sm:hidden" onClick={() => setShowSubmit(true)}>
                Submit
              </Button>
            </div>
          </div>
        </main>

        {/* Palette (desktop) */}
        <aside className="hidden w-72 shrink-0 lg:block">
          <div className="card sticky top-20 space-y-4 p-4">
            <PaletteLegend counts={counts} />
            <div className="h-px bg-slate-100 dark:bg-gray-700" />
            <QuestionPalette questions={questions} answers={answers} current={index} onJump={go} locked={!exam.allowNavigation} />
            <p className="text-xs muted">
              {answeredCount} answered · {questions.length - answeredCount} left
            </p>
          </div>
        </aside>
      </div>

      {/* Palette (mobile) */}
      <Modal open={showPalette} title="Questions" onClose={() => setShowPalette(false)}>
        <div className="space-y-4">
          <PaletteLegend counts={counts} />
          <QuestionPalette questions={questions} answers={answers} current={index} onJump={go} locked={!exam.allowNavigation} />
        </div>
      </Modal>

      {/* Submit confirmation */}
      <Modal
        open={showSubmit}
        title="Submit test?"
        onClose={() => !submitting && setShowSubmit(false)}
        footer={
          <>
            <Button variant="secondary" disabled={submitting} onClick={() => setShowSubmit(false)}>
              Back to test
            </Button>
            <Button variant="danger" loading={submitting} onClick={() => void submit(false)}>
              Submit now
            </Button>
          </>
        }
      >
        <div className="space-y-3 text-sm">
          <p>You can't change your answers after submitting.</p>
          <dl className="grid grid-cols-2 gap-2">
            <div className="rounded-xl bg-emerald-50 p-3 dark:bg-emerald-950/40">
              <dt className="text-xs muted">Answered</dt>
              <dd className="text-xl font-bold">{answeredCount}</dd>
            </div>
            <div className="rounded-xl bg-red-50 p-3 dark:bg-red-950/40">
              <dt className="text-xs muted">Not answered</dt>
              <dd className="text-xl font-bold">{questions.length - answeredCount}</dd>
            </div>
            <div className="rounded-xl bg-violet-50 p-3 dark:bg-violet-950/40">
              <dt className="text-xs muted">Marked for review</dt>
              <dd className="text-xl font-bold">{counts.marked + counts['answered-marked']}</dd>
            </div>
            <div className="rounded-xl bg-slate-100 p-3 dark:bg-gray-700/50">
              <dt className="text-xs muted">Time left</dt>
              <dd className="font-mono text-xl font-bold">{formatClock(remaining)}</dd>
            </div>
          </dl>
          {session.pendingCount > 0 && (
            <Alert tone="info">{session.pendingCount} recent answer(s) will be sent with your submission.</Alert>
          )}
          {submitError && <Alert tone="error">{submitError}</Alert>}
        </div>
      </Modal>

      {/* Auto-submit in progress */}
      {submitting && !showSubmit && (
        <div className="fixed inset-0 z-[80] flex flex-col items-center justify-center gap-3 bg-white/90 dark:bg-gray-900/90">
          <Loader2 className="h-8 w-8 animate-spin text-brand-600" />
          <p className="font-semibold">Submitting your test…</p>
          {submitError && <p className="max-w-sm text-center text-sm text-red-600">{submitError}</p>}
        </div>
      )}

      {/* Leaving the exam inside the app */}
      <Modal
        open={blocker.state === 'blocked'}
        title="Leave the test?"
        onClose={() => blocker.reset?.()}
        footer={
          <>
            <Button variant="secondary" onClick={() => blocker.reset?.()}>
              Stay
            </Button>
            <Button
              variant="danger"
              onClick={() => {
                void session.flush();
                blocker.proceed?.();
              }}
            >
              Leave
            </Button>
          </>
        }
      >
        <p className="flex gap-2 text-sm">
          <AlertTriangle className="h-5 w-5 shrink-0 text-amber-500" />
          The timer keeps running while you are away. Your answers are saved, and you can continue from “My tests” before time runs out.
        </p>
      </Modal>
    </div>
  );
}

function SaveIndicator({ state, pending }: { state: SaveState; pending: number }) {
  const map: Record<SaveState, { text: string; icon: JSX.Element; className: string }> = {
    saved: { text: 'All answers saved', icon: <Check className="h-4 w-4" />, className: 'text-emerald-600 dark:text-emerald-400' },
    pending: { text: 'Unsaved changes', icon: <Loader2 className="h-4 w-4" />, className: 'muted' },
    saving: { text: 'Saving…', icon: <Loader2 className="h-4 w-4 animate-spin" />, className: 'muted' },
    offline: {
      text: `Offline — ${pending} waiting`,
      icon: <CloudOff className="h-4 w-4" />,
      className: 'text-amber-600 dark:text-amber-400',
    },
    error: { text: 'Save problem', icon: <AlertTriangle className="h-4 w-4" />, className: 'text-red-600 dark:text-red-400' },
  };
  const m = map[state];
  return (
    <span role="status" aria-live="polite" className={clsx('hidden items-center gap-1.5 text-xs font-medium md:flex', m.className)}>
      {m.icon}
      {m.text}
    </span>
  );
}
