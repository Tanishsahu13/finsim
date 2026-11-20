package io.github.mhnuk2007.finsim.transfer.internal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import io.github.mhnuk2007.finsim.transfer.internal.ParticipantClient.CallStatus;
import io.github.mhnuk2007.finsim.transfer.internal.ParticipantClient.LegRequestPayload;
import io.github.mhnuk2007.finsim.transfer.internal.ParticipantClient.MoneyPayload;
import io.github.mhnuk2007.finsim.transfer.internal.ParticipantClient.PaymentCallOutcome;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

class ParticipantClientTest {

  private RestClient.Builder restClientBuilder;
  private MockRestServiceServer mockServer;
  private ParticipantClient client;

  @BeforeEach
  void setUp() {
    restClientBuilder = RestClient.builder();
    mockServer = MockRestServiceServer.bindTo(restClientBuilder).build();
    client = new ParticipantClient(restClientBuilder.build());
  }

  @Test
  @DisplayName(
      "Issue #52: HTTP 422 with JSON error body is classified as REJECTED with reason code")
  void http422ClassifiedAsRejectedWithReasonCode() {
    mockServer
        .expect(requestTo("http://localhost:9001/finsim/v1/payments"))
        .andExpect(method(HttpMethod.POST))
        .andExpect(header("Idempotency-Key", "idemp-key-1"))
        .andRespond(
            withStatus(HttpStatus.UNPROCESSABLE_ENTITY)
                .contentType(MediaType.APPLICATION_JSON)
                .body("{\"code\": \"AM04\", \"message\": \"Insufficient funds.\"}"));

    LegRequestPayload request =
        new LegRequestPayload(
            "tx-1",
            "DEBIT",
            "ACC-1",
            new MoneyPayload("100.00", "USD"),
            "REF-1",
            null,
            "SYNTHETIC");

    PaymentCallOutcome outcome = client.applyLeg("http://localhost:9001", "idemp-key-1", request);

    assertThat(outcome.status()).isEqualTo(CallStatus.REJECTED);
    assertThat(outcome.reasonCode()).isEqualTo("AM04");
    assertThat(outcome.errorMessage()).isEqualTo("Insufficient funds.");
    mockServer.verify();
  }

  @Test
  @DisplayName("Issue #52: HTTP 400 is classified as REJECTED with reason code")
  void http400ClassifiedAsRejected() {
    mockServer
        .expect(requestTo("http://localhost:9001/finsim/v1/payments"))
        .andExpect(method(HttpMethod.POST))
        .andRespond(
            withStatus(HttpStatus.BAD_REQUEST)
                .contentType(MediaType.APPLICATION_JSON)
                .body("{\"code\": \"AC01\", \"message\": \"Account does not exist.\"}"));

    LegRequestPayload request =
        new LegRequestPayload(
            "tx-2", "DEBIT", "ACC-X", new MoneyPayload("50.00", "USD"), "REF-2", null, "SYNTHETIC");

    PaymentCallOutcome outcome = client.applyLeg("http://localhost:9001", "idemp-key-2", request);

    assertThat(outcome.status()).isEqualTo(CallStatus.REJECTED);
    assertThat(outcome.reasonCode()).isEqualTo("AC01");
    mockServer.verify();
  }

  @Test
  @DisplayName("HTTP 200/201 with COMPLETED is classified as COMPLETED")
  void http200ClassifiedAsCompleted() {
    mockServer
        .expect(requestTo("http://localhost:9001/finsim/v1/payments"))
        .andExpect(method(HttpMethod.POST))
        .andRespond(
            withSuccess(
                "{\"status\": \"COMPLETED\", \"participantReference\": \"PART-REF-99\"}",
                MediaType.APPLICATION_JSON));

    LegRequestPayload request =
        new LegRequestPayload(
            "tx-3", "DEBIT", "ACC-1", new MoneyPayload("10.00", "USD"), "REF-3", null, "SYNTHETIC");

    PaymentCallOutcome outcome = client.applyLeg("http://localhost:9001", "idemp-key-3", request);

    assertThat(outcome.status()).isEqualTo(CallStatus.COMPLETED);
    assertThat(outcome.participantReference()).isEqualTo("PART-REF-99");
    mockServer.verify();
  }
}
