# Word Generation Scripts

This folder contains scripts for generating words with progressive, intriguing hints using Claude API.

## Word List

The system includes a curated list of **120+ words** ranging from 6-11 letters across diverse categories:
- Nature & Animals
- Science & Technology  
- Arts & Culture
- Emotions & Abstract Concepts
- Historical & Mythical
- Geography & Places
- And more!

## Setup

1. Install the Anthropic SDK (already done):
```bash
npm install @anthropic-ai/sdk
```

2. Get your Claude API key from: https://console.anthropic.com/

## Generate Words with Hints

The script uses your custom expert linguist prompt to generate 5 progressive hints per word that go from cryptic to revealing, making the game engaging and educational.

### Quick Start

```bash
# Generate 10 words (default)
CLAUDE_API_KEY=your_api_key_here npm run generate:words

# Or use the helper script
./scripts/quick-generate.sh YOUR_CLAUDE_API_KEY
```

### Custom Options

```bash
# Generate 20 words starting from a specific date
CLAUDE_API_KEY=your_key \
NUM_WORDS=20 \
START_DATE=2025-11-13 \
TABLE_NAME=ClueLuxWords \
node scripts/generateWords.js
```

### Environment Variables

- `CLAUDE_API_KEY` (required): Your Claude API key
- `NUM_WORDS` (default: 10): Number of words to generate per run
- `START_DATE` (default: today): Starting date in YYYY-MM-DD format
- `TABLE_NAME` (default: ClueLuxWords): DynamoDB table name

## Check Progress

See how many words have been generated:

```bash
node scripts/checkProgress.js
```

## Output

The script will:
1. Load ungenerated words from the curated list
2. Generate 5 progressive hints for each word using your custom prompt
3. Save them to `data/generated-words.json` as a backup
4. Upload them to DynamoDB with sequential dates

## Hint Structure

Each word gets 5 hints following this emotional arc:

1. **Cryptic/Metaphorical** 🤔 - Mystery & curiosity
2. **Riddle/Cultural** 🎯 - Excitement & pursuit  
3. **Elimination/Twist** 🔄 - Surprise & recalibration
4. **Near-Reveal/Stylized** ⚡ - Near-certainty & anticipation
5. **Structural/Confirmatory** 🎉 - Triumph & confirmation

## Example Generated Word

```json
{
  "date": "2025-11-13",
  "word": "PHOENIX",
  "length": 7,
  "type": "noun",
  "category": "mythical",
  "hints": [
    "🔥 Where endings become beginnings, and ash transforms into glory",
    "This creature knows death is just intermission, not finale",
    "Not an eagle, not a dragon, but legendary nonetheless",
    "Rebirth and flames are its signature moves",
    "Starts with 'P', ends with 'X', seven letters of mythology"
  ],
  "fact": "The Phoenix legend appears in cultures worldwide..."
}
```

## Word Tracking

The system automatically tracks which words have been generated to avoid duplicates. Progress is saved in the wordList.js file.
