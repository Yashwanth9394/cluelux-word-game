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
 * A completely separate, isolated UAT environment — its own DynamoDB table,
 * Lambda and API Gateway, sharing nothing with ClueLuxBackendStack (prod).
 *
 * Deliberately a SEPARATE CLASS rather than a parameterized version of
 * ClueLuxBackendStack: the prod stack's logical IDs and physical names are
 * tied to real, live, already-deployed resources (see the warning in that
 * file), so refactoring it to be "environment-aware" would risk an accidental
 * drift in the one stack that must never change shape by surprise. Copying
 * instead of parameterizing costs some duplication but makes "can this ever
 * touch prod" trivially true: no, this class never even imports prod's.
 *
 * Deliberately no CloudFront here: UAT is for active testing, where seeing
 * a fresh write immediately matters more than edge caching. Call the API
 * Gateway URL directly.
 */
public class ClueLuxUatStack extends Stack {

  public ClueLuxUatStack(final Construct scope, final String id, final StackProps props) {
    super(scope, id, props);

    Table wordsTable = new Table(this, "WordsTableUAT", TableProps.builder()
        .tableName("ClueLuxWords-uat")
        .partitionKey(Attribute.builder().name("date").type(AttributeType.STRING).build())
        .billingMode(BillingMode.PAY_PER_REQUEST)
        // UAT data is disposable test data, unlike prod's RETAIN.
        .removalPolicy(RemovalPolicy.DESTROY)
        .build());

    Function getDailyWordFunction = new Function(this, "GetDailyWordFunctionUAT", FunctionProps.builder()
        .functionName("cluelux-get-daily-word-uat")
        .runtime(Runtime.JAVA_21)
        .code(Code.fromAsset("lambda/target/cluelux-lambda.jar"))
        .handler("com.cluelux.lambda.GetDailyWordHandler::handleRequest")
        .environment(Map.of(
            "TABLE_NAME", wordsTable.getTableName(),
            "PREVIEW_KEY", System.getenv().getOrDefault("PREVIEW_KEY", "")))
        .timeout(Duration.seconds(20))
        .memorySize(512)
        .build());

    wordsTable.grantReadData(getDailyWordFunction);

    RestApi api = new RestApi(this, "ClueLuxApiUAT", RestApiProps.builder()
        .restApiName("ClueLux Game API (UAT)")
        .description("Isolated UAT copy of the ClueLux API - own table, own Lambda, shares nothing with prod")
        .defaultCorsPreflightOptions(CorsOptions.builder()
            .allowOrigins(Cors.ALL_ORIGINS)
            .allowMethods(Cors.ALL_METHODS)
            .allowHeaders(List.of("Content-Type", "Authorization"))
            .build())
        .build());

    LambdaIntegration dailyWordIntegration = new LambdaIntegration(getDailyWordFunction);
    Resource dailyWordResource = api.getRoot().addResource("daily-word");
    dailyWordResource.addMethod("GET", dailyWordIntegration);

    new CfnOutput(this, "ApiUrl", CfnOutputProps.builder()
        .value(api.getUrl())
        .description("UAT API Gateway URL - call this directly, no CDN in front")
        .exportName("ClueLuxUatApiUrl")
        .build());

    new CfnOutput(this, "DailyWordEndpoint", CfnOutputProps.builder()
        .value(api.getUrl() + "daily-word")
        .description("UAT daily-word endpoint")
        .exportName("ClueLuxUatDailyWordEndpoint")
        .build());

    new CfnOutput(this, "TableName", CfnOutputProps.builder()
        .value(wordsTable.getTableName())
        .description("UAT DynamoDB table name")
        .exportName("ClueLuxUatWordsTableName")
        .build());
  }
}
