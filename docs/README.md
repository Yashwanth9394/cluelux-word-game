# ClueLux Documentation

ClueLux is a daily word-guessing game (Wordle-style) with a twist: instead of a fixed 5-letter grid, each day's word can be 5–12 letters long, and players can unlock progressive text **hints** one guess at a time. It ships as a static React SPA (frontend) backed by a small serverless API (backend) that serves the word-of-the-day.

This `docs/` folder documents the codebase as it exists on disk today, based on a direct read of the core source files. It does not describe aspirational or planned features — see the repo-root `STEVE_JOBS_*` and `PROJECT_ROADMAP.md` files for product vision/roadmap notes, which are separate from this technical documentation.

## Repo layout

```
cluelux-app/
├── cluelux-frontend/        # React + Vite SPA (the game UI)
│   └── src/                 # App.tsx, components/, lib/, services/, data/, types/
├── cluelux-backend/         # Content tooling only — NOT deployable (see note below)
│   ├── shared/types/         # Type references (now orphaned, kept for documentation value)
│   ├── data/                  # Word data files (sample, generated, seed script)
│   └── scripts/               # Word/hint generation + QA scripts (Node, run manually)
└── cluelux-backend-java/     # The actual deployable backend — Java 21 + AWS CDK, two stacks (prod + UAT)
    ├── lambda/                # GetDailyWordHandler.java + its pom.xml
    └── infra/                 # ClueLuxBackendStack.java (prod), ClueLuxUatStack.java (UAT), app entry
```

> **Note:** `cluelux-backend/` used to contain a parallel TypeScript CDK stack and Lambda handler. Both were **deleted** (recoverable via `git log`) because leaving them in place was an active deploy hazard — running them would have silently reverted the live Lambda from Java back to Node, since both CDK apps targeted the same CloudFormation stack. `cluelux-backend-java/` is now the one and only deployable backend. Full detail in `backend.md`.

## Docs in this folder

| Doc | Covers |
|---|---|
| [architecture.md](./architecture.md) | How the frontend and backend fit together, request flow, caching, known gaps |
| [frontend.md](./frontend.md) | React app structure, game state machine, components, local storage, PWA |
| [backend.md](./backend.md) | CDK stack, Lambda handler, DynamoDB schema, shared types |
| [content-and-hints.md](./content-and-hints.md) | Word lists, daily word data, hint-generation prompts, hint-quality QA, progressive hint-unlock logic |
| [testing.md](./testing.md) | Current (minimal) automated test coverage and how to manually verify changes |
| [deployment.md](./deployment.md) | How the frontend (Amplify) and backend (CDK) are deployed, env vars, costs |
| [responsive-verification.md](./responsive-verification.md) | Real screenshots of the game board at 6-9 letters, at phone and tablet widths |

## Quick start (local dev)

Frontend:
```bash
cd cluelux-frontend
npm install
npm run dev        # Vite dev server
```

Backend (optional — the frontend falls back to `src/data/today.json` if the API is unreachable; prod and UAT already exist and are usually already running, see `backend.md`):
```bash
cd cluelux-backend-java
export CDK_DEFAULT_ACCOUNT=<account> CDK_DEFAULT_REGION=us-east-1 PREVIEW_KEY=<the key>
npx --prefix ../cluelux-backend cdk deploy ClueLuxBackendStackUAT   # or ClueLuxBackendStack for prod
```

Then point the frontend at the deployed API by setting `VITE_API_URL` in `cluelux-frontend/.env.local` to the CloudFront (prod) or direct API Gateway (UAT has no CloudFront) URL from the stack's outputs.

## Key facts worth knowing up front

- **No fixed word length.** `getMaxAttempts()` in `src/lib/gameEngine.ts` always returns 6 guesses regardless of word length (5–12 letters).
- **Hints unlock one per guess**, up to 5 hints, and must be revealed strictly in order — see [content-and-hints.md](./content-and-hints.md).
- **There is no automated test suite** in either the frontend or backend. See [testing.md](./testing.md).
- **The frontend calls a `/practice-word` endpoint** (`src/services/api.ts`) that does **not** exist on either the prod or UAT backend — only `/daily-word` is implemented. Flagged in [architecture.md](./architecture.md) and [backend.md](./backend.md).
- **There's an existing AWS Amplify app already set up for the frontend**, with an unfinished `cluelux.com` domain attachment. Whether to finish that path or deploy fresh via S3+CloudFront is an open decision — see [deployment.md](./deployment.md).
