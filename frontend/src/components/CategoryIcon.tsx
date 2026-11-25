import {
  Award,
  Banknote,
  BookOpen,
  Calculator,
  ClipboardList,
  Cpu,
  FlaskConical,
  Globe,
  GraduationCap,
  Landmark,
  Map,
  Newspaper,
  Scale,
  Shield,
  Sparkles,
  TrainFront,
  type LucideIcon,
} from 'lucide-react';

/** Icons an admin can pick for a category. The name is stored in the database (shared with mobile). */
export const CATEGORY_ICONS: Record<string, LucideIcon> = {
  Landmark,
  ClipboardList,
  Banknote,
  TrainFront,
  Map,
  Cpu,
  FlaskConical,
  Newspaper,
  Sparkles,
  GraduationCap,
  BookOpen,
  Calculator,
  Globe,
  Scale,
  Shield,
  Award,
};

export function CategoryIcon({ name, className = 'h-5 w-5' }: { name: string | null; className?: string }) {
  const Icon = (name && CATEGORY_ICONS[name]) || Award;
  return <Icon className={className} aria-hidden="true" />;
}
