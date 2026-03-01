# Deployment Guide

## Prerequisites

1. **AWS CLI** installed and configured
2. **Node.js** 18+ and npm
3. **AWS CDK CLI** installed globally:
   ```bash
   npm install -g aws-cdk
   ```

## Step 1: Configure AWS Credentials

```bash
aws configure
```

Enter your:
- AWS Access Key ID
- AWS Secret Access Key
- Default region (e.g., us-east-1)
- Default output format (json)

## Step 2: Install Dependencies

```bash
npm install
```

## Step 3: Bootstrap CDK (First Time Only)

This creates the necessary AWS resources for CDK to work:

```bash
npm run cdk:bootstrap
```

## Step 4: Review Changes

See what will be deployed:

```bash
npm run cdk:synth
```

Or check the difference with existing stack:

```bash
npm run cdk:diff
```

## Step 5: Deploy

Deploy the stack to AWS:

```bash
npm run deploy
```

This will:
- Create a DynamoDB table named `ClueLuxWords`
- Create a Lambda function to handle API requests
- Create an API Gateway REST API
- Output the API endpoint URL

**Note the API URL from the output!** It will look like:
```
https://xxxxxxxxxx.execute-api.us-east-1.amazonaws.com/prod/
```

## Step 6: Seed the Database

After deployment, populate the database with sample words:

```bash
npm run seed
```

## Step 7: Test the API

```bash
curl https://YOUR-API-URL/daily-word
```

Expected response:
```json
{
  "date": "2025-11-12",
  "word": "LIGHT",
  "hints": [
    "Category: Illumination",
    "You turn this on in a dark room",
    "Opposite of darkness"
  ],
  "fact": "The speed of light is 299,792,458 meters per second, making it the fastest thing in the universe!"
}
```

## Update Frontend

Add the API URL to your frontend's environment variables or config:

```typescript
// frontend/src/config.ts
export const API_BASE_URL = 'https://YOUR-API-URL';
```

## Useful Commands

- `npm run build` - Compile TypeScript
- `npm run watch` - Watch for changes
- `npm run cdk:synth` - Synthesize CloudFormation template
- `npm run cdk:diff` - Compare deployed stack with current state
- `npm run deploy` - Deploy to AWS
- `npm run cdk:destroy` - Delete all AWS resources (⚠️ be careful!)
- `npm run seed` - Populate database with sample words

## Adding New Words

1. Edit `data/sample-words.json`
2. Run `npm run seed`

Or manually add via AWS Console:
- Go to DynamoDB → Tables → ClueLuxWords
- Create item with:
  - `date` (String): "YYYY-MM-DD"
  - `word` (String): "YOURWORD"
  - `hints` (List): ["Hint 1", "Hint 2", "Hint 3"]
  - `fact` (String): "Fun fact about the word"

## Costs

With AWS Free Tier:
- **DynamoDB**: 25 GB storage, 25 read/write capacity units
- **Lambda**: 1M requests/month, 400,000 GB-seconds compute
- **API Gateway**: 1M API calls/month

Expected cost: **$0** for low traffic (within free tier)

## Troubleshooting

### Lambda errors
Check CloudWatch Logs:
```bash
aws logs tail /aws/lambda/ClueLuxBackendStack-GetDailyWordFunction --follow
```

### Deploy fails
- Ensure AWS credentials are configured
- Check you have necessary IAM permissions
- Try `cdk bootstrap` again

### API returns 500
- Check Lambda logs in CloudWatch
- Verify DynamoDB table exists and has data
- Check table name in Lambda environment variables
