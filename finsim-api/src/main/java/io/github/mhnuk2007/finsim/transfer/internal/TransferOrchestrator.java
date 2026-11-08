package io.github.mhnuk2007.finsim.transfer.internal;

import io.github.mhnuk2007.finsim.core.AccountRef;
import io.github.mhnuk2007.finsim.core.Emitter;
import io.github.mhnuk2007.finsim.core.Money;
import io.github.mhnuk2007.finsim.core.PendingAction;
import io.github.mhnuk2007.finsim.core.ReasonCode;
import io.github.mhnuk2007.finsim.core.StageEntry;
import io.github.mhnuk2007.finsim.core.Transfer;
import io.github.mhnuk2007.finsim.registry.Capability;
import io.github.mhnuk2007.finsim.registry.Institution;
import io.github.mhnuk2007.finsim.registry.Network;
import io.github.mhnuk2007.finsim.registry.Registry;
import io.github.mhnuk2007.finsim.registry.RegistryException;
import io.github.mhnuk2007.finsim.transfer.TransferDtos.CreateTransferRequest;
import io.github.mhnuk2007.finsim.transfer.internal.ParticipantClient.LegRequestPayload;
import io.github.mhnuk2007.finsim.transfer.internal.ParticipantClient.MoneyPayload;
import io.github.mhnuk2007.finsim.transfer.internal.ParticipantClient.PaymentCallOutcome;
import java.time.Clock;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class TransferOrchestrator {

  private final Registry registry;
  private final ParticipantClient participantClient;
  private final TransferRepository transferRepository;
  private final TransferStageRepository stageRepository;
  private final Clock clock;

  public TransferOrchestrator(
      Registry registry,
      ParticipantClient participantClient,
      TransferRepository transferRepository,
      TransferStageRepository stageRepository,
      Clock clock) {
    this.registry = registry;
    this.participantClient = participantClient;
    this.transferRepository = transferRepository;
    this.stageRepository = stageRepository;
    this.clock = clock;
  }

  public Transfer orchestrate(
      String transactionId,
      String networkId,
      String idempotencyKey,
      CreateTransferRequest request) {

    AccountRef from =
        new AccountRef(request.from().institutionId(), request.from().accountReference());
    AccountRef to = new AccountRef(request.to().institutionId(), request.to().accountReference());
    Money money = Money.of(request.money().amount(), request.money().currency());

    // 1. Validate routability and network membership
    Institution fromInst;
    Institution toInst;
    try {
      fromInst =
          registry.requireRoutable(networkId, from.institutionId(), Capability.ACCOUNT_DEBIT);
      toInst = registry.requireRoutable(networkId, to.institutionId(), Capability.ACCOUNT_CREDIT);
      Network net = registry.network(networkId);
      if (!net.currencies().contains(money.currency())) {
        return createAndReject(
            transactionId,
            networkId,
            idempotencyKey,
            from,
            to,
            money,
            request.reference(),
            ReasonCode.ROUTING_ERROR.code(),
            Emitter.FINSIM,
            "Currency " + money.currency() + " not supported on network " + networkId);
      }
    } catch (RegistryException ex) {
      return createAndReject(
          transactionId,
          networkId,
          idempotencyKey,
          from,
          to,
          money,
          request.reference(),
          ReasonCode.ROUTING_ERROR.code(),
          Emitter.FINSIM,
          ex.getMessage());
    }

    // 2. Initialize Transfer aggregate in CREATED state, transition to ACCEPTED, then IN_PROGRESS
    Transfer transfer =
        Transfer.create(
            transactionId,
            networkId,
            idempotencyKey,
            from,
            to,
            money,
            request.reference(),
            clock.instant());

    transfer.accept("accepted", clock.instant());
    transfer.startProcessing("debit_source", clock.instant());
    persistTransfer(transfer);

    // 3. Execute Step 1: Outbound DEBIT to source institution
    String debitKey = transactionId + "-debit";
    LegRequestPayload debitPayload =
        new LegRequestPayload(
            transactionId,
            "DEBIT",
            from.accountReference(),
            new MoneyPayload(money.amount().toPlainString(), money.currency()),
            request.reference(),
            null,
            "SYNTHETIC");

    PaymentCallOutcome debitOutcome =
        participantClient.applyLeg(fromInst.baseUrl(), debitKey, debitPayload);

    switch (debitOutcome.status()) {
      case REJECTED -> {
        transfer.reject(
            "debit_source",
            debitOutcome.reasonCode(),
            Emitter.SOURCE,
            debitOutcome.errorMessage(),
            clock.instant());
        persistTransfer(transfer);
        return transfer;
      }
      case TIMED_OUT -> {
        transfer.markUnknown(
            "debit_source",
            PendingAction.REVERSE_DEBIT,
            false,
            debitOutcome.errorMessage(),
            clock.instant());
        persistTransfer(transfer);
        return transfer;
      }
      case COMPLETED -> {
        // Debit succeeded; proceed to CREDIT
      }
    }

    // 4. Execute Step 2: Outbound CREDIT to destination institution
    String creditKey = transactionId + "-credit";
    LegRequestPayload creditPayload =
        new LegRequestPayload(
            transactionId,
            "CREDIT",
            to.accountReference(),
            new MoneyPayload(money.amount().toPlainString(), money.currency()),
            request.reference(),
            null,
            "SYNTHETIC");

    PaymentCallOutcome creditOutcome =
        participantClient.applyLeg(toInst.baseUrl(), creditKey, creditPayload);

    switch (creditOutcome.status()) {
      case COMPLETED -> transfer.complete("credit_destination", clock.instant());
      case TIMED_OUT ->
          // A timeout on credit moves the transfer to UNKNOWN (never REJECTED!)
          transfer.markUnknown(
              "credit_destination",
              PendingAction.RETRY_CREDIT,
              false,
              creditOutcome.errorMessage(),
              clock.instant());
      case REJECTED ->
          transfer.reject(
              "credit_destination",
              creditOutcome.reasonCode(),
              Emitter.DESTINATION,
              creditOutcome.errorMessage(),
              clock.instant());
    }

    persistTransfer(transfer);
    return transfer;
  }

  public Transfer resolve(Transfer transfer) {
    if (transfer.getState() != io.github.mhnuk2007.finsim.core.TransferState.UNKNOWN) {
      return transfer;
    }

    Institution fromInst;
    Institution toInst;
    try {
      fromInst = registry.institution(transfer.getFrom().institutionId());
      toInst = registry.institution(transfer.getTo().institutionId());
    } catch (Exception ex) {
      transfer.recordStage(
          "resolve",
          io.github.mhnuk2007.finsim.core.StageOutcome.UNKNOWN,
          "Failed to look up institutions in registry: " + ex.getMessage(),
          clock.instant());
      persistTransfer(transfer);
      return transfer;
    }

    // 1. Query debit leg at source institution
    java.util.Optional<ParticipantClient.LegResponsePayload> debitLegOpt;
    try {
      debitLegOpt = participantClient.queryLegStatus(fromInst.baseUrl(), transfer.getId(), "DEBIT");
    } catch (Exception ex) {
      // Row 7: Query timed out or errored -> stays UNKNOWN
      transfer.recordStage(
          "query_debit",
          io.github.mhnuk2007.finsim.core.StageOutcome.UNKNOWN,
          "Failed to query debit leg at source: " + ex.getMessage(),
          clock.instant());
      persistTransfer(transfer);
      return transfer;
    }

    // Row 1: Debit leg not found (404) -> Nothing was applied -> REJECTED (FINSIM_TIMEOUT, FINSIM)
    if (debitLegOpt.isEmpty()) {
      transfer.reject(
          "resolve",
          ReasonCode.FINSIM_TIMEOUT.code(),
          Emitter.FINSIM,
          "Debit leg was not found at source institution",
          clock.instant());
      persistTransfer(transfer);
      return transfer;
    }

    ParticipantClient.LegResponsePayload debitLeg = debitLegOpt.get();

    // Row 2: Debit leg was REJECTED by source -> REJECTED (source code, SOURCE)
    if ("REJECTED".equalsIgnoreCase(debitLeg.status())) {
      String code =
          debitLeg.reasonCode() != null && !debitLeg.reasonCode().isBlank()
              ? debitLeg.reasonCode()
              : "REJECTED";
      transfer.reject(
          "resolve",
          code,
          Emitter.SOURCE,
          "Debit leg was rejected by source institution",
          clock.instant());
      persistTransfer(transfer);
      return transfer;
    }

    // If debit is not COMPLETED, cannot decide -> stays UNKNOWN
    if (!"COMPLETED".equalsIgnoreCase(debitLeg.status())) {
      transfer.recordStage(
          "query_debit",
          io.github.mhnuk2007.finsim.core.StageOutcome.UNKNOWN,
          "Source reported debit status " + debitLeg.status(),
          clock.instant());
      persistTransfer(transfer);
      return transfer;
    }

    // 2. Debit is COMPLETED. Query credit leg at destination institution
    java.util.Optional<ParticipantClient.LegResponsePayload> creditLegOpt;
    try {
      creditLegOpt = participantClient.queryLegStatus(toInst.baseUrl(), transfer.getId(), "CREDIT");
    } catch (Exception ex) {
      // Row 7: Credit query timed out or errored -> stays UNKNOWN
      transfer.recordStage(
          "query_credit",
          io.github.mhnuk2007.finsim.core.StageOutcome.UNKNOWN,
          "Failed to query credit leg at destination: " + ex.getMessage(),
          clock.instant());
      persistTransfer(transfer);
      return transfer;
    }

    // Row 3: Destination credit is COMPLETED -> Both legs applied -> COMPLETED
    if (creditLegOpt.isPresent() && "COMPLETED".equalsIgnoreCase(creditLegOpt.get().status())) {
      transfer.complete("resolve", clock.instant());
      persistTransfer(transfer);
      return transfer;
    }

    // Row 6: Destination credit is ACCEPTED (not yet COMPLETED) -> Destination is still processing
    // -> stays UNKNOWN
    if (creditLegOpt.isPresent() && "ACCEPTED".equalsIgnoreCase(creditLegOpt.get().status())) {
      transfer.updateResolutionStatus(PendingAction.RETRY_CREDIT, false, clock.instant());
      transfer.recordStage(
          "query_credit",
          io.github.mhnuk2007.finsim.core.StageOutcome.UNKNOWN,
          "Destination reported credit status ACCEPTED",
          clock.instant());
      persistTransfer(transfer);
      return transfer;
    }

    // Row 4: Credit not found (404) at destination -> Re-send credit once with the SAME idempotency
    // key
    if (creditLegOpt.isEmpty()) {
      String creditKey = transfer.getId() + "-credit";
      LegRequestPayload creditPayload =
          new LegRequestPayload(
              transfer.getId(),
              "CREDIT",
              transfer.getTo().accountReference(),
              new MoneyPayload(
                  transfer.getMoney().amount().toPlainString(), transfer.getMoney().currency()),
              transfer.getReference(),
              null,
              "SYNTHETIC");

      PaymentCallOutcome creditOutcome =
          participantClient.applyLeg(toInst.baseUrl(), creditKey, creditPayload);

      switch (creditOutcome.status()) {
        case COMPLETED -> {
          transfer.complete("resolve_credit", clock.instant());
          persistTransfer(transfer);
          return transfer;
        }
        case TIMED_OUT -> {
          transfer.updateResolutionStatus(PendingAction.RETRY_CREDIT, false, clock.instant());
          transfer.recordStage(
              "resolve_credit",
              io.github.mhnuk2007.finsim.core.StageOutcome.UNKNOWN,
              "Re-sending credit timed out: " + creditOutcome.errorMessage(),
              clock.instant());
          persistTransfer(transfer);
          return transfer;
        }
        case REJECTED -> {
          // Re-send resulted in definitive rejection -> proceed to Row 5 (reversal)
          return executeReversal(transfer, fromInst, creditOutcome.reasonCode());
        }
      }
    }

    // Row 5: Credit was definitively REJECTED by destination -> Reverse debit
    ParticipantClient.LegResponsePayload creditLeg = creditLegOpt.get();
    if ("REJECTED".equalsIgnoreCase(creditLeg.status())) {
      return executeReversal(transfer, fromInst, creditLeg.reasonCode());
    }

    // Any other unexpected state -> stays UNKNOWN
    transfer.recordStage(
        "resolve",
        io.github.mhnuk2007.finsim.core.StageOutcome.UNKNOWN,
        "Ambiguous participant state during resolution",
        clock.instant());
    persistTransfer(transfer);
    return transfer;
  }

  private Transfer executeReversal(
      Transfer transfer, Institution fromInst, String rejectionReason) {
    String destReason =
        rejectionReason != null && !rejectionReason.isBlank() ? rejectionReason : "REJECTED";
    String revTxId = transfer.getId() + "-rev";
    String revKey = transfer.getIdempotencyKey() + ":rev";
    LegRequestPayload revPayload =
        new LegRequestPayload(
            revTxId,
            "CREDIT",
            transfer.getFrom().accountReference(),
            new MoneyPayload(
                transfer.getMoney().amount().toPlainString(), transfer.getMoney().currency()),
            "Reversal of " + transfer.getId(),
            transfer.getId(),
            "SYNTHETIC");

    PaymentCallOutcome revOutcome =
        participantClient.applyLeg(fromInst.baseUrl(), revKey, revPayload);

    switch (revOutcome.status()) {
      case COMPLETED -> {
        // Reversal succeeded: terminal REJECTED state with destination's reason code
        transfer.reject(
            "resolve_reversal",
            destReason,
            Emitter.DESTINATION,
            "Reversal completed after destination rejection",
            clock.instant());
        persistTransfer(transfer);
        return transfer;
      }
      case TIMED_OUT -> {
        // Reversal call timed out: stays UNKNOWN with pendingAction: REVERSE_DEBIT,
        // needsManualReview: false
        transfer.updateResolutionStatus(PendingAction.REVERSE_DEBIT, false, clock.instant());
        transfer.recordStage(
            "resolve_reversal",
            io.github.mhnuk2007.finsim.core.StageOutcome.UNKNOWN,
            "Reversal leg timed out: " + revOutcome.errorMessage(),
            clock.instant());
        persistTransfer(transfer);
        return transfer;
      }
      case REJECTED -> {
        // Reversal rejected by source: stays UNKNOWN, pendingAction: REVERSE_DEBIT,
        // needsManualReview: true
        transfer.updateResolutionStatus(PendingAction.REVERSE_DEBIT, true, clock.instant());
        transfer.recordStage(
            "resolve_reversal",
            io.github.mhnuk2007.finsim.core.StageOutcome.FAILED,
            "Reversal was rejected by source: " + revOutcome.errorMessage(),
            clock.instant());
        persistTransfer(transfer);
        return transfer;
      }
    }
    return transfer;
  }

  private Transfer createAndReject(
      String transactionId,
      String networkId,
      String idempotencyKey,
      AccountRef from,
      AccountRef to,
      Money money,
      String reference,
      String reasonCode,
      Emitter emitter,
      String details) {
    Transfer transfer =
        Transfer.create(
            transactionId, networkId, idempotencyKey, from, to, money, reference, clock.instant());
    transfer.reject("validate_routing", reasonCode, emitter, details, clock.instant());
    persistTransfer(transfer);
    return transfer;
  }

  @Transactional
  public void persistTransfer(Transfer transfer) {
    TransferEntity entity =
        transferRepository
            .findById(transfer.getId())
            .map(
                existing -> {
                  existing.updateFrom(transfer);
                  return existing;
                })
            .orElseGet(() -> TransferEntity.from(transfer));

    transferRepository.save(entity);

    // Save any newly appended stages
    long existingCount = stageRepository.findByTransferIdOrderByIdAsc(transfer.getId()).size();
    if (transfer.getStageHistory().size() > existingCount) {
      for (int i = (int) existingCount; i < transfer.getStageHistory().size(); i++) {
        StageEntry entry = transfer.getStageHistory().get(i);
        TransferStageEntity stageEntity =
            TransferStageEntity.of(
                transfer.getId(),
                entry.stage(),
                entry.outcome().name(),
                entry.details(),
                entry.at());
        stageRepository.save(stageEntity);
      }
    }
  }
}
