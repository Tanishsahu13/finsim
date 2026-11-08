package io.github.mhnuk2007.finsim;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.jayway.jsonpath.JsonPath;
import io.github.mhnuk2007.finsim.transfer.internal.ParticipantClient;
import io.github.mhnuk2007.finsim.transfer.internal.ParticipantClient.PaymentCallOutcome;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpRequest.BodyPublishers;
import java.net.http.HttpResponse;
import java.net.http.HttpResponse.BodyHandlers;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(TestcontainersConfiguration.class)
class TransferWorkflowTests {

  @Value("${local.server.port}")
  int port;

  @MockitoBean ParticipantClient participantClient;

  private final HttpClient http = HttpClient.newHttpClient();

  private HttpResponse<String> postTransfer(String networkId, String key, String json)
      throws Exception {
    HttpRequest.Builder builder =
        HttpRequest.newBuilder(
                URI.create(
                    "http://localhost:" + port + "/api/v1/networks/" + networkId + "/transfers"))
            .header("Content-Type", "application/json")
            .POST(BodyPublishers.ofString(json));

    if (key != null) {
      builder.header("Idempotency-Key", key);
    }
    return http.send(builder.build(), BodyHandlers.ofString());
  }

  private HttpResponse<String> getTransfer(String transactionId) throws Exception {
    return http.send(
        HttpRequest.newBuilder(
                URI.create("http://localhost:" + port + "/api/v1/transfers/" + transactionId))
            .GET()
            .build(),
        BodyHandlers.ofString());
  }

  private static String randomKey() {
    return "key-" + UUID.randomUUID().toString();
  }

  private static String transferJson(
      String fromInst, String fromAcc, String toInst, String toAcc, String amount, String ref) {
    return """
        {
          "from": { "institutionId": "%s", "accountReference": "%s" },
          "to": { "institutionId": "%s", "accountReference": "%s" },
          "money": { "amount": "%s", "currency": "USD" },
          "reference": "%s"
        }
        """
        .formatted(fromInst, fromAcc, toInst, toAcc, amount, ref);
  }

  @Test
  @DisplayName("Missing Idempotency-Key header returns HTTP 400 with MISSING_IDEMPOTENCY_KEY")
  void missingIdempotencyKeyReturns400() throws Exception {
    String payload = transferJson("bank-a", "ACC-1", "bank-b", "ACC-2", "100.00", "DEMO-001");
    HttpResponse<String> res = postTransfer("sandbox", null, payload);

    assertThat(res.statusCode()).isEqualTo(400);
    assertThat(JsonPath.<String>read(res.body(), "$.code")).isEqualTo("MISSING_IDEMPOTENCY_KEY");
  }

  @Test
  @DisplayName("Happy path: orchestrates DEBIT then CREDIT and completes transfer (201 Created)")
  void happyPathTransferCompletes() throws Exception {
    String key = randomKey();
    String payload = transferJson("bank-a", "ACC-1", "bank-b", "ACC-2", "100.00", "REF-HAPPY");

    when(participantClient.applyLeg(eq("http://localhost:9001"), any(), any()))
        .thenReturn(PaymentCallOutcome.completed("part-ref-debit-1"));
    when(participantClient.applyLeg(eq("http://localhost:9002"), any(), any()))
        .thenReturn(PaymentCallOutcome.completed("part-ref-credit-1"));

    HttpResponse<String> res = postTransfer("sandbox", key, payload);

    assertThat(res.statusCode()).isEqualTo(201);
    String txId = JsonPath.read(res.body(), "$.transactionId");
    assertThat(txId).startsWith("tx-");
    assertThat(JsonPath.<String>read(res.body(), "$.state")).isEqualTo("COMPLETED");
    assertThat(JsonPath.<String>read(res.body(), "$.currentStage")).isEqualTo("credit_destination");
    assertThat(JsonPath.<String>read(res.body(), "$.from.institutionId")).isEqualTo("bank-a");
    assertThat(JsonPath.<String>read(res.body(), "$.to.institutionId")).isEqualTo("bank-b");
    assertThat(JsonPath.<String>read(res.body(), "$.money.amount")).isEqualTo("100.00");

    // Verify derived idempotency keys were sent to participants
    verify(participantClient)
        .applyLeg(
            eq("http://localhost:9001"),
            eq(txId + "-debit"),
            argThat(req -> "DEBIT".equals(req.direction())));
    verify(participantClient)
        .applyLeg(
            eq("http://localhost:9002"),
            eq(txId + "-credit"),
            argThat(req -> "CREDIT".equals(req.direction())));

    // Inspect via GET /api/v1/transfers/{transactionId}
    HttpResponse<String> getRes = getTransfer(txId);
    assertThat(getRes.statusCode()).isEqualTo(200);
    assertThat(JsonPath.<String>read(getRes.body(), "$.state")).isEqualTo("COMPLETED");
  }

  @Test
  @DisplayName(
      "Idempotent replay: identical request with same key returns HTTP 200 with original transfer")
  void idempotentReplayReturns200() throws Exception {
    String key = randomKey();
    String payload = transferJson("bank-a", "ACC-1", "bank-b", "ACC-2", "50.00", "REF-REPLAY");

    when(participantClient.applyLeg(any(), any(), any()))
        .thenReturn(PaymentCallOutcome.completed("ack"));

    // First call: 201 Created
    HttpResponse<String> res1 = postTransfer("sandbox", key, payload);
    assertThat(res1.statusCode()).isEqualTo(201);
    String txId1 = JsonPath.read(res1.body(), "$.transactionId");

    // Second call with same key and body: 200 OK
    HttpResponse<String> res2 = postTransfer("sandbox", key, payload);
    assertThat(res2.statusCode()).isEqualTo(200);
    String txId2 = JsonPath.read(res2.body(), "$.transactionId");
    assertThat(txId2).isEqualTo(txId1);
  }

  @Test
  @DisplayName(
      "Idempotency conflict: same key with different body returns HTTP 409 IDEMPOTENCY_CONFLICT")
  void idempotencyConflictReturns409() throws Exception {
    String key = randomKey();
    String payload1 = transferJson("bank-a", "ACC-1", "bank-b", "ACC-2", "50.00", "REF-ORIGINAL");
    String payload2 = transferJson("bank-a", "ACC-1", "bank-b", "ACC-2", "75.00", "REF-MODIFIED");

    when(participantClient.applyLeg(any(), any(), any()))
        .thenReturn(PaymentCallOutcome.completed("ack"));

    HttpResponse<String> res1 = postTransfer("sandbox", key, payload1);
    assertThat(res1.statusCode()).isEqualTo(201);

    HttpResponse<String> res2 = postTransfer("sandbox", key, payload2);
    assertThat(res2.statusCode()).isEqualTo(409);
    assertThat(JsonPath.<String>read(res2.body(), "$.code")).isEqualTo("IDEMPOTENCY_CONFLICT");
  }

  @Test
  @DisplayName("Source bank rejection: transfer transitions to REJECTED and credit is never called")
  void sourceRejectionTransitionsToRejected() throws Exception {
    String key = randomKey();
    String payload = transferJson("bank-a", "ACC-1", "bank-b", "ACC-2", "999.00", "REF-NSF");

    when(participantClient.applyLeg(eq("http://localhost:9001"), any(), any()))
        .thenReturn(PaymentCallOutcome.rejected("AM04", "Insufficient funds"));

    HttpResponse<String> res = postTransfer("sandbox", key, payload);

    assertThat(res.statusCode()).isEqualTo(201);
    assertThat(JsonPath.<String>read(res.body(), "$.state")).isEqualTo("REJECTED");
    assertThat(JsonPath.<String>read(res.body(), "$.reasonCode")).isEqualTo("AM04");
    assertThat(JsonPath.<String>read(res.body(), "$.emittedBy")).isEqualTo("SOURCE");

    // Destination institution must NEVER be called
    verify(participantClient, never()).applyLeg(eq("http://localhost:9002"), any(), any());
  }

  @Test
  @DisplayName(
      "Timeout on credit moves transfer to UNKNOWN; retry returns 409 RECONCILIATION_REQUIRED")
  void timeoutOnCreditMovesToUnknownAndBlocksRetry() throws Exception {
    String key = randomKey();
    String payload = transferJson("bank-a", "ACC-1", "bank-b", "ACC-2", "80.00", "REF-TIMEOUT");

    when(participantClient.applyLeg(eq("http://localhost:9001"), any(), any()))
        .thenReturn(PaymentCallOutcome.completed("debit-ok"));
    when(participantClient.applyLeg(eq("http://localhost:9002"), any(), any()))
        .thenReturn(PaymentCallOutcome.timedOut("Connection timed out"));

    HttpResponse<String> res1 = postTransfer("sandbox", key, payload);

    assertThat(res1.statusCode()).isEqualTo(201);
    assertThat(JsonPath.<String>read(res1.body(), "$.state")).isEqualTo("UNKNOWN");
    assertThat(JsonPath.<String>read(res1.body(), "$.pendingAction")).isEqualTo("RETRY_CREDIT");

    // Retrying with the same key and payload while in UNKNOWN state returns 409
    // RECONCILIATION_REQUIRED
    HttpResponse<String> res2 = postTransfer("sandbox", key, payload);
    assertThat(res2.statusCode()).isEqualTo(409);
    assertThat(JsonPath.<String>read(res2.body(), "$.code")).isEqualTo("RECONCILIATION_REQUIRED");
  }

  @Test
  @DisplayName("Routability check failure rejects transfer with ROUTING_ERROR")
  void routabilityFailureRejectsTransfer() throws Exception {
    String key = randomKey();
    // Non-member or non-existent institution
    String payload =
        transferJson("unknown-bank", "ACC-1", "bank-b", "ACC-2", "20.00", "REF-BAD-INST");

    HttpResponse<String> res = postTransfer("sandbox", key, payload);

    assertThat(res.statusCode()).isEqualTo(201);
    assertThat(JsonPath.<String>read(res.body(), "$.state")).isEqualTo("REJECTED");
    assertThat(JsonPath.<String>read(res.body(), "$.reasonCode")).isEqualTo("ROUTING_ERROR");
    assertThat(JsonPath.<String>read(res.body(), "$.emittedBy")).isEqualTo("FINSIM");
  }
}
