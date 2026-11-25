import { Hammer } from 'lucide-react';
import { EmptyState } from '@/components/ui/Feedback';
import { PageHeader } from '@/components/PageHeader';

/** Placeholder for screens scheduled in later phases (see docs/PROJECT_PLAN.md, Progress tracker). */
export default function ComingSoonPage({ title, description, phase }: { title: string; description: string; phase: number }) {
  return (
    <div>
      <PageHeader title={title} description={description} />
      <EmptyState icon={<Hammer className="h-6 w-6" />} title="Under construction">
        This screen is part of phase {phase} of the ExamForge build plan.
      </EmptyState>
    </div>
  );
}
