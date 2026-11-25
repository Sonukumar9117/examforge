// Types mirroring the backend DTOs (backend/src/main/java/com/examforge/**/…Dtos.java).
// Keep in sync with the API. The future mobile app can reuse this file as-is.

export type RoleName = 'ROLE_STUDENT' | 'ROLE_ADMIN';
export type ThemePreference = 'LIGHT' | 'DARK' | 'SYSTEM';

export interface Profile {
  themePreference: ThemePreference;
  bio: string | null;
  phone: string | null;
  targetCategoryId: number | null;
  currentStreak: number;
  longestStreak: number;
  showOnLeaderboard: boolean;
}

export interface User {
  id: number;
  username: string;
  email: string;
  fullName: string;
  avatarUrl: string | null;
  emailVerified: boolean;
  hasPassword: boolean;
  googleLinked: boolean;
  roles: RoleName[];
  createdAt: string;
  profile: Profile | null;
}

export interface AuthResponse {
  accessToken?: string;
  tokenType?: 'Bearer';
  expiresIn?: number;
  refreshToken?: string; // only for mobile clients
  user: User;
  emailVerificationRequired: boolean;
}

export interface AuthConfig {
  googleEnabled: boolean;
  googleClientId: string | null;
  emailVerificationRequired: boolean;
}

export interface MessageResponse {
  message: string;
}

export interface PageResponse<T> {
  content: T[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
}

export interface AdminStats {
  totalUsers: number;
  totalStudents: number;
  totalAdmins: number;
  verifiedUsers: number;
  newUsersLast7Days: number;
  totalCategories: number;
  totalQuestions: number;
  approvedQuestions: number;
  publishedExams: number;
  draftExams: number;
  totalAttempts: number;
  finishedAttempts: number;
}

export interface AdminUser {
  id: number;
  username: string;
  email: string;
  fullName: string;
  emailVerified: boolean;
  enabled: boolean;
  googleLinked: boolean;
  roles: RoleName[];
  createdAt: string;
  lastLoginAt: string | null;
}

export interface AuditLogEntry {
  id: number;
  actorId: number | null;
  actorUsername: string | null;
  action: string;
  entityType: string | null;
  entityId: string | null;
  details: string | null;
  ipAddress: string | null;
  createdAt: string;
}

// ---------------------------------------------------------------- catalog (phase 2)

export interface Subject {
  id: number;
  name: string;
  slug: string;
  description: string | null;
  /** admin endpoints only */
  questionCount?: number;
}

export interface ExamCategory {
  id: number;
  name: string;
  slug: string;
  description: string | null;
  icon: string | null;
  displayOrder: number;
  active: boolean;
  subjects: Subject[];
  publishedExamCount: number;
}

export interface CategoryInput {
  name: string;
  description?: string;
  icon?: string;
  displayOrder: number;
  active: boolean;
  subjectIds: number[];
}

// ---------------------------------------------------------------- question bank (phase 2)

export type Difficulty = 'EASY' | 'MEDIUM' | 'HARD';
export type QuestionType = 'MCQ' | 'MULTI_SELECT' | 'TRUE_FALSE';
export type QuestionStatus = 'DRAFT' | 'APPROVED';

export interface QuestionOption {
  id: number;
  label: string;
  text: string;
  correct: boolean;
}

export interface Question {
  id: number;
  subject: Subject;
  topic: string | null;
  type: QuestionType;
  questionText: string;
  imageUrl: string | null;
  explanation: string | null;
  difficulty: Difficulty;
  language: string;
  marks: number;
  negativeMarks: number;
  source: string | null;
  status: QuestionStatus;
  tags: string[];
  options: QuestionOption[];
  createdAt: string;
  updatedAt: string;
}

export interface QuestionInput {
  subjectId: number;
  topic?: string;
  type: QuestionType;
  questionText: string;
  imageUrl?: string;
  explanation?: string;
  difficulty: Difficulty;
  language?: string;
  marks: number;
  negativeMarks: number;
  source?: string;
  status: QuestionStatus;
  tags: string[];
  options: { text: string; correct: boolean }[];
}

export interface QuestionFilters {
  q?: string;
  subjectId?: number | '';
  difficulty?: Difficulty | '';
  type?: QuestionType | '';
  status?: QuestionStatus | '';
  tag?: string;
  page?: number;
  size?: number;
}

export interface ImportReport {
  totalRows: number;
  validRows: number;
  imported: number;
  dryRun: boolean;
  errors: { row: number; message: string }[];
}

// ---------------------------------------------------------------- exams (phase 2)

export type ExamType = 'PRACTICE' | 'MOCK' | 'LIVE';
export type ExamStatus = 'DRAFT' | 'PUBLISHED' | 'ARCHIVED';
export type ResultRelease = 'IMMEDIATE' | 'AFTER_END' | 'MANUAL';

export interface ExamSummary {
  id: number;
  title: string;
  slug: string;
  category: { id: number; name: string; slug: string; icon: string | null };
  type: ExamType;
  difficulty: Difficulty;
  status: ExamStatus;
  durationMinutes: number;
  questionCount: number;
  totalMarks: number;
  passMarks: number | null;
  startsAt: string | null;
  endsAt: string | null;
  maxAttempts: number | null;
  publishedAt: string | null;
  updatedAt: string;
}

export interface ExamDetail {
  summary: ExamSummary;
  description: string | null;
  instructions: string | null;
  shuffleQuestions: boolean;
  shuffleOptions: boolean;
  allowNavigation: boolean;
  resultRelease: ResultRelease;
  resultsReleasedAt?: string | null;
  questions: { order: number; marks: number; negativeMarks: number; question: Question }[];
}

export interface ExamInput {
  categoryId: number;
  title: string;
  description?: string;
  instructions?: string;
  type: ExamType;
  difficulty: Difficulty;
  durationMinutes: number;
  passMarks?: number | null;
  startsAt?: string | null;
  endsAt?: string | null;
  maxAttempts?: number | null;
  shuffleQuestions: boolean;
  shuffleOptions: boolean;
  allowNavigation: boolean;
  resultRelease: ResultRelease;
}

export interface ExamQuestionInput {
  questionId: number;
  marks?: number | null;
  negativeMarks?: number | null;
}

export interface ExamPublic {
  summary: ExamSummary;
  description: string | null;
  instructions: string | null;
  allowNavigation: boolean;
  negativeMarking: boolean;
  maxNegativeMarks: number;
  subjects: Subject[];
}

export interface ExamBrowseFilters {
  q?: string;
  category?: string;
  subjectId?: number | '';
  difficulty?: Difficulty | '';
  type?: ExamType | '';
  page?: number;
  size?: number;
}

// ---------------------------------------------------------------- attempts & results (phase 3)

export type AttemptStatus = 'IN_PROGRESS' | 'SUBMITTED' | 'AUTO_SUBMITTED';

export interface AttemptQuestion {
  questionId: number;
  number: number;
  type: QuestionType;
  text: string;
  imageUrl: string | null;
  subject: string;
  marks: number;
  negativeMarks: number;
  options: { id: number; label: string; text: string }[];
}

export interface AnswerState {
  questionId: number;
  selectedOptionIds: number[];
  markedForReview: boolean;
  visited: boolean;
  seq: number;
}

export interface Attempt {
  attemptId: number;
  attemptNo: number;
  status: AttemptStatus;
  exam: { id: number; title: string; slug: string; categoryName: string; durationMinutes: number; allowNavigation: boolean; totalMarks: number };
  startedAt: string;
  deadlineAt: string;
  serverNow: string;
  submittedAt: string | null;
  questions: AttemptQuestion[];
  answers: AnswerState[];
}

export interface SaveAnswersResult {
  savedAt: string;
  serverNow: string;
  deadlineAt: string;
  status: AttemptStatus;
}

export interface ExamRef {
  id: number;
  title: string;
  slug: string;
  categoryName: string;
  categoryIcon: string | null;
}

export interface AttemptSummary {
  attemptId: number;
  attemptNo: number;
  status: AttemptStatus;
  exam: ExamRef;
  startedAt: string;
  deadlineAt: string;
  submittedAt: string | null;
  score: number | null;
  maxScore: number | null;
  percentage: number | null;
  passed: boolean | null;
}

export interface ReviewOption {
  id: number;
  label: string;
  text: string;
  correct: boolean;
  selected: boolean;
}

export interface ReviewItem {
  number: number;
  questionId: number;
  type: QuestionType;
  text: string;
  imageUrl: string | null;
  subject: string;
  topic: string | null;
  marks: number;
  negativeMarks: number;
  marksAwarded: number;
  outcome: 'CORRECT' | 'INCORRECT' | 'UNANSWERED';
  explanation: string | null;
  saved: boolean;
  options: ReviewOption[];
}

export interface SubjectScore {
  subjectId: number;
  subject: string;
  score: number;
  maxScore: number;
  correct: number;
  incorrect: number;
  unanswered: number;
  accuracy: number;
}

export interface ExamResult {
  attemptId: number;
  attemptNo: number;
  status: AttemptStatus;
  exam: ExamRef;
  score: number;
  maxScore: number;
  percentage: number;
  accuracy: number;
  passMarks: number | null;
  passed: boolean | null;
  totalQuestions: number;
  correct: number;
  incorrect: number;
  unanswered: number;
  timeTakenSeconds: number;
  submittedAt: string;
  rank: number | null;
  percentile: number | null;
  candidates: number;
  rankNote: string | null;
  leaderboardAvailable: boolean;
  subjects: SubjectScore[];
  reviewAvailable: boolean;
  reviewMessage: string | null;
  review: ReviewItem[] | null;
  recommendations: ExamSummary[];
}

export interface StudentStats {
  testsTaken: number;
  averagePercentage: number | null;
  bestPercentage: number | null;
  accuracy: number | null;
  totalTimeSeconds: number;
  currentStreak: number;
  longestStreak: number;
  inProgress: AttemptSummary[];
  recent: AttemptSummary[];
  trend: { date: string; examTitle: string; percentage: number }[];
  subjects: { subjectId: number; subject: string; correct: number; incorrect: number; unanswered: number; accuracy: number }[];
}

export interface SavedQuestion {
  id: number;
  note: string | null;
  createdAt: string;
  questionId: number;
  type: QuestionType;
  text: string;
  subject: string;
  topic: string | null;
  explanation: string | null;
  options: ReviewOption[];
}

export interface AnswerInput {
  questionId: number;
  selectedOptionIds: number[];
  markedForReview: boolean;
  visited: boolean;
  seq: number;
}

// ---------------------------------------------------------------- live tests & leaderboards (phase 4)

export interface LiveTestItem {
  exam: ExamSummary;
  myAttemptId: number | null;
  myStatus: AttemptStatus | null;
  myScore: number | null;
}

export interface LiveTests {
  open: LiveTestItem[];
  upcoming: LiveTestItem[];
  ended: LiveTestItem[];
  serverNow: string;
}

export interface LeaderboardEntry {
  rank: number;
  name: string;
  me: boolean;
  score: number;
  maxScore: number;
  percentage: number;
  accuracy: number;
  timeTakenSeconds: number;
  betterThanPercent: number;
}

export interface Leaderboard {
  exam: ExamSummary;
  available: boolean;
  message: string | null;
  opensAt: string | null;
  totalParticipants: number;
  entries: LeaderboardEntry[];
  me: LeaderboardEntry | null;
}

// ---------------------------------------------------------------- admin reports (phase 4)

export interface ExamReport {
  exam: ExamSummary;
  totalAttempts: number;
  finishedAttempts: number;
  inProgressAttempts: number;
  uniqueStudents: number;
  averageScore: number | null;
  averagePercentage: number | null;
  highestScore: number | null;
  lowestScore: number | null;
  passRate: number | null;
  averageTimeSeconds: number | null;
  scoreDistribution: { from: number; to: number; count: number }[];
  questions: { number: number; questionId: number; text: string; subject: string; difficulty: Difficulty; correct: number; incorrect: number; unanswered: number; accuracy: number }[];
  subjects: { subject: string; correct: number; incorrect: number; unanswered: number; accuracy: number }[];
}

export interface AttemptReportRow {
  attemptId: number;
  userId: number;
  username: string | null;
  fullName: string | null;
  email: string | null;
  attemptNo: number;
  status: AttemptStatus;
  startedAt: string;
  submittedAt: string | null;
  score: number | null;
  maxScore: number | null;
  percentage: number | null;
  correct: number | null;
  incorrect: number | null;
  unanswered: number | null;
  timeTakenSeconds: number | null;
  passed: boolean | null;
}
