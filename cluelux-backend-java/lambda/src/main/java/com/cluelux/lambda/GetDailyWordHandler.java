package com.cluelux.lambda;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyRequestEvent;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyResponseEvent;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.ObjectMapper;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;
import software.amazon.awssdk.services.dynamodb.model.GetItemRequest;
import software.amazon.awssdk.services.dynamodb.model.GetItemResponse;

import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Serves today's word, hints and fact to the frontend.
 * Java port of lambda/getDailyWord.ts — same table, same date logic
 * (ET calendar day), same response shape, same fallback word.
 */
public class GetDailyWordHandler
    implements RequestHandler<APIGatewayProxyRequestEvent, APIGatewayProxyResponseEvent> {

  private static final ZoneId ET_ZONE = ZoneId.of("America/New_York");
  private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd");
  private static final String TABLE_NAME = System.getenv().getOrDefault("TABLE_NAME", "ClueLuxWords");

  // Lets a tester preview a future day's word before it's actually "today" —
  // e.g. ?previewDate=2026-11-01&previewKey=... — without any second
  // environment. Only works with the exact right key; absent or wrong key,
  // this is a no-op and the request behaves exactly like a normal one.
  // Always call this via the direct API Gateway URL, never the CloudFront
  // one real players use — CloudFront's cache key ignores query strings
  // (see the CachePolicy in the CDK stack), so a preview response hitting
  // that endpoint could get cached and served to real visitors.
  private static final String PREVIEW_KEY = System.getenv("PREVIEW_KEY");

  private final DynamoDbClient dynamoDb = DynamoDbClient.create();
  private final ObjectMapper mapper = new ObjectMapper();

  @Override
  public APIGatewayProxyResponseEvent handleRequest(APIGatewayProxyRequestEvent event, Context context) {
    context.getLogger().log("Event: " + event);

    try {
      boolean isPreview = isValidPreviewRequest(event);
      String today = isPreview
          ? event.getQueryStringParameters().get("previewDate")
          : ZonedDateTime.now(ET_ZONE).format(DATE_FORMAT);
      context.getLogger().log((isPreview ? "PREVIEW " : "") + "Fetching word for date (ET): " + today);

      GetItemResponse response = dynamoDb.getItem(GetItemRequest.builder()
          .tableName(TABLE_NAME)
          .key(Map.of("date", AttributeValue.builder().s(today).build()))
          .build());

      DailyWordResponse body;
      if (!response.hasItem()) {
        context.getLogger().log("No word found for today, using fallback");
        body = fallback(today);
      } else {
        body = fromItem(response.item());
        context.getLogger().log("Returning word: " + body.word);
      }

      // A preview response must never be cacheable — if it ever reached
      // CloudFront (it shouldn't; call the direct API Gateway URL for
      // previews) a cached preview would get served to real players next.
      if (isPreview) {
        return ok(body, "no-store");
      }

      long cacheSeconds = secondsUntilMidnightET();
      context.getLogger().log("Cache TTL: " + cacheSeconds + " seconds until midnight ET");
      return ok(body, "public, max-age=" + cacheSeconds + ", s-maxage=" + cacheSeconds);
    } catch (Exception e) {
      context.getLogger().log("Error: " + e);
      return error(e);
    }
  }

  private boolean isValidPreviewRequest(APIGatewayProxyRequestEvent event) {
    Map<String, String> params = event.getQueryStringParameters();
    if (params == null || PREVIEW_KEY == null || PREVIEW_KEY.isEmpty()) {
      return false;
    }
    String requestedKey = params.get("previewKey");
    String requestedDate = params.get("previewDate");
    return PREVIEW_KEY.equals(requestedKey) && requestedDate != null && !requestedDate.isBlank();
  }

  private DailyWordResponse fallback(String today) {
    DailyWordResponse body = new DailyWordResponse();
    body.date = today;
    body.word = "LIGHT";
    body.hints = List.of(
        "Category: Illumination",
        "You turn this on in a dark room",
        "Opposite of darkness");
    body.fact = "The speed of light is 299,792,458 meters per second!";
    return body;
  }

  private DailyWordResponse fromItem(Map<String, AttributeValue> item) {
    DailyWordResponse body = new DailyWordResponse();
    body.date = item.containsKey("date") ? item.get("date").s() : null;
    body.word = item.containsKey("word") ? item.get("word").s() : null;

    List<String> hints = new ArrayList<>();
    if (item.containsKey("hints") && item.get("hints").hasL()) {
      for (AttributeValue hint : item.get("hints").l()) {
        hints.add(hint.s());
      }
    }
    body.hints = hints;
    body.fact = item.containsKey("fact") ? item.get("fact").s() : null;
    return body;
  }

  private long secondsUntilMidnightET() {
    ZonedDateTime now = ZonedDateTime.now(ET_ZONE);
    ZonedDateTime midnight = now.toLocalDate().plusDays(1).atStartOfDay(ET_ZONE);
    long seconds = java.time.Duration.between(now, midnight).getSeconds();
    return Math.max(seconds, 60);
  }

  private APIGatewayProxyResponseEvent ok(DailyWordResponse body, String cacheControl) {
    Map<String, String> headers = new LinkedHashMap<>();
    headers.put("Content-Type", "application/json");
    headers.put("Access-Control-Allow-Origin", "*");
    headers.put("Access-Control-Allow-Headers", "Content-Type");
    headers.put("Access-Control-Allow-Methods", "GET,OPTIONS");
    headers.put("Cache-Control", cacheControl);

    return new APIGatewayProxyResponseEvent()
        .withStatusCode(200)
        .withHeaders(headers)
        .withBody(toJson(body));
  }

  private APIGatewayProxyResponseEvent error(Exception e) {
    Map<String, String> headers = new LinkedHashMap<>();
    headers.put("Content-Type", "application/json");
    headers.put("Access-Control-Allow-Origin", "*");

    Map<String, String> payload = new LinkedHashMap<>();
    payload.put("message", "Internal server error");
    payload.put("error", e.getMessage() != null ? e.getMessage() : "Unknown error");

    return new APIGatewayProxyResponseEvent()
        .withStatusCode(500)
        .withHeaders(headers)
        .withBody(toJson(payload));
  }

  private String toJson(Object value) {
    try {
      return mapper.writeValueAsString(value);
    } catch (Exception e) {
      throw new RuntimeException("Failed to serialize response", e);
    }
  }

  @JsonInclude(JsonInclude.Include.NON_NULL)
  static class DailyWordResponse {
    public String date;
    public String word;
    public List<String> hints;
    public String fact;
  }
}
