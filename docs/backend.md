# Backend

**The only deployable backend is `cluelux-backend-java/`** (Java 21, Maven, AWS CDK). `cluelux-backend/` used to contain a parallel TypeScript CDK stack and Lambda handler — both were removed (see History below); that folder is now purely **content tooling** (word/hint generation scripts, data files) with no deploy capability at all.

## Two stacks, fully isolated

Both are defined in `cluelux-backend-java/infra/src/main/java/com/cluelux/infra/`, deployed from the same CDK app (`ClueLuxBackendApp.java`), but share **zero** resources:

| | Prod — `ClueLuxBackendStack` | UAT — `ClueLuxBackendStackUAT` |
|---|---|---|
| Stack class | `ClueLuxBackendStack.java` | `ClueLuxUatStack.java` (separate class, not parameterized — see rationale in its header comment) |
| DynamoDB table | `ClueLuxWords` (65+ items, real content) | `ClueLuxWords-uat` (empty by default, disposable) |
| Lambda | `GetDailyWordFunction` (auto-named) | `cluelux-get-daily-word-uat` (explicit name) |
| API Gateway | `ClueLuxApi` → `https://qp5p347l9l.execute-api.us-east-1.amazonaws.com/prod/` | `ClueLuxApiUAT` → `https://2ju9d2h9pj.execute-api.us-east-1.amazonaws.com/prod/` |
| CloudFront in front | Yes — `ClueLuxDistribution`, `https://d2g37ugrz9k0kj.cloudfront.net` | **No**, by design — UAT is for active testing, where seeing a fresh write immediately matters more than edge caching |
| Table `removalPolicy` | `RETAIN` | `DESTROY` (disposable test data) |

Both run the identical Lambda code (`lambda/src/main/java/com/cluelux/lambda/GetDailyWordHandler.java`) — same jar, same handler class, same logic. UAT exists to test that code against its own isolated table before it ever touches the prod table.

## Monitoring — prod only

Dashboard: **ClueLux-Prod** in CloudWatch (console → CloudWatch → Dashboards) — API request count/4xx/5xx, API latency, Lambda invocations/errors/throttles/duration, DynamoDB consumed read capacity.

5 alarms, all notifying one SNS topic (`cluelux-prod-alarms`) with an email subscription to `yashwanth753@gmail.com` — **the subscription has to be confirmed via the link AWS emails on first deploy, or it's silently inert**:

| Alarm | Fires when |
|---|---|
| `ClueLux-LambdaErrors` | ≥1 Lambda error in 5 min |
| `ClueLux-LambdaThrottles` | ≥1 Lambda throttle in 5 min |
| `ClueLux-LambdaDurationHigh` | p99 duration >15s (timeout is 20s) over 2 consecutive 5-min periods |
| `ClueLux-Api5xxErrors` | ≥1 API Gateway 5xx in 5 min |
| `ClueLux-DynamoDbThrottles` | ≥1 DynamoDB throttled request in 5 min |

Thresholds are deliberately 1, not some higher burst count — this is low-traffic, so a single error is worth knowing about immediately, not just a spike of them. Deliberately prod-only: paging for UAT test traffic isn't useful, so `ClueLuxUatStack.java` has none of this.

## Lambda — `GetDailyWordHandler.java`

Java 21, `RequestHandler<APIGatewayProxyRequestEvent, APIGatewayProxyResponseEvent>`, packaged as a shaded (fat) jar via `lambda/pom.xml`'s `maven-shade-plugin`.

1. Computes "today" in **America/New_York**, `yyyy-MM-dd`.
2. `GetItem` against its table (`TABLE_NAME` env var) with key `{date: today}`.
3. No item found → hardcoded fallback (`LIGHT`, 3 canned hints, a fact), still HTTP 200 — never 404s on a missing day.
4. Item found → maps `date`/`word`/`hints`/`fact` straight into the response; `fact` is omitted (not null) from the JSON if absent, matching the original TS behavior exactly.
5. Every response carries `Cache-Control: public, max-age=<N>, s-maxage=<N>` where `N` = seconds until midnight ET (min 60) — this header, not CloudFront's own TTL config, is what drives the daily rollover.
6. Thrown exception → HTTP 500, `{message, error}`.

Memory/timeout: **512MB / 20s** (vs. the old Node Lambda's 256MB/10s) — deliberately raised for JVM cold-start headroom. In practice this barely matters: the prod endpoint sits behind a 12-hour CloudFront cache, so cold starts are rare.

### Preview-date testing (no second environment needed, for quick ad-hoc checks)

Separate from the UAT stack above — a lighter-weight mechanism for previewing a specific date's content through the **same** Lambda (prod or UAT) without writing real data for "today."

Optional query params: `?previewDate=YYYY-MM-DD&previewKey=<secret>`. Right key → looks up that date instead of real today. Wrong/missing key → completely normal behavior, zero effect. A valid preview response is marked `Cache-Control: no-store` so it can never get cached and served to a real visitor.

**Always call the direct API Gateway URL for this, never the CloudFront one** — CloudFront's cache policy ignores query strings (see the `CachePolicy` in `ClueLuxBackendStack.java`), so a preview response hitting that endpoint could get cached and served to everyone.

The key value itself is intentionally not written in any doc or committed file — it was shared directly between the user and Claude in chat, and only exists as the `PREVIEW_KEY` Lambda environment variable, set from the shell's `PREVIEW_KEY` env var **at deploy time**. A deploy run without that var exported silently resets it to empty, disabling preview mode until it's exported again on the next deploy.

## CDK app — `infra/src/main/java/com/cluelux/infra/ClueLuxBackendApp.java`

Instantiates both stacks in one `main()`, same `CDK_DEFAULT_ACCOUNT`/`CDK_DEFAULT_REGION` env resolution as the old TS app. `cdk.json`'s `app` command (`mvn -q -pl lambda package -DskipTests && mvn -q -pl infra compile exec:java`) rebuilds the jar fresh **every** `cdk synth`/`diff`/`deploy` call — see "Build-once, promote" below for why that's worth changing before this project has a real release process.

## Build-once, promote the same artifact (recommended, not yet implemented)

Right now, deploying to UAT and then to prod runs `mvn package` twice — once per `cdk deploy` invocation. If the source changes between the two commands (even a stray edit), UAT and prod can end up running code that was never actually tested together. The standard fix:

1. Build the jar once: `mvn -pl lambda package -DskipTests`.
2. Deploy UAT from that jar, verify it.
3. Deploy prod from the **same, unmodified** jar — don't rebuild.
4. Tag the commit once prod deploy succeeds, e.g. `git tag -a v2026.11.01 -m "Nov 2026 launch, Java backend"`, so there's always a clear answer to "what commit is actually running in prod right now."

This isn't automated yet (`cdk.json` still rebuilds every time) — worth doing before the project does frequent prod deploys, not urgent for a once-a-day word game.

## Deploy commands

```bash
cd cluelux-backend-java
export CDK_DEFAULT_ACCOUNT=211125414476
export CDK_DEFAULT_REGION=us-east-1
export PREVIEW_KEY=<the key, not committed anywhere>

npx --prefix ../cluelux-backend cdk diff ClueLuxBackendStackUAT      # review first
npx --prefix ../cluelux-backend cdk deploy ClueLuxBackendStackUAT    # deploy UAT

npx --prefix ../cluelux-backend cdk diff ClueLuxBackendStack         # review prod separately
npx --prefix ../cluelux-backend cdk deploy ClueLuxBackendStack       # deploy prod
```

(The CDK CLI itself still lives in `cluelux-backend/node_modules` via its `package.json` — only the stack definitions moved to Java, not the CLI tooling.) Always deploy stacks **individually** (by name), not `--all`, so a UAT change can never accidentally bundle a prod change into the same approval.

## Loading word data

Neither stack's deploy process seeds data automatically. Load words via the AWS CLI directly, e.g.:
```bash
aws dynamodb put-item --table-name ClueLuxWords --item file://item.json       # prod
aws dynamodb put-item --table-name ClueLuxWords-uat --item file://item.json   # UAT
```
See `content-and-hints.md` for the data shape and the hint-writing/leak-checking process that should happen before any word reaches either table.

## History — the removed TypeScript backend

`cluelux-backend/cdk/` (CDK stack + app entry) and `cluelux-backend/lambda/getDailyWord.ts` were **deleted** (recoverable via git history — `git log -- cluelux-backend/cdk`). They were the original implementation, fully superseded by the Java port above, and were removed specifically because leaving them in place was an active hazard: running `cd cluelux-backend && npm run deploy` would have silently reverted the live `ClueLuxBackendStack`'s Lambda from Java back to Node, since both CDK apps targeted the identical stack name and logical IDs. `cluelux-backend/package.json`'s CDK-related scripts and devDependencies (`aws-cdk-lib`, `typescript`, `esbuild`, `ts-node`, etc.) were removed at the same time; only the content-generation scripts and their runtime deps (`@anthropic-ai/sdk`, the DynamoDB SDK clients) remain.
