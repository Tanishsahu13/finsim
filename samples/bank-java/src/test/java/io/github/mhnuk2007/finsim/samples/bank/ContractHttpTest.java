package io.github.mhnuk2007.finsim.samples.bank;

import static org.assertj.core.api.Assertions.assertThat;

import com.jayway.jsonpath.JsonPath;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpRequest.BodyPublishers;
import java.net.http.HttpResponse;
import java.net.http.HttpResponse.BodyHandlers;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * Talks to the running bank over real HTTP, the way FinSim will. These checks are the seed of
 * {@code finsim verify}.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class ContractHttpTest {

  @Value("${local.server.port}")
  int port;

  private final HttpClient http = HttpClient.newHttpClient();

  private HttpResponse<String> get(String path) throws Exception {
    HttpRequest request =
        HttpRequest.newBuilder(URI.create("http://localhost:" + port + path)).GET().build();
    return http.send(request, BodyHandlers.ofString());
  }

  private HttpResponse<String> post(String key, String json) throws Exception {
    HttpRequest.Builder builder =
        HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/finsim/v1/payments"))
            .header("Content-Type", "application/json")
            .POST(BodyPublishers.ofString(json));
    if (key != null) {
      builder.header("Idempotency-Key", key);
    }
    return http.send(builder.build(), BodyHandlers.ofString());
  }

  private static String leg(String tx, String direction, String account, String amount) {
    return """
        {"transactionId":"%s","direction":"%s","accountReference":"%s",\
        "money":{"amount":"%s","currency":"USD"},"reference":"TEST","provenance":"SYNTHETIC"}"""
        .formatted(tx, direction, account, amount);
  }

  private static String unique(String prefix) {
    return prefix + "-" + UUID.randomUUID();
  }

  @Test
  void healthReportsUpAndTheContractVersion() throws Exception {
    HttpResponse<String> response = get("/finsim/v1/institution/health");

    assertThat(response.statusCode()).isEqualTo(200);
    assertThat((String) JsonPath.read(response.body(), "$.status")).isEqualTo("UP");
    assertThat((String) JsonPath.read(response.body(), "$.contractVersions[0]"))
        .isEqualTo("finsim/v1");
  }

  @Test
  void capabilitiesDeclareDebitCreditAndStatusQuery() throws Exception {
    HttpResponse<String> response = get("/finsim/v1/institution/capabilities");

    assertThat(response.statusCode()).isEqualTo(200);
    assertThat(response.body()).contains("ACCOUNT_DEBIT", "ACCOUNT_CREDIT", "STATUS_QUERY");
  }

  @Test
  void aNewLegIsCreatedAndAReplayReturnsTheOriginal() throws Exception {
    String key = unique("key");
    String body = leg(unique("tx"), "DEBIT", "ACC-1", "1.00");

    HttpResponse<String> first = post(key, body);
    HttpResponse<String> replay = post(key, body);

    assertThat(first.statusCode()).isEqualTo(201);
    assertThat((String) JsonPath.read(first.body(), "$.status")).isEqualTo("COMPLETED");
    assertThat(replay.statusCode()).isEqualTo(200);
    assertThat(replay.body()).isEqualTo(first.body());
  }

  @Test
  void sameKeyWithADifferentBodyIsAConflict() throws Exception {
    String key = unique("key");
    String tx = unique("tx");
    post(key, leg(tx, "DEBIT", "ACC-1", "1.00"));

    HttpResponse<String> response = post(key, leg(tx, "DEBIT", "ACC-1", "2.00"));

    assertThat(response.statusCode()).isEqualTo(409);
    assertThat((String) JsonPath.read(response.body(), "$.code")).isEqualTo("IDEMPOTENCY_CONFLICT");
  }

  @Test
  void aMissingIdempotencyKeyIsABadRequest() throws Exception {
    HttpResponse<String> response = post(null, leg(unique("tx"), "DEBIT", "ACC-1", "1.00"));

    assertThat(response.statusCode()).isEqualTo(400);
  }

  @Test
  void malformedJsonGetsTheContractErrorShape() throws Exception {
    HttpResponse<String> response = post(unique("key"), "{ not json");

    assertThat(response.statusCode()).isEqualTo(400);
    assertThat((String) JsonPath.read(response.body(), "$.code")).isEqualTo("INVALID_REQUEST");
  }

  @Test
  void aBusinessRejectionIs422WithAReasonCodeAndIsQueryableAsRejected() throws Exception {
    String tx = unique("tx");

    HttpResponse<String> response = post(unique("key"), leg(tx, "DEBIT", "ACC-1", "999999.00"));
    HttpResponse<String> status = get("/finsim/v1/payments/" + tx + "?leg=DEBIT");

    assertThat(response.statusCode()).isEqualTo(422);
    assertThat((String) JsonPath.read(response.body(), "$.code")).isEqualTo("AM04");
    assertThat(status.statusCode()).isEqualTo(200);
    assertThat((String) JsonPath.read(status.body(), "$.status")).isEqualTo("REJECTED");
    assertThat((String) JsonPath.read(status.body(), "$.reasonCode")).isEqualTo("AM04");
  }

  @Test
  void aLegCanBeQueriedAfterItIsAppliedAndIsNotFoundIfItNeverArrived() throws Exception {
    String tx = unique("tx");
    post(unique("key"), leg(tx, "CREDIT", "ACC-2", "1.00"));

    HttpResponse<String> found = get("/finsim/v1/payments/" + tx + "?leg=CREDIT");
    HttpResponse<String> missing =
        get("/finsim/v1/payments/" + unique("never-sent") + "?leg=CREDIT");

    assertThat(found.statusCode()).isEqualTo(200);
    assertThat((String) JsonPath.read(found.body(), "$.status")).isEqualTo("COMPLETED");
    assertThat(missing.statusCode()).isEqualTo(404);
  }
}
