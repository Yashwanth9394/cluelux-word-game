package com.cluelux.infra;

import software.amazon.awscdk.CfnOutput;
import software.amazon.awscdk.CfnOutputProps;
import software.amazon.awscdk.Duration;
import software.amazon.awscdk.RemovalPolicy;
import software.amazon.awscdk.Stack;
import software.amazon.awscdk.StackProps;
import software.amazon.awscdk.services.apigateway.Cors;
import software.amazon.awscdk.services.apigateway.CorsOptions;
import software.amazon.awscdk.services.apigateway.LambdaIntegration;
import software.amazon.awscdk.services.apigateway.Resource;
import software.amazon.awscdk.services.apigateway.RestApi;
import software.amazon.awscdk.services.apigateway.RestApiProps;
import software.amazon.awscdk.services.cloudfront.AllowedMethods;
import software.amazon.awscdk.services.cloudfront.BehaviorOptions;
import software.amazon.awscdk.services.cloudfront.CacheCookieBehavior;
import software.amazon.awscdk.services.cloudfront.CacheHeaderBehavior;
import software.amazon.awscdk.services.cloudfront.CachePolicy;
import software.amazon.awscdk.services.cloudfront.CachePolicyProps;
import software.amazon.awscdk.services.cloudfront.CacheQueryStringBehavior;
import software.amazon.awscdk.services.cloudfront.Distribution;
import software.amazon.awscdk.services.cloudfront.DistributionProps;
import software.amazon.awscdk.services.cloudfront.OriginRequestPolicy;
import software.amazon.awscdk.services.cloudfront.ResponseHeadersPolicy;
import software.amazon.awscdk.services.cloudfront.ViewerProtocolPolicy;
import software.amazon.awscdk.services.cloudfront.origins.RestApiOrigin;
import software.amazon.awscdk.services.cloudfront.origins.RestApiOriginProps;
import software.amazon.awscdk.services.dynamodb.Attribute;
import software.amazon.awscdk.services.dynamodb.AttributeType;
import software.amazon.awscdk.services.dynamodb.BillingMode;
import software.amazon.awscdk.services.dynamodb.Table;
import software.amazon.awscdk.services.dynamodb.TableProps;
import software.amazon.awscdk.services.lambda.Code;
import software.amazon.awscdk.services.lambda.Function;
import software.amazon.awscdk.services.lambda.FunctionProps;
import software.amazon.awscdk.services.lambda.Runtime;
import software.constructs.Construct;

import java.util.List;
import java.util.Map;

/**
 * Java port of cdk/lib/cluelux-backend-stack.ts.
 *
 * Every logical construct ID below (WordsTable, GetDailyWordFunction,
 * ClueLuxApi, ClueLuxCachePolicy, ClueLuxDistribution) is kept IDENTICAL
 * to the TypeScript version on purpose: `cdk deploy` tracks resources by
 * logical ID within this same stack name (ClueLuxBackendStack), so matching
 * IDs makes this an in-place update of the live stack (DynamoDB table and
 * CloudFront distribution kept, Lambda code/runtime swapped Node->Java)
 * instead of CloudFormation tearing the old resources down and standing up
 * new ones — which would orphan the CloudFront domain the frontend already
 * points at and the DynamoDB data already in the table.
 */
public class ClueLuxBackendStack extends Stack {

  public ClueLuxBackendStack(final Construct scope, final String id, final StackProps props) {
    super(scope, id, props);

    Table wordsTable = new Table(this, "WordsTable", TableProps.builder()
        .tableName("ClueLuxWords")
        .partitionKey(Attribute.builder().name("date").type(AttributeType.STRING).build())
        .billingMode(BillingMode.PAY_PER_REQUEST)
        .removalPolicy(RemovalPolicy.RETAIN)
        .build());

    Function getDailyWordFunction = new Function(this, "GetDailyWordFunction", FunctionProps.builder()
        .runtime(Runtime.JAVA_21)
        .code(Code.fromAsset("lambda/target/cluelux-lambda.jar"))
        .handler("com.cluelux.lambda.GetDailyWordHandler::handleRequest")
        .environment(Map.of(
            "TABLE_NAME", wordsTable.getTableName(),
            // Only set when PREVIEW_KEY is exported before `cdk deploy` runs;
            // empty disables the preview-date override entirely (closed by
            // default). Rotate by re-exporting and redeploying.
            "PREVIEW_KEY", System.getenv().getOrDefault("PREVIEW_KEY", "")))
        // Java cold starts run longer than Node's; 20s/512MB gives headroom
        // the original 10s/256MB Node config didn't need to budget for.
        .timeout(Duration.seconds(20))
        .memorySize(512)
        .build());

    wordsTable.grantReadData(getDailyWordFunction);

    RestApi api = new RestApi(this, "ClueLuxApi", RestApiProps.builder()
        .restApiName("ClueLux Game API")
        .description("API for ClueLux game")
        .defaultCorsPreflightOptions(CorsOptions.builder()
            .allowOrigins(Cors.ALL_ORIGINS)
            .allowMethods(Cors.ALL_METHODS)
            .allowHeaders(List.of("Content-Type", "Authorization"))
            .build())
        .build());

    LambdaIntegration dailyWordIntegration = new LambdaIntegration(getDailyWordFunction);
    Resource dailyWordResource = api.getRoot().addResource("daily-word");
    dailyWordResource.addMethod("GET", dailyWordIntegration);

    CachePolicy cachePolicy = new CachePolicy(this, "ClueLuxCachePolicy", CachePolicyProps.builder()
        .cachePolicyName("ClueLuxApiCachePolicy")
        .comment("Cache daily word until midnight ET (respects Cache-Control headers)")
        .defaultTtl(Duration.hours(12))
        .minTtl(Duration.seconds(60))
        .maxTtl(Duration.hours(24))
        .enableAcceptEncodingGzip(true)
        .enableAcceptEncodingBrotli(true)
        .headerBehavior(CacheHeaderBehavior.allowList("Accept", "Accept-Language"))
        .queryStringBehavior(CacheQueryStringBehavior.none())
        .cookieBehavior(CacheCookieBehavior.none())
        .build());

    Distribution distribution = new Distribution(this, "ClueLuxDistribution", DistributionProps.builder()
        .defaultBehavior(BehaviorOptions.builder()
            .origin(new RestApiOrigin(api, RestApiOriginProps.builder().originPath("/prod").build()))
            .allowedMethods(AllowedMethods.ALLOW_GET_HEAD_OPTIONS)
            .viewerProtocolPolicy(ViewerProtocolPolicy.REDIRECT_TO_HTTPS)
            .cachePolicy(cachePolicy)
            .originRequestPolicy(OriginRequestPolicy.CORS_CUSTOM_ORIGIN)
            .responseHeadersPolicy(ResponseHeadersPolicy.CORS_ALLOW_ALL_ORIGINS)
            .build())
        .comment("ClueLux Game CDN - Auto-invalidates at midnight ET")
        .build());

    new CfnOutput(this, "CloudFrontUrl", CfnOutputProps.builder()
        .value("https://" + distribution.getDistributionDomainName())
        .description("CloudFront CDN URL (Use this for best performance!)")
        .exportName("ClueLuxCloudFrontUrl")
        .build());

    new CfnOutput(this, "CloudFrontDailyWordEndpoint", CfnOutputProps.builder()
        .value("https://" + distribution.getDistributionDomainName() + "/daily-word")
        .description("CloudFront Daily Word Endpoint (Cached)")
        .exportName("ClueLuxCloudFrontDailyWordEndpoint")
        .build());

    new CfnOutput(this, "ApiUrl", CfnOutputProps.builder()
        .value(api.getUrl())
        .description("API Gateway URL (Direct, not cached)")
        .exportName("ClueLuxApiUrl")
        .build());

    new CfnOutput(this, "DailyWordEndpoint", CfnOutputProps.builder()
        .value(api.getUrl() + "daily-word")
        .description("Daily Word Endpoint (Direct)")
        .exportName("ClueLuxDailyWordEndpoint")
        .build());

    new CfnOutput(this, "TableName", CfnOutputProps.builder()
        .value(wordsTable.getTableName())
        .description("DynamoDB Table Name")
        .exportName("ClueLuxWordsTableName")
        .build());
  }
}
