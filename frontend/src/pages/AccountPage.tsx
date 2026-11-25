import { zodResolver } from '@hookform/resolvers/zod';
import { useQuery } from '@tanstack/react-query';
import { useState } from 'react';
import { useForm } from 'react-hook-form';
import { z } from 'zod';
import { errorMessage } from '@/api/client';
import { catalogApi, meApi } from '@/api/endpoints';
import type { User } from '@/api/types';
import { useAuth } from '@/auth/AuthProvider';
import { PasswordStrength } from '@/components/auth/PasswordStrength';
import { PageHeader } from '@/components/PageHeader';
import { ThemeToggle } from '@/components/ThemeToggle';
import { Avatar } from '@/components/UserMenu';
import { Button } from '@/components/ui/Button';
import { Alert, Badge } from '@/components/ui/Feedback';
import { Checkbox, PasswordField, SelectField, TextField } from '@/components/ui/Field';

const profileSchema = z.object({
  fullName: z.string().trim().min(2, 'Enter your full name').max(100),
  phone: z
    .string()
    .trim()
    .regex(/^$|^[+0-9 ()-]{6,20}$/, 'Enter a valid phone number'),
  avatarUrl: z
    .string()
    .trim()
    .max(500)
    .regex(/^$|^https:\/\/.+/, 'Must be an https:// link'),
  bio: z.string().trim().max(500, 'At most 500 characters'),
  targetCategoryId: z.string(),
});

const passwordSchema = z
  .object({
    currentPassword: z.string(),
    newPassword: z
      .string()
      .min(8, 'At least 8 characters')
      .max(72)
      .regex(/[A-Za-z]/, 'Include at least one letter')
      .regex(/\d/, 'Include at least one number'),
    confirmPassword: z.string(),
  })
  .refine((v) => v.newPassword === v.confirmPassword, { path: ['confirmPassword'], message: 'Passwords do not match' });

/** Account settings — shared by students (/profile) and admins (/admin/account). */
export default function AccountPage() {
  const { user, setUser } = useAuth();
  if (!user) return null;

  return (
    <div className="mx-auto max-w-3xl space-y-6">
      <PageHeader title="Account settings" description="Manage your profile, appearance and password." />

      <section className="card flex flex-wrap items-center gap-4 p-5">
        <Avatar name={user.fullName} url={user.avatarUrl} size={56} />
        <div className="min-w-0 flex-1">
          <p className="truncate text-lg font-bold">{user.fullName}</p>
          <p className="truncate text-sm muted">
            @{user.username} · {user.email}
          </p>
        </div>
        <div className="flex flex-wrap gap-2">
          {user.emailVerified ? <Badge tone="success">Email verified</Badge> : <Badge tone="warning">Email not verified</Badge>}
          {user.googleLinked && <Badge>Google linked</Badge>}
          {user.roles.includes('ROLE_ADMIN') && <Badge tone="brand">Admin</Badge>}
        </div>
      </section>

      <ProfileForm onSaved={setUser} />

      <section className="card p-5 sm:p-6">
        <h2 className="text-lg font-bold">Appearance</h2>
        <p className="mt-1 text-sm muted">Choose light, dark, or follow your device. Saved to your account.</p>
        <ThemeToggle className="mt-4" />
      </section>

      <section className="card p-5 sm:p-6">
        <h2 className="text-lg font-bold">Privacy</h2>
        <Checkbox
          className="mt-3"
          label="Show my name on leaderboards (otherwise I appear as “Anonymous student”)"
          checked={user.profile?.showOnLeaderboard ?? true}
          onChange={(e) => {
            meApi.setLeaderboardVisibility(e.target.checked).then(setUser).catch(() => undefined);
          }}
        />
      </section>

      <PasswordForm hasPassword={user.hasPassword} />
    </div>
  );
}

function ProfileForm({ onSaved }: { onSaved: (u: User) => void }) {
  const { user } = useAuth();
  const [status, setStatus] = useState<{ tone: 'success' | 'error'; text: string } | null>(null);
  const categories = useQuery({ queryKey: ['catalog', 'categories'], queryFn: catalogApi.categories });
  const { register, handleSubmit, formState } = useForm<z.infer<typeof profileSchema>>({
    resolver: zodResolver(profileSchema),
    defaultValues: {
      fullName: user?.fullName ?? '',
      phone: user?.profile?.phone ?? '',
      avatarUrl: user?.avatarUrl ?? '',
      bio: user?.profile?.bio ?? '',
      targetCategoryId: user?.profile?.targetCategoryId ? String(user.profile.targetCategoryId) : '',
    },
  });

  const onSubmit = handleSubmit(async ({ targetCategoryId, ...values }) => {
    setStatus(null);
    try {
      onSaved(await meApi.update({ ...values, targetCategoryId: targetCategoryId ? Number(targetCategoryId) : null }));
      setStatus({ tone: 'success', text: 'Profile saved.' });
    } catch (e) {
      setStatus({ tone: 'error', text: errorMessage(e) });
    }
  });

  return (
    <form onSubmit={onSubmit} noValidate className="card space-y-4 p-5 sm:p-6">
      <h2 className="text-lg font-bold">Profile</h2>
      {status && <Alert tone={status.tone}>{status.text}</Alert>}
      <div className="grid gap-4 sm:grid-cols-2">
        <TextField label="Full name" autoComplete="name" error={formState.errors.fullName?.message} {...register('fullName')} />
        <TextField label="Phone (optional)" type="tel" autoComplete="tel" error={formState.errors.phone?.message} {...register('phone')} />
      </div>
      <TextField label="Avatar URL (optional)" placeholder="https://…" error={formState.errors.avatarUrl?.message} {...register('avatarUrl')} />
      <div>
        <label htmlFor="bio" className="mb-1.5 block text-sm font-medium text-slate-700 dark:text-slate-300">
          Bio (optional)
        </label>
        <textarea
          id="bio"
          rows={3}
          className="block w-full rounded-xl border border-slate-300 bg-white px-3.5 py-2.5 text-sm focus:border-brand-500 focus:outline-none focus:ring-2 focus:ring-brand-500/30 dark:border-gray-600 dark:bg-gray-900/60"
          {...register('bio')}
        />
        {formState.errors.bio && <p className="mt-1.5 text-xs text-red-600 dark:text-red-400">{formState.errors.bio.message}</p>}
      </div>
      <SelectField
        label="Target exam"
        hint="Used to recommend mock tests on your dashboard."
        disabled={categories.isPending}
        {...register('targetCategoryId')}
      >
        <option value="">Not decided yet</option>
        {categories.data?.map((c) => (
          <option key={c.id} value={c.id}>
            {c.name}
          </option>
        ))}
      </SelectField>
      <div className="flex justify-end">
        <Button type="submit" loading={formState.isSubmitting}>
          Save profile
        </Button>
      </div>
    </form>
  );
}

function PasswordForm({ hasPassword }: { hasPassword: boolean }) {
  const [status, setStatus] = useState<{ tone: 'success' | 'error'; text: string } | null>(null);
  const { register, handleSubmit, watch, reset, setError, formState } = useForm<z.infer<typeof passwordSchema>>({
    resolver: zodResolver(passwordSchema),
    defaultValues: { currentPassword: '', newPassword: '', confirmPassword: '' },
  });

  const onSubmit = handleSubmit(async (v) => {
    setStatus(null);
    if (hasPassword && !v.currentPassword) {
      setError('currentPassword', { message: 'Enter your current password' });
      return;
    }
    try {
      await meApi.changePassword({ currentPassword: hasPassword ? v.currentPassword : undefined, newPassword: v.newPassword });
      reset();
      setStatus({ tone: 'success', text: 'Password updated.' });
    } catch (e) {
      setStatus({ tone: 'error', text: errorMessage(e) });
    }
  });

  return (
    <form onSubmit={onSubmit} noValidate className="card space-y-4 p-5 sm:p-6">
      <div>
        <h2 className="text-lg font-bold">{hasPassword ? 'Change password' : 'Set a password'}</h2>
        {!hasPassword && <p className="mt-1 text-sm muted">You sign in with Google. Add a password to also sign in with email.</p>}
      </div>
      {status && <Alert tone={status.tone}>{status.text}</Alert>}
      {hasPassword && (
        <PasswordField label="Current password" autoComplete="current-password" error={formState.errors.currentPassword?.message} {...register('currentPassword')} />
      )}
      <div className="grid gap-4 sm:grid-cols-2">
        <div>
          <PasswordField label="New password" autoComplete="new-password" error={formState.errors.newPassword?.message} {...register('newPassword')} />
          <PasswordStrength password={watch('newPassword')} />
        </div>
        <PasswordField label="Confirm new password" autoComplete="new-password" error={formState.errors.confirmPassword?.message} {...register('confirmPassword')} />
      </div>
      <div className="flex justify-end">
        <Button type="submit" loading={formState.isSubmitting}>
          Update password
        </Button>
      </div>
    </form>
  );
}
