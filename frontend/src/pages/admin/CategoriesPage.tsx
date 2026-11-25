import { zodResolver } from '@hookform/resolvers/zod';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import clsx from 'clsx';
import { Pencil, Plus, Trash2 } from 'lucide-react';
import { useState } from 'react';
import { useForm } from 'react-hook-form';
import { z } from 'zod';
import { errorMessage } from '@/api/client';
import { adminCatalogApi } from '@/api/endpoints';
import type { ExamCategory, Subject } from '@/api/types';
import { CATEGORY_ICONS, CategoryIcon } from '@/components/CategoryIcon';
import { PageHeader } from '@/components/PageHeader';
import { Button } from '@/components/ui/Button';
import { Alert, Badge, Spinner } from '@/components/ui/Feedback';
import { Checkbox, SelectField, TextAreaField, TextField } from '@/components/ui/Field';
import { Modal } from '@/components/ui/Modal';

export default function CategoriesPage() {
  const categories = useQuery({ queryKey: ['admin', 'categories'], queryFn: adminCatalogApi.categories });
  const subjects = useQuery({ queryKey: ['admin', 'subjects'], queryFn: adminCatalogApi.subjects });
  const [editingCategory, setEditingCategory] = useState<ExamCategory | 'new' | null>(null);
  const [editingSubject, setEditingSubject] = useState<Subject | 'new' | null>(null);

  return (
    <div className="space-y-10">
      <PageHeader
        title="Categories & subjects"
        description="Exam categories students prepare for, and the subjects used to organise questions."
      />

      {/* Categories */}
      <section>
        <div className="mb-3 flex items-center justify-between">
          <h2 className="text-lg font-bold">Exam categories</h2>
          <Button size="sm" onClick={() => setEditingCategory('new')}>
            <Plus className="h-4 w-4" /> New category
          </Button>
        </div>
        {categories.isError && <Alert tone="error">{errorMessage(categories.error)}</Alert>}
        {categories.isPending ? (
          <Spinner />
        ) : (
          <div className="grid gap-3 sm:grid-cols-2 xl:grid-cols-3">
            {categories.data?.map((c) => (
              <div key={c.id} className={clsx('card flex flex-col p-4', !c.active && 'opacity-60')}>
                <div className="flex items-start gap-3">
                  <span className="flex h-10 w-10 shrink-0 items-center justify-center rounded-xl bg-brand-50 text-brand-600 dark:bg-brand-950/50 dark:text-brand-400">
                    <CategoryIcon name={c.icon} />
                  </span>
                  <div className="min-w-0 flex-1">
                    <p className="font-semibold leading-tight">{c.name}</p>
                    <p className="mt-0.5 text-xs muted">
                      /{c.slug} · order {c.displayOrder}
                    </p>
                  </div>
                  <button
                    type="button"
                    onClick={() => setEditingCategory(c)}
                    className="rounded-lg p-1.5 text-slate-500 hover:bg-slate-100 dark:hover:bg-gray-700"
                    aria-label={`Edit ${c.name}`}
                  >
                    <Pencil className="h-4 w-4" />
                  </button>
                </div>
                <div className="mt-3 flex flex-wrap gap-1">
                  {c.subjects.map((s) => (
                    <Badge key={s.id}>{s.name}</Badge>
                  ))}
                  {c.subjects.length === 0 && <span className="text-xs muted">No subjects linked</span>}
                </div>
                <div className="mt-3 flex items-center gap-2 text-xs muted">
                  {c.active ? <Badge tone="success">Active</Badge> : <Badge tone="warning">Hidden</Badge>}
                  <span>{c.publishedExamCount} published exams</span>
                </div>
              </div>
            ))}
          </div>
        )}
      </section>

      {/* Subjects */}
      <section>
        <div className="mb-3 flex items-center justify-between">
          <h2 className="text-lg font-bold">Subjects</h2>
          <Button size="sm" variant="secondary" onClick={() => setEditingSubject('new')}>
            <Plus className="h-4 w-4" /> New subject
          </Button>
        </div>
        {subjects.isError && <Alert tone="error">{errorMessage(subjects.error)}</Alert>}
        <div className="card overflow-hidden">
          <div className="overflow-x-auto">
            <table className="w-full min-w-[520px] text-left text-sm">
              <thead className="border-b border-slate-200 bg-slate-50 text-xs uppercase tracking-wider text-slate-500 dark:border-gray-700 dark:bg-gray-800 dark:text-slate-400">
                <tr>
                  <th className="px-4 py-3 font-semibold">Subject</th>
                  <th className="px-4 py-3 font-semibold">Questions</th>
                  <th className="px-4 py-3 text-right font-semibold">Actions</th>
                </tr>
              </thead>
              <tbody className="divide-y divide-slate-100 dark:divide-gray-700/70">
                {subjects.isPending && (
                  <tr>
                    <td colSpan={3} className="px-4 py-6"><Spinner className="mx-auto" /></td>
                  </tr>
                )}
                {subjects.data?.map((s) => (
                  <tr key={s.id}>
                    <td className="px-4 py-3">
                      <div className="font-semibold">{s.name}</div>
                      {s.description && <div className="text-xs muted">{s.description}</div>}
                    </td>
                    <td className="px-4 py-3">{s.questionCount ?? 0}</td>
                    <td className="px-4 py-3 text-right">
                      <Button size="sm" variant="ghost" onClick={() => setEditingSubject(s)}>
                        <Pencil className="h-4 w-4" /> Edit
                      </Button>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        </div>
      </section>

      <CategoryDialog
        key={editingCategory === 'new' ? 'new' : editingCategory?.id ?? 'none'}
        category={editingCategory}
        subjects={subjects.data ?? []}
        onClose={() => setEditingCategory(null)}
      />
      <SubjectDialog
        key={editingSubject === 'new' ? 'new-s' : editingSubject?.id ?? 'none-s'}
        subject={editingSubject}
        onClose={() => setEditingSubject(null)}
      />
    </div>
  );
}

// ------------------------------------------------------------------ category dialog

const categorySchema = z.object({
  name: z.string().trim().min(2, 'Enter a name').max(100),
  description: z.string().max(1000),
  icon: z.string(),
  displayOrder: z.coerce.number().int().min(0).max(10000),
  active: z.boolean(),
  subjectIds: z.array(z.number()),
});
type CategoryForm = z.infer<typeof categorySchema>;

function CategoryDialog({ category, subjects, onClose }: { category: ExamCategory | 'new' | null; subjects: Subject[]; onClose: () => void }) {
  const queryClient = useQueryClient();
  const existing = category && category !== 'new' ? category : null;
  const [error, setError] = useState<string | null>(null);
  const [confirmDelete, setConfirmDelete] = useState(false);

  const { register, handleSubmit, watch, setValue, formState } = useForm<CategoryForm>({
    resolver: zodResolver(categorySchema),
    defaultValues: {
      name: existing?.name ?? '',
      description: existing?.description ?? '',
      icon: existing?.icon ?? 'Award',
      displayOrder: existing?.displayOrder ?? 100,
      active: existing?.active ?? true,
      subjectIds: existing?.subjects.map((s) => s.id) ?? [],
    },
  });
  const selected = watch('subjectIds');
  const icon = watch('icon');

  const done = () => {
    queryClient.invalidateQueries({ queryKey: ['admin', 'categories'] });
    queryClient.invalidateQueries({ queryKey: ['catalog'] });
    onClose();
  };

  const save = useMutation({
    mutationFn: (v: CategoryForm) => (existing ? adminCatalogApi.updateCategory(existing.id, v) : adminCatalogApi.createCategory(v)),
    onSuccess: done,
    onError: (e) => setError(errorMessage(e)),
  });
  const remove = useMutation({
    mutationFn: () => adminCatalogApi.deleteCategory(existing!.id),
    onSuccess: done,
    onError: (e) => {
      setConfirmDelete(false);
      setError(errorMessage(e));
    },
  });

  const toggleSubject = (id: number) =>
    setValue('subjectIds', selected.includes(id) ? selected.filter((s) => s !== id) : [...selected, id], { shouldDirty: true });

  return (
    <Modal
      open={category !== null}
      title={existing ? `Edit ${existing.name}` : 'New exam category'}
      onClose={onClose}
      size="lg"
      footer={
        <>
          {existing && !confirmDelete && (
            <Button variant="ghost" className="mr-auto text-red-600 dark:text-red-400" onClick={() => setConfirmDelete(true)}>
              <Trash2 className="h-4 w-4" /> Delete
            </Button>
          )}
          {confirmDelete && (
            <Button variant="danger" className="mr-auto" loading={remove.isPending} onClick={() => remove.mutate()}>
              Confirm delete
            </Button>
          )}
          <Button variant="secondary" onClick={onClose}>
            Cancel
          </Button>
          <Button loading={save.isPending} onClick={handleSubmit((v) => { setError(null); save.mutate(v); })}>
            Save
          </Button>
        </>
      }
    >
      <form className="space-y-4" onSubmit={(e) => e.preventDefault()}>
        {error && <Alert tone="error">{error}</Alert>}
        <TextField label="Name" error={formState.errors.name?.message} {...register('name')} />
        <TextAreaField label="Description" rows={2} {...register('description')} />
        <div className="grid gap-4 sm:grid-cols-2">
          <SelectField label="Icon" {...register('icon')}>
            {Object.keys(CATEGORY_ICONS).map((n) => (
              <option key={n} value={n}>
                {n}
              </option>
            ))}
          </SelectField>
          <TextField
            label="Display order"
            type="number"
            min={0}
            error={formState.errors.displayOrder?.message}
            {...register('displayOrder')}
          />
        </div>
        <div className="flex items-center gap-2 text-sm muted">
          Preview: <CategoryIcon name={icon} className="h-5 w-5 text-brand-600" />
        </div>
        <Checkbox label="Active (visible to students)" {...register('active')} />
        <fieldset>
          <legend className="mb-2 text-sm font-medium text-slate-700 dark:text-slate-300">Subjects</legend>
          <div className="flex flex-wrap gap-2">
            {subjects.map((s) => {
              const on = selected.includes(s.id);
              return (
                <button
                  key={s.id}
                  type="button"
                  aria-pressed={on}
                  onClick={() => toggleSubject(s.id)}
                  className={clsx(
                    'rounded-full border px-3 py-1 text-xs font-semibold transition-colors',
                    on
                      ? 'border-brand-600 bg-brand-600 text-white'
                      : 'border-slate-300 text-slate-600 hover:border-brand-400 dark:border-gray-600 dark:text-slate-300',
                  )}
                >
                  {s.name}
                </button>
              );
            })}
          </div>
        </fieldset>
      </form>
    </Modal>
  );
}

// ------------------------------------------------------------------ subject dialog

const subjectSchema = z.object({
  name: z.string().trim().min(2, 'Enter a name').max(100),
  description: z.string().max(500),
});

function SubjectDialog({ subject, onClose }: { subject: Subject | 'new' | null; onClose: () => void }) {
  const queryClient = useQueryClient();
  const existing = subject && subject !== 'new' ? subject : null;
  const [error, setError] = useState<string | null>(null);
  const { register, handleSubmit, formState } = useForm<z.infer<typeof subjectSchema>>({
    resolver: zodResolver(subjectSchema),
    defaultValues: { name: existing?.name ?? '', description: existing?.description ?? '' },
  });

  const done = () => {
    queryClient.invalidateQueries({ queryKey: ['admin', 'subjects'] });
    queryClient.invalidateQueries({ queryKey: ['admin', 'categories'] });
    onClose();
  };
  const save = useMutation({
    mutationFn: (v: z.infer<typeof subjectSchema>) =>
      existing ? adminCatalogApi.updateSubject(existing.id, v) : adminCatalogApi.createSubject(v),
    onSuccess: done,
    onError: (e) => setError(errorMessage(e)),
  });
  const remove = useMutation({
    mutationFn: () => adminCatalogApi.deleteSubject(existing!.id),
    onSuccess: done,
    onError: (e) => setError(errorMessage(e)),
  });

  return (
    <Modal
      open={subject !== null}
      title={existing ? `Edit ${existing.name}` : 'New subject'}
      onClose={onClose}
      footer={
        <>
          {existing && (
            <Button variant="ghost" className="mr-auto text-red-600 dark:text-red-400" loading={remove.isPending} onClick={() => remove.mutate()}>
              <Trash2 className="h-4 w-4" /> Delete
            </Button>
          )}
          <Button variant="secondary" onClick={onClose}>
            Cancel
          </Button>
          <Button loading={save.isPending} onClick={handleSubmit((v) => { setError(null); save.mutate(v); })}>
            Save
          </Button>
        </>
      }
    >
      <form className="space-y-4" onSubmit={(e) => e.preventDefault()}>
        {error && <Alert tone="error">{error}</Alert>}
        {existing && (existing.questionCount ?? 0) > 0 && (
          <Alert tone="info">This subject has {existing.questionCount} questions, so it can be renamed but not deleted.</Alert>
        )}
        <TextField label="Name" error={formState.errors.name?.message} {...register('name')} />
        <TextAreaField label="Description (optional)" rows={2} {...register('description')} />
      </form>
    </Modal>
  );
}
