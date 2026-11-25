# ExamForge — Master Project Plan

> **Read this first.** This is the single source of truth for the ExamForge project:
> architecture, database design, API contract, conventions, and the phase-by-phase
> progress checklist. Anyone (human or AI) continuing the work should read this file,
> check the **Progress Tracker** (section 10), and update it when a step is finished.

---

## 1. Product summary

ExamForge is an online examination platform for students preparing for Indian
competitive exams (UPSC, SSC, Banking, Railway, State PCS, GATE, JEE/NEET, GK). It
offers a realistic computer-based test (CBT) with a server-enforced timer, a question
palette, autosave/resume, negative marking, detailed results, and live multi-user exam rooms.

Clients:

| Client | Status | Folder |
|---|---|---|
| Web app (React + TypeScript) | in progress | `frontend/` |
| Mobile app (Android/iOS) | planned, not started | `mobile/` (future) |
| Admin panel | part of the web app (`/admin/*` routes) | `frontend/` |

**The backend is client-agnostic.** Every feature must work for the web and the
future mobile app through the same `/api/v1` REST + WebSocket API. Never put business
rules (scoring, timing, permissions) only in the web frontend.

---

## 2. Tech stack

| Layer | Choice |
|---|---|
| Backend | Java 21, Spring Boot 3.3.x, Spring Web, Spring Security (OAuth2 Resource Server for JWT), Spring Data JPA, Bean Validation, Flyway, springdoc-openapi (Swagger), Spring Mail, Spring WebSocket (STOMP) |
| Database | MySQL 8 (schema owned by Flyway; Hibernate runs with `ddl-auto=validate`) |
| Auth | Email/username + password (BCrypt), Google Sign-In (ID token verified on the server), short-lived JWT access tokens (HS256) + rotating refresh tokens stored hashed in DB |
| Web frontend | React 18, TypeScript, Vite, Tailwind CSS 3 (`darkMode: 'class'`), React Router 6, TanStack Query 5, react-hook-form + zod, lucide-react icons, `@react-oauth/google` |
| Mobile (future) | Recommended: React Native + Expo + TypeScript, so API types and validation schemas can be shared with the web app |
| Build | Maven (backend), npm (frontend) |
| Dev infra | `docker-compose.yml` (MySQL + Mailpit) — optional; a local MySQL works too |

---

## 3. Architecture

```
┌──────────────┐   HTTPS /api/v1 (JSON)    ┌─────────────────────────────┐     ┌─────────┐
│  Web (React) │ ────────────────────────▶ │ Spring Boot API             │ ──▶ │ MySQL 8 │
│  Mobile app  │ ◀──── WebSocket /ws ───── │  auth · users · admin       │     └─────────┘
└──────────────┘   (STOMP, JWT on CONNECT) │  catalog · attempts · rooms │
        │                                  │  results · leaderboards     │
        │ Google Sign-In (ID token)        └─────────────────────────────┘
        ▼                                         │ verifies ID token with Google JWKS
   Google Identity ◀──────────────────────────────┘
```

### Backend package layout (layered)

```
backend/src/main/java/com/examforge/
├── ExamForgeApplication.java
├── config/        # @Configuration: SecurityConfig, JwtConfig, OpenApiConfig, AppProperties, AdminBootstrap
├── controller/    # @RestController only — HTTP in/out, validation, no business logic
├── service/       # @Service — business rules + @Transactional (AuthService, UserService, MailService, …)
├── repository/    # Spring Data JPA interfaces
├── entity/        # JPA @Entity classes (match Flyway tables)
├── dto/
│   ├── request/   # incoming bodies (records + Bean Validation), e.g. LoginRequest
│   └── response/  # outgoing bodies (records), e.g. UserResponse, PageResponse
├── mapper/        # entity → response DTO (UserMapper, AuditLogMapper)
├── exception/     # ApiException, ApiError, GlobalExceptionHandler
├── security/      # JwtService, GoogleTokenVerifier, RateLimitFilter, CurrentUser, JSON 401/403 handler
└── util/          # Tokens (random/hash), RequestInfo, ValidationPatterns
```

Tests mirror this: `src/test/java/com/examforge/controller/*IntegrationTest`, `service/*Test`.

Rules: controllers call services only (never repositories); services own transactions; controllers
never return entities — map them with a `mapper/` class. New features add classes to these same
folders (e.g. phase 2: `ExamController`, `ExamService`, `ExamRepository`, `Exam`, `CreateExamRequest`,
`ExamResponse`, `ExamMapper`).

### Frontend layout

```
frontend/src/
├── main.tsx, App.tsx      # providers + router
├── api/                   # http client (token refresh), typed endpoint functions, types.ts
├── auth/                  # AuthProvider, useAuth, RequireAuth / RequireRole guards
├── theme/                 # ThemeProvider (light/dark/system), persisted
├── components/ui/         # Button, Input, Card, Badge, Spinner, ... (reusable)
├── components/brand/      # Logo
├── layouts/               # AuthLayout, StudentLayout, AdminLayout
├── pages/auth/            # Login, Signup, ForgotPassword, ResetPassword, VerifyEmail
├── pages/student/         # Dashboard, Explore, MyTests, Results, Leaderboard, Profile, ExamHall
└── pages/admin/           # Dashboard, Exams, Questions, Categories, Users, Rooms, Audit
```

---

## 4. Authentication design (web + mobile)

* **Access token**: JWT, HS256, 15 min, claims `sub` (user id), `username`, `roles`. Sent as `Authorization: Bearer <token>`.
* **Refresh token**: random 256-bit value; only its SHA-256 hash is stored (`refresh_tokens`). **Rotated** on each use; reuse of an old token revokes the whole token family (theft detection). Exception: a token rotated less than `app.jwt.refresh-reuse-grace` (10 s) ago while its session is still active counts as a concurrent refresh (two tabs), not theft.
  * **Web**: refresh token is set as an `HttpOnly; Secure; SameSite=Strict` cookie `ef_refresh` (path `/api/v1/auth`). The access token lives in memory only.
  * **Mobile**: send header `X-Client-Type: mobile`; the refresh token is returned in the JSON body and the app stores it in secure storage (Keychain / Keystore). Send it back in the body of `/auth/refresh` and `/auth/logout`.
  * "Remember me": 30-day refresh token (persistent cookie). Otherwise 1 day + session cookie.
* **Google Sign-In**: client gets a Google **ID token** (web: Google Identity Services button; mobile: native Google Sign-In) → `POST /api/v1/auth/google {idToken}`. Server verifies signature (Google JWKS), issuer, expiry, `email_verified`, and that `aud` is `GOOGLE_WEB_CLIENT_ID` or one of `GOOGLE_EXTRA_CLIENT_IDS` (Android/iOS client IDs). Links to an existing account by `google_id`, then by verified email; otherwise creates a STUDENT account.
* **Roles**: `ROLE_STUDENT` (default for every signup) and `ROLE_ADMIN`. There is **no** endpoint that lets a user choose or raise their own role. The first admin is created at startup from `ADMIN_EMAIL` / `ADMIN_PASSWORD` env vars (only if no admin exists). Admins can promote users later via `/api/v1/admin/users/{id}/roles` (audited).
* **Brute force**: per-IP rate limit on auth endpoints + account lock for 15 min after 5 failed logins.
* **Email verification / password reset**: single-use tokens (hashed, `auth_tokens` table). When `MAIL_ENABLED=false` the links are printed in the backend log (dev only).

---

## 5. Database design

Schema lives in `backend/src/main/resources/db/migration/V{n}__*.sql`.
**Never edit a migration that has already been applied — add a new one.**

```
users ─┬─< user_roles >── roles ──< role_permissions >── permissions
       ├── user_profiles (1:1)
       ├─< refresh_tokens
       ├─< auth_tokens            (EMAIL_VERIFICATION | PASSWORD_RESET)
       ├─< audit_logs (actor)
       ├─< exam_attempts ─┬─< attempt_answers ─< attempt_answer_options >─ question_options
       │                  └── results (1:1) ─< result_subject_scores
       ├─< saved_questions >── questions
       └─< exam_room_participants >── exam_rooms ── exams

exam_categories ─< exams ─┬─< exam_sections ── subjects
                          └─< exam_questions >── questions ─┬─< question_options
exam_categories ─< category_subjects >─ subjects ─< questions └─< question_tags
```

| Table | Phase | Key columns / rules |
|---|---|---|
| `users` | 1 | `username` UQ, `email` UQ, `password_hash` (NULL for Google-only), `google_id` UQ, `email_verified`, `enabled`, `failed_login_attempts`, `locked_until` |
| `roles`, `permissions`, `role_permissions`, `user_roles` | 1 | seeded `ROLE_STUDENT`, `ROLE_ADMIN` |
| `user_profiles` | 1 | `user_id` UQ, `target_category_id`, `bio`, `phone`, `theme_preference` (LIGHT/DARK/SYSTEM), streak fields |
| `refresh_tokens` | 1 | `token_hash` UQ, `family_id`, `expires_at`, `revoked_at`, `remember_me` |
| `auth_tokens` | 1 | `token_hash` UQ, `type`, `expires_at`, `used_at` |
| `audit_logs` | 1 | `actor_id`, `action`, `entity_type`, `entity_id`, `details` (JSON), `ip` |
| `exam_categories` | 2 | `slug` UQ, `icon`, `display_order`, `active` |
| `subjects`, `category_subjects` | 2 | subjects are shared between categories |
| `questions` | 2 | `type` (MCQ, MULTI_SELECT, TRUE_FALSE, NUMERIC), `difficulty`, `language`, default `marks`/`negative_marks`, `status` (DRAFT/APPROVED), `source` |
| `question_options`, `question_tags` | 2 | `is_correct` is **never** sent to students before results are released |
| `exams` | 2 | `type` (PRACTICE, MOCK, LIVE), `status` (DRAFT, PUBLISHED, ARCHIVED), `duration_minutes`, `starts_at`/`ends_at`, `max_attempts`, `shuffle_*`, `allow_navigation`, `result_release` (IMMEDIATE, AFTER_END, MANUAL) |
| `exam_sections`, `exam_questions` | 2 | per-exam marks/negative marks override question defaults; UQ(`exam_id`,`question_id`) |
| `exam_attempts` | 3 | `status` (IN_PROGRESS, SUBMITTED, AUTO_SUBMITTED), `started_at`, `deadline_at` (server), `submitted_at`, `version` (optimistic lock); UQ(`exam_id`,`user_id`,`attempt_no`) |
| `attempt_answers`, `attempt_answer_options` | 3 | UQ(`attempt_id`,`question_id`), `visited`, `marked_for_review`, `client_seq` (ignore stale autosaves) |
| `results`, `result_subject_scores` | 3 | computed only on the server inside the submit transaction |
| `saved_questions` | 3 | PK(`user_id`,`question_id`) |
| `exam_rooms`, `exam_room_participants`, `room_announcements` | 4 | `code` UQ (8 chars), `status` (SCHEDULED, WAITING, LIVE, ENDED, CANCELLED), `late_join_minutes` |

---

## 6. REST API (all under `/api/v1`, JSON)

Errors always use one shape:
```json
{ "status": 400, "code": "VALIDATION_FAILED", "message": "…", "fieldErrors": { "email": "must be a well-formed email address" }, "timestamp": "…", "path": "/api/v1/auth/register" }
```
Lists use `?page=0&size=20&sort=createdAt,desc` and return `{ content, page, size, totalElements, totalPages }`.

| Method & path | Who | Phase |
|---|---|---|
| `POST /auth/register` | public | 1 |
| `POST /auth/login` (`identifier` = email or username, `password`, `rememberMe`) | public | 1 |
| `POST /auth/google` (`idToken`, `rememberMe`) | public | 1 |
| `POST /auth/refresh`, `POST /auth/logout` | cookie or body token | 1 |
| `POST /auth/verify-email` | public | 1 |
| `POST /users/me/verification-email` (resend link) | user | 1 |
| `POST /auth/forgot-password`, `POST /auth/reset-password` | public | 1 |
| `GET /auth/username-available?username=` | public | 1 |
| `GET /auth/config` (Google client id, feature flags) | public | 1 |
| `GET/PUT /users/me`, `PUT /users/me/password`, `PUT /users/me/preferences` | user | 1 |
| `GET /admin/stats`, `GET /admin/users`, `PUT /admin/users/{id}/roles`, `PUT /admin/users/{id}/status` | admin | 1–2 |
| `GET /admin/audit-logs` | admin | 2 |
| `GET /exam-categories`, `GET /exam-categories/{slug}` | public | 2 |
| `CRUD /admin/categories`, `/admin/subjects`, `/admin/questions` (+ `POST /admin/questions/import` CSV/JSON) | admin | 2 |
| `CRUD /admin/exams`, `POST /admin/exams/{id}/publish|unpublish` | admin | 2 |
| `GET /exams?category=&subject=&difficulty=&q=` , `GET /exams/{id}` | user | 2–3 |
| `POST /exams/{id}/attempts` (start or resume) | user | 3 |
| `GET /attempts/{id}` (questions without answers + saved state + `serverNow` + `deadlineAt`) | owner | 3 |
| `PUT /attempts/{id}/answers` (batch autosave with `clientSeq`) | owner | 3 |
| `POST /attempts/{id}/submit` (idempotent) | owner | 3 |
| `GET /attempts/me` | user | 3 |
| `GET /results/{attemptId}`, `GET /results/{attemptId}/report` (PDF/CSV) | owner/admin | 3–4 |
| `GET/POST/DELETE /users/me/saved-questions` | user | 3 |
| `GET /exams/live` (live tests: open / upcoming / recently closed, with my status) | user | 4 |
| `GET /leaderboards/exams/{slug}?limit=` (live tests: only after the window closes) | user | 4 |
| `PUT /users/me/preferences {showOnLeaderboard}` | user | 4 |
| `GET /admin/reports/exams/{examId}`, `GET …/attempts`, `GET …/attempts.csv` | admin | 4 |

Live API docs: `http://localhost:8080/swagger-ui.html`.

### CBT timing rules (phase 3)
* `deadline_at = min(started_at + duration, exam.ends_at)` set by the server when an attempt starts.
* Each `GET /attempts/{id}` returns `serverNow` and `deadlineAt`; the client computes remaining time from the offset, not from its own clock.
* Answer saves after `deadline_at + 30s grace` are rejected; an expired IN_PROGRESS attempt is auto-submitted (on the next request and by a scheduled job).
* Submit is idempotent: a second submit returns the same result.

---

## 7. UI / brand

* Name **ExamForge**. Logo: red shield with a white checkmark (`frontend/src/components/brand/Logo.tsx`, `frontend/public/favicon.svg`).
* Colors — primary `#E11D48` (Tailwind `brand-600`), secondary `#991B1B`, dark bg `#111827`, light bg `#F8FAFC`.
* Use red for primary actions, active nav, and brand marks only — neutral grays for everything else.
* **Light + dark mode on every screen.** Tailwind `darkMode: 'class'`; every surface must have both a light and a `dark:` style. Theme choice (light/dark/system) is saved in `localStorage` and in `user_profiles.theme_preference` so it follows the user to the mobile app.
* Accessibility: visible focus rings (`focus-visible:ring-2 ring-brand-500`), labels on every input, keyboard-reachable controls, WCAG AA contrast.
* CBT palette colors: unvisited = gray, not answered = red, answered = green, marked = purple, answered+marked = purple with green dot.

---

## 8. Configuration & secrets

All secrets come from environment variables (or a git-ignored `backend/.env` file, loaded automatically). See `backend/.env.example` and `frontend/.env.example`.

| Variable | Purpose |
|---|---|
| `DB_URL`, `DB_USERNAME`, `DB_PASSWORD` | MySQL connection |
| `JWT_SECRET` | ≥ 32 random bytes, base64 |
| `GOOGLE_WEB_CLIENT_ID` | OAuth client id for the web button (served to the web app via `/auth/config`) |
| `GOOGLE_EXTRA_CLIENT_IDS` | comma-separated Android/iOS client ids whose ID tokens are also accepted |
| `ADMIN_EMAIL`, `ADMIN_PASSWORD`, `ADMIN_USERNAME` | first admin bootstrap |
| `MAIL_ENABLED`, `MAIL_HOST`, `MAIL_PORT`, `MAIL_USERNAME`, `MAIL_PASSWORD`, `MAIL_FROM` | SMTP |
| `APP_FRONTEND_URL` | used to build links in emails |
| `CORS_ALLOWED_ORIGINS` | comma-separated |
| `VITE_API_BASE_URL`, `VITE_GOOGLE_CLIENT_ID` | frontend |

Run backend: `./mvnw spring-boot:run` (profile `mysql` is the default; `--spring.profiles.active=mysql` also works). Requires **JDK 21** — set `JAVA_HOME` to it.

---

## 9. Conventions (follow these)

1. **Never trust the client** for user id, role, score, remaining time, or correct answers.
2. DTOs are Java `record`s with Bean Validation; controllers are thin; business logic in services; `@Transactional` on service methods that write.
3. Ownership checks in services (`attempt.user.id == currentUser.id` or admin).
4. New tables/columns → new Flyway migration `V{next}__description.sql`.
5. Frontend: TypeScript strict, API types in `src/api/types.ts` mirror backend DTOs, server state via TanStack Query, forms via react-hook-form + zod.
6. Every new screen supports light and dark mode and works at 360 px width.
7. Don't log passwords, tokens, or full emails in errors.
8. Seed/demo questions are clearly marked as **sample practice questions**, never as official exam questions.
9. Update the **Progress Tracker** below when you finish a step.

---

## 10. Progress tracker

Legend: `[x]` done · `[~]` partially done · `[ ]` not started

### Phase 1 — setup, branding, auth, roles
- [x] Old BCS quiz app removed; ExamForge rebuilt from scratch
- [x] Master plan (`docs/PROJECT_PLAN.md`) + `AGENTS.md`
- [x] Backend skeleton: Spring Boot 3.3, Flyway, MySQL profile, env-based secrets, Swagger
- [x] Flyway V1: users, roles, permissions, user_roles, user_profiles, refresh_tokens, auth_tokens, audit_logs
- [x] JWT access tokens + rotating refresh tokens (cookie for web, body for mobile)
- [x] Register, login (email or username), logout, refresh, username availability
- [x] Google Sign-In (server-side ID token verification)
- [x] Email verification + forgot/reset password (console links when mail disabled)
- [x] Admin bootstrap from env vars; no self-promotion path
- [x] Rate limiting + account lockout on auth endpoints
- [x] Global error format, CORS, `/users/me`, `/admin/stats` (basic)
- [x] Backend integration tests for auth + authorization
- [x] Frontend: React + TS + Tailwind, brand/logo, light/dark theme toggle (persisted)
- [x] Frontend: login, signup (strength meter, show/hide, terms), forgot/reset, verify email, Google button
- [x] Frontend: role-based routing, separate student and admin layouts (dashboards are placeholders)

### Phase 2 — admin, question bank, categories, exams
- [x] V2 migration: exam_categories, subjects, category_subjects, questions, question_options, question_tags, exams, exam_questions; 9 categories + 16 subjects seeded
- [x] Dev sample data: `SEED_DEMO_DATA=true` adds 19 labelled sample questions + 3 published sample exams (DemoDataSeeder)
- [x] Admin CRUD: categories (icon, order, active, linked subjects), subjects; target exam on profile
- [x] Question bank: CRUD, search/filter/paginate, bulk approve, CSV/JSON import with dry run + row-level error report (types: MCQ, MULTI_SELECT, TRUE_FALSE; NUMERIC not yet)
- [x] Exam builder: details, pick questions, order, per-exam marks/negatives, schedule, attempt limits, publish/unpublish with readiness checks
- [ ] Exam sections (exam_sections table, per-section timing) — deferred; not needed for current exams
- [x] Admin user management (list, search, enable/disable, roles) + audit log viewer (done early, in phase 1)
- [x] Admin dashboard stats (users, categories, questions, exams); attempt stats come with phase 3

### Phase 3 — student dashboard + CBT
- [x] V3 migration: exam_attempts, attempt_answers, attempt_answer_options, results, result_subject_scores, saved_questions (+ exams.results_released_at)
- [x] Explore exams (search by name/subject/category/difficulty/type, URL filters), exam detail page, dashboard recommendations by target exam
- [x] Instructions screen + confirm → start attempt (server start time); start resumes an attempt in progress
- [x] Exam hall: server-synced timer, palette with 5 statuses, Save & Next / Previous / Clear / Mark for Review, keyboard 1–8 and arrows
- [x] Autosave (on change + every 15 s), localStorage queue + retry with backoff, saved/offline indicator, resume after refresh (`frontend/src/exam/useExamSession.ts`)
- [x] Idempotent submit, auto-submit on expiry (client + `AttemptExpiryJob`), leave-page warning, optional fullscreen
- [x] Server scoring with negative marks + subject-wise breakdown (`ScoringService`; multi-select is all-or-nothing)
- [x] Student dashboard: stats, continue banner, score trend chart, subjects to practise, recent attempts, recommendations, streak
- [x] Results page (rank/percentile at 10+ candidates, review gated by release rule, print/save PDF) + My tests + saved questions; admin "Release answers" for MANUAL exams

### Phase 4 — live tests, leaderboards, analytics
> **Product decision (2026-10-30):** no join-by-code rooms. A *live test* is a published exam of type
> `LIVE` with a start and end time. Every student takes it once, any time inside that window; when the
> window closes, a leaderboard ranks everyone. (A real-time room version was built and parked in
> `git stash` — "live rooms by join code" — in case it is ever wanted.)
- [x] Live tests: LIVE exams need a window; one attempt per student (`LIVE_TEST_TAKEN`); deadline capped at window end
- [x] `GET /exams/live` (open / upcoming / closed in last 30 days, with the student's status) + dashboard "Live tests" section
- [x] Leaderboards `GET /leaderboards/exams/{slug}`: one entry per student (first attempt), score → time → submit time; live tests only after close; result page rank from leaderboard
- [x] Privacy: `user_profiles.show_on_leaderboard` (V4), toggle in account settings, hidden students shown as "Anonymous student"
- [x] Admin reports: exam analytics (averages, pass rate, score distribution, hardest questions, subjects), attempts list, CSV export (formula-safe)
- [x] Admin "Live tests" overview page
- [~] Downloadable result report: browser Print → Save as PDF on the result page; a server-generated PDF is not built

### Phase 5 — hardening & delivery
- [ ] Tests: exam timing, scoring, ownership, room flows
- [ ] Security review (headers, CSP, rate limits everywhere needed)
- [ ] Responsive/accessibility pass on all screens
- [ ] Docker Compose for full stack, production deployment guide
- [ ] Mobile app (`mobile/`, React Native + Expo) — start after phase 3 API is stable

---

## 11. Notes for the future mobile app

* Use the same `/api/v1` endpoints. Send `X-Client-Type: mobile` on auth calls to receive the refresh token in the response body.
* Google Sign-In: create Android/iOS OAuth client IDs in Google Cloud and add them to `GOOGLE_EXTRA_CLIENT_IDS`; send the native ID token to `/auth/google`.
* Exam timing: always use `serverNow`/`deadlineAt` from the API; never trust the device clock.
* Realtime rooms: connect to `/ws` with STOMP, `Authorization: Bearer <access token>` header on CONNECT.
* Theme: read/write `themePreference` via `/users/me/preferences`.

---

## 12. Implementation notes (keep updated)

* **JDK**: this machine's default `JAVA_HOME` points to JDK 17. Build with JDK 21 (`C:\Program Files\Java\jdk-21`).
* **Tests** wipe and re-migrate the `examforge_test` database on every run (`IntegrationTestBase`). Never point `TEST_DB_URL` at a real database.
* **Web auth flow** (`frontend/src/api/client.ts`): access token in memory; on 401 the client calls `/auth/refresh` once (single-flight) and retries. On page load `AuthProvider` calls `/auth/refresh` to restore the session.
* **Theme**: `index.html` applies the saved theme before first paint; `ThemeProvider` stores it in `localStorage` (`ef-theme`); `ThemeToggle` also saves it to the account; `ThemeSync` (App.tsx) applies the account preference after sign-in.
* **Dev email**: with `MAIL_ENABLED=false`, verification/reset links are printed in the backend console as `[DEV MAIL — not sent]`.
* Screens not built yet render `ComingSoonPage` with their phase number — replace them as phases are completed.
