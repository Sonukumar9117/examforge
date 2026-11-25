import { useState } from 'react';
import { errorMessage } from '@/api/client';
import { meApi } from '@/api/endpoints';
import { useAuth } from '@/auth/AuthProvider';

export function VerifyEmailBanner() {
  const { user } = useAuth();
  const [state, setState] = useState<'idle' | 'sending' | 'sent' | string>('idle');
  if (!user || user.emailVerified) return null;

  const resend = async () => {
    setState('sending');
    try {
      await meApi.resendVerification();
      setState('sent');
    } catch (e) {
      setState(errorMessage(e));
    }
  };

  return (
    <div className="border-b border-amber-200 bg-amber-50 text-amber-900 dark:border-amber-900/50 dark:bg-amber-950/40 dark:text-amber-200">
      <div className="mx-auto flex max-w-7xl flex-wrap items-center gap-x-3 gap-y-1 px-4 py-2 text-sm sm:px-6">
        <span>
          Please verify <strong>{user.email}</strong> to secure your account.
        </span>
        {state === 'sent' ? (
          <span className="font-semibold">Link sent — check your inbox.</span>
        ) : (
          <button type="button" onClick={resend} disabled={state === 'sending'} className="font-semibold underline underline-offset-2">
            {state === 'sending' ? 'Sending…' : 'Resend verification email'}
          </button>
        )}
        {state !== 'idle' && state !== 'sending' && state !== 'sent' && <span>{state}</span>}
      </div>
    </div>
  );
}
