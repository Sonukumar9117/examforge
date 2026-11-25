import { Link } from 'react-router-dom';
import { Logo } from '@/components/brand/Logo';
import { ThemeToggle } from '@/components/ThemeToggle';

export function NotFoundPage() {
  return (
    <div className="flex min-h-screen flex-col items-center justify-center gap-4 px-4 text-center">
      <Logo />
      <p className="font-display text-6xl font-extrabold text-brand-600">404</p>
      <p className="muted">This page doesn&apos;t exist.</p>
      <Link to="/" className="link">
        Go home
      </Link>
    </div>
  );
}

export function TermsPage() {
  return (
    <div className="mx-auto max-w-3xl px-4 py-10">
      <div className="mb-8 flex items-center justify-between">
        <Link to="/">
          <Logo size="sm" />
        </Link>
        <ThemeToggle />
      </div>
      <article className="card space-y-4 p-6 text-sm leading-relaxed sm:p-8">
        <h1 className="text-2xl font-extrabold">Terms &amp; Conditions</h1>
        <p className="muted">Placeholder text — replace with terms reviewed by your legal advisor before launch.</p>
        <p>
          ExamForge provides practice tests for exam preparation. Sample questions are created for practice and are not
          official exam papers. Scores and ranks are indicative only.
        </p>
        <p>
          You are responsible for keeping your password safe. Don&apos;t share your account or attempt to access other
          users&apos; data. We may suspend accounts that abuse the service.
        </p>
        <h2 className="pt-2 text-lg font-bold">Privacy</h2>
        <p>
          We store your name, username, email, and exam activity to run the service. Passwords are stored only as secure
          hashes. We don&apos;t sell personal data.
        </p>
      </article>
    </div>
  );
}
