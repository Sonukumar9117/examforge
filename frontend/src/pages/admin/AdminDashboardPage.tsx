import { useQuery } from '@tanstack/react-query';
import { BookCopy, FileQuestion, FolderTree, MailCheck, ShieldCheck, Timer, UserPlus, Users } from 'lucide-react';
import { Link } from 'react-router-dom';
import { errorMessage } from '@/api/client';
import { adminApi } from '@/api/endpoints';
import { PageHeader, StatCard } from '@/components/PageHeader';
import { Alert, Spinner } from '@/components/ui/Feedback';

export default function AdminDashboardPage() {
  const stats = useQuery({ queryKey: ['admin', 'stats'], queryFn: adminApi.stats });

  return (
    <div className="space-y-8">
      <PageHeader title="Admin dashboard" description="Platform overview: users, exams and the question bank." />

      {stats.isError && <Alert tone="error">{errorMessage(stats.error)}</Alert>}
      {stats.isPending ? (
        <Spinner />
      ) : stats.data ? (
        <section className="grid grid-cols-2 gap-4 lg:grid-cols-4">
          <StatCard label="Total users" value={stats.data.totalUsers} icon={<Users className="h-4 w-4" />} hint={`${stats.data.totalStudents} students`} />
          <StatCard label="Admins" value={stats.data.totalAdmins} icon={<ShieldCheck className="h-4 w-4" />} />
          <StatCard label="Verified emails" value={stats.data.verifiedUsers} icon={<MailCheck className="h-4 w-4" />} />
          <StatCard label="New this week" value={stats.data.newUsersLast7Days} icon={<UserPlus className="h-4 w-4" />} hint="Last 7 days" />
          <StatCard label="Published exams" value={stats.data.publishedExams} icon={<BookCopy className="h-4 w-4" />} hint={`${stats.data.draftExams} drafts`} />
          <StatCard label="Questions" value={stats.data.totalQuestions} icon={<FileQuestion className="h-4 w-4" />} hint={`${stats.data.approvedQuestions} approved`} />
          <StatCard label="Exam categories" value={stats.data.totalCategories} icon={<FolderTree className="h-4 w-4" />} />
          <StatCard label="Test attempts" value={stats.data.totalAttempts} icon={<Timer className="h-4 w-4" />} hint={`${stats.data.finishedAttempts} finished`} />
        </section>
      ) : null}

      <section className="grid gap-4 sm:grid-cols-3">
        {[
          { to: '/admin/exams', title: 'Create an exam', text: 'Set duration, marks, negative marking and schedule.', Icon: BookCopy },
          { to: '/admin/questions', title: 'Build the question bank', text: 'Add questions or import them from CSV/JSON.', Icon: FileQuestion },
          { to: '/admin/users', title: 'Manage users', text: 'Search accounts, change roles, disable access.', Icon: Users },
        ].map(({ to, title, text, Icon }) => (
          <Link key={to} to={to} className="card group p-5 transition hover:border-brand-300 dark:hover:border-brand-800">
            <Icon className="h-5 w-5 text-brand-600 dark:text-brand-400" />
            <h2 className="mt-3 font-bold group-hover:text-brand-700 dark:group-hover:text-brand-300">{title}</h2>
            <p className="mt-1 text-sm muted">{text}</p>
          </Link>
        ))}
      </section>
    </div>
  );
}
