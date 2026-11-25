import type { AuthResponse } from './types';

/**
 * Small fetch wrapper.
 * - The access token lives only in memory (never localStorage).
 * - The refresh token is an HttpOnly cookie the browser sends to /api/v1/auth/* automatically.
 * - On a 401 we refresh once (single-flight, shared by concurrent requests) and retry.
 */

const BASE_URL = (import.meta.env.VITE_API_BASE_URL ?? '').replace(/\/$/, '');
export const API = `${BASE_URL}/api/v1`;

export class ApiError extends Error {
  constructor(
    public status: number,
    public code: string,
    message: string,
    public fieldErrors: Record<string, string> = {},
  ) {
    super(message);
  }
}

let accessToken: string | null = null;
let refreshInFlight: Promise<AuthResponse | null> | null = null;
type SessionListener = (session: AuthResponse | null) => void;
const listeners = new Set<SessionListener>();

export function setAccessToken(token: string | null) {
  accessToken = token;
}

/** AuthProvider subscribes so a background refresh (or expiry) updates the signed-in user. */
export function onSessionChange(listener: SessionListener) {
  listeners.add(listener);
  return () => listeners.delete(listener);
}

function notify(session: AuthResponse | null) {
  listeners.forEach((l) => l(session));
}

/** Exchange the refresh cookie for a new access token. Returns null when not signed in. */
export function refreshSession(): Promise<AuthResponse | null> {
  if (!refreshInFlight) {
    refreshInFlight = (async () => {
      try {
        const res = await fetch(`${API}/auth/refresh`, { method: 'POST', credentials: 'include' });
        if (!res.ok) {
          setAccessToken(null);
          return null;
        }
        const session = (await res.json()) as AuthResponse;
        setAccessToken(session.accessToken ?? null);
        return session;
      } catch {
        return null; // network error: keep current state, caller decides
      } finally {
        setTimeout(() => (refreshInFlight = null), 0);
      }
    })();
  }
  return refreshInFlight;
}

interface RequestOptions {
  method?: 'GET' | 'POST' | 'PUT' | 'PATCH' | 'DELETE';
  body?: unknown;
  /** Send the access token (default true). Auth endpoints use false. */
  auth?: boolean;
  signal?: AbortSignal;
}

export async function api<T>(path: string, opts: RequestOptions = {}, retried = false): Promise<T> {
  const { method = 'GET', body, auth = true, signal } = opts;
  const headers: Record<string, string> = { Accept: 'application/json' };
  if (body !== undefined) headers['Content-Type'] = 'application/json';
  if (auth && accessToken) headers.Authorization = `Bearer ${accessToken}`;

  let res: Response;
  try {
    res = await fetch(`${API}${path}`, {
      method,
      headers,
      body: body === undefined ? undefined : JSON.stringify(body),
      credentials: 'include',
      signal,
    });
  } catch (e) {
    if ((e as Error).name === 'AbortError') throw e;
    throw new ApiError(0, 'NETWORK_ERROR', 'Cannot reach the server. Check your connection and try again.');
  }

  if (res.status === 401 && auth && !retried) {
    const session = await refreshSession();
    if (session?.accessToken) {
      notify(session);
      return api<T>(path, opts, true);
    }
    notify(null);
  }

  if (!res.ok) {
    let payload: { code?: string; message?: string; fieldErrors?: Record<string, string> } = {};
    try {
      payload = await res.json();
    } catch {
      /* empty body */
    }
    throw new ApiError(
      res.status,
      payload.code ?? 'HTTP_' + res.status,
      payload.message ?? 'Something went wrong. Please try again.',
      payload.fieldErrors ?? {},
    );
  }

  if (res.status === 204) return undefined as T;
  const text = await res.text();
  return (text ? JSON.parse(text) : undefined) as T;
}

/** Human-friendly message for any thrown error. */
export function errorMessage(e: unknown): string {
  if (e instanceof ApiError) return e.message;
  if (e instanceof Error) return e.message;
  return 'Something went wrong. Please try again.';
}

/** Download a file from the API with the access token (refreshing once if needed) and save it. */
export async function downloadFile(path: string, filename: string): Promise<void> {
  const get = () => fetch(`${API}${path}`, { headers: accessToken ? { Authorization: `Bearer ${accessToken}` } : {}, credentials: 'include' });
  let res = await get();
  if (res.status === 401 && (await refreshSession())?.accessToken) res = await get();
  if (!res.ok) throw new ApiError(res.status, 'DOWNLOAD_FAILED', 'The file could not be downloaded.');
  const url = URL.createObjectURL(await res.blob());
  const a = document.createElement('a');
  a.href = url;
  a.download = filename;
  a.click();
  URL.revokeObjectURL(url);
}
