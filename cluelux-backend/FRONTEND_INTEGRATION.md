# Frontend Integration Guide

## ✅ COMPLETED

The frontend has been updated to fetch daily words from this backend API.

## What Was Done

### 1. API Service Created
- **Location:** `Wordle-like Game Mocks/src/services/api.ts`
- **Functions:**
  - `fetchDailyWord()` - Fetches from `/daily-word` endpoint
  - `formatDailyWordForGame()` - Transforms API response for game use
- **Features:**
  - Error handling with fallback
  - Type-safe responses
  - Environment variable configuration

### 2. Frontend Updated
- **File:** `Wordle-like Game Mocks/src/App.tsx`
- **Changes:**
  - Fetches data on component mount
  - Loading state while fetching
  - Falls back to local JSON if API unavailable
  - Toast notifications for status

### 3. Environment Configuration
- **File:** `Wordle-like Game Mocks/.env.example`
- **Variable:** `VITE_API_URL`
- **Default:** `http://localhost:3000`

## Next Steps to Complete Integration

### Step 1: Deploy Backend (Required)

```bash
# In this directory (cluelux-backend)
npm install
npm run cdk:bootstrap  # One-time only
npm run cdk:deploy
```

**Note:** Take note of the API URL from the deployment output:
```
Outputs:
ClueLuxStack.DailyWordEndpoint = https://abc123.execute-api.us-east-1.amazonaws.com/prod/daily-word
```

### Step 2: Configure Frontend

```bash
cd "../Wordle-like Game Mocks"

# Create .env file
cp .env.example .env

# Edit .env and add your deployed API URL
# VITE_API_URL=https://abc123.execute-api.us-east-1.amazonaws.com/prod
```

### Step 3: Test Integration

```bash
# Start frontend
cd "../Wordle-like Game Mocks"
npm run dev

# Open browser and check:
# 1. DevTools Network tab - should see request to /daily-word
# 2. Console - should show "Daily word loaded! 🎯"
# 3. Game should display today's word and hints from backend
```

## API Endpoint

**Endpoint:** `GET /daily-word`

**Response:**
```json
{
  "date": "2025-11-13",
  "word": "LIBRARY",
  "hints": [
    "A public institution dedicated to knowledge and learning 📚",
    "Students visit here to research papers and borrow materials",
    "Belle from Beauty and the Beast dreamed of having her own",
    "A building or room containing collections of books and resources",
    "Sounds like 'lie-brary' when mispronounced by young children"
  ]
}
```

## Current Status

### Backend
- ✅ CDK Stack configured
- ✅ Lambda function created
- ✅ DynamoDB table defined
- ✅ API Gateway with CORS
- ✅ 92 words generated and stored
- ⏸️ **Awaiting deployment** (npm run cdk:deploy)

### Frontend
- ✅ API service implemented
- ✅ App.tsx updated to use API
- ✅ Error handling added
- ✅ Fallback to local JSON
- ✅ Loading states
- ⏸️ **Awaiting API URL configuration**

## Troubleshooting

### Backend Won't Deploy

**Issue:** TypeScript/CDK errors

**Solution:**
```bash
rm -rf node_modules package-lock.json
npm install
npm run cdk:deploy
```

### Frontend Not Connecting

**Check:**
1. Backend is deployed: `aws cloudformation describe-stacks --stack-name ClueLuxStack`
2. `.env` file exists with correct API URL
3. CORS is enabled on API Gateway
4. DynamoDB has data for today's date

### No Data for Today

**Generate more words:**
```bash
CLAUDE_API_KEY=sk-ant-your-key NUM_WORDS=30 npm run generate:words
```

## Documentation

- **Backend Guide:** `README.md`
- **Word Generation:** `WORD_GENERATION.md`
- **Deployment:** `DEPLOYMENT.md`
- **Frontend Integration:** `../Wordle-like Game Mocks/BACKEND_INTEGRATION.md`
- **Hint Quality:** `HINT_QUALITY_FIX.md`

## Architecture

```
┌─────────────────┐
│   React App     │
│   (Frontend)    │
└────────┬────────┘
         │
         │ HTTPS GET /daily-word
         │
┌────────▼────────┐
│  API Gateway    │
└────────┬────────┘
         │
         │ Invoke
         │
┌────────▼────────┐
│ Lambda Function │
│ getDailyWord()  │
└────────┬────────┘
         │
         │ Query
         │
┌────────▼────────┐
│   DynamoDB      │
│ ClueLuxWords    │
└─────────────────┘
```

## Summary

Everything is ready for deployment! Just need to:
1. Deploy backend with `npm run cdk:deploy`
2. Update frontend `.env` with API URL
3. Test the integration

The frontend will automatically use the backend API when configured, with graceful fallback to local data if the API is unavailable.
