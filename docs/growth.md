# Growth — Domain, Analytics, Social & GTM

## Custom Domain — cluelux.com

**Registrar:** Hostinger (ns1.dns-parking.com, ns2.dns-parking.com)
**Hosting:** AWS Amplify app `d12ua58fdpbtag` (cluelux-frontend), us-east-1

### DNS records (both in Hostinger)

| Type | Name | Target | Purpose |
|---|---|---|---|
| CNAME | `_658041cb3c5dd2cb3d0bf80602af9c19` | `_31d476e187c7b82757913907697dea9d.jkddzztszm.acm-validations.aws` | ACM certificate verification |
| ALIAS | `@` | `d2xnllwvghglav.cloudfront.net` | Root domain → Amplify CloudFront |

### How it works

1. Amplify creates a managed ACM certificate for `cluelux.com`.
2. ACM needs proof you own the domain → the CNAME record above (AWS checks for it automatically, no manual "verify" button).
3. Once certificate is issued, Amplify provisions a CloudFront distribution and routes `cluelux.com` traffic to it.
4. The ALIAS record points root traffic (`cluelux.com`) to that CloudFront distribution.

### Troubleshooting

- **Domain status stuck on PENDING_VERIFICATION:** The CNAME verification name must match exactly (character-for-character). Common mistake: transposing characters in the hash — always copy-paste from `aws amplify get-domain-association` output.
- **Domain status FAILED (AmplifyWaitTimeout):** AWS gave up waiting. Fix DNS, then delete + recreate the domain association:
  ```bash
  aws amplify delete-domain-association --app-id d12ua58fdpbtag --domain-name cluelux.com --region us-east-1
  aws amplify create-domain-association --app-id d12ua58fdpbtag --domain-name cluelux.com --sub-domain-settings prefix="",branchName=master --region us-east-1
  ```
- **Check current status:**
  ```bash
  aws amplify get-domain-association --app-id d12ua58fdpbtag --domain-name cluelux.com --region us-east-1
  ```
- The CloudFront distribution ID can change when you recreate the association — always check the new `dnsRecord` output and update the ALIAS if it changed.

### Mistake log entry

2026-11-01: CNAME verification record had a subtle typo (`...cb30dbf806...` vs `...cb3d0bf806...`). The association timed out after 72h. Fixed by correcting the CNAME name and recreating the association.

---

## Analytics — PostHog

**SDK:** `posthog-js`, initialized in `src/main.tsx` via `src/lib/analytics.ts`.
**Mode:** Anonymous device tracking, no login required. Uses `localStorage` persistence to maintain device identity across sessions.
**Activation:** Only fires when `VITE_POSTHOG_KEY` env var is set (no-op otherwise → safe in dev).

### Setup

1. Create account at posthog.com (free tier: 1M events/month).
2. Get your project API key from Settings → Project → API Key.
3. Add to `cluelux-frontend/.env.local`:
   ```
   VITE_POSTHOG_KEY=phc_yourKeyHere
   ```
4. For Amplify prod, add the same env var in Amplify Console → App settings → Environment variables.

### Events tracked

| Event | When | Key properties |
|---|---|---|
| `game_start` | Player begins daily/vocab/revise | `mode`, `wordLength` |
| `guess` | Each guess submitted | `mode`, `guessNumber`, `wordLength` |
| `hint_revealed` | Player taps a hint | `mode`, `hintIndex`, `guessNumber` |
| `game_end` | Win or loss | `mode`, `won`, `guesses`, `hintsUsed`, `timeTakenMs` |
| `mode_switch` | Tab change (daily/vocab/revise) | `from`, `to` |
| `share` | Share button tapped | `mode`, `method` (native/clipboard) |
| `drop_off` | Player exits vocab/revise mid-game | `mode`, `stage`, `guessNumber` |

Plus PostHog autocapture (clicks, pageviews, pageleave) and session recording (off by default, enable in PostHog dashboard).

### Key questions analytics answers

- What % of players finish vs. drop off, and at which guess?
- How many hints does the average player use?
- Do hint-heavy players come back less? (retention by hint usage)
- What share method is used and does sharing correlate with return visits?
- Time-of-day distribution — when should we post social content?

---

## Social Media & GTM Strategy

### Handles to claim

Claim `@cluelux` on: Instagram, X (Twitter), TikTok, Reddit (r/cluelux or u/cluelux), Facebook page, YouTube.

### Content engine — daily Reel/Story

Each day's Reel follows a template built from the daily word's hints:

1. **Logo animation** (0.5s)
2. **Hook:** "73% Failed" or similar social proof stat (pulled from actual play data once analytics is live)
3. **Mystery tiles** — N tiles with `?` marks, pulsing
4. **1-2 hints revealed** — enough to intrigue, never enough to solve
5. **Locked hints** — 🔒 icons for remaining hints
6. **CTA:** "Comment your guess ⬇️" + "Play → cluelux.com"

Reel template lives at: published artifact (regenerate daily with new word/hints).

### Engagement strategy — Cialdini's 7 principles

| Principle | How we use it |
|---|---|
| **Social proof** | "73% failed" stat in every Reel; show player count once live |
| **Scarcity** | "Only 4 hours left" evening posts; one word per day |
| **Authority** | Feature word origins, etymology — "today's word comes from Old English..." |
| **Commitment** | Streaks — once someone plays 3 days, they don't want to break it |
| **Liking** | Warm founder-journey content; behind-the-scenes of building ClueLux |
| **Unity** | "ClueLux players" as identity; "join 500 daily players" |
| **Reciprocity** | Free game, no ads, no login — players feel goodwill, share willingly |

### Comment seeding (alt accounts)

Seed 5-7 wrong-answer comments within the first hour of posting. The mix:

- **1 semi-plausible** wrong answer (e.g., "JUNGLE" when the word is FOREST) — anchors the thread as a real guessing game
- **2-3 confidently wrong with fake logic** (e.g., "DENTIST — trees have roots, teeth have roots 🦷") — gets quoted/shared
- **2-3 absurdly wrong** (e.g., "BLUETOOTH 💀", "KEVIN") — pure engagement bait, people reply to mock them

Key rules:
- Every seeded comment only partially fits one hint at most (or none at all)
- Never seed the correct answer
- Confidence in delivery matters more than plausibility — "obviously" and "easy" trigger correction impulse
- One "I know it but I'm not saying 😏" comment gets 10x replies (Zeigarnik effect)

### Share card (in-app viral loop)

`src/lib/shareCard.ts` generates a 1080×1920 PNG canvas image with:
- ClueLux branding + game number
- Tile grid (green/yellow/gray) showing the player's result
- Hints used count
- "Can you beat this?" CTA + cluelux.com link

Shared via Web Share API (`navigator.share` with file) → lands directly in WhatsApp Status, Instagram Stories, Snapchat, Telegram. Falls back to clipboard text on desktop.

### Highest-ROI growth moves (priority order)

1. **Share card in-app** — every solve becomes a post. This is how Wordle grew. Zero cost, compounds forever.
2. **Creator outreach** — DM puzzle/gaming creators to stitch/duet the Reel. One 50K creator > 1000 seeded comments.
3. **Daily Reel + comment seeding** — consistent presence, builds algorithmic trust on Instagram/TikTok.
4. **"Don't tell them" comment** — single most engaging comment type (exclusion triggers Zeigarnik).
5. **Streak/leaderboard sharing** — players flex streaks, bringing friends organically.
6. **ManyChat "comment CLUE for hint 3"** — Instagram boosts posts with high comment volume ($15/mo).
7. **Evening scarcity posts** — "Only 4 hours left" drives same-day conversions.

### Automation tools evaluated

| Tool | What it does | Posting | Commenting | Cost |
|---|---|---|---|---|
| **n8n** (recommended) | Self-hosted workflow automation | ✅ via Instagram API | ✅ via API | Free |
| **Phantom Buster** | Browser-based Instagram automation | ✅ | ✅ | ~$50/mo |
| **Jarvee** | Desktop Instagram bot (Windows/VPS) | ✅ | ✅ | ~$30/mo |
| **Blotato** | Multi-platform posting | ✅ | ❌ | ~$20/mo |
| **ManyChat** | Comment-triggered DM automation | ❌ | Auto-reply only | ~$15/mo |
| **IGBot/Instagrapi** | Open-source Python Instagram lib | ✅ | ✅ | Free |

**Recommendation:** Start manual (1 post + 5-7 comments = 5 min/day). Automate with n8n once posting daily for 2+ weeks and patterns are stable.
