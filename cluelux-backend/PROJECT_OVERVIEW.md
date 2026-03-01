# ClueLux Backend - Project Overview

## 📁 Project Structure

```
cluelux-backend/
├── lambda/                    # Lambda function code
│   ├── getDailyWord.ts       # Main handler for API
│   └── package.json          # Lambda dependencies
├── cdk/                      # Infrastructure as Code
│   ├── bin/
│   │   └── app.ts           # CDK app entry point
│   └── lib/
│       └── cluelux-backend-stack.ts  # Stack definition
├── shared/                   # Shared TypeScript types
│   └── types/
│       └── index.ts         # Common interfaces
├── data/                    # Sample data and scripts
│   ├── sample-words.json    # Sample word entries
│   └── seed.js             # Database seeding script
├── package.json            # Project dependencies
├── tsconfig.json           # TypeScript configuration
├── cdk.json               # CDK configuration
├── setup.sh              # Quick setup script
├── DEPLOYMENT.md        # Deployment instructions
├── FRONTEND_INTEGRATION.md  # Frontend integration guide
└── README.md           # This file
```

## 🏗️ Architecture

```
┌─────────────┐
│   Client    │
│  (Frontend) │
└──────┬──────┘
       │
       │ HTTPS
       │
       ▼
┌─────────────┐
│ API Gateway │
│   REST API  │
└──────┬──────┘
       │
       │ Invoke
       │
       ▼
┌─────────────┐     ┌──────────────┐
│   Lambda    │────▶│  DynamoDB    │
│ getDailyWord│     │ ClueLuxWords  │
└─────────────┘     └──────────────┘
```

### Components

1. **API Gateway**: Public REST API endpoint
   - Endpoint: `GET /daily-word`
   - CORS enabled for all origins
   - Returns JSON response

2. **Lambda Function**: Node.js 20.x runtime
   - Handler: `getDailyWord.handler`
   - Timeout: 10 seconds
   - Memory: 256 MB
   - Environment: `TABLE_NAME`

3. **DynamoDB Table**: NoSQL database
   - Table: `ClueLuxWords`
   - Partition Key: `date` (String)
   - Billing: Pay-per-request
   - Retention: RETAIN (data preserved on stack deletion)

## 📊 Data Model

### DynamoDB Item Structure

```json
{
  "date": "2025-11-12",          // Partition key (YYYY-MM-DD)
  "word": "LIGHT",               // The daily word
  "hints": [                     // Array of progressive hints
    "Category: Illumination",
    "You turn this on in a dark room",
    "Opposite of darkness"
  ],
  "fact": "The speed of light..." // Fun fact revealed at the end
}
```

## 🔌 API Reference

### GET /daily-word

Returns the word of the day with hints and fact.

**Request:**
```bash
curl https://YOUR-API-URL/daily-word
```

**Response (200 OK):**
```json
{
  "date": "2025-11-12",
  "word": "LIGHT",
  "hints": [
    "Category: Illumination",
    "You turn this on in a dark room",
    "Opposite of darkness"
  ],
  "fact": "The speed of light is 299,792,458 meters per second!"
}
```

**Response (500 Internal Server Error):**
```json
{
  "message": "Internal server error",
  "error": "Error details..."
}
```

## 🚀 Quick Start

### 1. Setup
```bash
./setup.sh
```

### 2. Deploy
```bash
npm run deploy
```

### 3. Seed Database
```bash
npm run seed
```

### 4. Test
```bash
curl https://YOUR-API-URL/daily-word
```

## 📝 Development Workflow

### Local Development
```bash
# Watch for changes
npm run watch

# Test compilation
npm run build

# Preview CloudFormation
npm run cdk:synth
```

### Deployment
```bash
# Check what will change
npm run cdk:diff

# Deploy changes
npm run deploy
```

### Adding New Words

**Option 1: Bulk import via JSON**

1. Edit `data/sample-words.json`:
```json
{
  "date": "2025-11-17",
  "word": "CLOUD",
  "hints": [
    "Category: Weather",
    "Fluffy white things in the sky",
    "Can bring rain"
  ],
  "fact": "Clouds are made of tiny water droplets or ice crystals!"
}
```

2. Run seed script:
```bash
npm run seed
```

**Option 2: AWS Console**

1. Go to DynamoDB → Tables → ClueLuxWords
2. Create item
3. Fill in fields (date, word, hints, fact)

**Option 3: AWS CLI**
```bash
aws dynamodb put-item \
  --table-name ClueLuxWords \
  --item '{
    "date": {"S": "2025-11-17"},
    "word": {"S": "CLOUD"},
    "hints": {"L": [
      {"S": "Category: Weather"},
      {"S": "Fluffy white things in the sky"}
    ]},
    "fact": {"S": "Clouds are made of tiny water droplets!"}
  }'
```

## 💰 AWS Costs

### Free Tier (First 12 months)
- **Lambda**: 1M requests/month, 400,000 GB-seconds
- **API Gateway**: 1M calls/month (first year)
- **DynamoDB**: 25 GB storage, 25 WCU, 25 RCU

### Beyond Free Tier
- **Lambda**: $0.20 per 1M requests + $0.0000166667 per GB-second
- **API Gateway**: $3.50 per million requests
- **DynamoDB**: $0.25 per GB/month + $0.00013 per read/write

**Estimated cost for 10,000 daily users**: ~$5-10/month

## 🔒 Security Considerations

1. **CORS**: Currently allows all origins (`*`)
   - Update in production to specific domain
   
2. **Rate Limiting**: Not implemented
   - Add API Gateway usage plans if needed

3. **Authentication**: Not required for public API
   - Add API keys or Cognito if needed

4. **Data Validation**: Basic validation in Lambda
   - Add request/response validation in API Gateway

## 🐛 Troubleshooting

### CloudWatch Logs
```bash
aws logs tail /aws/lambda/ClueLuxBackendStack-GetDailyWordFunction --follow
```

### Test Lambda directly
```bash
aws lambda invoke \
  --function-name ClueLuxBackendStack-GetDailyWordFunction \
  --payload '{}' \
  response.json
```

### Check DynamoDB
```bash
aws dynamodb scan --table-name ClueLuxWords --limit 5
```

## 🧹 Cleanup

To delete all AWS resources:
```bash
npm run cdk:destroy
```

⚠️ **Warning**: This will delete the DynamoDB table and all data (unless RETAIN policy prevents it).

## 📚 Resources

- [AWS CDK Documentation](https://docs.aws.amazon.com/cdk/)
- [Lambda Best Practices](https://docs.aws.amazon.com/lambda/latest/dg/best-practices.html)
- [DynamoDB Developer Guide](https://docs.aws.amazon.com/dynamodb/)
- [API Gateway Documentation](https://docs.aws.amazon.com/apigateway/)
