package io.github.mhnuk2007.finsim.samples.bank;

import java.util.List;

/**
 * Wire types of the finsim/v1 participant contract, written independently of FinSim's own code. See
 * contract/finsim-v1.openapi.yaml in the repository root.
 */
public final class Contract {

  private Contract() {}

  public enum Direction {
    DEBIT,
    CREDIT
  }

  /** A participant reports only what it knows. It never reports UNKNOWN. */
  public enum LegStatus {
    ACCEPTED,
    COMPLETED,
    REJECTED
  }

  public record MoneyDto(String amount, String currency) {}

  public record PaymentLegRequest(
      String transactionId,
      Direction direction,
      String accountReference,
      MoneyDto money,
      String reference,
      String reversalOf,
      String provenance) {}

  public record PaymentLegResult(
      String transactionId,
      Direction direction,
      LegStatus status,
      String reasonCode,
      String participantReference,
      String provenance) {}

  public record ErrorBody(String code, String message) {}

  public record Health(String status, List<String> contractVersions) {}

  public record Capabilities(
      String institutionType, List<String> capabilities, List<String> currencies) {}
}
