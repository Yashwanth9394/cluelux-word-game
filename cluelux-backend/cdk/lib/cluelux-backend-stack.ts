import * as cdk from 'aws-cdk-lib';
import { Construct } from 'constructs';
import * as lambda from 'aws-cdk-lib/aws-lambda';
import { NodejsFunction } from 'aws-cdk-lib/aws-lambda-nodejs';
import * as apigateway from 'aws-cdk-lib/aws-apigateway';
import * as dynamodb from 'aws-cdk-lib/aws-dynamodb';
import * as cloudfront from 'aws-cdk-lib/aws-cloudfront';
import * as origins from 'aws-cdk-lib/aws-cloudfront-origins';
import * as path from 'path';

export class ClueLuxBackendStack extends cdk.Stack {
  constructor(scope: Construct, id: string, props?: cdk.StackProps) {
    super(scope, id, props);

    // DynamoDB Table for storing daily words
    const wordsTable = new dynamodb.Table(this, 'WordsTable', {
      tableName: 'ClueLuxWords',
      partitionKey: {
        name: 'date',
        type: dynamodb.AttributeType.STRING,
      },
      billingMode: dynamodb.BillingMode.PAY_PER_REQUEST,
      removalPolicy: cdk.RemovalPolicy.RETAIN, // Keep data on stack deletion
    });

    // Lambda function to get daily word
    const getDailyWordFunction = new NodejsFunction(this, 'GetDailyWordFunction', {
      runtime: lambda.Runtime.NODEJS_20_X,
      entry: path.join(__dirname, '../../lambda/getDailyWord.ts'),
      handler: 'handler',
      environment: {
        TABLE_NAME: wordsTable.tableName,
      },
      timeout: cdk.Duration.seconds(10),
      memorySize: 256,
      bundling: {
        minify: true,
        sourceMap: true,
      },
    });

    // Grant Lambda permission to read from DynamoDB
    wordsTable.grantReadData(getDailyWordFunction);

    // API Gateway
    const api = new apigateway.RestApi(this, 'ClueLuxApi', {
      restApiName: 'ClueLux Game API',
      description: 'API for ClueLux game',
      defaultCorsPreflightOptions: {
        allowOrigins: apigateway.Cors.ALL_ORIGINS,
        allowMethods: apigateway.Cors.ALL_METHODS,
        allowHeaders: ['Content-Type', 'Authorization'],
      },
    });

    // API Gateway integration
    const dailyWordIntegration = new apigateway.LambdaIntegration(getDailyWordFunction);

    // Add /daily-word endpoint
    const dailyWordResource = api.root.addResource('daily-word');
    dailyWordResource.addMethod('GET', dailyWordIntegration);

    // CloudFront Distribution for CDN caching
    const distribution = new cloudfront.Distribution(this, 'ClueLuxDistribution', {
      defaultBehavior: {
        origin: new origins.RestApiOrigin(api, {
          originPath: '/prod',
        }),
        allowedMethods: cloudfront.AllowedMethods.ALLOW_GET_HEAD_OPTIONS,
        viewerProtocolPolicy: cloudfront.ViewerProtocolPolicy.REDIRECT_TO_HTTPS,
        cachePolicy: new cloudfront.CachePolicy(this, 'ClueLuxCachePolicy', {
          cachePolicyName: 'ClueLuxApiCachePolicy',
          comment: 'Cache daily word until midnight ET (respects Cache-Control headers)',
          defaultTtl: cdk.Duration.hours(12), // Default if no Cache-Control header
          minTtl: cdk.Duration.seconds(60), // Minimum 1 minute
          maxTtl: cdk.Duration.hours(24), // Maximum 24 hours
          enableAcceptEncodingGzip: true,
          enableAcceptEncodingBrotli: true,
          headerBehavior: cloudfront.CacheHeaderBehavior.allowList(
            'Accept',
            'Accept-Language'
          ),
          queryStringBehavior: cloudfront.CacheQueryStringBehavior.none(),
          // Respect Cache-Control headers from Lambda
          cookieBehavior: cloudfront.CacheCookieBehavior.none(),
        }),
        originRequestPolicy: cloudfront.OriginRequestPolicy.CORS_CUSTOM_ORIGIN,
        responseHeadersPolicy: cloudfront.ResponseHeadersPolicy.CORS_ALLOW_ALL_ORIGINS,
      },
      comment: 'ClueLux Game CDN - Auto-invalidates at midnight ET',
    });

    // Outputs
    new cdk.CfnOutput(this, 'CloudFrontUrl', {
      value: `https://${distribution.distributionDomainName}`,
      description: 'CloudFront CDN URL (Use this for best performance!)',
      exportName: 'ClueLuxCloudFrontUrl',
    });

    new cdk.CfnOutput(this, 'CloudFrontDailyWordEndpoint', {
      value: `https://${distribution.distributionDomainName}/daily-word`,
      description: 'CloudFront Daily Word Endpoint (Cached)',
      exportName: 'ClueLuxCloudFrontDailyWordEndpoint',
    });

    new cdk.CfnOutput(this, 'ApiUrl', {
      value: api.url,
      description: 'API Gateway URL (Direct, not cached)',
      exportName: 'ClueLuxApiUrl',
    });

    new cdk.CfnOutput(this, 'DailyWordEndpoint', {
      value: `${api.url}daily-word`,
      description: 'Daily Word Endpoint (Direct)',
      exportName: 'ClueLuxDailyWordEndpoint',
    });

    new cdk.CfnOutput(this, 'TableName', {
      value: wordsTable.tableName,
      description: 'DynamoDB Table Name',
      exportName: 'ClueLuxWordsTableName',
    });
  }
}
