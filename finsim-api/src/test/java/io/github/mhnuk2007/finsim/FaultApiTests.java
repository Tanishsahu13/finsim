package io.github.mhnuk2007.finsim;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.jayway.jsonpath.JsonPath;
import io.github.mhnuk2007.finsim.faults.FaultService;
import io.github.mhnuk2007.finsim.transfer.internal.ParticipantClient;
import io.github.mhnuk2007.finsim.transfer.internal.ParticipantClient.PaymentCallOutcome;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpRequest.BodyPublishers;
import java.net.http.HttpResponse;
import java.net.http.HttpResponse.BodyHandlers;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(TestcontainersConfiguration.class)
class FaultApiTests {

  @Value("${local.server.port}")
  int port;

  @MockitoBean ParticipantClient participantClient;
  @Autowired FaultService faultService;

  private final HttpClient http = HttpClient.newHttpClient();

  @BeforeEach
  void cleanFaultRulesBefore() {
    faultService.clearAll();
    reset(participantClient);
  }

  @AfterEach
  void cleanFaultRulesAfter() {
    faultService.clearAll();
    reset(participantClient);
  }

  private HttpResponse<String> postFault(String json) throws Exception {
    return http.send(
        HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/api/v1/faults"))
            .header("Content-Type", "application/json")
            .POST(BodyPublishers.ofString(json))
            .build(),
        BodyHandlers.ofString());
  }

  private HttpResponse<String> getFaults() throws Exception {
    return http.send(
        HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/api/v1/faults"))
            .GET()
            .build(),
        BodyHandlers.ofString());
  }

  private HttpResponse<String> deleteFault(String faultId) throws Exception {
    return http.send(
        HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/api/v1/faults/" + faultId))
            .DELETE()
            .build(),
        BodyHandlers.ofString());
  }

  private HttpResponse<String> postTransfer(String networkId, String key, String json)
      throws Exception {
    HttpRequest.Builder builder =
        HttpRequest.newBuilder(
                URI.create(
                    "http://localhost:" + port + "/api/v1/networks/" + networkId + "/transfers"))
            .header("Content-Type", "application/json")
            .header("Idempotency-Key", key)
            .POST(BodyPublishers.ofString(json));
    return http.send(builder.build(), BodyHandlers.ofString());
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
  @DisplayName("Create DELAY fault rule returns 201 Created and persists rule")
  void createDelayRule() throws Exception {
    String payload =
        """
        {
          "target": "bank-b",
          "type": "DELAY",
          "delayMs": 500,
          "probability": 0.75,
          "seed": 12345
        }
        """;
    HttpResponse<String> res = postFault(payload);
    assertThat(res.statusCode()).isEqualTo(201);

    String faultId = JsonPath.read(res.body(), "$.faultId");
    assertThat(faultId).isNotBlank();
    assertThat(JsonPath.<String>read(res.body(), "$.target")).isEqualTo("bank-b");
    assertThat(JsonPath.<String>read(res.body(), "$.type")).isEqualTo("DELAY");
    assertThat(JsonPath.<Integer>read(res.body(), "$.delayMs")).isEqualTo(500);
    assertThat(JsonPath.<Double>read(res.body(), "$.probability")).isEqualTo(0.75);
    assertThat(((Number) JsonPath.read(res.body(), "$.seed")).longValue()).isEqualTo(12345L);
  }

  @Test
  @DisplayName("Create TIMEOUT rule with cap on times returns 201 Created")
  void createTimeoutRuleWithTimes() throws Exception {
    String payload =
        """
        {
          "target": "bank-b",
          "type": "TIMEOUT",
          "times": 3
        }
        """;
    HttpResponse<String> res = postFault(payload);
    assertThat(res.statusCode()).isEqualTo(201);

    assertThat(JsonPath.<String>read(res.body(), "$.target")).isEqualTo("bank-b");
    assertThat(JsonPath.<String>read(res.body(), "$.type")).isEqualTo("TIMEOUT");
    assertThat(JsonPath.<Integer>read(res.body(), "$.times")).isEqualTo(3);
    assertThat(JsonPath.<Double>read(res.body(), "$.probability")).isEqualTo(1.0);
  }

  @Test
  @DisplayName("Create DUPLICATE_DELIVERY rule returns 201 Created")
  void createDuplicateDeliveryRule() throws Exception {
    String payload =
        """
        {
          "target": "bank-a",
          "type": "DUPLICATE_DELIVERY"
        }
        """;
    HttpResponse<String> res = postFault(payload);
    assertThat(res.statusCode()).isEqualTo(201);

    assertThat(JsonPath.<String>read(res.body(), "$.target")).isEqualTo("bank-a");
    assertThat(JsonPath.<String>read(res.body(), "$.type")).isEqualTo("DUPLICATE_DELIVERY");
  }

  @Test
  @DisplayName("Invalid fault rule payloads reject with HTTP 400")
  void invalidPayloadsReturn400() throws Exception {
    // Missing target
    HttpResponse<String> noTarget =
        postFault(
            """
        {"type": "TIMEOUT"}
        """);
    assertThat(noTarget.statusCode()).isEqualTo(400);

    // Missing type
    HttpResponse<String> noType =
        postFault(
            """
        {"target": "bank-b"}
        """);
    assertThat(noType.statusCode()).isEqualTo(400);

    // DELAY without delayMs
    HttpResponse<String> noDelayMs =
        postFault(
            """
        {"target": "bank-b", "type": "DELAY"}
        """);
    assertThat(noDelayMs.statusCode()).isEqualTo(400);

    // Negative delayMs
    HttpResponse<String> negativeDelay =
        postFault(
            """
        {"target": "bank-b", "type": "DELAY", "delayMs": -10}
        """);
    assertThat(negativeDelay.statusCode()).isEqualTo(400);

    // Invalid probability
    HttpResponse<String> highProb =
        postFault(
            """
        {"target": "bank-b", "type": "TIMEOUT", "probability": 1.5}
        """);
    assertThat(highProb.statusCode()).isEqualTo(400);

    // Times <= 0
    HttpResponse<String> zeroTimes =
        postFault(
            """
        {"target": "bank-b", "type": "TIMEOUT", "times": 0}
        """);
    assertThat(zeroTimes.statusCode()).isEqualTo(400);
  }

  @Test
  @DisplayName("List and delete fault rules")
  void listAndDeleteFaultRules() throws Exception {
    postFault(
        """
        {"target": "bank-a", "type": "TIMEOUT"}
        """);
    HttpResponse<String> postRes =
        postFault(
            """
        {"target": "bank-b", "type": "DUPLICATE_DELIVERY"}
        """);
    String idB = JsonPath.read(postRes.body(), "$.faultId");

    HttpResponse<String> listRes = getFaults();
    assertThat(listRes.statusCode()).isEqualTo(200);
    List<String> targets = JsonPath.read(listRes.body(), "$[*].target");
    assertThat(targets).contains("bank-a", "bank-b");

    HttpResponse<String> delRes = deleteFault(idB);
    assertThat(delRes.statusCode()).isEqualTo(204);

    HttpResponse<String> listAfter = getFaults();
    List<String> targetsAfter = JsonPath.read(listAfter.body(), "$[*].target");
    assertThat(targetsAfter).contains("bank-a").doesNotContain("bank-b");

    // Deleting again returns 404
    HttpResponse<String> delNotFound = deleteFault(idB);
    assertThat(delNotFound.statusCode()).isEqualTo(404);
  }

  @Test
  @DisplayName("Injected TIMEOUT on destination moves transfer to UNKNOWN (RETRY_CREDIT)")
  void injectedTimeoutOnDestinationMovesTransferToUnknown() throws Exception {
    postFault(
        """
        {"target": "bank-b", "type": "TIMEOUT"}
        """);

    when(participantClient.applyLeg(eq("http://localhost:9001"), any(), any()))
        .thenReturn(PaymentCallOutcome.completed("part-ref-deb-ok"));

    String key = "key-timeout-" + UUID.randomUUID();
    String payload =
        transferJson("bank-a", "ACC-1", "bank-b", "ACC-2", "150.00", "REF-TIMEOUT-FAULT");

    HttpResponse<String> res = postTransfer("sandbox", key, payload);
    assertThat(res.statusCode()).isEqualTo(201);

    assertThat(JsonPath.<String>read(res.body(), "$.state")).isEqualTo("UNKNOWN");
    assertThat(JsonPath.<String>read(res.body(), "$.pendingAction")).isEqualTo("RETRY_CREDIT");
    assertThat(JsonPath.<String>read(res.body(), "$.currentStage")).isEqualTo("credit_destination");
  }

  @Test
  @DisplayName("Injected DUPLICATE_DELIVERY dispatches identical request twice to participant")
  void injectedDuplicateDeliveryDispatchesTwice() throws Exception {
    postFault(
        """
        {"target": "bank-b", "type": "DUPLICATE_DELIVERY"}
        """);

    when(participantClient.applyLeg(eq("http://localhost:9001"), any(), any()))
        .thenReturn(PaymentCallOutcome.completed("part-ref-deb-ok"));
    when(participantClient.applyLeg(eq("http://localhost:9002"), any(), any()))
        .thenReturn(PaymentCallOutcome.completed("part-ref-cred-ok"));

    String key = "key-dup-" + UUID.randomUUID();
    String payload = transferJson("bank-a", "ACC-1", "bank-b", "ACC-2", "200.00", "REF-DUP-FAULT");

    HttpResponse<String> res = postTransfer("sandbox", key, payload);
    assertThat(res.statusCode()).isEqualTo(201);
    assertThat(JsonPath.<String>read(res.body(), "$.state")).isEqualTo("COMPLETED");

    String txId = JsonPath.read(res.body(), "$.transactionId");
    // Verify participantClient received applyLeg twice for the credit leg with exact same arguments
    verify(participantClient, times(2))
        .applyLeg(
            eq("http://localhost:9002"),
            eq(txId + "-credit"),
            argThat(
                req -> "CREDIT".equals(req.direction()) && "200.00".equals(req.money().amount())));
  }

  @Test
  @DisplayName("Fault rule with times=1 expires after single trigger, subsequent transfer succeeds")
  void faultRuleExpiresAfterConfiguredTimes() throws Exception {
    postFault(
        """
        {"target": "bank-b", "type": "TIMEOUT", "times": 1}
        """);

    when(participantClient.applyLeg(eq("http://localhost:9001"), any(), any()))
        .thenReturn(PaymentCallOutcome.completed("deb-ok"));
    when(participantClient.applyLeg(eq("http://localhost:9002"), any(), any()))
        .thenReturn(PaymentCallOutcome.completed("cred-ok"));

    // First transfer triggers TIMEOUT -> ends in UNKNOWN
    String key1 = "key-times-1-" + UUID.randomUUID();
    String payload1 = transferJson("bank-a", "ACC-1", "bank-b", "ACC-2", "50.00", "REF-T1");
    HttpResponse<String> res1 = postTransfer("sandbox", key1, payload1);
    assertThat(res1.statusCode()).isEqualTo(201);
    assertThat(JsonPath.<String>read(res1.body(), "$.state")).isEqualTo("UNKNOWN");

    // Second transfer: rule has expired -> succeeds as COMPLETED
    String key2 = "key-times-2-" + UUID.randomUUID();
    String payload2 = transferJson("bank-a", "ACC-1", "bank-b", "ACC-2", "50.00", "REF-T2");
    HttpResponse<String> res2 = postTransfer("sandbox", key2, payload2);
    assertThat(res2.statusCode()).isEqualTo(201);
    assertThat(JsonPath.<String>read(res2.body(), "$.state")).isEqualTo("COMPLETED");
  }
}
