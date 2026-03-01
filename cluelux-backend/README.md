# ClueLux Backend

Backend API for the ClueLux game built with AWS CDK, Lambda, and DynamoDB.

## Architecture

- **API Gateway**: REST API endpoint
- **Lambda**: Serverless function to handle requests
- **DynamoDB**: Store daily words, hints, and facts

## API Endpoint

### GET /daily-word

Returns the word of the day along with hints and a fun fact.

**Response:**
```json
{
  "date": "2025-11-12",
  "word": "LIGHT",
  "hints": [
    "Category: Illumination",
    "It's what you turn on in a dark room",
    "Opposite of darkness"
  ],
  "fact": "The speed of light is 299,792,458 meters per second!"
}
```

## Setup

1. Install dependencies:
```bash
npm install
```

2. Bootstrap CDK (first time only):
```bash
npm run cdk:bootstrap
```

3. Deploy:
```bash
npm run deploy
```

## Development

- `npm run build` - Compile TypeScript
- `npm run watch` - Watch for changes
- `npm run test` - Run tests
- `npm run cdk:diff` - Show deployment changes
- `npm run cdk:synth` - Synthesize CloudFormation template

## Project Structure

```
cluelux-backend/
├── lambda/           # Lambda function code
│   ├── getDailyWord.ts
│   └── utils/
├── cdk/             # Infrastructure code
│   ├── bin/
│   └── lib/
├── shared/          # Shared types
└── data/           # Sample word data
```
