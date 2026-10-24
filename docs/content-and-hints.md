# Content & Hints

ClueLux's content pipeline has two independent halves: **valid-word lists** (used for client-side guess validation) and **daily word + hint + fact content** (used as the actual daily puzzle, generated offline and seeded into DynamoDB).

## Valid word lists — `cluelux-frontend/src/data/words/`

Files: `valid_5_letter.json` through `valid_12_letter.json`, plus `master_words.json`. Each per-length file matches the `ValidWordList` type (`src/types/game.types.ts`):

```ts
export interface ValidWordList {
  length: number;
  count: number;
  words: string[];
  extracted: string;
}
```

`validationEngine.ts` loads all eight files at module init and builds one `Set<string>` per length (`validWordSets`, keyed `5`–`12`) for O(1) membership checks via `isValidWord(word, length)`. These lists exist purely to validate player *guesses* — a guess only needs to be "a real word of the right length," it never needs to be in the curated daily-answer pool. `scripts/extract_wordlists.js` (`cluelux-frontend/scripts/`, run via `npm run extract-words`) is presumably what regenerates these from some larger source dictionary.

## Daily answer content — `cluelux-backend/data/`

- `sample-words.json` — small hand-written seed set referenced by `DEPLOYMENT.md`'s seeding instructions.
- `generated-words.json` — output of the generation pipeline (see below); consumed by `checkHintLeaks.js` and `fixProblematicHints.js`.
- `november-2026-words.json` — a batch of pre-generated days. Verified shape (first entry):
  ```json
  {
    "date": "2026-11-01",
    "word": "FOREST",
    "length": 6,
    "hints": [
      "A natural area densely packed with trees and wildlife",
      "Covers large areas of land, home to deer, bears, and countless bird species",
      "Robin Hood lived in Sherwood, Little Red Riding Hood walked through one",
      "Wooded ecosystem that produces oxygen and provides timber for construction",
      "Rhymes with 'poorest' and sounds like 'for rest'"
    ]
  }
  ```
  30 entries total in the file — i.e., one month of daily words pre-generated ahead of time. Note this sample has no `fact` field, even though `WordEntry`/`DailyWordResponse` include one; the Lambda treats a missing `fact` as `undefined` rather than erroring.
- `data/seed.js` — reads one of these JSON files and writes each entry into the `ClueLuxWords` DynamoDB table (`npm run seed`).

## Hint-generation prompts — `cluelux-backend/scripts/hintPrompt*.js`

Three prompt variants exist side by side (`hintPrompt.js`, `hintPromptGameDesign.js`, `hintPromptNew.js`) — these are **not executed automatically**; they're candidate LLM prompt templates that `generateWords.js` (or a developer manually) picks from when generating hints for a word. `hintPromptNew.js` is the most recent/polished of the three ("Steve Jobs-inspired... Simple. Helpful. Delightful.") and defines a structured 5-hint philosophy:

| Hint # | Angle | Example given in the prompt |
|---|---|---|
| 1 | WHAT IT IS | "A natural disaster involving water" |
| 2 | WHERE/WHEN YOU FIND IT | "Often seen in coastal regions during storms" |
| 3 | FAMOUS EXAMPLE | "Katrina devastated New Orleans in 2005" |
| 4 | HOW IT WORKS/FEELS | "Causes flooding when water rises over land" |
| 5 | SIMILAR TO | "Related to floods and storms" |

Hard constraints baked into the prompt: 6–12 words per hint, roughly equal (medium) difficulty across all 5, every hint must add new information, and critically — **hints must never state letter count or starting/ending letters**, since the game UI already surfaces word length via the tile grid. The prompt asks for strict JSON output: `{ "word": "<WORD>", "hints": [5 strings] }`.

## Hint-quality QA — `scripts/checkHintLeaks.js`

A deterministic (non-LLM) regex safety net, run **after** generation, regardless of which prompt produced the hints — "the prompt tells the model not to leak; this is what actually verifies it didn't" (per the script's own header comment). It flags, per hint:

- Letter-count mentions, numeric (`"6 letters"`, `"9-letter word"`) or spelled out (`"six letters"`) — covers one through twelve.
- `"starts with"` / `"begins with"`.
- `"ends with"` / `"ends in"`.
- Generic `"first/last/initial/final letter(s)"` phrasing.
- The answer word itself appearing in its own hint text (word-boundary matched, so a hint for `FORESTRY` doesn't false-positive on the substring `FOREST`).

Run directly: `node scripts/checkHintLeaks.js [path-to-generated-words.json]` (defaults to `data/generated-words.json`). Prints each flagged word with the offending hint(s) and a final `"X/Y words clean"` tally. `fixProblematicHints.js` (`npm run fix:hints`) is the companion script that presumably regenerates hints for whatever this flags — it is the closest thing this project has to a content regression test; see `testing.md`.

## Progressive hint unlock — `cluelux-frontend/src/lib/hintManager.ts`

This is the client-side runtime logic that governs how hints surface during play (separate from the authoring-time content above).

- **Hint count is fixed at 5** regardless of word length (`getHintUnlockSchedule` / `getAvailableHintsCount` both hardcode `5`).
- **One hint unlocks per guess submitted**, in order: after 1 guess, hint 0 is unlocked; after 2 guesses, hint 1; etc. (`getAvailableHintsCount(guessCount) = min(guessCount, 5)`).
- **"Unlocked" ≠ "revealed."** `unlocked` just means the player is *allowed* to open it; `revealed` means they clicked it and have actually seen the text. `canRevealHint(hintStates, index)` additionally requires that **every earlier hint has already been revealed** — hints must be opened strictly in order 1→2→3→4→5, no skipping ahead even if a later one is "unlocked" by guess count.
- `revealHint()` is a no-op (returns state unchanged) if `canRevealHint` is false for that index — the UI (`GameBoard.tsx`, `HintDisplay.tsx`) also independently gates the click handler on `canRevealHint`, so this is defense in depth rather than the only guard.
- `getRevealedHintsCount()` feeds directly into: the in-game hint counter, the share-text "⚡" glyph (`App.tsx`'s `handleShare`), the win/loss dialog copy, and the `GameStats.avgHintsPerGame` / `noHintStreak` stat tracking in `localStorage.ts`. Using zero hints and winning increments `noHintStreak`; using any hint resets it to 0.

### UI surfaces for hints

- `GameBoard.tsx` renders a thin colored bar between guess rows (gray/locked, amber/pulsing "available," blue/"already viewed") that opens a 3.5-second auto-dismissing modal popup with the hint text when clicked.
- `HintDisplay.tsx` is a separate collapsible panel below the board listing all currently-unlocked hints as cards (locked/grey, revealable/yellow with a "Costs N" label, or revealed/blue with the text shown) — `hint.index + 1` is displayed to the player as the hint's "cost" in the UI copy, though no points/currency system actually deducts anything; it's purely a framing device for the share-card hint count.

Both components call the same `canRevealHint` / `onRevealHint` contract from `hintManager.ts`, so the one piece of truth for "can this hint be opened" lives in one place even though it's rendered twice.
