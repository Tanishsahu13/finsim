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
