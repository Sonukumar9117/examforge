import { keepPreviousData, useQuery } from '@tanstack/react-query';
import clsx from 'clsx';
import { Compass, Search } from 'lucide-react';
import { useEffect, useState } from 'react';
import { useSearchParams } from 'react-router-dom';
import { errorMessage } from '@/api/client';
import { catalogApi, examApi } from '@/api/endpoints';
import type { Difficulty, ExamType } from '@/api/types';
import { CategoryIcon } from '@/components/CategoryIcon';
import { ExamCard } from '@/components/ExamCard';
import { PageHeader } from '@/components/PageHeader';
import { Button } from '@/components/ui/Button';
import { Alert, EmptyState, Spinner } from '@/components/ui/Feedback';

const selectClass = 'h-10 rounded-xl border border-slate-300 bg-white px-3 text-sm dark:border-gray-600 dark:bg-gray-800';

export default function ExplorePage() {
  // Filters live in the URL so results can be shared and survive a refresh.
  const [params, setParams] = useSearchParams();
  const category = params.get('category') ?? '';
  const subjectId = params.get('subject') ?? '';
  const difficulty = (params.get('difficulty') ?? '') as Difficulty | '';
  const type = (params.get('type') ?? '') as ExamType | '';
  const q = params.get('q') ?? '';
  const page = Number(params.get('page') ?? 0);
  const [search, setSearch] = useState(q);

  const set = (patch: Record<string, string>) => {
    const next = new URLSearchParams(params);
    Object.entries(patch).forEach(([k, v]) => (v ? next.set(k, v) : next.delete(k)));
    if (!('page' in patch)) next.delete('page');
    setParams(next, { replace: true });
  };

  useEffect(() => {
    if (search.trim() === q) return;
    const t = setTimeout(() => set({ q: search.trim() }), 300);
    return () => clearTimeout(t);
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [search]);

  const categories = useQuery({ queryKey: ['catalog', 'categories'], queryFn: catalogApi.categories });
  const activeCategory = categories.data?.find((c) => c.slug === category);
  const exams = useQuery({
    queryKey: ['exams', 'browse', q, category, subjectId, difficulty, type, page],
    queryFn: () =>
      examApi.browse({ q, category, subjectId: subjectId ? Number(subjectId) : '', difficulty, type, page, size: 12 }),
    placeholderData: keepPreviousData,
  });

  return (
    <div>
      <PageHeader title="Explore exams" description="Find mock tests by exam, subject and difficulty." />

      {/* Category chips */}
      <div className="-mx-4 mb-5 flex gap-2 overflow-x-auto px-4 pb-1 sm:mx-0 sm:flex-wrap sm:px-0" role="tablist" aria-label="Exam categories">
        <button
          type="button"
          role="tab"
          aria-selected={!category}
          onClick={() => set({ category: '', subject: '' })}
          className={clsx(
            'shrink-0 rounded-full border px-4 py-2 text-sm font-semibold transition-colors',
            !category ? 'border-brand-600 bg-brand-600 text-white' : 'border-slate-300 hover:border-brand-400 dark:border-gray-600',
          )}
        >
          All exams
        </button>
        {categories.data?.map((c) => (
          <button
            key={c.id}
            type="button"
            role="tab"
            aria-selected={category === c.slug}
            onClick={() => set({ category: c.slug, subject: '' })}
            className={clsx(
              'flex shrink-0 items-center gap-2 rounded-full border px-4 py-2 text-sm font-semibold transition-colors',
              category === c.slug
                ? 'border-brand-600 bg-brand-600 text-white'
                : 'border-slate-300 hover:border-brand-400 dark:border-gray-600',
            )}
          >
            <CategoryIcon name={c.icon} className="h-4 w-4" />
            {c.name}
            <span className={clsx('text-xs', category === c.slug ? 'text-white/80' : 'muted')}>{c.publishedExamCount}</span>
          </button>
        ))}
      </div>

      {activeCategory?.description && <p className="mb-4 text-sm muted">{activeCategory.description}</p>}

      <div className="mb-6 grid gap-3 sm:grid-cols-[1fr_auto_auto_auto]">
        <label className="relative">
          <span className="sr-only">Search exams</span>
          <Search className="pointer-events-none absolute left-3 top-1/2 h-4 w-4 -translate-y-1/2 text-slate-400" />
          <input
            value={search}
            onChange={(e) => setSearch(e.target.value)}
            placeholder="Search exams by name"
            className="h-10 w-full rounded-xl border border-slate-300 bg-white pl-9 pr-3 text-sm focus:border-brand-500 focus:outline-none focus:ring-2 focus:ring-brand-500/30 dark:border-gray-600 dark:bg-gray-800"
          />
        </label>
        <select aria-label="Subject" className={selectClass} value={subjectId} onChange={(e) => set({ subject: e.target.value })} disabled={!activeCategory}>
          <option value="">{activeCategory ? 'All subjects' : 'Pick an exam first'}</option>
          {activeCategory?.subjects.map((s) => (
            <option key={s.id} value={s.id}>
              {s.name}
            </option>
          ))}
        </select>
        <select aria-label="Difficulty" className={selectClass} value={difficulty} onChange={(e) => set({ difficulty: e.target.value })}>
          <option value="">Any difficulty</option>
          <option value="EASY">Easy</option>
          <option value="MEDIUM">Medium</option>
          <option value="HARD">Hard</option>
        </select>
        <select aria-label="Test type" className={selectClass} value={type} onChange={(e) => set({ type: e.target.value })}>
          <option value="">Any type</option>
          <option value="PRACTICE">Practice</option>
          <option value="MOCK">Mock test</option>
          <option value="LIVE">Live test</option>
        </select>
      </div>

      {exams.isError && <Alert tone="error">{errorMessage(exams.error)}</Alert>}
      {exams.isPending ? (
        <Spinner />
      ) : exams.data && exams.data.totalElements === 0 ? (
        <EmptyState icon={<Compass className="h-6 w-6" />} title="No exams match">
          Try another category or clear the filters. New mock tests are added regularly.
        </EmptyState>
      ) : (
        <>
          <div className="grid gap-4 sm:grid-cols-2 lg:grid-cols-3">
            {exams.data?.content.map((e) => (
              <ExamCard key={e.id} exam={e} />
            ))}
          </div>
          {exams.data && exams.data.totalPages > 1 && (
            <div className="mt-6 flex items-center justify-center gap-3 text-sm">
              <Button size="sm" variant="secondary" disabled={page === 0} onClick={() => set({ page: String(page - 1) })}>
                Previous
              </Button>
              <span className="muted">
                Page {page + 1} of {exams.data.totalPages}
              </span>
              <Button size="sm" variant="secondary" disabled={page + 1 >= exams.data.totalPages} onClick={() => set({ page: String(page + 1) })}>
                Next
              </Button>
            </div>
          )}
        </>
      )}
    </div>
  );
}
