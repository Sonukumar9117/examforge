# AGENTS.md — start here

This repository is **ExamForge**, an online exam/CBT platform (Spring Boot + MySQL backend,
React + TypeScript web app, mobile app planned).

1. Read [`docs/PROJECT_PLAN.md`](docs/PROJECT_PLAN.md) — architecture, DB design, API list,
   conventions, and the **Progress Tracker** (section 10) that says what is done and what is next.
2. Work on the next unchecked item of the current phase, in small testable steps.
3. When you finish a step, tick it in the Progress Tracker.

## Run

```bash
# backend (needs MySQL 8 running; copy backend/.env.example to backend/.env first)
cd backend && mvn spring-boot:run -Dspring-boot.run.profiles=mysql
# tests (uses database examforge_test on the same MySQL)
cd backend && mvn test

# frontend (copy frontend/.env.example to frontend/.env)
cd frontend && npm install && npm run dev      # http://localhost:5173
```

## Non-negotiable rules
- Server decides user id, role, scores, timers, and correct answers — never the client.
- No secrets in source. Use env vars / `backend/.env` (git-ignored).
- Schema changes = new Flyway migration; never edit an applied one.
- Every UI screen supports light **and** dark mode and works on mobile widths.
- The API must stay usable by the future mobile app (no web-only business logic).
