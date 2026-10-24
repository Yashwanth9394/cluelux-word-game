# Mistakes log

Real mistakes made while building ClueLux's launch infrastructure, in the order they happened. Not a list of features shipped — a list of things that went wrong, how each was caught, and what changed as a result. Written for whoever (including future-me) wants to not repeat them.

---

### 1. Left a live deploy hazard sitting in the repo
The original TypeScript CDK backend (`cluelux-backend/cdk/`) was left in place after the Java rewrite went live. Both CDK apps targeted the exact same CloudFormation stack name and logical IDs, so running the old `npm run deploy` from the TypeScript side would have **silently reverted the live Lambda from Java back to Node** — no error, no warning, just a quiet regression. Caught by thinking through "what else can still touch this stack" before moving on, not by it actually happening. Fixed by deleting the TS backend entirely (`git rm`, recoverable from history) rather than just leaving it as a trap.

### 2. Trusted the prompt instead of verifying the output
The hint-generation prompt explicitly instructed the model never to leak letter count or first/last letters. Despite that, **29% of the live hint data leaked letter count anyway** (e.g. "Six letters, chemical symbol O..."). A prompt telling a model not to do something isn't the same as it not doing it. Fixed by building a separate, deterministic leak-checker (`checkHintLeaks.js`) that verifies the actual output, not the instructions given.

### 3. Leaked the answer while hand-writing "leak-free" hints
Even with the leak-checker already built and the letter-count rule in mind, two hand-written hints (FAMILY, GOLDEN) accidentally contained the answer word itself inside a "famous example" clue (e.g. "...The Addams **Family**..."). Caught by actually running the checker against every hint before accepting it, not by assuming hand-written meant safe.

### 4. A delegated research task returned success having done nothing
The first attempt to consolidate documentation into `docs/` came back as "complete" — but with **zero tool calls** and no files actually written. The completion notification looked identical to a real success. Caught by checking the filesystem directly instead of trusting the agent's own report. General lesson: a subagent's summary describes what it intended, not necessarily what it did — verify before reporting work as done.

### 5. The redo got a core fact backwards
The second (successful) documentation pass wrote the docs treating the *old, removed* TypeScript backend as the real one, and the Java backend — which was actually live and deployed — as an unverified side project to "check before trusting." It also documented a frontend deployment plan (fresh S3+CloudFront) that had already been superseded by a different decision. Caught by spot-reading the generated docs against what was actually known to be true, rather than publishing them unread.

### 6. Designed new infrastructure without checking what already existed
Spent a full session designing a from-scratch S3+CloudFront+ACM plan for the frontend, before discovering there was already a real, working AWS Amplify app connected to the same GitHub repo — with a half-finished custom domain attempt already in progress. Should have checked existing AWS resources before designing anything. Lesson: enumerate what's already there before building something new to replace it.

### 7. Almost deployed stale code by forgetting it wasn't pushed
All of a session's worth of frontend fixes (keyboard, hints, PWA) were sitting as uncommitted local changes when asked to "deploy from frontend." Amplify builds from GitHub, not from local files — triggering a deploy without first committing and pushing would have shipped the old, months-stale version while believing the new one was live. Caught before it happened, by checking `git status` against the remote before triggering any build.

### 8. Staged build artifacts for commit
The first `git add` for the new Java backend picked up compiled `.class` files, fat jars, and the entire `cdk.out/` synthesis output — none of which belong in version control. Caught before committing, by reviewing the staged file list rather than running `git add -A && git commit` blind. Fixed with a proper `.gitignore` for the new module.

### 9. A log-cleaning filter accidentally hid the thing it was supposed to show
Tried to strip ANSI color codes from `cdk diff` output with `grep -v "^\["`. CDK's actual diff markers (`[+]`, `[~]`, `[-]`) also start with `[`, so the filter silently deleted the real change list along with the noise — making a safety-critical diff review look like there was nothing to review, when there was. Caught by noticing the output looked suspiciously empty for a change that should have shown resources, not by the filter itself warning about anything.

### 10. A responsive-sizing fix introduced a new bug at large text sizes
The game board's fluid tile-size formula used `rem` units for its min/max bounds, mixed with `vmin` for the live-scaling middle term. `rem` scales with the root font-size (what OS/browser "larger text" settings actually change); `vmin` doesn't. At 200% text zoom the two fought each other and produced a new overflow that hadn't existed before the "fix." Caught by actually testing at 200% zoom as part of the accessibility check, not by assuming the formula was correct because it worked at normal zoom. Fixed by moving the bounds to fixed `px`, decoupling them from font-size entirely.

### 11. Enabling Basic Auth broke the app it was protecting
Turning on HTTP Basic Auth for the UAT environment caused a blank white screen. Root cause: Vite always adds a bare `crossorigin` attribute to its built `<script>`/`<link>` tags, which tells the browser to fetch those files *without* credentials — so the HTML loaded (top-level navigation sent the auth header), but the JS bundle silently 401'd and never ran. No visible error anywhere, just nothing rendering. Caught by checking actual HTTP status codes for each asset with and without credentials, rather than guessing from the symptom. Fixed with a small Vite plugin stripping `crossorigin` from the build output.

### 12. A known accessibility violation had been shipping unnoticed
`user-scalable=no` was present in the viewport meta tag from early on — it blocks pinch-zoom entirely, which fails WCAG's text-resize requirement outright. It wasn't something introduced during this work; it had simply never been checked until explicitly asked about device/accessibility compatibility. Lesson: "it was already like that" isn't the same as "it was fine" — nobody had actually verified it.
