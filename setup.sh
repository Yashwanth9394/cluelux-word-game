#!/bin/bash

# ClueLux App Setup Script
# This script helps you set up both frontend and backend

set -e

echo "╔═══════════════════════════════════════╗"
echo "║     ClueLux App Setup Script          ║"
echo "╚═══════════════════════════════════════╝"
echo ""

# Colors
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
RED='\033[0;31m'
NC='\033[0m' # No Color

# Check if we're in the right directory
if [ ! -d "cluelux-backend" ] || [ ! -d "cluelux-frontend" ]; then
    echo -e "${RED}❌ Error: Must run from cluelux-app directory${NC}"
    exit 1
fi

echo -e "${YELLOW}📦 Step 1: Installing Backend Dependencies${NC}"
cd cluelux-backend
npm install
echo -e "${GREEN}✅ Backend dependencies installed${NC}"
echo ""

echo -e "${YELLOW}📦 Step 2: Installing Frontend Dependencies${NC}"
cd ../cluelux-frontend
npm install
echo -e "${GREEN}✅ Frontend dependencies installed${NC}"
echo ""

echo -e "${YELLOW}⚙️  Step 3: Setting up Frontend Environment${NC}"
if [ ! -f .env ]; then
    cp .env.example .env
    echo -e "${GREEN}✅ Created .env file${NC}"
    echo -e "${YELLOW}⚠️  Please edit cluelux-frontend/.env and add your API URL${NC}"
else
    echo -e "${GREEN}✅ .env file already exists${NC}"
fi
cd ..
echo ""

echo "╔═══════════════════════════════════════╗"
echo "║     Setup Complete! 🎉                ║"
echo "╚═══════════════════════════════════════╝"
echo ""
echo "Next steps:"
echo ""
echo "1. Generate words (requires Claude API key):"
echo "   cd cluelux-backend"
echo "   CLAUDE_API_KEY=sk-ant-your-key NUM_WORDS=30 npm run generate:words"
echo ""
echo "2. Deploy backend to AWS:"
echo "   npm run cdk:bootstrap  # One-time only"
echo "   npm run cdk:deploy"
echo ""
echo "3. Update frontend with API URL:"
echo "   cd ../cluelux-frontend"
echo "   # Edit .env and set VITE_API_URL=<your-api-url>"
echo ""
echo "4. Start frontend:"
echo "   npm run dev"
echo ""
echo "For detailed instructions, see README.md"
