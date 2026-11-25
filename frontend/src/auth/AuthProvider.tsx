import { createContext, useCallback, useContext, useEffect, useMemo, useState, type ReactNode } from 'react';
import { useQueryClient } from '@tanstack/react-query';
import { onSessionChange, refreshSession, setAccessToken } from '@/api/client';
import { authApi, meApi } from '@/api/endpoints';
import type { AuthConfig, AuthResponse, User } from '@/api/types';

type Status = 'loading' | 'authenticated' | 'anonymous';

interface AuthContextValue {
  status: Status;
  user: User | null;
  config: AuthConfig | null;
  login: (identifier: string, password: string, rememberMe: boolean) => Promise<User>;
  register: (data: { fullName: string; username: string; email: string; password: string; acceptTerms: boolean }) => Promise<AuthResponse>;
  loginWithGoogle: (idToken: string, rememberMe: boolean) => Promise<User>;
  logout: () => Promise<void>;
  refreshUser: () => Promise<void>;
  setUser: (user: User) => void;
}

const AuthContext = createContext<AuthContextValue | null>(null);

export function AuthProvider({ children }: { children: ReactNode }) {
  const queryClient = useQueryClient();
  const [status, setStatus] = useState<Status>('loading');
  const [user, setUserState] = useState<User | null>(null);
  const [config, setConfig] = useState<AuthConfig | null>(null);

  const applySession = useCallback((session: AuthResponse | null) => {
    if (session?.accessToken) {
      setAccessToken(session.accessToken);
      setUserState(session.user);
      setStatus('authenticated');
    } else {
      setAccessToken(null);
      setUserState(null);
      setStatus('anonymous');
    }
  }, []);

  // Restore the session from the refresh cookie on first load.
  useEffect(() => {
    let cancelled = false;
    refreshSession().then((s) => !cancelled && applySession(s));
    authApi
      .config()
      .then((c) => !cancelled && setConfig(c))
      .catch(() => !cancelled && setConfig({ googleEnabled: false, googleClientId: null, emailVerificationRequired: false }));
    return () => {
      cancelled = true;
    };
  }, [applySession]);

  // Background refreshes / expiries from the API client.
  useEffect(() => {
    const off = onSessionChange((s) => {
      if (s) applySession(s);
      else {
        applySession(null);
        queryClient.clear();
      }
    });
    return () => {
      off();
    };
  }, [applySession, queryClient]);

  const login = useCallback(
    async (identifier: string, password: string, rememberMe: boolean) => {
      const res = await authApi.login({ identifier, password, rememberMe });
      applySession(res);
      return res.user;
    },
    [applySession],
  );

  const register = useCallback(
    async (data: { fullName: string; username: string; email: string; password: string; acceptTerms: boolean }) => {
      const res = await authApi.register(data);
      if (res.accessToken) applySession(res);
      return res;
    },
    [applySession],
  );

  const loginWithGoogle = useCallback(
    async (idToken: string, rememberMe: boolean) => {
      const res = await authApi.google({ idToken, rememberMe });
      applySession(res);
      return res.user;
    },
    [applySession],
  );

  const logout = useCallback(async () => {
    try {
      await authApi.logout();
    } finally {
      applySession(null);
      queryClient.clear();
    }
  }, [applySession, queryClient]);

  const refreshUser = useCallback(async () => {
    setUserState(await meApi.get());
  }, []);

  const value = useMemo<AuthContextValue>(
    () => ({ status, user, config, login, register, loginWithGoogle, logout, refreshUser, setUser: setUserState }),
    [status, user, config, login, register, loginWithGoogle, logout, refreshUser],
  );

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>;
}

export function useAuth() {
  const ctx = useContext(AuthContext);
  if (!ctx) throw new Error('useAuth must be used inside AuthProvider');
  return ctx;
}

export const isAdmin = (user: User | null) => !!user?.roles.includes('ROLE_ADMIN');
export const isStudent = (user: User | null) => !!user?.roles.includes('ROLE_STUDENT');

/** Where a user lands after signing in. */
export const homePathFor = (user: User | null) => (isAdmin(user) ? '/admin' : '/dashboard');
