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
import software.amazon.awscdk.services.cloudwatch.Alarm;
import software.amazon.awscdk.services.cloudwatch.ComparisonOperator;
import software.amazon.awscdk.services.cloudwatch.Dashboard;
import software.amazon.awscdk.services.cloudwatch.GraphWidget;
import software.amazon.awscdk.services.cloudwatch.Metric;
import software.amazon.awscdk.services.cloudwatch.MetricOptions;
import software.amazon.awscdk.services.cloudwatch.TreatMissingData;
import software.amazon.awscdk.services.cloudwatch.actions.SnsAction;
import software.amazon.awscdk.services.sns.Topic;
import software.amazon.awscdk.services.sns.subscriptions.EmailSubscription;
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

    // ---- Monitoring: alarms (email) + a dashboard. Prod only - a test
    // environment paging someone for test traffic isn't useful, so UAT
    // deliberately has none of this. ----

    Topic alarmTopic = new Topic(this, "AlarmTopic", software.amazon.awscdk.services.sns.TopicProps.builder()
        .topicName("cluelux-prod-alarms")
        .build());
    alarmTopic.addSubscription(new EmailSubscription("yashwanth753@gmail.com"));
    SnsAction notify = new SnsAction(alarmTopic);

    Alarm lambdaErrors = Alarm.Builder.create(this, "LambdaErrorsAlarm")
        .alarmName("ClueLux-LambdaErrors")
        .alarmDescription("Any Lambda error - low-traffic app, a single error is worth knowing about")
        .metric(getDailyWordFunction.metricErrors(MetricOptions.builder()
            .period(Duration.minutes(5)).statistic("Sum").build()))
        .threshold(1)
        .evaluationPeriods(1)
        .comparisonOperator(ComparisonOperator.GREATER_THAN_OR_EQUAL_TO_THRESHOLD)
        .treatMissingData(TreatMissingData.NOT_BREACHING)
        .build();
    lambdaErrors.addAlarmAction(notify);

    Alarm lambdaThrottles = Alarm.Builder.create(this, "LambdaThrottlesAlarm")
        .alarmName("ClueLux-LambdaThrottles")
        .alarmDescription("Lambda is being throttled - requests are being rejected outright")
        .metric(getDailyWordFunction.metricThrottles(MetricOptions.builder()
            .period(Duration.minutes(5)).statistic("Sum").build()))
        .threshold(1)
        .evaluationPeriods(1)
        .comparisonOperator(ComparisonOperator.GREATER_THAN_OR_EQUAL_TO_THRESHOLD)
        .treatMissingData(TreatMissingData.NOT_BREACHING)
        .build();
    lambdaThrottles.addAlarmAction(notify);

    Alarm lambdaDurationHigh = Alarm.Builder.create(this, "LambdaDurationAlarm")
        .alarmName("ClueLux-LambdaDurationHigh")
        .alarmDescription("p99 duration above 15s - approaching the 20s timeout")
        .metric(getDailyWordFunction.metricDuration(MetricOptions.builder()
            .period(Duration.minutes(5)).statistic("p99").build()))
        .threshold(15000) // ms
        .evaluationPeriods(2)
        .comparisonOperator(ComparisonOperator.GREATER_THAN_THRESHOLD)
        .treatMissingData(TreatMissingData.NOT_BREACHING)
        .build();
    lambdaDurationHigh.addAlarmAction(notify);

    Alarm api5xx = Alarm.Builder.create(this, "Api5xxAlarm")
        .alarmName("ClueLux-Api5xxErrors")
        .alarmDescription("Any 5xx from API Gateway - low-traffic app, a single server error is worth knowing about")
        .metric(api.metricServerError(MetricOptions.builder()
            .period(Duration.minutes(5)).statistic("Sum").build()))
        .threshold(1)
        .evaluationPeriods(1)
        .comparisonOperator(ComparisonOperator.GREATER_THAN_OR_EQUAL_TO_THRESHOLD)
        .treatMissingData(TreatMissingData.NOT_BREACHING)
        .build();
    api5xx.addAlarmAction(notify);

    // Not a CDK helper method (Table doesn't expose a stable one across
    // versions for this) - built directly against the raw CloudWatch metric.
    Metric ddbThrottledRequests = Metric.Builder.create()
        .namespace("AWS/DynamoDB")
        .metricName("ThrottledRequests")
        .dimensionsMap(Map.of("TableName", wordsTable.getTableName()))
        .statistic("Sum")
        .period(Duration.minutes(5))
        .build();

    Alarm ddbThrottleAlarm = Alarm.Builder.create(this, "DynamoDbThrottleAlarm")
        .alarmName("ClueLux-DynamoDbThrottles")
        .alarmDescription("DynamoDB is throttling requests - shouldn't happen on-demand billing")
        .metric(ddbThrottledRequests)
        .threshold(1)
        .evaluationPeriods(1)
        .comparisonOperator(ComparisonOperator.GREATER_THAN_OR_EQUAL_TO_THRESHOLD)
        .treatMissingData(TreatMissingData.NOT_BREACHING)
        .build();
    ddbThrottleAlarm.addAlarmAction(notify);

    Dashboard dashboard = Dashboard.Builder.create(this, "ClueLuxDashboard")
        .dashboardName("ClueLux-Prod")
        .build();

    dashboard.addWidgets(
        GraphWidget.Builder.create()
            .title("API requests (count / 4xx / 5xx)")
            .left(List.of(api.metricCount(), api.metricClientError(), api.metricServerError()))
            .width(12).height(6)
            .build(),
        GraphWidget.Builder.create()
            .title("API latency")
            .left(List.of(api.metricLatency()))
            .width(12).height(6)
            .build());

    dashboard.addWidgets(
        GraphWidget.Builder.create()
            .title("Lambda (invocations / errors / throttles)")
            .left(List.of(getDailyWordFunction.metricInvocations(), getDailyWordFunction.metricErrors(), getDailyWordFunction.metricThrottles()))
            .width(12).height(6)
            .build(),
        GraphWidget.Builder.create()
            .title("Lambda duration")
            .left(List.of(getDailyWordFunction.metricDuration()))
            .width(12).height(6)
            .build());

    dashboard.addWidgets(
        GraphWidget.Builder.create()
            .title("DynamoDB consumed read capacity")
            .left(List.of(wordsTable.metricConsumedReadCapacityUnits()))
            .width(24).height(6)
            .build());

    new CfnOutput(this, "DashboardUrl", CfnOutputProps.builder()
        .value("https://" + this.getRegion() + ".console.aws.amazon.com/cloudwatch/home?region="
            + this.getRegion() + "#dashboards:name=" + dashboard.getDashboardName())
        .description("CloudWatch dashboard - request counts, error rates, latency, Lambda, DynamoDB")
        .build());
  }
}
