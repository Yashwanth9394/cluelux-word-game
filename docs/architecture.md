# Architecture

## Overview

ClueLux is split into two independently deployable pieces:

1. **Frontend** — a static React/Vite single-page app (`cluelux-frontend/`). All game logic (guess evaluation, hint unlocking, word validation, stats, persistence) runs **client-side**. There's an existing AWS Amplify app (`cluelux-frontend`, connected to the frontend's GitHub repo, last deployed Nov 2025) with an unfinished `cluelux.com` domain attachment (status `FAILED` — DNS verification record was never published). Whether that Amplify path gets finished or the frontend instead deploys via a fresh S3+CloudFront build is an open decision — see `deployment.md`.
2. **Backend** — an AWS CDK stack, written in **Java** (`cluelux-backend-java/`), exposing a tiny read-only API: a Lambda function backed by DynamoDB, fronted by API Gateway and CloudFront. Deployed as two fully isolated stacks, prod and UAT — see `backend.md`.

The backend's only job is to tell the frontend *what today's word, hints, and fun-fact are*. Everything else (scoring, validation, win/loss, stats, hint reveal order) happens in the browser.

## Request flow (daily word)

```
Browser (App.tsx)
  └─ fetchDailyWord()                         [src/services/api.ts]
       └─ GET {CloudFront or API Gateway}/daily-word?v=<today-ET>
            └─ CloudFront Distribution         [cdk-java: ClueLuxDistribution, prod only — no CDN on UAT]
                 cache key ignores query string, TTL set by Lambda's
                 Cache-Control header (max-age = seconds until midnight ET)
                 └─ API Gateway (/prod)         [cdk-java: ClueLuxApi / ClueLuxApiUAT]
                      └─ Lambda: GetDailyWordHandler (Java) [cluelux-backend-java/lambda/]
                           └─ DynamoDB GetItem    [table: ClueLuxWords (prod) / ClueLuxWords-uat (UAT), pk: date]
```

- The frontend adds `?v=<YYYY-MM-DD in America/New_York>` as a cache-busting query param so each day gets a logically distinct URL. CloudFront's cache policy explicitly ignores query strings (`CacheQueryStringBehavior.none()`), so in practice this param does nothing to CloudFront's cache key — the real daily rollover is driven by the Lambda's `Cache-Control: max-age=<seconds-until-midnight-ET>` header, which both CloudFront and the browser respect. See `backend.md` for the exact TTL calculation.
- If the fetch fails for any reason, `App.tsx` falls back to the bundled `src/data/today.json` snapshot and shows a toast ("Using offline word").

## Request flow (practice word) — incomplete

`src/services/api.ts` also defines `fetchPracticeWord()`, which calls `GET {API_BASE_URL}/practice-word`. **This endpoint does not exist on either the prod or UAT API Gateway** — both only register `GET /daily-word` (see `backend.md`). Practice mode is fully built out on the frontend (`App.tsx` has `handleStartPractice`, a 5-games/day limit via `localStorage`, separate practice stats in `GameStats.practice`), but has no working backend endpoint to call today. It will fail its fetch and surface the `toast.error('Could not start practice mode')` path in `App.tsx`.

## Client-side game state machine

All gameplay state lives in `App.tsx` as React `useState`, built from pure functions in `src/lib/`:

- `gameEngine.ts` — `evaluateGuess()` (two-pass Wordle-style correct/present/absent scoring), `isWinningGuess()`, `validateGuessInput()`, keyboard state tracking. `getMaxAttempts()` is hardcoded to `6` for every word length.
- `hintManager.ts` — hint unlock/reveal state machine (see `content-and-hints.md`).
- `validationEngine.ts` — checks a guess against per-length word lists bundled in `src/data/words/`.
- `localStorage.ts` — persists `GameState` (`cluelux_game_state`) and `GameStats` (`cluelux_stats`) to `window.localStorage`, keyed/reset by the current ET date.

On mount, `App.tsx`:
1. Fetches the daily word (or falls back to `today.json`).
2. Checks `localStorage` for a saved game matching today's date; if found, replays guesses through `updateKeyboardState`/`updateHintStates` to rebuild UI state instead of storing derived state directly.
3. Wires a `keydown` listener for physical-keyboard play alongside the on-screen `Keyboard` component.
4. Runs a 1-second interval to show a countdown to the next midnight-ET puzzle.

## Backend architecture (CDK stack, Java)

Defined in `cluelux-backend-java/infra/src/main/java/com/cluelux/infra/` — `ClueLuxBackendStack.java` (prod) and `ClueLuxUatStack.java` (UAT), deployed by `ClueLuxBackendApp.java`. Full detail in `backend.md`; summary:

- **DynamoDB**: `ClueLuxWords` (prod, `RemovalPolicy.RETAIN`) / `ClueLuxWords-uat` (UAT, `RemovalPolicy.DESTROY`) — partition key `date` (string), on-demand billing.
- **Lambda**: Java 21, `GetDailyWordHandler`, 512MB/20s timeout, same handler code deployed to both stacks against their own table.
- **API Gateway**: REST API, CORS wide open, one resource: `GET /daily-word`. Separate API per stack.
- **CloudFront**: prod only, custom cache policy tuned for daily rollover (default TTL 12h, min 60s, max 24h, query strings ignored). UAT has no CDN in front — call its API Gateway URL directly.

## Known architectural gaps / risks

- **Practice endpoint missing** (above) — frontend/backend contract mismatch, true on both prod and UAT.
- **No automated tests** anywhere in the repo (confirmed: no `*.test.*`/`*.spec.*` files exist). See `testing.md`.
- **Single backend now** — the old TypeScript CDK stack and Lambda (`cluelux-backend/cdk/`, `cluelux-backend/lambda/`) were removed entirely (see `backend.md`'s History section); `cluelux-backend-java/` is the only deployable backend. `cluelux-backend/` remains as content-generation tooling only.
- **Build artifacts aren't promoted between environments** — `cdk deploy` rebuilds the Lambda jar fresh every time, for both UAT and prod. See the "Build-once, promote" section in `backend.md`.
- **Preview-date override** (`VITE_PREVIEW_DATE` / `VITE_PREVIEW_KEY` in `.env.local`, read by `getPreviewOverride()` in `services/api.ts`) hits a hardcoded `PREVIEW_API_URL` constant (the prod API Gateway) rather than `API_BASE_URL`. It's dev-only (`import.meta.env.DEV` gated, so dead-code-eliminated from production builds), but it does hardcode a specific API Gateway hostname in source, and currently has no way to target the UAT API instead.
