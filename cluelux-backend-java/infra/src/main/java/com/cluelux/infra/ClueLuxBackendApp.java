package com.cluelux.infra;

import software.amazon.awscdk.App;
import software.amazon.awscdk.Environment;
import software.amazon.awscdk.StackProps;

/** Java port of cdk/bin/app.ts — same stack name, same account/region source. */
public final class ClueLuxBackendApp {
  private ClueLuxBackendApp() {}

  public static void main(final String[] args) {
    App app = new App();

    String account = System.getenv("CDK_DEFAULT_ACCOUNT");
    String region = System.getenv().getOrDefault("CDK_DEFAULT_REGION", "us-east-1");

    Environment.Builder envBuilder = Environment.builder().region(region);
    if (account != null) {
      envBuilder = envBuilder.account(account);
    }

    new ClueLuxBackendStack(app, "ClueLuxBackendStack", StackProps.builder()
        .env(envBuilder.build())
        .description("Backend infrastructure for ClueLux game")
        .build());

    new ClueLuxUatStack(app, "ClueLuxBackendStackUAT", StackProps.builder()
        .env(envBuilder.build())
        .description("Isolated UAT environment for ClueLux - separate table, Lambda, API from prod")
        .build());

    app.synth();
  }
}
