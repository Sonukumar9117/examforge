import { CheckCircle2, XCircle } from 'lucide-react';
import { useEffect, useRef, useState } from 'react';
import { Link, useSearchParams } from 'react-router-dom';
import { errorMessage } from '@/api/client';
import { authApi } from '@/api/endpoints';
import { homePathFor, useAuth } from '@/auth/AuthProvider';
import { Spinner } from '@/components/ui/Feedback';
import { AuthLayout } from '@/layouts/AuthLayout';

export default function VerifyEmailPage() {
  const [params] = useSearchParams();
  const token = params.get('token') ?? '';
  const { status, user, refreshUser } = useAuth();
  const [state, setState] = useState<'working' | 'ok' | 'error'>('working');
  const [message, setMessage] = useState('');
  const started = useRef(false); // tokens are single-use; avoid the StrictMode double call

  useEffect(() => {
    if (started.current) return;
    started.current = true;
    if (!token) {
      setState('error');
      setMessage('This verification link is incomplete.');
      return;
    }
    authApi
      .verifyEmail(token)
      .then(() => setState('ok'))
      .catch((e) => {
        setState('error');
        setMessage(errorMessage(e));
      });
  }, [token]);

  useEffect(() => {
    if (state === 'ok' && status === 'authenticated') refreshUser().catch(() => undefined);
  }, [state, status, refreshUser]);

  const next = status === 'authenticated' ? homePathFor(user) : '/login';

  return (
    <AuthLayout title="Email verification">
      <div className="card p-6 text-center">
        {state === 'working' && <Spinner className="mx-auto h-8 w-8" />}
        {state === 'ok' && (
          <>
            <CheckCircle2 className="mx-auto h-10 w-10 text-emerald-500" />
            <p className="mt-3 text-sm">Your email address is verified. Thanks!</p>
          </>
        )}
        {state === 'error' && (
          <>
            <XCircle className="mx-auto h-10 w-10 text-red-500" />
            <p className="mt-3 text-sm">{message}</p>
            <p className="mt-1 text-xs muted">Sign in and use “Resend verification email” to get a new link.</p>
          </>
        )}
        {state !== 'working' && (
          <Link to={next} className="link mt-5 inline-block text-sm">
            Continue
          </Link>
        )}
      </div>
    </AuthLayout>
  );
}
