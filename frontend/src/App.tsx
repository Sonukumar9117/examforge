import { GoogleOAuthProvider } from '@react-oauth/google';
import { useEffect, useRef, type ReactNode } from 'react';
import { createBrowserRouter, RouterProvider } from 'react-router-dom';
import { useAuth } from '@/auth/AuthProvider';
import { GuestOnly, RequireRole, RootRedirect } from '@/auth/guards';
import { AdminLayout } from '@/layouts/AdminLayout';
import { StudentLayout } from '@/layouts/StudentLayout';
import AccountPage from '@/pages/AccountPage';
import AdminDashboardPage from '@/pages/admin/AdminDashboardPage';
import AdminUsersPage from '@/pages/admin/AdminUsersPage';
import AuditLogsPage from '@/pages/admin/AuditLogsPage';
import CategoriesPage from '@/pages/admin/CategoriesPage';
import ExamEditorPage from '@/pages/admin/ExamEditorPage';
import ExamsPage from '@/pages/admin/ExamsPage';
import LiveTestsAdminPage from '@/pages/admin/LiveTestsAdminPage';
import QuestionsPage from '@/pages/admin/QuestionsPage';
import ReportsPage from '@/pages/admin/ReportsPage';
import LoginPage from '@/pages/auth/LoginPage';
import { ForgotPasswordPage, ResetPasswordPage } from '@/pages/auth/PasswordPages';
import SignupPage from '@/pages/auth/SignupPage';
import VerifyEmailPage from '@/pages/auth/VerifyEmailPage';
import { NotFoundPage, TermsPage } from '@/pages/MiscPages';
import DashboardPage from '@/pages/student/DashboardPage';
import ExamDetailPage from '@/pages/student/ExamDetailPage';
import ExplorePage from '@/pages/student/ExplorePage';
import LeaderboardPage from '@/pages/student/LeaderboardPage';
import ResultPage from '@/pages/student/ResultPage';
import MyTestsPage from '@/pages/student/MyTestsPage';
import ResultsListPage from '@/pages/student/ResultsListPage';
import ExamHallPage from '@/pages/exam/ExamHallPage';
import { useTheme, type ThemeMode } from '@/theme/ThemeProvider';

const router = createBrowserRouter([
  { path: '/', element: <RootRedirect /> },
  {
    element: <GuestOnly />,
    children: [
      { path: '/login', element: <LoginPage /> },
      { path: '/signup', element: <SignupPage /> },
      { path: '/forgot-password', element: <ForgotPasswordPage /> },
    ],
  },
  { path: '/reset-password', element: <ResetPasswordPage /> },
  { path: '/verify-email', element: <VerifyEmailPage /> },
  { path: '/terms', element: <TermsPage /> },

  // Student area
  {
    element: <RequireRole role="ROLE_STUDENT" />,
    children: [
      // Exam hall: full-screen, no site navigation
      { path: '/attempts/:attemptId', element: <ExamHallPage /> },
      {
        element: <StudentLayout />,
        children: [
          { path: '/dashboard', element: <DashboardPage /> },
          { path: '/exams', element: <ExplorePage /> },
          { path: '/exams/:slug', element: <ExamDetailPage /> },
          { path: '/results/:attemptId', element: <ResultPage /> },
          { path: '/my-tests', element: <MyTestsPage /> },
          { path: '/results', element: <ResultsListPage /> },
          { path: '/leaderboard', element: <LeaderboardPage /> },
          { path: '/profile', element: <AccountPage /> },
        ],
      },
    ],
  },

  // Admin area — the server also checks ROLE_ADMIN on every /api/v1/admin request.
  {
    element: <RequireRole role="ROLE_ADMIN" />,
    children: [
      {
        path: '/admin',
        element: <AdminLayout />,
        children: [
          { index: true, element: <AdminDashboardPage /> },
          { path: 'exams', element: <ExamsPage /> },
          { path: 'exams/:id', element: <ExamEditorPage /> },
          { path: 'questions', element: <QuestionsPage /> },
          { path: 'categories', element: <CategoriesPage /> },
          { path: 'live-tests', element: <LiveTestsAdminPage /> },
          { path: 'reports', element: <ReportsPage /> },
          { path: 'users', element: <AdminUsersPage /> },
          { path: 'audit-logs', element: <AuditLogsPage /> },
          { path: 'account', element: <AccountPage /> },
        ],
      },
    ],
  },
  { path: '*', element: <NotFoundPage /> },
]);

/** When a user signs in, adopt the theme saved on their account (shared with the mobile app). */
function ThemeSync() {
  const { user } = useAuth();
  const { setMode } = useTheme();
  const appliedFor = useRef<number | null>(null);
  useEffect(() => {
    if (!user || appliedFor.current === user.id) return;
    appliedFor.current = user.id;
    const pref = user.profile?.themePreference;
    if (pref) setMode(pref.toLowerCase() as ThemeMode);
  }, [user, setMode]);
  return null;
}

function GoogleProvider({ children }: { children: ReactNode }) {
  const { config } = useAuth();
  if (!config?.googleClientId) return <>{children}</>;
  return <GoogleOAuthProvider clientId={config.googleClientId}>{children}</GoogleOAuthProvider>;
}

export default function App() {
  return (
    <GoogleProvider>
      <ThemeSync />
      <RouterProvider router={router} />
    </GoogleProvider>
  );
}
