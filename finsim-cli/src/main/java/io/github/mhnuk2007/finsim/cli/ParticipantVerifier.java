package io.github.mhnuk2007.finsim.cli;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** HTTP-only verification of a participant's public finsim/v1 contract. */
final class ParticipantVerifier {
  record Phase(String name, String status, List<String> details) {}

  record Result(String target, boolean passed, long durationMs, List<Phase> phases) {}

  private record Reply(int status, Map<String, Object> body, String raw) {}

  private final String base;
  private final Duration timeout;
  private final boolean verbose;
  private final HttpClient client;
  private final String runId = UUID.randomUUID().toString();
  private final List<Phase> phases = new ArrayList<>();
  private List<String> details;
  private String lastEndpoint;
  private String debitId;
  private String creditId;
  private String rejectedId;
  private Map<String, Object> debitResult;

  ParticipantVerifier(String base, int timeoutMs, boolean verbose) {
    this.base = base.replaceAll("/+$", "");
    this.timeout = Duration.ofMillis(timeoutMs);
    this.verbose = verbose;
    this.client = HttpClient.newBuilder().connectTimeout(timeout).build();
  }

  static boolean validUrl(String value) {
    try {
      URI uri = URI.create(value);
      return ("http".equalsIgnoreCase(uri.getScheme()) || "https".equalsIgnoreCase(uri.getScheme()))
          && uri.getHost() != null
          && uri.getUserInfo() == null
          && uri.getQuery() == null
          && uri.getFragment() == null
          && (uri.getPath() == null || uri.getPath().isEmpty() || uri.getPath().equals("/"));
    } catch (IllegalArgumentException e) {
      return false;
    }
  }

  Result run() {
    long start = System.nanoTime();
    phase("Phase 1: Health check", this::health);
    phase("Phase 2: Capabilities", this::capabilities);
    phase("Phase 3: Payment leg execution", this::execution);
    phase("Phase 4: Idempotency replay", this::replay);
    phase("Phase 5: Idempotency conflict", this::conflict);
    phase("Phase 6: Business rejections", this::rejections);
    phase("Phase 7: Leg status query", this::status);
    return new Result(
        base,
        phases.stream().allMatch(p -> p.status().equals("PASS")),
        Duration.ofNanos(System.nanoTime() - start).toMillis(),
        List.copyOf(phases));
  }

  private void phase(String name, Runnable check) {
    details = new ArrayList<>();
    check.run();
    phases.add(
        new Phase(
            name,
            details.stream().anyMatch(s -> s.startsWith("FAIL"))
                ? "FAIL"
                : details.stream().anyMatch(s -> s.startsWith("SKIP")) ? "SKIP" : "PASS",
            List.copyOf(details)));
  }

  private void health() {
    Reply r = get("/finsim/v1/institution/health");
    check(r, 200, "health", "Check the participant health endpoint.");
    field(r, "status", "UP");
    contains(r, "contractVersions", "finsim/v1");
  }

  private void capabilities() {
    Reply r = get("/finsim/v1/institution/capabilities");
    check(r, 200, "capabilities", "Declare supported operations and currencies.");
    if (r != null && !(r.body().get("institutionType") instanceof String))
      fail(lastEndpoint + ": capabilities.institutionType must be a string");
    for (String capability : List.of("ACCOUNT_DEBIT", "ACCOUNT_CREDIT", "STATUS_QUERY"))
      contains(r, "capabilities", capability);
    contains(r, "currencies", "USD");
  }

  private void execution() {
    debitId = id("debit");
    creditId = id("credit");
    Reply debit = post(payment(debitId, "DEBIT", "ACC-1", "1.00", "USD"), key("debit"));
    check(debit, 201, "DEBIT", "Accept synthetic debit on ACC-1.");
    leg(debit, debitId, "DEBIT", "COMPLETED");
    if (debit != null && debit.status() == 201 && "COMPLETED".equals(debit.body().get("status")))
      debitResult = debit.body();
    Reply credit = post(payment(creditId, "CREDIT", "ACC-2", "1.00", "USD"), key("credit"));
    check(credit, 201, "CREDIT", "Accept synthetic credit on ACC-2.");
    leg(credit, creditId, "CREDIT", "COMPLETED");
  }

  private void replay() {
    if (debitResult == null) {
      skip("Original debit was not completed; replay cannot be compared.");
      return;
    }
    Reply r = post(payment(debitId, "DEBIT", "ACC-1", "1.00", "USD"), key("debit"));
    check(r, 200, "replay", "Return the original result for the same key and body.");
    if (r != null && !debitResult.equals(r.body()))
      fail(
          lastEndpoint
              + ": replay response differs from the original result; expected "
              + debitResult
              + ", actual "
              + r.body());
  }

  private void conflict() {
    if (debitResult == null) {
      skip("Original debit was not completed; key conflict has no valid prerequisite.");
      return;
    }
    Reply r = post(payment(debitId, "DEBIT", "ACC-1", "2.00", "USD"), key("debit"));
    check(r, 409, "conflict", "Reject a changed body with an existing key.");
    field(r, "code", "IDEMPOTENCY_CONFLICT");
    errorShape(r);
  }

  private void rejections() {
    rejection("unknown", "CREDIT", "ACC-UNKNOWN", "1.00", "USD", "AC01");
    rejection("closed", "CREDIT", "ACC-CLOSED", "1.00", "USD", "AC04");
    rejectedId = id("insufficient");
    reject(rejectedId, "DEBIT", "ACC-1", "999999999.00", "USD", "AM04");
    rejection("zero", "CREDIT", "ACC-2", "0.00", "USD", "AM02");
    rejection("currency", "CREDIT", "ACC-2", "1.00", "XXX", "AM03");
  }

  private void rejection(
      String suffix,
      String direction,
      String account,
      String amount,
      String currency,
      String code) {
    reject(id(suffix), direction, account, amount, currency, code);
  }

  private void reject(
      String id, String direction, String account, String amount, String currency, String code) {
    Reply r = post(payment(id, direction, account, amount, currency), key(id));
    check(r, 422, code, "Return business rejection " + code + " without changing balances.");
    field(r, "code", code);
    errorShape(r);
  }

  private void status() {
    if (debitResult != null) {
      Reply r = get("/finsim/v1/payments/" + debitId + "?leg=DEBIT");
      check(r, 200, "completed leg", "Return the completed debit.");
      leg(r, debitId, "DEBIT", "COMPLETED");
      Reply other = get("/finsim/v1/payments/" + debitId + "?leg=CREDIT");
      check(other, 404, "opposite leg", "A never received direction must be absent.");
      field(other, "code", "LEG_NOT_FOUND");
      errorShape(other);
    } else skip("Completed debit was unavailable; completed-leg query skipped.");
    Reply missing = get("/finsim/v1/payments/" + id("missing") + "?leg=CREDIT");
    check(missing, 404, "missing leg", "Return LEG_NOT_FOUND for unknown transactions.");
    field(missing, "code", "LEG_NOT_FOUND");
    errorShape(missing);
    Reply rejected = get("/finsim/v1/payments/" + rejectedId + "?leg=DEBIT");
    check(rejected, 200, "rejected leg", "Remember rejected business legs.");
    leg(rejected, rejectedId, "DEBIT", "REJECTED");
    field(rejected, "reasonCode", "AM04");
  }

  private Reply get(String path) {
    return send("GET", path, null, null);
  }

  private Reply post(String body, String key) {
    return send("POST", "/finsim/v1/payments", body, key);
  }

  private Reply send(String method, String path, String body, String key) {
    String endpoint = base + path;
    lastEndpoint = endpoint;
    try {
      HttpRequest.Builder builder =
          HttpRequest.newBuilder(URI.create(endpoint))
              .timeout(timeout)
              .header("Accept", "application/json");
      if (body == null) builder.GET();
      else
        builder
            .header("Content-Type", "application/json")
            .header("Idempotency-Key", key)
            .POST(HttpRequest.BodyPublishers.ofString(body));
      HttpResponse<String> response =
          client.send(builder.build(), HttpResponse.BodyHandlers.ofString());
      if (verbose)
        details.add(
            method
                + " "
                + endpoint
                + " request="
                + (body == null ? "{}" : body)
                + " response="
                + response.statusCode()
                + " "
                + sanitize(response.body()));
      Object parsed = ContractJson.parse(response.body());
      if (!(parsed instanceof Map<?, ?> parsedMap)
          || parsedMap.keySet().stream().anyMatch(k -> !(k instanceof String)))
        throw new IllegalArgumentException("Expected JSON object");
      Map<String, Object> object = new LinkedHashMap<>();
      parsedMap.forEach((k, v) -> object.put((String) k, v));
      return new Reply(response.statusCode(), object, response.body());
    } catch (IOException | IllegalArgumentException e) {
      fail(
          method
              + " "
              + endpoint
              + ": "
              + e.getMessage()
              + "; expected valid JSON response. Check endpoint and response shape.");
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      fail(method + " " + endpoint + ": interrupted while waiting for response.");
    }
    return null;
  }

  private void check(Reply r, int expected, String name, String hint) {
    if (r != null && r.status() != expected)
      fail(
          name
              + " at "
              + lastEndpoint
              + ": expected HTTP "
              + expected
              + ", actual HTTP "
              + r.status()
              + ", body "
              + sanitize(r.raw())
              + ". "
              + hint);
  }

  private void field(Reply r, String field, String expected) {
    if (r != null && !expected.equals(r.body().get(field)))
      fail(
          lastEndpoint
              + ": expected "
              + field
              + "="
              + expected
              + ", actual="
              + r.body().get(field)
              + ". Check response contract fields.");
  }

  private void contains(Reply r, String field, String expected) {
    if (r != null
        && (!(r.body().get(field) instanceof List<?> values) || !values.contains(expected)))
      fail(
          lastEndpoint
              + ": expected "
              + field
              + " to contain "
              + expected
              + ", actual="
              + r.body().get(field)
              + ". Update participant declaration.");
  }

  private void leg(Reply r, String id, String direction, String status) {
    field(r, "transactionId", id);
    field(r, "direction", direction);
    field(r, "status", status);
    field(r, "provenance", "SYNTHETIC");
    if (r != null
        && status.equals("COMPLETED")
        && (!(r.body().get("participantReference") instanceof String ref) || ref.isBlank()))
      fail(lastEndpoint + ": participantReference must be populated for completed legs.");
  }

  private void errorShape(Reply r) {
    if (r != null && (!(r.body().get("message") instanceof String message) || message.isBlank()))
      fail(lastEndpoint + ": error.message must be a nonempty string.");
  }

  private void fail(String text) {
    details.add("FAIL: " + text);
  }

  private void skip(String text) {
    details.add("SKIP: " + text);
  }

  private static String sanitize(String body) {
    return body.replaceAll(
        "(?i)(\"(?:password|token|secret|authorization|apiKey)\"\\s*:\\s*)\"(?:\\\\.|[^\"\\\\])*\"",
        "$1\"[REDACTED]\"");
  }

  private String id(String suffix) {
    return "verify-" + runId + "-" + suffix;
  }

  private String key(String suffix) {
    return "verify-key-" + runId + "-" + suffix;
  }

  private String payment(
      String id, String direction, String account, String amount, String currency) {
    return ContractJson.write(
        Map.of(
            "transactionId",
            id,
            "direction",
            direction,
            "accountReference",
            account,
            "money",
            Map.of("amount", amount, "currency", currency),
            "reference",
            "CONFORMANCE",
            "provenance",
            "SYNTHETIC"));
  }

  static String json(Result result) {
    List<Object> phases = new ArrayList<>();
    for (Phase p : result.phases())
      phases.add(Map.of("name", p.name(), "status", p.status(), "details", p.details()));
    Map<String, Object> body = new LinkedHashMap<>();
    body.put("target", result.target());
    body.put("passed", result.passed());
    body.put("durationMs", result.durationMs());
    body.put("warning", "Successful checks can change synthetic account balances.");
    body.put("phases", phases);
    return ContractJson.write(body);
  }
}
