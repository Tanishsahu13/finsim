package io.github.mhnuk2007.finsim.transfer.internal;

import java.time.Duration;
import java.util.Optional;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

@Component
public class ParticipantClient {

  private final RestClient restClient;

  public ParticipantClient() {
    this(Duration.ofSeconds(2), Duration.ofSeconds(4));
  }

  public ParticipantClient(Duration connectTimeout, Duration readTimeout) {
    SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
    factory.setConnectTimeout(connectTimeout);
    factory.setReadTimeout(readTimeout);
    this.restClient = RestClient.builder().requestFactory(factory).build();
  }

  public enum CallStatus {
    COMPLETED,
    REJECTED,
    TIMED_OUT
  }

  public record PaymentCallOutcome(
      CallStatus status, String reasonCode, String participantReference, String errorMessage) {

    public static PaymentCallOutcome completed(String participantReference) {
      return new PaymentCallOutcome(CallStatus.COMPLETED, null, participantReference, null);
    }

    public static PaymentCallOutcome rejected(String reasonCode, String message) {
      return new PaymentCallOutcome(CallStatus.REJECTED, reasonCode, null, message);
    }

    public static PaymentCallOutcome timedOut(String message) {
      return new PaymentCallOutcome(CallStatus.TIMED_OUT, null, null, message);
    }
  }

  public record LegRequestPayload(
      String transactionId,
      String direction,
      String accountReference,
      MoneyPayload money,
      String reference,
      String reversalOf,
      String provenance) {}

  public record MoneyPayload(String amount, String currency) {}

  public record LegResponsePayload(
      String transactionId,
      String direction,
      String status,
      String reasonCode,
      String participantReference,
      String provenance) {}

  public record ParticipantErrorPayload(String code, String message) {}

  /**
   * Dispatches a payment leg (DEBIT or CREDIT) to a participant bank.
   *
   * @param baseUrl the participant base URL (e.g. http://localhost:9001)
   * @param idempotencyKey the idempotency key for this leg
   * @param request the payment leg payload
   * @return the outcome (COMPLETED, REJECTED, or TIMED_OUT)
   */
  public PaymentCallOutcome applyLeg(
      String baseUrl, String idempotencyKey, LegRequestPayload request) {
    String url = normalizeUrl(baseUrl) + "/finsim/v1/payments";
    try {
      LegResponsePayload response =
          restClient
              .post()
              .uri(url)
              .header("Idempotency-Key", idempotencyKey)
              .contentType(MediaType.APPLICATION_JSON)
              .body(request)
              .retrieve()
              .onStatus(
                  HttpStatusCode::is4xxClientError,
                  (req, resp) -> {
                    // Let RestClientResponseException handle 4xx
                  })
              .body(LegResponsePayload.class);

      if (response != null && "COMPLETED".equalsIgnoreCase(response.status())) {
        return PaymentCallOutcome.completed(response.participantReference());
      }
      return PaymentCallOutcome.completed(
          response != null ? response.participantReference() : "ACK");
    } catch (ResourceAccessException ex) {
      // Timeout or connection failure -> UNKNOWN outcome
      return PaymentCallOutcome.timedOut(ex.getMessage());
    } catch (RestClientResponseException ex) {
      if (ex.getStatusCode().value() == 422) {
        try {
          ParticipantErrorPayload error = ex.getResponseBodyAs(ParticipantErrorPayload.class);
          String code = error != null && error.code() != null ? error.code() : "REJECTED";
          String message = error != null ? error.message() : ex.getMessage();
          return PaymentCallOutcome.rejected(code, message);
        } catch (Exception ignored) {
          return PaymentCallOutcome.rejected("REJECTED", ex.getResponseBodyAsString());
        }
      }
      return PaymentCallOutcome.timedOut(
          "HTTP " + ex.getStatusCode().value() + ": " + ex.getMessage());
    } catch (Exception ex) {
      return PaymentCallOutcome.timedOut(ex.getMessage());
    }
  }

  /** Queries a participant bank for the recorded status of a leg. */
  public Optional<LegResponsePayload> queryLegStatus(
      String baseUrl, String transactionId, String direction) {
    String url =
        normalizeUrl(baseUrl)
            + "/finsim/v1/payments/"
            + transactionId
            + "?leg="
            + direction.toUpperCase();
    try {
      LegResponsePayload response =
          restClient.get().uri(url).retrieve().body(LegResponsePayload.class);
      return Optional.ofNullable(response);
    } catch (RestClientResponseException ex) {
      if (ex.getStatusCode().value() == 404) {
        return Optional.empty();
      }
      throw ex;
    }
  }

  private static String normalizeUrl(String url) {
    return url.endsWith("/") ? url.substring(0, url.length() - 1) : url;
  }
}
