# Testing

## Current state: no automated test suite

Verified directly: a repo-wide search for `*.test.*` and `*.spec.*` files (excluding `node_modules`) returns **zero results** in both `cluelux-frontend` and `cluelux-backend`. There is no Jest/Vitest/Mocha config, no `__tests__` directory, and no testing library listed in either `package.json`'s dependencies.

- `cluelux-backend/package.json`'s `test` script is a literal stub:
  ```json
  "test": "echo \"Error: no test specified\" && exit 1"
  ```
  Running `npm test` in the backend (content-tooling package) fails on purpose — it's the default `npm init` placeholder, never replaced.
- `cluelux-frontend/package.json` has no `test` script at all.
- `cluelux-backend-java/` has no test classes either (no `src/test/` directory in `lambda/` or `infra/`) — correctness there currently rests on manual verification against the live UAT stack (see below) plus the `cdk diff` checks described in `backend.md`.

This means **every change today is verified manually** (running the dev server and clicking through the game) or by code review alone. Treat any claim of "tests pass" for this project with suspicion until a real suite exists.

## The one piece of automated QA that does exist

`cluelux-backend/scripts/checkHintLeaks.js` is the closest thing to a regression test in this codebase, but it's content QA, not code QA: it statically scans generated daily-word hint text for rule violations (letter-count mentions, "starts/ends with," the answer word leaking into its own hint) and prints a pass/fail tally. It is not wired into CI and must be run manually:

```bash
cd cluelux-backend
node scripts/checkHintLeaks.js data/generated-words.json
```

See `content-and-hints.md` for the exact leak patterns it checks.

## UAT — a real isolated environment for the backend

`ClueLuxBackendStackUAT` (see `backend.md`) is a separate DynamoDB table, Lambda and API Gateway from prod — a bad test write there can never reach real player data. There's no UAT frontend deployment; test the frontend locally (`npm run dev`) pointed at the UAT API's direct URL (no CloudFront in front of UAT, so just set `VITE_API_URL` to the UAT stack's `ApiUrl` output in `.env.local` for a session), or use the `VITE_PREVIEW_DATE`/`VITE_PREVIEW_KEY` mechanism against prod for quick single-date checks (see `frontend.md`). Recommended order before any prod backend change: deploy to UAT → verify with real requests → only then deploy the same, unrebuilt artifact to prod (see "Build-once, promote" in `backend.md`).

## Manual verification checklist (recommended, until real tests exist)

Given there's no safety net, changes to the files below should be manually re-verified against this checklist before shipping:

**`src/lib/gameEngine.ts`**
- Guess with a repeated letter where only one instance is correct (e.g. guessing `SPEED` against answer `ERASE` — duplicate-letter handling via `usedIndices` is the trickiest part of Wordle-style scoring).
- A guess that is too short/too long/contains non-letters — confirm the right `error` message and shake animation, not a silent failure.
- Winning on the last allowed guess (6th) vs. the first.

**`src/lib/hintManager.ts`**
- Hints unlock exactly one per submitted guess, up to 5 (word lengths above 5 don't get more hints, word lengths below 5 still cap at `min(guessCount, 5)`).
- Attempting to reveal hint N while hint N-1 is still unrevealed is a no-op in both `GameBoard.tsx`'s popup and `HintDisplay.tsx`'s card list (both gate on `canRevealHint`, but verify neither has drifted from the other after a UI change).

**`src/lib/localStorage.ts`**
- Refresh mid-game: guesses, evaluations, keyboard colors, and hint reveal state should all restore identically.
- Play across a simulated ET midnight boundary (e.g., by mocking `Date`) — stale state from "yesterday" should be discarded, not restored.
- Practice mode: confirm the 5-games/day cap actually blocks a 6th game and resets the next ET day.

**`services/api.ts` / backend integration**
- Backend unreachable → frontend falls back to `src/data/today.json` and shows the offline toast, game remains fully playable.
- Backend returns an item with a missing/malformed `hints` array → confirm `fetchDailyWord`'s format-validation throws (it checks `Array.isArray(data.hints)`) rather than crashing the render.
- `fetchPracticeWord()` currently has no live endpoint on either the prod or UAT backend — confirm the practice-mode error path surfaces the toast rather than crashing, or add the missing endpoint before testing happy-path practice mode.

**Backend (`GetDailyWordHandler.java`)**
- No item for today → fallback `LIGHT` response, still HTTP 200, still carries the midnight-ET `Cache-Control` header.
- DynamoDB throwing (e.g., bad IAM permissions) → HTTP 500 with the actual error message in the body (useful for CloudWatch-less debugging, but also means raw DynamoDB error text can reach a client — not currently a secret-leak risk given the error surface, but worth knowing).
- `checkHintLeaks.js` clean (0 flagged) run against whatever file is about to be seeded.

## If/when a real suite gets added

Natural first targets, given the architecture:
- **Unit tests for `src/lib/*.ts`** (`gameEngine`, `hintManager`, `validationEngine`, `localStorage`) — these are pure(ish) functions with no React/DOM dependency, so a plain Vitest setup (matching the Vite toolchain already in use) would cover the highest-value logic with the least setup cost.
- **Lambda handler test** for `getDailyWord.ts` — mock the DynamoDB client, assert on the fallback path, the found-item path, the `Cache-Control` TTL math, and the 500 path.
- **A CI step that runs `checkHintLeaks.js`** against any newly generated word file before it's allowed into `data/generated-words.json` or seeded.
