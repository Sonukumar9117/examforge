import { useCallback, useEffect, useMemo, useRef, useState } from 'react';
import { ApiError } from '@/api/client';
import { attemptApi } from '@/api/endpoints';
import type { AnswerInput, Attempt } from '@/api/types';

/** The student's state for one question, as kept in the browser. */
export interface LocalAnswer {
  selected: number[];
  marked: boolean;
  visited: boolean;
  /** increases with every change; the server ignores saves older than what it has */
  seq: number;
}

export type SaveState = 'saved' | 'pending' | 'saving' | 'offline' | 'error';

const BLANK: LocalAnswer = { selected: [], marked: false, visited: false, seq: 0 };
const AUTOSAVE_DEBOUNCE_MS = 800;
const AUTOSAVE_INTERVAL_MS = 15_000;

const storageKey = (attemptId: number) => `ef-attempt-${attemptId}`;

function readLocal(attemptId: number): Record<number, LocalAnswer> {
  try {
    return JSON.parse(localStorage.getItem(storageKey(attemptId)) ?? '{}');
  } catch {
    return {};
  }
}

export function clearLocalAttempt(attemptId: number) {
  try {
    localStorage.removeItem(storageKey(attemptId));
  } catch {
    /* ignore */
  }
}

/**
 * Answer state + autosave for one attempt.
 * - Every change is applied instantly in the UI and queued as "pending".
 * - Pending changes are mirrored to localStorage, so a refresh or crash loses nothing.
 * - They are sent to the server shortly after a change, every 15 s, when the network comes back,
 *   and before navigating; failures are retried with backoff.
 * - Timing uses the server clock: `serverOffsetMs` corrects the device clock.
 */
export function useExamSession(attempt: Attempt, onClosed: (code: string) => void) {
  const attemptId = attempt.attemptId;

  const initial = useMemo(() => {
    const map: Record<number, LocalAnswer> = {};
    for (const a of attempt.answers) {
      map[a.questionId] = { selected: a.selectedOptionIds, marked: a.markedForReview, visited: a.visited, seq: a.seq };
    }
    const pending = new Set<number>();
    // Changes made before a refresh that never reached the server win if they are newer.
    const local = readLocal(attemptId);
    for (const [qid, ans] of Object.entries(local)) {
      const id = Number(qid);
      if (!map[id] || ans.seq > map[id].seq) {
        map[id] = ans;
        pending.add(id);
      }
    }
    const maxSeq = Math.max(0, ...Object.values(map).map((a) => a.seq));
    return { map, pending, maxSeq };
  }, [attempt, attemptId]);

  const [answers, setAnswers] = useState<Record<number, LocalAnswer>>(initial.map);
  const answersRef = useRef(answers);
  answersRef.current = answers;
  const pending = useRef<Set<number>>(new Set(initial.pending));
  const seq = useRef(initial.maxSeq);
  const [saveState, setSaveState] = useState<SaveState>(initial.pending.size ? 'pending' : 'saved');
  const [pendingCount, setPendingCount] = useState(initial.pending.size);
  const [lastSavedAt, setLastSavedAt] = useState<Date | null>(null);
  const [serverOffsetMs, setServerOffsetMs] = useState(() => new Date(attempt.serverNow).getTime() - Date.now());

  const inFlight = useRef(false);
  const flushAgain = useRef(false);
  const debounce = useRef<number>();
  const retry = useRef<number>();
  const retryDelay = useRef(2000);
  const closed = useRef(false);

  const persist = useCallback(() => {
    const snapshot: Record<number, LocalAnswer> = {};
    pending.current.forEach((qid) => (snapshot[qid] = answersRef.current[qid]));
    try {
      if (pending.current.size) localStorage.setItem(storageKey(attemptId), JSON.stringify(snapshot));
      else localStorage.removeItem(storageKey(attemptId));
    } catch {
      /* storage full or blocked: the server save still runs */
    }
    setPendingCount(pending.current.size);
  }, [attemptId]);

  const pendingItems = useCallback((): AnswerInput[] => {
    return [...pending.current].map((qid) => {
      const a = answersRef.current[qid] ?? BLANK;
      return { questionId: qid, selectedOptionIds: a.selected, markedForReview: a.marked, visited: a.visited, seq: a.seq };
    });
  }, []);

  const flush = useCallback(async (): Promise<void> => {
    window.clearTimeout(debounce.current);
    if (closed.current || pending.current.size === 0) return;
    if (inFlight.current) {
      flushAgain.current = true;
      return;
    }
    const items = pendingItems();
    inFlight.current = true;
    setSaveState('saving');
    try {
      const res = await attemptApi.save(attemptId, items);
      // Only clear what we sent; anything changed while the request was in flight stays pending.
      for (const item of items) {
        if (answersRef.current[item.questionId]?.seq === item.seq) pending.current.delete(item.questionId);
      }
      persist();
      retryDelay.current = 2000;
      setLastSavedAt(new Date(res.savedAt));
      setServerOffsetMs(new Date(res.serverNow).getTime() - Date.now());
      setSaveState(pending.current.size ? 'pending' : 'saved');
    } catch (e) {
      if (e instanceof ApiError && (e.code === 'ATTEMPT_EXPIRED' || e.code === 'ATTEMPT_CLOSED')) {
        closed.current = true;
        onClosed(e.code);
      } else if (e instanceof ApiError && e.status >= 400 && e.status < 500 && e.status !== 408 && e.status !== 429) {
        setSaveState('error'); // a bug or tampered data; keep local copy, don't hammer the server
      } else {
        setSaveState('offline');
        window.clearTimeout(retry.current);
        retry.current = window.setTimeout(() => void flush(), retryDelay.current);
        retryDelay.current = Math.min(retryDelay.current * 2, 30_000);
      }
    } finally {
      inFlight.current = false;
      if (flushAgain.current) {
        flushAgain.current = false;
        void flush();
      }
    }
  }, [attemptId, onClosed, pendingItems, persist]);

  const update = useCallback(
    (questionId: number, patch: Partial<Omit<LocalAnswer, 'seq'>>) => {
      if (closed.current) return;
      seq.current += 1;
      const next = { ...(answersRef.current[questionId] ?? BLANK), ...patch, seq: seq.current };
      answersRef.current = { ...answersRef.current, [questionId]: next };
      setAnswers(answersRef.current);
      pending.current.add(questionId);
      persist();
      setSaveState((s) => (s === 'offline' ? s : 'pending'));
      window.clearTimeout(debounce.current);
      debounce.current = window.setTimeout(() => void flush(), AUTOSAVE_DEBOUNCE_MS);
    },
    [flush, persist],
  );

  useEffect(() => {
    const interval = window.setInterval(() => void flush(), AUTOSAVE_INTERVAL_MS);
    const online = () => void flush();
    window.addEventListener('online', online);
    if (pending.current.size) void flush();
    return () => {
      window.clearInterval(interval);
      window.clearTimeout(debounce.current);
      window.clearTimeout(retry.current);
      window.removeEventListener('online', online);
    };
  }, [flush]);

  const close = useCallback(() => {
    closed.current = true;
    window.clearTimeout(debounce.current);
    window.clearTimeout(retry.current);
  }, []);

  return { answers, update, flush, pendingItems, saveState, pendingCount, lastSavedAt, serverOffsetMs, close };
}

/** Palette status of a question, following the usual CBT colour scheme. */
export type QuestionStatus = 'not-visited' | 'not-answered' | 'answered' | 'marked' | 'answered-marked';

export function statusOf(a: LocalAnswer | undefined): QuestionStatus {
  if (!a || (!a.visited && a.selected.length === 0 && !a.marked)) return 'not-visited';
  const answered = a.selected.length > 0;
  if (answered && a.marked) return 'answered-marked';
  if (answered) return 'answered';
  if (a.marked) return 'marked';
  return 'not-answered';
}
