import { zodResolver } from '@hookform/resolvers/zod';
import { useState } from 'react';
import { useForm } from 'react-hook-form';
import { z } from 'zod';
import { errorMessage } from '@/api/client';
import type { ExamCategory, ExamDetail, ExamInput } from '@/api/types';
import { Button } from '@/components/ui/Button';
import { Alert } from '@/components/ui/Feedback';
import { Checkbox, SelectField, TextAreaField, TextField } from '@/components/ui/Field';

/** ISO instant <-> value for <input type="datetime-local"> (in the admin's local time zone). */
export const toLocalInput = (iso: string | null | undefined) => {
  if (!iso) return '';
  const d = new Date(iso);
  const pad = (n: number) => String(n).padStart(2, '0');
  return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())}T${pad(d.getHours())}:${pad(d.getMinutes())}`;
};
const fromLocalInput = (v: string) => (v ? new Date(v).toISOString() : null);

const optionalNumber = z.union([z.literal(''), z.coerce.number()]).transform((v) => (v === '' ? null : v));

const schema = z
  .object({
    categoryId: z.coerce.number().min(1, 'Choose a category'),
    title: z.string().trim().min(3, 'Enter a title').max(200),
    description: z.string().max(5000),
    instructions: z.string().max(10000),
    type: z.enum(['PRACTICE', 'MOCK', 'LIVE']),
    difficulty: z.enum(['EASY', 'MEDIUM', 'HARD']),
    durationMinutes: z.coerce.number().int().min(1, 'At least 1 minute').max(600, 'At most 600 minutes'),
    passMarks: optionalNumber.refine((v) => v === null || v >= 0, 'Must be 0 or more'),
    maxAttempts: optionalNumber.refine((v) => v === null || (Number.isInteger(v) && v >= 1 && v <= 100), '1–100, or empty for unlimited'),
    startsAt: z.string(),
    endsAt: z.string(),
    shuffleQuestions: z.boolean(),
    shuffleOptions: z.boolean(),
    allowNavigation: z.boolean(),
    resultRelease: z.enum(['IMMEDIATE', 'AFTER_END', 'MANUAL']),
  })
  .superRefine((v, ctx) => {
    if (v.type === 'LIVE' && (!v.startsAt || !v.endsAt)) {
      ctx.addIssue({ code: 'custom', path: ['startsAt'], message: 'A live exam needs a start and end time' });
    }
    if (v.startsAt && v.endsAt && new Date(v.endsAt) <= new Date(v.startsAt)) {
      ctx.addIssue({ code: 'custom', path: ['endsAt'], message: 'Must be after the start time' });
    }
  });
type FormIn = z.input<typeof schema>;
type FormOut = z.output<typeof schema>;

export const DEFAULT_INSTRUCTIONS = `1. The timer starts when you begin and cannot be paused.
2. Each question shows its marks; wrong answers may carry negative marks.
3. Use "Mark for review" to come back to a question later.
4. Your answers are saved automatically. The test is submitted when time runs out.`;

export function ExamDetailsForm({
  exam,
  categories,
  onSubmit,
  submitLabel,
  locked,
}: {
  exam: ExamDetail | null;
  categories: ExamCategory[];
  onSubmit: (input: ExamInput) => Promise<unknown>;
  submitLabel: string;
  locked?: boolean;
}) {
  const [status, setStatus] = useState<{ tone: 'success' | 'error'; text: string } | null>(null);
  const s = exam?.summary;
  const { register, handleSubmit, watch, formState } = useForm<FormIn, unknown, FormOut>({
    resolver: zodResolver(schema),
    defaultValues: {
      categoryId: s?.category.id ?? 0,
      title: s?.title ?? '',
      description: exam?.description ?? '',
      instructions: exam?.instructions ?? DEFAULT_INSTRUCTIONS,
      type: s?.type ?? 'MOCK',
      difficulty: s?.difficulty ?? 'MEDIUM',
      durationMinutes: s?.durationMinutes ?? 60,
      passMarks: s?.passMarks ?? '',
      maxAttempts: s?.maxAttempts ?? '',
      startsAt: toLocalInput(s?.startsAt),
      endsAt: toLocalInput(s?.endsAt),
      shuffleQuestions: exam?.shuffleQuestions ?? false,
      shuffleOptions: exam?.shuffleOptions ?? false,
      allowNavigation: exam?.allowNavigation ?? true,
      resultRelease: exam?.resultRelease ?? 'IMMEDIATE',
    },
  });
  const type = watch('type');
  const errors = formState.errors;

  const submit = handleSubmit(async (v) => {
    setStatus(null);
    try {
      await onSubmit({ ...v, startsAt: fromLocalInput(v.startsAt), endsAt: fromLocalInput(v.endsAt) });
      if (exam) setStatus({ tone: 'success', text: 'Saved.' });
    } catch (e) {
      setStatus({ tone: 'error', text: errorMessage(e) });
    }
  });

  return (
    <form onSubmit={submit} noValidate className="space-y-4">
      {status && <Alert tone={status.tone}>{status.text}</Alert>}
      <fieldset disabled={locked} className="space-y-4 disabled:opacity-70">
        <div className="grid gap-4 sm:grid-cols-3">
          <TextField label="Title" className="sm:col-span-2" error={errors.title?.message} {...register('title')} />
          <SelectField label="Category" error={errors.categoryId?.message} {...register('categoryId')}>
            <option value={0}>Choose…</option>
            {categories.map((c) => (
              <option key={c.id} value={c.id}>
                {c.name}
              </option>
            ))}
          </SelectField>
        </div>
        <TextAreaField label="Description (shown on the exam card)" rows={2} {...register('description')} />
        <div className="grid gap-4 sm:grid-cols-4">
          <SelectField label="Type" {...register('type')}>
            <option value="PRACTICE">Practice</option>
            <option value="MOCK">Mock test</option>
            <option value="LIVE">Live (scheduled)</option>
          </SelectField>
          <SelectField label="Difficulty" {...register('difficulty')}>
            <option value="EASY">Easy</option>
            <option value="MEDIUM">Medium</option>
            <option value="HARD">Hard</option>
          </SelectField>
          <TextField label="Duration (minutes)" type="number" min={1} max={600} error={errors.durationMinutes?.message} {...register('durationMinutes')} />
          <TextField label="Pass marks (optional)" type="number" min={0} step="0.5" error={errors.passMarks?.message} {...register('passMarks')} />
        </div>
        <div className="grid gap-4 sm:grid-cols-3">
          <TextField
            label={type === 'LIVE' ? 'Starts at' : 'Available from (optional)'}
            type="datetime-local"
            error={errors.startsAt?.message}
            {...register('startsAt')}
          />
          <TextField label={type === 'LIVE' ? 'Ends at' : 'Available until (optional)'} type="datetime-local" error={errors.endsAt?.message} {...register('endsAt')} />
          {type === 'LIVE' ? (
            <div className="text-xs muted sm:pt-7">One attempt per student. The leaderboard opens when the test ends.</div>
          ) : (
            <TextField label="Attempt limit (optional)" type="number" min={1} max={100} placeholder="Unlimited" error={errors.maxAttempts?.message} {...register('maxAttempts')} />
          )}
        </div>
        <TextAreaField label="Instructions (shown before the test starts)" rows={5} {...register('instructions')} />
        <div className="grid gap-3 sm:grid-cols-2">
          <Checkbox label="Allow moving between questions freely" {...register('allowNavigation')} />
          <Checkbox label="Shuffle question order for each student" {...register('shuffleQuestions')} />
          <Checkbox label="Shuffle answer options" {...register('shuffleOptions')} />
          <SelectField label="Show answers & explanations" {...register('resultRelease')}>
            <option value="IMMEDIATE">Right after submitting</option>
            <option value="AFTER_END">After the exam window ends</option>
            <option value="MANUAL">When an admin releases them</option>
          </SelectField>
        </div>
      </fieldset>
      {!locked && (
        <div className="flex justify-end">
          <Button type="submit" loading={formState.isSubmitting}>
            {submitLabel}
          </Button>
        </div>
      )}
    </form>
  );
}
