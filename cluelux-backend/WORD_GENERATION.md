# ClueLux Word Generation System

## Overview

Complete system for generating engaging word puzzles with progressive, intriguing hints using Claude AI.

## Features

✅ **Curated Word List**: 110+ words ranging from 6-12 letters
✅ **Smart Tracking**: Automatically tracks which words have hints generated
✅ **Progressive Hints**: 5 hints per word, from cryptic to revealing
✅ **Expert Prompt**: Uses your custom linguist prompt for engaging, puzzle-like hints
✅ **Auto-Save**: Backs up to JSON file and uploads to DynamoDB
✅ **Progress Tracking**: Visual progress bars by word length

## Quick Start

### 1. Check Current Progress
```bash
npm run progress
```

### 2. Generate Hints for Words
```bash
CLAUDE_API_KEY=your_api_key npm run generate:words
```

### 3. Generate Custom Amount
```bash
# Generate 20 words starting Nov 13, 2025
CLAUDE_API_KEY=your_key NUM_WORDS=20 START_DATE=2025-11-13 npm run generate:words
```

## Word Collection

The system includes diverse categories:
- 🌳 Nature & Animals (15 words)
- 🔬 Science & Technology (18 words)
- 🎨 Arts & Culture (12 words)
- 💭 Emotions & Abstract (20 words)
- ⚔️ Historical & Mythical (15 words)
- 🌍 Geography & Places (10 words)
- 🎮 Sports & Games (8 words)
- 🍔 Food & Taste (8 words)
- And more!

## Hint Generation Philosophy

Each word receives 5 progressive hints following your expert linguist prompt:

1. **Cryptic Scene-Setter** 🤔
   - Uses metaphor, imagery, mysterious context
   - Player thinks: "Wait, what? I need to know!"

2. **Riddle or Association** 🎯
   - Cultural references, wordplay, oblique connections
   - Player thinks: "Ooh, I'm getting warmer..."

3. **Plot Twist/Elimination** 🔄
   - Subverts expectations, rules out similar words
   - Player thinks: "Aha! Not what I thought!"

4. **Near-Reveal with Flair** ⚡
   - Strong clue with clever phrasing or pop culture
   - Player thinks: "I can taste it!"

5. **Satisfying Closer** 🎉
   - Structural clue, rhyme, etymology, partial reveal
   - Player thinks: "YES! I knew it!"

## Example Output

```json
{
  "date": "2025-11-13",
  "word": "LABYRINTH",
  "length": 9,
  "type": "noun",
  "category": "structure",
  "hints": [
    "🌀 Where straight lines are myths and every turn whispers doubt",
    "Theseus needed thread; you'll need wits and patience",
    "Not just any maze—this one has mythology in its DNA",
    "Minotaur's playground, now a metaphor for complex journeys",
    "Nine letters starting with 'L', Greek origins, ends with 'H'"
  ],
  "fact": "The original Labyrinth in Greek mythology was built by Daedalus..."
}
```

## File Structure

```
scripts/
├── generateWords.js      # Main generation script
├── wordList.js          # Curated word collection with tracking
├── hintPrompt.js        # Your expert linguist prompt
├── checkProgress.js     # Progress visualization
├── quick-generate.sh    # Helper bash script
└── README.md           # Documentation
```

## Rate Limiting

The script includes automatic delays between API calls (2 seconds) to respect Claude API rate limits.

## Backup & Recovery

All generated words are saved to `data/generated-words.json` before uploading to DynamoDB, so you never lose progress.

## Next Steps

1. Run `npm run progress` to see current status
2. Get your Claude API key from https://console.anthropic.com/
3. Start generating: `CLAUDE_API_KEY=sk-ant-xxx npm run generate:words`
4. Words are automatically uploaded to DynamoDB with sequential dates
5. Check your API endpoint: `https://your-api.amazonaws.com/prod/daily-word`

## Tips

- Start with 5-10 words to test the quality
- Review generated hints in `data/generated-words.json`
- Adjust the word list in `scripts/wordList.js` as needed
- The system tracks which words are generated to avoid duplicates
- You can re-run generation multiple times—it picks up where it left off

Happy word generating! 🎯
