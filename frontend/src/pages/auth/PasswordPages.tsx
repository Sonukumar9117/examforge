import { zodResolver } from '@hookform/resolvers/zod';
import { CheckCircle2, MailCheck } from 'lucide-react';
import { useState } from 'react';
import { useForm } from 'react-hook-form';
import { Link, useSearchParams } from 'react-router-dom';
import { z } from 'zod';
import { errorMessage } from '@/api/client';
import { authApi } from '@/api/endpoints';
import { PasswordStrength } from '@/components/auth/PasswordStrength';
import { Button } from '@/components/ui/Button';
import { Alert } from '@/components/ui/Feedback';
import { PasswordField, TextField } from '@/components/ui/Field';
import { AuthLayout } from '@/layouts/AuthLayout';

export function ForgotPasswordPage() {
  const [sent, setSent] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const { register, handleSubmit, formState } = useForm<{ email: string }>({
    resolver: zodResolver(z.object({ email: z.string().trim().email('Enter a valid email address') })),
  });

  const onSubmit = handleSubmit(async ({ email }) => {
    setError(null);
    try {
      await authApi.forgotPassword(email);
      setSent(true);
    } catch (e) {
      setError(errorMessage(e));
    }
  });

  return (
    <AuthLayout title="Forgot your password?" subtitle="Enter your account email and we'll send you a reset link.">
      {sent ? (
        <div className="card p-6 text-center">
          <MailCheck className="mx-auto h-10 w-10 text-brand-600" />
          <p className="mt-3 text-sm">If an account exists for that email, a reset link is on its way. It expires in 30 minutes.</p>
          <Link to="/login" className="link mt-5 inline-block text-sm">
            Back to sign in
          </Link>
        </div>
      ) : (
        <form onSubmit={onSubmit} noValidate className="space-y-4">
          {error && <Alert tone="error">{error}</Alert>}
          <TextField label="Email address" type="email" autoComplete="email" autoFocus error={formState.errors.email?.message} {...register('email')} />
          <Button type="submit" fullWidth size="lg" loading={formState.isSubmitting}>
            Send reset link
          </Button>
          <p className="text-center text-sm">
            <Link to="/login" className="link">
              Back to sign in
            </Link>
          </p>
        </form>
      )}
    </AuthLayout>
  );
}

const resetSchema = z
  .object({
    password: z
      .string()
      .min(8, 'At least 8 characters')
      .max(72)
      .regex(/[A-Za-z]/, 'Include at least one letter')
      .regex(/\d/, 'Include at least one number'),
    confirmPassword: z.string(),
  })
  .refine((v) => v.password === v.confirmPassword, { path: ['confirmPassword'], message: 'Passwords do not match' });

export function ResetPasswordPage() {
  const [params] = useSearchParams();
  const token = params.get('token') ?? '';
  const [done, setDone] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const { register, handleSubmit, watch, formState } = useForm<z.infer<typeof resetSchema>>({
    resolver: zodResolver(resetSchema),
    defaultValues: { password: '', confirmPassword: '' },
  });

  const onSubmit = handleSubmit(async ({ password }) => {
    setError(null);
    try {
      await authApi.resetPassword(token, password);
      setDone(true);
    } catch (e) {
      setError(errorMessage(e));
    }
  });

  if (!token) {
    return (
      <AuthLayout title="Reset password">
        <Alert tone="error">
          This reset link is incomplete. <Link to="/forgot-password" className="link">Request a new one</Link>.
        </Alert>
      </AuthLayout>
    );
  }

  return (
    <AuthLayout title="Choose a new password" subtitle="You'll be signed out on all other devices.">
      {done ? (
        <div className="card p-6 text-center">
          <CheckCircle2 className="mx-auto h-10 w-10 text-emerald-500" />
          <p className="mt-3 text-sm">Your password has been updated.</p>
          <Link to="/login" className="link mt-5 inline-block text-sm">
            Sign in
          </Link>
        </div>
      ) : (
        <form onSubmit={onSubmit} noValidate className="space-y-4">
          {error && (
            <Alert tone="error">
              {error} <Link to="/forgot-password" className="link">Request a new link</Link>.
            </Alert>
          )}
          <div>
            <PasswordField label="New password" autoComplete="new-password" autoFocus error={formState.errors.password?.message} {...register('password')} />
            <PasswordStrength password={watch('password')} />
          </div>
          <PasswordField label="Confirm new password" autoComplete="new-password" error={formState.errors.confirmPassword?.message} {...register('confirmPassword')} />
          <Button type="submit" fullWidth size="lg" loading={formState.isSubmitting}>
            Update password
          </Button>
        </form>
      )}
    </AuthLayout>
  );
}
