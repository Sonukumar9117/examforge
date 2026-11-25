import { zodResolver } from '@hookform/resolvers/zod';
import { CheckCircle2, MailCheck, XCircle } from 'lucide-react';
import { useEffect, useState } from 'react';
import { useForm } from 'react-hook-form';
import { Link, useNavigate } from 'react-router-dom';
import { z } from 'zod';
import { ApiError, errorMessage } from '@/api/client';
import { authApi } from '@/api/endpoints';
import type { User } from '@/api/types';
import { homePathFor, useAuth } from '@/auth/AuthProvider';
import { GoogleSignInButton } from '@/components/auth/GoogleSignInButton';
import { PasswordStrength } from '@/components/auth/PasswordStrength';
import { Button } from '@/components/ui/Button';
import { Alert, Spinner } from '@/components/ui/Feedback';
import { Checkbox, PasswordField, TextField } from '@/components/ui/Field';
import { AuthLayout, OrDivider } from '@/layouts/AuthLayout';

const USERNAME = /^[A-Za-z0-9_]{3,30}$/;

const schema = z
  .object({
    fullName: z.string().trim().min(2, 'Enter your full name').max(100),
    username: z.string().trim().regex(USERNAME, '3–30 characters: letters, numbers or underscore'),
    email: z.string().trim().email('Enter a valid email address').max(254),
    password: z
      .string()
      .min(8, 'At least 8 characters')
      .max(72, 'At most 72 characters')
      .regex(/[A-Za-z]/, 'Include at least one letter')
      .regex(/\d/, 'Include at least one number'),
    confirmPassword: z.string(),
    acceptTerms: z.boolean().refine((v) => v, 'You must accept the terms to continue'),
  })
  .refine((v) => v.password === v.confirmPassword, { path: ['confirmPassword'], message: 'Passwords do not match' });
type FormValues = z.infer<typeof schema>;

type Availability = 'idle' | 'checking' | 'available' | 'taken';

export default function SignupPage() {
  const { register: registerAccount } = useAuth();
  const navigate = useNavigate();
  const [error, setError] = useState<string | null>(null);
  const [verifyEmailSentTo, setVerifyEmailSentTo] = useState<string | null>(null);
  const [availability, setAvailability] = useState<Availability>('idle');

  const {
    register,
    handleSubmit,
    watch,
    setError: setFieldError,
    formState: { errors, isSubmitting },
  } = useForm<FormValues>({
    resolver: zodResolver(schema),
    defaultValues: { fullName: '', username: '', email: '', password: '', confirmPassword: '', acceptTerms: false },
  });

  const username = watch('username');
  const password = watch('password');

  // Debounced username availability check.
  useEffect(() => {
    const value = username.trim();
    if (!USERNAME.test(value)) return setAvailability('idle');
    setAvailability('checking');
    const ctrl = new AbortController();
    const t = setTimeout(() => {
      authApi
        .usernameAvailable(value)
        .then((r) => !ctrl.signal.aborted && setAvailability(r.available ? 'available' : 'taken'))
        .catch(() => !ctrl.signal.aborted && setAvailability('idle'));
    }, 400);
    return () => {
      ctrl.abort();
      clearTimeout(t);
    };
  }, [username]);

  const onSubmit = handleSubmit(async ({ confirmPassword: _ignored, ...values }) => {
    setError(null);
    if (availability === 'taken') {
      setFieldError('username', { message: 'This username is already taken' });
      return;
    }
    try {
      const res = await registerAccount({ ...values, email: values.email.toLowerCase() });
      if (res.emailVerificationRequired || !res.accessToken) setVerifyEmailSentTo(res.user.email);
      else navigate(homePathFor(res.user), { replace: true });
    } catch (e) {
      if (e instanceof ApiError) {
        if (e.code === 'USERNAME_TAKEN') return setFieldError('username', { message: e.message });
        if (e.code === 'EMAIL_TAKEN') return setFieldError('email', { message: e.message });
        Object.entries(e.fieldErrors).forEach(([field, msg]) =>
          setFieldError(field as keyof FormValues, { message: msg }),
        );
      }
      setError(errorMessage(e));
    }
  });

  if (verifyEmailSentTo) {
    return (
      <AuthLayout title="Check your inbox">
        <div className="card p-6 text-center">
          <MailCheck className="mx-auto h-10 w-10 text-brand-600" />
          <p className="mt-3 text-sm">
            We sent a verification link to <strong>{verifyEmailSentTo}</strong>. Open it to activate your account, then sign in.
          </p>
          <Link to="/login" className="link mt-5 inline-block text-sm">
            Back to sign in
          </Link>
        </div>
      </AuthLayout>
    );
  }

  const usernameStatus =
    availability === 'checking' ? (
      <Spinner className="h-4 w-4" />
    ) : availability === 'available' ? (
      <CheckCircle2 className="h-4 w-4 text-emerald-500" aria-label="Username available" />
    ) : availability === 'taken' ? (
      <XCircle className="h-4 w-4 text-red-500" aria-label="Username taken" />
    ) : null;

  return (
    <AuthLayout
      title="Create your account"
      subtitle={
        <>
          Already have an account?{' '}
          <Link to="/login" className="link">
            Sign in
          </Link>
        </>
      }
    >
      <GoogleSignInButton mode="signup" onSuccess={(u: User) => navigate(homePathFor(u), { replace: true })} onError={setError} />
      <OrDivider />

      <form onSubmit={onSubmit} noValidate className="space-y-4">
        {error && <Alert tone="error">{error}</Alert>}
        <TextField label="Full name" autoComplete="name" error={errors.fullName?.message} {...register('fullName')} />
        <TextField
          label="Username"
          autoComplete="username"
          trailing={usernameStatus}
          error={errors.username?.message ?? (availability === 'taken' ? 'This username is already taken' : undefined)}
          hint="Letters, numbers and underscores. Shown on leaderboards."
          {...register('username')}
        />
        <TextField label="Email address" type="email" autoComplete="email" error={errors.email?.message} {...register('email')} />
        <div>
          <PasswordField
            label="Password"
            autoComplete="new-password"
            error={errors.password?.message}
            {...register('password')}
          />
          <PasswordStrength password={password} />
        </div>
        <PasswordField
          label="Confirm password"
          autoComplete="new-password"
          error={errors.confirmPassword?.message}
          {...register('confirmPassword')}
        />
        <Checkbox
          label={
            <>
              I agree to the{' '}
              <Link to="/terms" target="_blank" className="link">
                Terms &amp; Conditions
              </Link>{' '}
              and Privacy Policy.
            </>
          }
          error={errors.acceptTerms?.message}
          {...register('acceptTerms')}
        />
        <Button type="submit" fullWidth size="lg" loading={isSubmitting}>
          Create account
        </Button>
      </form>
    </AuthLayout>
  );
}
