# Frontend

Location: `cluelux-frontend/`. React 18 + Vite, TypeScript, Tailwind. A shadcn/ui-style component library lives under `src/components/ui/` (Radix primitives wrapped with Tailwind variants) — those are generic UI primitives, not game-specific, and are not detailed further here.

## Entry point — `src/main.tsx`

```tsx
createRoot(document.getElementById("root")!).render(<App />);
```

Also registers `public/sw.js` as a service worker, but **only in production builds** (`import.meta.env.PROD` gate) and only if the browser supports `serviceWorker` — i.e., no PWA offline behavior in local dev. Registration failures are caught and logged, not surfaced to the user.

## App shell — `src/App.tsx`

This is the single stateful component that owns the entire game. There is no router, no global state library (no Redux/Zustand/Context) — everything is `useState`/`useEffect` in one file, composed from pure helper modules in `src/lib/`.

### State owned here
`challenge` (today's word/hints/metadata, from API or fallback), `isLoading`, `gameMode` (`'daily' | 'practice'`), `guesses`, `currentGuess`, `evaluations`, `keyStates`, `gameStatus` (`'playing' | 'won' | 'lost'`), `hintStates`, `hintsUsedPerGuess`, plus UI-only flags (`showResult`, `showHelp`, `showStats`, `shakeRow`, `timeUntilNext`, `showTutorial`).

### Lifecycle (in order on mount)
1. `loadDailyWord()` — calls `fetchDailyWord()`; on failure, keeps the bundled `src/data/today.json` and shows a "Using offline word" toast (`sonner`).
2. Restore-or-start: if `localStorage` has a `GameState` matching today's date, replays it (guesses/evaluations/keyboard/hints are rebuilt deterministically from the stored guess list, not stored redundantly); otherwise clears any stale state.
3. Attaches a global `keydown` listener (letters, Enter, Backspace) that no-ops once `gameStatus !== 'playing'`.
4. Starts a 1-second `setInterval` computing time-until-midnight-ET for the "next puzzle" countdown shown in the result dialog.
5. On `gameStatus` becoming `'won'`, deliberately delays showing the result dialog by **7 seconds** ("Steve Jobs principle: let them SAVOR the moment" — explicit comment in source) with no toast/sound on win, only the tile color animation. A loss shows the result dialog immediately.

### Core interaction handlers
- `handleKeyPress` / `handleBackspace` — mutate `currentGuess` directly (bounded to `challenge.wordLength`).
- `handleEnter` — validates via `validateGuessInput`, triggers a shake animation (`shakeRow`, auto-cleared after 500ms) on invalid input instead of a blocking error dialog, otherwise evaluates the guess, updates keyboard/hint state, checks win/loss, and persists `GameState` + updates `GameStats` via `localStorage.ts`.
- `handleRevealHint` — delegates to `hintManager.revealHint`, then patches `hintsRevealed` into the already-saved `GameState`.
- `handleStartPractice` / `handleBackToDaily` — switch `gameMode` and fully reset per-game state (note: `handleStartPractice` calls `fetchPracticeWord()`, which hits an endpoint not implemented in the current backend stack — see `architecture.md`).
- `handleShare` — builds an emoji grid (🟩🟨⬜) + hint-count glyph, tries `navigator.share()` first (mobile native share sheet), falls back to `navigator.clipboard.writeText` with a toast; silently no-ops if the user cancels the native share dialog.

### Rendering
Header (title + daily/practice badge + help/stats icon buttons) → `GameBoard` → `HintDisplay` → `Keyboard` → conditional mode-toggle buttons (practice entry point / "back to daily") → three `Dialog`s (result, help, stats). A separate full-screen loading state is rendered while `isLoading` is true.

## Components — `src/components/`

| File | Role |
|---|---|
| `GameBoard.tsx` | Renders the guess grid (`GameTile` per letter); also owns the hint "light bar" indicators between rows and the hint-reveal popup modal. Computes row display state (past guess / current in-progress row padded with spaces / empty future row) purely from props — holds no game state of its own beyond local popup UI state. |
| `GameTile.tsx` | Single letter tile; not read in detail here, but consumed by `GameBoard` with `letter`, `state`, `position`, `delay` props (delay drives the row's staggered flip animation). |
| `HintDisplay.tsx` | Collapsible card list of all currently-unlocked hints, independent of `GameBoard`'s inline hint bar — both read/write the same `HintState[]` via `hintManager`. |
| `Keyboard.tsx` | On-screen QWERTY keyboard; colors keys from `keyStates`, disabled once the game isn't `'playing'`. |
| `FirstTimeExperience.tsx` | First-run tutorial overlay; exports `shouldShowTutorial()` (checked once in `App.tsx`'s initial state) alongside the component itself. |
| `SoundToggle.tsx` | Mutes/unmutes game sound effects (see `src/utils/sounds.ts`). |
| `components/figma/ImageWithFallback.tsx` | Image component with a fallback — a Figma-import artifact, generic utility rather than game-specific. |
| `components/ui/*` | Generic shadcn/ui-style primitives (button, dialog, card, alert, etc.) — not game logic. |

Note: `GameBoard.tsx.new` and `src/lib/hintManager.ts.new` exist alongside their non-`.new` counterparts — these look like uncommitted/abandoned edit drafts sitting in the working tree. They are not imported anywhere (`App.tsx` imports from `./lib/hintManager` and `./components/GameBoard`, not the `.new` variants), so they're dead files, not an active alternate implementation. Worth deleting or reconciling so a future editor doesn't accidentally edit the wrong one.

## Core logic — `src/lib/`

| File | Responsibility |
|---|---|
| `gameEngine.ts` | Guess evaluation (two-pass correct/present/absent, duplicate-letter safe via `usedIndices`), win check, keyboard-state priority merge (correct > present > absent > unused), input validation (length, alpha-only, must be in the length-appropriate word list). |
| `validationEngine.ts` | Loads the 8 per-length word-list JSON files into `Set`s; `isValidWord`, `normalizeWord` (uppercase+trim), `getValidWords`, `getValidWordCount`. |
| `hintManager.ts` | Progressive hint unlock/reveal state machine — see `content-and-hints.md` for full detail. |
| `localStorage.ts` | Persistence for `GameState` (`cluelux_game_state`) and `GameStats` (`cluelux_stats`); date comparisons use America/New_York, not UTC or local browser time, so the "new day" boundary is consistent regardless of where the player is physically located. Also owns the practice-mode daily cap (`MAX_PRACTICE_GAMES = 5`, tracked via `stats.practice.gamesPlayedToday` + `lastPracticeDate`). |

## Services — `src/services/api.ts`

Thin fetch wrapper around the backend (see `architecture.md` for the full request-flow diagram). Key details:
- `API_BASE_URL` = `VITE_API_URL` env var, defaulting to `http://localhost:3000` if unset — i.e., a production build with no `VITE_API_URL` configured will try to call `localhost` from the user's browser and silently fail over to the bundled fallback data. This should be set explicitly in Amplify's build environment.
- `getPreviewOverride()` is a dev-only escape hatch (gated on `import.meta.env.DEV`) to force-load a specific future date by setting `VITE_PREVIEW_DATE` + `VITE_PREVIEW_KEY` in a local, gitignored `.env.local`; it routes to a separate hardcoded `PREVIEW_API_URL` constant (bypassing CloudFront).
- Both `fetchDailyWord` and `fetchPracticeWord` validate only that `word` and `hints` (array) are present on the response before trusting it; they don't validate `hints.length`, word length consistency, etc.
- `formatDailyWordForGame` derives a `gameNumber` as days-since-2025-01-01 — purely cosmetic (shown as `ClueLux #N` in the header/share text), not used for any game logic.

## Types — `src/types/game.types.ts`

Central type definitions: `TileState`, `GameStatus`, `KeyState`, `GameMode`, `MasterWord`, `DailyChallenge`, `GameState`, `GameStats` (with a nested, fully independent `practice` sub-object), `HintState`, `ValidWordList`. `GameStats.practice` is optional (`practice?`) for backward compatibility with stats saved before practice mode existed — `localStorage.ts`'s `loadStats()` backfills it with defaults when absent.

## Other notable files

- `src/utils/personality.ts` — generates the win/loss dialog copy (`getVictoryMessage`, `getDiscoveryMessage`) and greeting text; not read line-by-line here but is what drives the "SILENCE is golden" / no-toast-on-win product philosophy visible in `App.tsx`.
- `src/utils/timeUtils.ts`, `src/utils/sounds.ts`, `src/utils/wordList.ts` — supporting utilities (time formatting, sound effect playback, word-list helpers) referenced around the codebase but outside the 14 files directly reviewed for this doc set.
- `src/hooks/useWindowSize.ts` — presumably drives the `react-confetti` dependency's canvas sizing on win (confetti itself wasn't located in the files reviewed).
- `public/sw.js` + `public/manifest.webmanifest` — PWA install/offline support, registered from `main.tsx` in production only.
