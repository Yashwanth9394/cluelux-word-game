#!/bin/bash

# Quick start script for generating words
# Usage: ./scripts/quick-generate.sh YOUR_CLAUDE_API_KEY [NUM_WORDS] [START_DATE]

CLAUDE_API_KEY=$1
NUM_WORDS=${2:-30}
START_DATE=${3:-$(date +%Y-%m-%d)}

if [ -z "$CLAUDE_API_KEY" ]; then
  echo "Error: Claude API key is required"
  echo ""
  echo "Usage: ./scripts/quick-generate.sh YOUR_CLAUDE_API_KEY [NUM_WORDS] [START_DATE]"
  echo ""
  echo "Examples:"
  echo "  ./scripts/quick-generate.sh sk-ant-xxx                    # Generate 30 words from today"
  echo "  ./scripts/quick-generate.sh sk-ant-xxx 60                 # Generate 60 words from today"
  echo "  ./scripts/quick-generate.sh sk-ant-xxx 60 2025-11-13     # Generate 60 words from Nov 13, 2025"
  exit 1
fi

echo "=== ClueLux Word Generator ==="
echo "Claude API Key: ${CLAUDE_API_KEY:0:10}..."
echo "Number of Words: $NUM_WORDS"
echo "Start Date: $START_DATE"
echo ""

CLAUDE_API_KEY=$CLAUDE_API_KEY \
NUM_WORDS=$NUM_WORDS \
START_DATE=$START_DATE \
node scripts/generateWords.js
