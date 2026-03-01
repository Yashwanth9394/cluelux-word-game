#!/bin/bash

# Quick setup script for ClueLux Backend

set -e

echo "🎮 ClueLux Backend Setup"
echo "======================="
echo ""

# Check if AWS CLI is installed
if ! command -v aws &> /dev/null; then
    echo "❌ AWS CLI is not installed. Please install it first:"
    echo "   https://aws.amazon.com/cli/"
    exit 1
fi

# Check if CDK is installed
if ! command -v cdk &> /dev/null; then
    echo "📦 Installing AWS CDK globally..."
    npm install -g aws-cdk
fi

# Install dependencies
echo "📦 Installing dependencies..."
npm install

# Check AWS credentials
echo ""
echo "🔑 Checking AWS credentials..."
if aws sts get-caller-identity &> /dev/null; then
    echo "✅ AWS credentials configured"
    aws sts get-caller-identity --query 'Account' --output text | xargs -I {} echo "   Account: {}"
else
    echo "❌ AWS credentials not configured. Please run:"
    echo "   aws configure"
    exit 1
fi

# Build
echo ""
echo "🔨 Building TypeScript..."
npm run build

# Bootstrap CDK
echo ""
echo "🚀 Bootstrapping CDK (this may take a few minutes)..."
read -p "Continue with CDK bootstrap? (y/n) " -n 1 -r
echo
if [[ $REPLY =~ ^[Yy]$ ]]; then
    npm run cdk:bootstrap
fi

# Show what will be deployed
echo ""
echo "📋 Synthesizing stack..."
npm run cdk:synth

echo ""
echo "✅ Setup complete!"
echo ""
echo "Next steps:"
echo "1. Review the CloudFormation template above"
echo "2. Deploy with: npm run deploy"
echo "3. Seed database with: npm run seed"
echo "4. Test with: curl https://YOUR-API-URL/daily-word"
echo ""
