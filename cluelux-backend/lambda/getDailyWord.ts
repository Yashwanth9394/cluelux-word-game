import { APIGatewayProxyEvent, APIGatewayProxyResult } from 'aws-lambda';
import { DynamoDBClient } from '@aws-sdk/client-dynamodb';
import { DynamoDBDocumentClient, GetCommand } from '@aws-sdk/lib-dynamodb';
import { DailyWordResponse } from '../shared/types';

const client = new DynamoDBClient({});
const docClient = DynamoDBDocumentClient.from(client);

const TABLE_NAME = process.env.TABLE_NAME || 'ClueLuxWords';

/**
 * Calculate seconds until midnight ET (Eastern Time)
 */
function getSecondsUntilMidnightET(): number {
  const now = new Date();

  // Convert to ET (UTC-5 for EST, UTC-4 for EDT)
  const etOffset = now.toLocaleString('en-US', { timeZone: 'America/New_York' });
  const etNow = new Date(etOffset);

  // Get midnight ET today
  const midnightET = new Date(etNow);
  midnightET.setHours(24, 0, 0, 0); // Next midnight

  const secondsUntilMidnight = Math.floor((midnightET.getTime() - etNow.getTime()) / 1000);

  // Ensure at least 60 seconds to avoid edge cases
  return Math.max(secondsUntilMidnight, 60);
}

/**
 * Lambda handler to get the daily word, hints, and fact
 */
export const handler = async (
  event: APIGatewayProxyEvent
): Promise<APIGatewayProxyResult> => {
  console.log('Event:', JSON.stringify(event, null, 2));

  try {
    // Get today's date in YYYY-MM-DD format (ET timezone)
    const etDate = new Date().toLocaleString('en-US', {
      timeZone: 'America/New_York',
      year: 'numeric',
      month: '2-digit',
      day: '2-digit'
    });

    // Parse MM/DD/YYYY to YYYY-MM-DD
    const [month, day, year] = etDate.split('/');
    const today = `${year}-${month}-${day}`;

    console.log('Fetching word for date (ET):', today);

    // Query DynamoDB for today's word
    const command = new GetCommand({
      TableName: TABLE_NAME,
      Key: {
        date: today,
      },
    });

    const response = await docClient.send(command);

    if (!response.Item) {
      console.log('No word found for today, using fallback');

      // Fallback word if none exists for today
      const fallbackResponse: DailyWordResponse = {
        date: today,
        word: 'LIGHT',
        hints: [
          'Category: Illumination',
          'You turn this on in a dark room',
          'Opposite of darkness',
        ],
        fact: 'The speed of light is 299,792,458 meters per second!',
      };

      const cacheSeconds = getSecondsUntilMidnightET();
      console.log(`Cache TTL: ${cacheSeconds} seconds until midnight ET`);

      return {
        statusCode: 200,
        headers: {
          'Content-Type': 'application/json',
          'Access-Control-Allow-Origin': '*',
          'Access-Control-Allow-Headers': 'Content-Type',
          'Access-Control-Allow-Methods': 'GET,OPTIONS',
          'Cache-Control': `public, max-age=${cacheSeconds}, s-maxage=${cacheSeconds}`,
        },
        body: JSON.stringify(fallbackResponse),
      };
    }

    const dailyWord: DailyWordResponse = {
      date: response.Item.date as string,
      word: response.Item.word as string,
      hints: response.Item.hints as string[],
      fact: response.Item.fact as string,
    };

    console.log('Returning word:', dailyWord.word);

    const cacheSeconds = getSecondsUntilMidnightET();
    console.log(`Cache TTL: ${cacheSeconds} seconds until midnight ET`);

    return {
      statusCode: 200,
      headers: {
        'Content-Type': 'application/json',
        'Access-Control-Allow-Origin': '*',
        'Access-Control-Allow-Headers': 'Content-Type',
        'Access-Control-Allow-Methods': 'GET,OPTIONS',
        'Cache-Control': `public, max-age=${cacheSeconds}, s-maxage=${cacheSeconds}`,
      },
      body: JSON.stringify(dailyWord),
    };
  } catch (error) {
    console.error('Error:', error);

    return {
      statusCode: 500,
      headers: {
        'Content-Type': 'application/json',
        'Access-Control-Allow-Origin': '*',
      },
      body: JSON.stringify({
        message: 'Internal server error',
        error: error instanceof Error ? error.message : 'Unknown error',
      }),
    };
  }
};
