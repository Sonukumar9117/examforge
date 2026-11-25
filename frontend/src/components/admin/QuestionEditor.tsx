import { zodResolver } from '@hookform/resolvers/zod';
import { useMutation, useQueryClient } from '@tanstack/react-query';
import clsx from 'clsx';
import { Plus, Trash2, X } from 'lucide-react';
import { useState } from 'react';
import { useFieldArray, useForm } from 'react-hook-form';
import { z } from 'zod';
import { errorMessage } from '@/api/client';
import { adminQuestionApi } from '@/api/endpoints';
import type { Question, QuestionInput, Subject } from '@/api/types';
import { Button } from '@/components/ui/Button';
import { Alert } from '@/components/ui/Feedback';
import { SelectField, TextAreaField, TextField } from '@/components/ui/Field';
import { Modal } from '@/components/ui/Modal';

const schema = z
  .object({
    subjectId: z.coerce.number({ invalid_type_error: 'Choose a subject' }).min(1, 'Choose a subject'),
    topic: z.string().max(100),
    type: z.enum(['MCQ', 'MULTI_SELECT', 'TRUE_FALSE']),
    questionText: z.string().trim().min(3, 'Write the question'),
    explanation: z.string().max(10000),
    difficulty: z.enum(['EASY', 'MEDIUM', 'HARD']),
    marks: z.coerce.number().min(0).max(100),
    negativeMarks: z.coerce.number().min(0).max(100),
    status: z.enum(['DRAFT', 'APPROVED']),
    source: z.string().max(255),
    tagsText: z.string(),
    options: z.array(z.object({ text: z.string().trim().min(1, 'Option text is required'), correct: z.boolean() })).min(2).max(8),
  })
  .superRefine((v, ctx) => {
    const correct = v.options.filter((o) => o.correct).length;
    if (v.type !== 'MULTI_SELECT' && correct !== 1) ctx.addIssue({ code: 'custom', path: ['options'], message: 'Mark exactly one correct option' });
    if (v.type === 'MULTI_SELECT' && correct < 1) ctx.addIssue({ code: 'custom', path: ['options'], message: 'Mark at least one correct option' });
    if (v.type === 'TRUE_FALSE' && v.options.length !== 2) ctx.addIssue({ code: 'custom', path: ['options'], message: 'True/false needs exactly two options' });
    if (v.negativeMarks > v.marks) ctx.addIssue({ code: 'custom', path: ['negativeMarks'], message: 'Cannot exceed marks' });
  });
type FormValues = z.infer<typeof schema>;

const blankOptions = () => [
  { text: '', correct: true },
  { text: '', correct: false },
  { text: '', correct: false },
  { text: '', correct: false },
];

export function QuestionEditor({
  question,
  subjects,
  onClose,
}: {
  question: Question | 'new' | null;
  subjects: Subject[];
  onClose: () => void;
}) {
  const queryClient = useQueryClient();
  const existing = question && question !== 'new' ? question : null;
  const [error, setError] = useState<string | null>(null);

  const { register, control, handleSubmit, watch, setValue, formState } = useForm<FormValues>({
    resolver: zodResolver(schema),
    defaultValues: existing
      ? {
          subjectId: existing.subject.id,
          topic: existing.topic ?? '',
          type: existing.type,
          questionText: existing.questionText,
          explanation: existing.explanation ?? '',
          difficulty: existing.difficulty,
          marks: existing.marks,
          negativeMarks: existing.negativeMarks,
          status: existing.status,
          source: existing.source ?? '',
          tagsText: existing.tags.join(', '),
          options: existing.options.map((o) => ({ text: o.text, correct: o.correct })),
        }
      : {
          subjectId: 0,
          topic: '',
          type: 'MCQ',
          questionText: '',
          explanation: '',
          difficulty: 'MEDIUM',
          marks: 1,
          negativeMarks: 0,
          status: 'DRAFT',
          source: '',
          tagsText: '',
          options: blankOptions(),
        },
  });
  const { fields, append, remove, replace } = useFieldArray({ control, name: 'options' });
  const type = watch('type');
  const options = watch('options');

  const markCorrect = (index: number) => {
    if (type === 'MULTI_SELECT') setValue(`options.${index}.correct`, !options[index].correct, { shouldValidate: formState.isSubmitted });
    else options.forEach((_, i) => setValue(`options.${i}.correct`, i === index, { shouldValidate: formState.isSubmitted }));
  };

  const onTypeChange = (t: FormValues['type']) => {
    setValue('type', t);
    if (t === 'TRUE_FALSE') replace([{ text: 'True', correct: true }, { text: 'False', correct: false }]);
    else if (type === 'TRUE_FALSE') replace(blankOptions());
  };

  const save = useMutation({
    mutationFn: (v: FormValues) => {
      const body: QuestionInput = {
        subjectId: v.subjectId,
        topic: v.topic,
        type: v.type,
        questionText: v.questionText,
        explanation: v.explanation,
        difficulty: v.difficulty,
        marks: v.marks,
        negativeMarks: v.negativeMarks,
        status: v.status,
        source: v.source,
        tags: v.tagsText.split(',').map((t) => t.trim()).filter(Boolean),
        options: v.options,
      };
      return existing ? adminQuestionApi.update(existing.id, body) : adminQuestionApi.create(body);
    },
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['admin', 'questions'] });
      queryClient.invalidateQueries({ queryKey: ['admin', 'subjects'] });
      onClose();
    },
    onError: (e) => setError(errorMessage(e)),
  });

  const optionsError = (formState.errors.options as { message?: string; root?: { message?: string } } | undefined);

  return (
    <Modal
      open={question !== null}
      title={existing ? `Edit question #${existing.id}` : 'New question'}
      onClose={onClose}
      size="xl"
      footer={
        <>
          <Button variant="secondary" onClick={onClose}>
            Cancel
          </Button>
          <Button loading={save.isPending} onClick={handleSubmit((v) => { setError(null); save.mutate(v); })}>
            Save question
          </Button>
        </>
      }
    >
      <form className="space-y-4" onSubmit={(e) => e.preventDefault()}>
        {error && <Alert tone="error">{error}</Alert>}
        <div className="grid gap-4 sm:grid-cols-3">
          <SelectField label="Subject" error={formState.errors.subjectId?.message} {...register('subjectId')}>
            <option value={0}>Choose…</option>
            {subjects.map((s) => (
              <option key={s.id} value={s.id}>
                {s.name}
              </option>
            ))}
          </SelectField>
          <TextField label="Topic (optional)" placeholder="e.g. Kinematics" {...register('topic')} />
          <SelectField label="Type" value={type} onChange={(e) => onTypeChange(e.target.value as FormValues['type'])}>
            <option value="MCQ">Single answer (MCQ)</option>
            <option value="MULTI_SELECT">Multiple answers</option>
            <option value="TRUE_FALSE">True / False</option>
          </SelectField>
        </div>

        <TextAreaField label="Question" rows={3} error={formState.errors.questionText?.message} {...register('questionText')} />

        <fieldset>
          <legend className="mb-2 text-sm font-medium text-slate-700 dark:text-slate-300">
            Options <span className="font-normal muted">— click the letter to mark {type === 'MULTI_SELECT' ? 'correct answers' : 'the correct answer'}</span>
          </legend>
          <div className="space-y-2">
            {fields.map((field, i) => {
              const correct = options[i]?.correct;
              return (
                <div key={field.id} className="flex items-start gap-2">
                  <button
                    type="button"
                    onClick={() => markCorrect(i)}
                    aria-pressed={correct}
                    aria-label={`Mark option ${String.fromCharCode(65 + i)} as ${correct ? 'not correct' : 'correct'}`}
                    className={clsx(
                      'mt-0.5 flex h-10 w-10 shrink-0 items-center justify-center rounded-xl border text-sm font-bold transition-colors',
                      correct
                        ? 'border-emerald-500 bg-emerald-500 text-white'
                        : 'border-slate-300 text-slate-500 hover:border-emerald-400 dark:border-gray-600',
                    )}
                  >
                    {String.fromCharCode(65 + i)}
                  </button>
                  <div className="flex-1">
                    <input
                      aria-label={`Option ${String.fromCharCode(65 + i)}`}
                      readOnly={type === 'TRUE_FALSE'}
                      className="block h-10 w-full rounded-xl border border-slate-300 bg-white px-3.5 text-sm focus:border-brand-500 focus:outline-none focus:ring-2 focus:ring-brand-500/30 dark:border-gray-600 dark:bg-gray-900/60"
                      {...register(`options.${i}.text`)}
                    />
                    {formState.errors.options?.[i]?.text && (
                      <p className="mt-1 text-xs text-red-600 dark:text-red-400">{formState.errors.options[i]?.text?.message}</p>
                    )}
                  </div>
                  {type !== 'TRUE_FALSE' && fields.length > 2 && (
                    <button type="button" onClick={() => remove(i)} className="mt-0.5 rounded-lg p-2.5 text-slate-400 hover:bg-slate-100 hover:text-red-600 dark:hover:bg-gray-700" aria-label={`Remove option ${String.fromCharCode(65 + i)}`}>
                      <X className="h-4 w-4" />
                    </button>
                  )}
                </div>
              );
            })}
          </div>
          {(optionsError?.message || optionsError?.root?.message) && (
            <p role="alert" className="mt-2 text-xs font-medium text-red-600 dark:text-red-400">
              {optionsError?.message ?? optionsError?.root?.message}
            </p>
          )}
          {type !== 'TRUE_FALSE' && fields.length < 8 && (
            <Button size="sm" variant="ghost" className="mt-2" onClick={() => append({ text: '', correct: false })}>
              <Plus className="h-4 w-4" /> Add option
            </Button>
          )}
        </fieldset>

        <TextAreaField label="Explanation (shown after the exam)" rows={2} {...register('explanation')} />

        <div className="grid gap-4 sm:grid-cols-4">
          <SelectField label="Difficulty" {...register('difficulty')}>
            <option value="EASY">Easy</option>
            <option value="MEDIUM">Medium</option>
            <option value="HARD">Hard</option>
          </SelectField>
          <TextField label="Marks" type="number" step="0.25" min={0} error={formState.errors.marks?.message} {...register('marks')} />
          <TextField label="Negative marks" type="number" step="0.01" min={0} error={formState.errors.negativeMarks?.message} {...register('negativeMarks')} />
          <SelectField label="Status" {...register('status')}>
            <option value="DRAFT">Draft</option>
            <option value="APPROVED">Approved</option>
          </SelectField>
        </div>
        <div className="grid gap-4 sm:grid-cols-2">
          <TextField label="Tags (comma separated)" placeholder="kinematics, units" {...register('tagsText')} />
          <TextField label="Source (optional)" placeholder="e.g. Written by ExamForge team" {...register('source')} />
        </div>
      </form>
    </Modal>
  );
}

export function DeleteQuestionButton({ id, onDone }: { id: number; onDone: () => void }) {
  const [confirm, setConfirm] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const remove = useMutation({
    mutationFn: () => adminQuestionApi.remove(id),
    onSuccess: onDone,
    onError: (e) => {
      setConfirm(false);
      setError(errorMessage(e));
    },
  });
  return (
    <span className="inline-flex flex-col items-end">
      {confirm ? (
        <Button size="sm" variant="danger" loading={remove.isPending} onClick={() => remove.mutate()}>
          Confirm
        </Button>
      ) : (
        <Button size="sm" variant="ghost" onClick={() => setConfirm(true)} aria-label={`Delete question ${id}`}>
          <Trash2 className="h-4 w-4" />
        </Button>
      )}
      {error && <span className="mt-1 max-w-[14rem] text-right text-xs text-red-600 dark:text-red-400">{error}</span>}
    </span>
  );
}
