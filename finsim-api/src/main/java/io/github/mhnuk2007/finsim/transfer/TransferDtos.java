package io.github.mhnuk2007.finsim.transfer;

import io.github.mhnuk2007.finsim.core.StageEntry;
import io.github.mhnuk2007.finsim.core.Transfer;
import java.time.Instant;
import java.util.List;

public final class TransferDtos {

  private TransferDtos() {}

  public record AccountRefDto(String institutionId, String accountReference) {}

  public record MoneyDto(String amount, String currency) {}

  public record CreateTransferRequest(
      AccountRefDto from, AccountRefDto to, MoneyDto money, String reference) {}

  public record StageEntryDto(String stage, String outcome, Instant at, String details) {
    public static StageEntryDto from(StageEntry entry) {
      return new StageEntryDto(entry.stage(), entry.outcome().name(), entry.at(), entry.details());
    }
  }

  public record TransferResponse(
      String transactionId,
      String state,
      String currentStage,
      String reasonCode,
      String emittedBy,
      String pendingAction,
      boolean needsManualReview,
      AccountRefDto from,
      AccountRefDto to,
      MoneyDto money,
      String reference,
      List<StageEntryDto> stageHistory,
      String provenance) {

    public static TransferResponse from(Transfer transfer) {
      return new TransferResponse(
          transfer.getId(),
          transfer.getState().name(),
          transfer.getCurrentStage(),
          transfer.getReasonCode(),
          transfer.getEmittedBy() != null ? transfer.getEmittedBy().name() : null,
          transfer.getPendingAction() != null ? transfer.getPendingAction().name() : "NONE",
          transfer.isNeedsManualReview(),
          new AccountRefDto(
              transfer.getFrom().institutionId(), transfer.getFrom().accountReference()),
          new AccountRefDto(transfer.getTo().institutionId(), transfer.getTo().accountReference()),
          new MoneyDto(
              transfer.getMoney().amount().toPlainString(), transfer.getMoney().currency()),
          transfer.getReference(),
          transfer.getStageHistory().stream().map(StageEntryDto::from).toList(),
          transfer.getProvenance().name());
    }
  }

  public record ErrorResponse(
      String code, String message, String stage, String emittedBy, String traceId) {
    public ErrorResponse(String code, String message) {
      this(code, message, null, null, null);
    }
  }
}
