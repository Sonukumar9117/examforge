import { zodResolver } from '@hookform/resolvers/zod';
import { useState } from 'react';
import { useForm } from 'react-hook-form';
import { Link, useLocation, useNavigate } from 'react-router-dom';
import { z } from 'zod';
import { errorMessage } from '@/api/client';
import type { User } from '@/api/types';
import { homePathFor, useAuth } from '@/auth/AuthProvider';
import { GoogleSignInButton } from '@/components/auth/GoogleSignInButton';
import { Button } from '@/components/ui/Button';
import { Alert } from '@/components/ui/Feedback';
import { Checkbox, PasswordField, TextField } from '@/components/ui/Field';
import { AuthLayout, OrDivider } from '@/layouts/AuthLayout';

const schema = z.object({
  identifier: z.string().trim().min(1, 'Enter your email or username'),
  password: z.string().min(1, 'Enter your password'),
  rememberMe: z.boolean(),
});
type FormValues = z.infer<typeof schema>;

export default function LoginPage() {
  const { login } = useAuth();
  const navigate = useNavigate();
  const location = useLocation();
  const [error, setError] = useState<string | null>(null);
  const from = (location.state as { from?: string } | null)?.from;

  const {
    register,
    handleSubmit,
    watch,
    formState: { errors, isSubmitting },
  } = useForm<FormValues>({ resolver: zodResolver(schema), defaultValues: { identifier: '', password: '', rememberMe: false } });

  const goHome = (user: User) => {
    // Only return to the requested page if this user may see it.
    const target = from && !(from.startsWith('/admin') && !user.roles.includes('ROLE_ADMIN')) ? from : homePathFor(user);
    navigate(target, { replace: true });
  };

  const onSubmit = handleSubmit(async (values) => {
    setError(null);
    try {
      goHome(await login(values.identifier, values.password, values.rememberMe));
    } catch (e) {
      setError(errorMessage(e));
    }
  });

  return (
    <AuthLayout
      title="Welcome back"
      subtitle={
        <>
          New to ExamForge?{' '}
          <Link to="/signup" className="link">
            Create an account
          </Link>
        </>
      }
    >
      <GoogleSignInButton mode="signin" rememberMe={watch('rememberMe')} onSuccess={goHome} onError={setError} />
      <OrDivider />

      <form onSubmit={onSubmit} noValidate className="space-y-4">
        {error && <Alert tone="error">{error}</Alert>}
        <TextField
          label="Email or username"
          autoComplete="username"
          autoFocus
          error={errors.identifier?.message}
          {...register('identifier')}
        />
        <PasswordField
          label="Password"
          autoComplete="current-password"
          error={errors.password?.message}
          {...register('password')}
        />
        <div className="flex items-center justify-between gap-4">
          <Checkbox label="Remember me" {...register('rememberMe')} />
          <Link to="/forgot-password" className="link text-sm">
            Forgot password?
          </Link>
        </div>
        <Button type="submit" fullWidth size="lg" loading={isSubmitting}>
          Sign in
        </Button>
      </form>
    </AuthLayout>
  );
}
