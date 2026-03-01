# ClueLux App

A daily word puzzle game with progressive hints, built with React frontend and AWS serverless backend.

## 🎯 Project Overview

ClueLux is a Wordle-style word guessing game where players:
- Get a new word daily (6-12 letters)
- Receive 5 progressive hints that get clearer
- Use hints strategically to guess the word
- Track their performance with stats

## 📁 Project Structure

```
cluelux-app/
├── cluelux-frontend/          # React + Vite frontend
│   ├── src/
│   │   ├── components/        # UI components
│   │   ├── services/          # API service for backend
│   │   ├── lib/              # Game logic
│   │   └── App.tsx           # Main app
│   └── package.json
│
└── cluelux-backend/           # AWS CDK backend
    ├── cdk/                   # Infrastructure as code
    ├── lambda/                # Lambda functions
    ├── data/                  # Generated words
    ├── scripts/               # Word generation scripts
    └── package.json
```

## 🚀 Quick Start

### Prerequisites

- Node.js 18+ and npm
- AWS CLI configured with credentials
- AWS CDK CLI installed (`npm install -g aws-cdk`)
- Claude API key (for generating hints)

### 1. Setup Backend

```bash
cd cluelux-backend

# Install dependencies
npm install

# Generate words with hints (first time)
CLAUDE_API_KEY=sk-ant-your-key NUM_WORDS=30 npm run generate:words

# Deploy to AWS
npm run cdk:bootstrap  # One-time only
npm run cdk:deploy

# Note the API URL from output:
# ClueLuxStack.DailyWordEndpoint = https://xxx.execute-api.region.amazonaws.com/prod/daily-word
```

### 2. Setup Frontend

```bash
cd cluelux-frontend

# Install dependencies
npm install

# Configure API URL
cp .env.example .env
# Edit .env and add: VITE_API_URL=<your-api-url-from-backend-deployment>

# Start development server
npm run dev
```

### 3. Open Browser

Visit `http://localhost:5173` and start playing!

## 📚 Documentation

### Backend
- **[README](./cluelux-backend/README.md)** - Backend overview
- **[WORD_GENERATION](./cluelux-backend/WORD_GENERATION.md)** - How word hints are generated
- **[HINT_QUALITY_FIX](./cluelux-backend/HINT_QUALITY_FIX.md)** - Hint quality improvements
- **[DEPLOYMENT](./cluelux-backend/DEPLOYMENT.md)** - AWS deployment guide
- **[FRONTEND_INTEGRATION](./cluelux-backend/FRONTEND_INTEGRATION.md)** - Integration status

### Frontend
- **[BACKEND_INTEGRATION](./cluelux-frontend/BACKEND_INTEGRATION.md)** - API integration guide
- **[START_HERE](./cluelux-frontend/START_HERE.md)** - Game features and setup
- **[README_CLUELUX](./cluelux-frontend/README_CLUELUX.md)** - Game design

## 🎮 Features

### Game Features
- **Daily Word Challenge** - New word every day
- **Progressive Hints** - 5 hints from cryptic to revealing
- **Smart Keyboard** - Visual feedback (green/yellow/gray)
- **Statistics** - Track wins, streaks, and hint usage
- **Responsive Design** - Works on desktop and mobile
- **Offline Support** - Graceful fallback if API unavailable

### Technical Features
- **Serverless Backend** - AWS Lambda + API Gateway + DynamoDB
- **React Frontend** - TypeScript, Vite, Tailwind CSS
- **AI-Generated Hints** - Claude Sonnet 4 creates engaging hints
- **Random Word Selection** - Mixed word lengths for variety
- **CORS Enabled** - Cross-origin requests supported
- **Error Handling** - Robust fallbacks and user feedback

## 🏗️ Architecture

```
┌──────────────────┐
│  React Frontend  │
│  (Vite + TS)     │
└────────┬─────────┘
         │ HTTPS
         ▼
┌────────────────────┐
│   API Gateway      │
│   /daily-word      │
└────────┬───────────┘
         │
         ▼
┌────────────────────┐
│  Lambda Function   │
│  getDailyWord()    │
└────────┬───────────┘
         │
         ▼
┌────────────────────┐
│    DynamoDB        │
│  ClueLuxWords      │
│  {date, word,      │
│   hints, facts}    │
└────────────────────┘
```

## 🔧 Development

### Backend Development

```bash
cd cluelux-backend

# Generate more words
CLAUDE_API_KEY=sk-ant-your-key NUM_WORDS=50 npm run generate:words

# Check progress
npm run progress

# Fix problematic hints
CLAUDE_API_KEY=sk-ant-your-key npm run fix:hints

# Deploy changes
npm run cdk:deploy
```

### Frontend Development

```bash
cd cluelux-frontend

# Start dev server
npm run dev

# Build for production
npm run build

# Preview production build
npm run preview
```

## 📊 Current Status

### Backend
- ✅ Infrastructure defined (CDK)
- ✅ Lambda function created
- ✅ DynamoDB table configured
- ✅ 92 words generated with hints
- ✅ Random word ordering
- ✅ Quality hints (no letter count reveals)
- ⏸️ **Ready to deploy**

### Frontend
- ✅ Game UI complete
- ✅ API service implemented
- ✅ Error handling with fallbacks
- ✅ Stats and analytics
- ✅ Responsive design
- ✅ Tutorial system
- ⏸️ **Ready to configure with API URL**

## 🐛 Troubleshooting

### Backend won't deploy
```bash
cd cluelux-backend
rm -rf node_modules package-lock.json
npm install
npm run cdk:deploy
```

### Frontend not connecting to backend
1. Check `.env` file has correct `VITE_API_URL`
2. Verify backend is deployed
3. Check browser console for errors
4. Verify CORS is enabled on API Gateway

### No word for today
```bash
cd cluelux-backend
CLAUDE_API_KEY=your-key NUM_WORDS=30 npm run generate:words
```

## 📝 Environment Variables

### Backend
```env
# For word generation
CLAUDE_API_KEY=sk-ant-your-key
NUM_WORDS=30
START_DATE=2025-11-13
TABLE_NAME=ClueLuxWords
```

### Frontend
```env
# API endpoint from backend deployment
VITE_API_URL=https://your-api.execute-api.region.amazonaws.com/prod
```

## 🚢 Deployment Checklist

- [ ] Clone/setup repository
- [ ] Install dependencies (both projects)
- [ ] Configure AWS credentials
- [ ] Generate words with Claude API
- [ ] Deploy backend to AWS
- [ ] Note API endpoint URL
- [ ] Configure frontend .env
- [ ] Test integration
- [ ] Build frontend for production
- [ ] Deploy frontend to hosting (Vercel/Netlify/S3)

## 🔐 Security Notes

- API has CORS enabled (update origins in production)
- No authentication currently (add if needed)
- Consider rate limiting for production
- Claude API key stored securely (not in repo)
- DynamoDB on-demand pricing (cost-effective)

## 💡 Future Enhancements

- [ ] User accounts and authentication
- [ ] Leaderboards
- [ ] Social sharing with spoiler protection
- [ ] Word difficulty ratings
- [ ] Custom word lists
- [ ] Multiple languages
- [ ] Hint purchase system
- [ ] Daily streak bonuses

## 📄 License

Private project - All rights reserved

## 👥 Contributors

- Yashwanth - Full Stack Development

---

**Last Updated:** November 13, 2025

For detailed setup instructions, see documentation in each subdirectory.
