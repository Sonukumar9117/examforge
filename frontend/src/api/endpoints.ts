import { api, downloadFile } from './client';
import type {
  AdminStats,
  AnswerInput,
  Attempt,
  AttemptReportRow,
  AttemptSummary,
  AdminUser,
  AuditLogEntry,
  AuthConfig,
  AuthResponse,
  CategoryInput,
  ExamBrowseFilters,
  ExamCategory,
  ExamDetail,
  ExamInput,
  ExamPublic,
  ExamReport,
  ExamResult,
  ExamQuestionInput,
  ExamSummary,
  ImportReport,
  Leaderboard,
  LiveTests,
  MessageResponse,
  PageResponse,
  Question,
  QuestionFilters,
  QuestionInput,
  QuestionStatus,
  RoleName,
  SaveAnswersResult,
  SavedQuestion,
  StudentStats,
  Subject,
  ThemePreference,
  User,
} from './types';

export const authApi = {
  config: () => api<AuthConfig>('/auth/config', { auth: false }),
  register: (body: { fullName: string; username: string; email: string; password: string; acceptTerms: boolean }) =>
    api<AuthResponse>('/auth/register', { method: 'POST', body, auth: false }),
  login: (body: { identifier: string; password: string; rememberMe: boolean }) =>
    api<AuthResponse>('/auth/login', { method: 'POST', body, auth: false }),
  google: (body: { idToken: string; rememberMe: boolean }) =>
    api<AuthResponse>('/auth/google', { method: 'POST', body, auth: false }),
  logout: () => api<void>('/auth/logout', { method: 'POST', auth: false }),
  usernameAvailable: (username: string) =>
    api<{ username: string; available: boolean }>(
      `/auth/username-available?username=${encodeURIComponent(username)}`,
      { auth: false },
    ),
  verifyEmail: (token: string) => api<MessageResponse>('/auth/verify-email', { method: 'POST', body: { token }, auth: false }),
  forgotPassword: (email: string) =>
    api<MessageResponse>('/auth/forgot-password', { method: 'POST', body: { email }, auth: false }),
  resetPassword: (token: string, newPassword: string) =>
    api<MessageResponse>('/auth/reset-password', { method: 'POST', body: { token, newPassword }, auth: false }),
};

export const meApi = {
  get: () => api<User>('/users/me'),
  update: (body: { fullName: string; bio?: string; phone?: string; avatarUrl?: string; targetCategoryId?: number | null }) =>
    api<User>('/users/me', { method: 'PUT', body }),
  preferences: (themePreference: ThemePreference) =>
    api<User>('/users/me/preferences', { method: 'PUT', body: { themePreference } }),
  setLeaderboardVisibility: (showOnLeaderboard: boolean) =>
    api<User>('/users/me/preferences', { method: 'PUT', body: { showOnLeaderboard } }),
  changePassword: (body: { currentPassword?: string; newPassword: string }) =>
    api<MessageResponse>('/users/me/password', { method: 'PUT', body }),
  resendVerification: () => api<MessageResponse>('/users/me/verification-email', { method: 'POST' }),
};

export const adminApi = {
  stats: () => api<AdminStats>('/admin/stats'),
  users: (params: { q?: string; role?: string; page?: number; size?: number }) => {
    const qs = new URLSearchParams();
    Object.entries(params).forEach(([k, v]) => v !== undefined && v !== '' && qs.set(k, String(v)));
    return api<PageResponse<AdminUser>>(`/admin/users?${qs}`);
  },
  updateRoles: (id: number, roles: RoleName[]) =>
    api<AdminUser>(`/admin/users/${id}/roles`, { method: 'PUT', body: { roles } }),
  updateStatus: (id: number, enabled: boolean) =>
    api<AdminUser>(`/admin/users/${id}/status`, { method: 'PUT', body: { enabled } }),
  auditLogs: (params: { action?: string; page?: number; size?: number }) => {
    const qs = new URLSearchParams();
    Object.entries(params).forEach(([k, v]) => v !== undefined && v !== '' && qs.set(k, String(v)));
    return api<PageResponse<AuditLogEntry>>(`/admin/audit-logs?${qs}`);
  },
};

export const catalogApi = {
  categories: () => api<ExamCategory[]>('/exam-categories', { auth: false }),
  category: (slug: string) => api<ExamCategory>(`/exam-categories/${encodeURIComponent(slug)}`, { auth: false }),
  subjects: () => api<Subject[]>('/subjects', { auth: false }),
};

export const adminCatalogApi = {
  categories: () => api<ExamCategory[]>('/admin/categories'),
  createCategory: (body: CategoryInput) =>
    api<ExamCategory>('/admin/categories', { method: 'POST', body }),
  updateCategory: (id: number, body: CategoryInput) =>
    api<ExamCategory>(`/admin/categories/${id}`, { method: 'PUT', body }),
  deleteCategory: (id: number) => api<void>(`/admin/categories/${id}`, { method: 'DELETE' }),
  subjects: () => api<Subject[]>('/admin/subjects'),
  createSubject: (body: { name: string; description?: string }) =>
    api<Subject>('/admin/subjects', { method: 'POST', body }),
  updateSubject: (id: number, body: { name: string; description?: string }) =>
    api<Subject>(`/admin/subjects/${id}`, { method: 'PUT', body }),
  deleteSubject: (id: number) => api<void>(`/admin/subjects/${id}`, { method: 'DELETE' }),
};

const toQuery = (params: object) => {
  const qs = new URLSearchParams();
  Object.entries(params).forEach(([k, v]) => v !== undefined && v !== null && v !== '' && qs.set(k, String(v)));
  return qs.toString();
};

export const adminQuestionApi = {
  search: (filters: QuestionFilters) => api<PageResponse<Question>>(`/admin/questions?${toQuery(filters)}`),
  get: (id: number) => api<Question>(`/admin/questions/${id}`),
  create: (body: QuestionInput) => api<Question>('/admin/questions', { method: 'POST', body }),
  update: (id: number, body: QuestionInput) => api<Question>(`/admin/questions/${id}`, { method: 'PUT', body }),
  remove: (id: number) => api<void>(`/admin/questions/${id}`, { method: 'DELETE' }),
  bulkStatus: (ids: number[], status: QuestionStatus) =>
    api<MessageResponse>('/admin/questions/bulk-status', { method: 'POST', body: { ids, status } }),
  importFile: (format: 'CSV' | 'JSON', content: string, dryRun: boolean) =>
    api<ImportReport>('/admin/questions/import', { method: 'POST', body: { format, content, dryRun } }),
};

export const adminExamApi = {
  search: (params: { q?: string; categoryId?: number | ''; status?: string; page?: number; size?: number }) =>
    api<PageResponse<ExamSummary>>(`/admin/exams?${toQuery(params)}`),
  get: (id: number) => api<ExamDetail>(`/admin/exams/${id}`),
  create: (body: ExamInput) => api<ExamDetail>('/admin/exams', { method: 'POST', body }),
  update: (id: number, body: ExamInput) => api<ExamDetail>(`/admin/exams/${id}`, { method: 'PUT', body }),
  setQuestions: (id: number, questions: ExamQuestionInput[]) =>
    api<ExamDetail>(`/admin/exams/${id}/questions`, { method: 'PUT', body: { questions } }),
  publish: (id: number) => api<ExamDetail>(`/admin/exams/${id}/publish`, { method: 'POST' }),
  unpublish: (id: number) => api<ExamDetail>(`/admin/exams/${id}/unpublish`, { method: 'POST' }),
  remove: (id: number) => api<void>(`/admin/exams/${id}`, { method: 'DELETE' }),
};

export const examApi = {
  browse: (filters: ExamBrowseFilters) => api<PageResponse<ExamSummary>>(`/exams?${toQuery(filters)}`),
  get: (slug: string) => api<ExamPublic>(`/exams/${encodeURIComponent(slug)}`),
};

export const attemptApi = {
  start: (slug: string) => api<Attempt>(`/exams/${encodeURIComponent(slug)}/attempts`, { method: 'POST' }),
  get: (id: number) => api<Attempt>(`/attempts/${id}`),
  save: (id: number, answers: AnswerInput[]) =>
    api<SaveAnswersResult>(`/attempts/${id}/answers`, { method: 'PUT', body: { answers } }),
  submit: (id: number, answers: AnswerInput[]) =>
    api<ExamResult>(`/attempts/${id}/submit`, { method: 'POST', body: { answers } }),
  mine: (page = 0, size = 20) => api<PageResponse<AttemptSummary>>(`/attempts/me?page=${page}&size=${size}`),
  result: (id: number) => api<ExamResult>(`/results/${id}`),
};

export const studentApi = {
  stats: () => api<StudentStats>('/users/me/stats'),
  savedQuestions: (page = 0, size = 20) => api<PageResponse<SavedQuestion>>(`/users/me/saved-questions?page=${page}&size=${size}`),
  saveQuestion: (questionId: number, note?: string) =>
    api<SavedQuestion>('/users/me/saved-questions', { method: 'POST', body: { questionId, note } }),
  unsaveQuestion: (questionId: number) => api<void>(`/users/me/saved-questions/${questionId}`, { method: 'DELETE' }),
};

export const adminResultApi = {
  releaseResults: (examId: number) => api<ExamDetail>(`/admin/exams/${examId}/release-results`, { method: 'POST' }),
};

export const liveApi = {
  list: () => api<LiveTests>('/exams/live'),
  leaderboard: (slug: string, limit = 100) => api<Leaderboard>(`/leaderboards/exams/${encodeURIComponent(slug)}?limit=${limit}`),
};

export const adminReportApi = {
  exam: (examId: number) => api<ExamReport>(`/admin/reports/exams/${examId}`),
  attempts: (examId: number, page = 0, size = 25) =>
    api<PageResponse<AttemptReportRow>>(`/admin/reports/exams/${examId}/attempts?page=${page}&size=${size}`),
  exportCsv: (examId: number) => downloadFile(`/admin/reports/exams/${examId}/attempts.csv`, `exam-${examId}-attempts.csv`),
};
