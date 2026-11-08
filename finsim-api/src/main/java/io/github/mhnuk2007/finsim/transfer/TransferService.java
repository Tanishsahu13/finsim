package io.github.mhnuk2007.finsim.transfer;

import io.github.mhnuk2007.finsim.core.Transfer;
import io.github.mhnuk2007.finsim.core.TransferState;
import io.github.mhnuk2007.finsim.transfer.TransferDtos.CreateTransferRequest;
import io.github.mhnuk2007.finsim.transfer.TransferExceptions.IdempotencyConflictException;
import io.github.mhnuk2007.finsim.transfer.TransferExceptions.InvalidTransferRequestException;
import io.github.mhnuk2007.finsim.transfer.TransferExceptions.MissingIdempotencyKeyException;
import io.github.mhnuk2007.finsim.transfer.TransferExceptions.ReconciliationRequiredException;
import io.github.mhnuk2007.finsim.transfer.TransferExceptions.TransferNotFoundException;
import io.github.mhnuk2007.finsim.transfer.TransferExceptions.TransferNotResolvableException;
import io.github.mhnuk2007.finsim.transfer.internal.TransferEntity;
import io.github.mhnuk2007.finsim.transfer.internal.TransferOrchestrator;
import io.github.mhnuk2007.finsim.transfer.internal.TransferRepository;
import io.github.mhnuk2007.finsim.transfer.internal.TransferStageEntity;
import io.github.mhnuk2007.finsim.transfer.internal.TransferStageRepository;
import java.math.BigDecimal;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class TransferService {

  public record TransferExecutionResult(Transfer transfer, boolean isReplay) {}

  private final TransferRepository transferRepository;
  private final TransferStageRepository stageRepository;
  private final TransferOrchestrator orchestrator;

  public TransferService(
      TransferRepository transferRepository,
      TransferStageRepository stageRepository,
      TransferOrchestrator orchestrator) {
    this.transferRepository = transferRepository;
    this.stageRepository = stageRepository;
    this.orchestrator = orchestrator;
  }

  /**
   * Executes or replays a transfer on a network with strict idempotency guarantees.
   *
   * @param networkId target network slug (e.g. "sandbox")
   * @param idempotencyKey client-supplied key (8 to 128 characters)
   * @param request transfer request payload
   * @return execution result containing the Transfer domain model and a replay flag
   */
  public TransferExecutionResult executeTransfer(
      String networkId, String idempotencyKey, CreateTransferRequest request) {

    validateIdempotencyKey(idempotencyKey);
    validateRequest(networkId, request);

    // 1. Check if an execution with this idempotency key already exists
    Optional<TransferEntity> existingOpt = transferRepository.findByIdempotencyKey(idempotencyKey);
    if (existingOpt.isPresent()) {
      return handleExisting(existingOpt.get(), networkId, request);
    }

    // 2. New transfer: generate unique transactionId and orchestrate payment legs
    String transactionId = "tx-" + UUID.randomUUID().toString().replace("-", "").substring(0, 16);
    try {
      Transfer transfer =
          orchestrator.orchestrate(transactionId, networkId, idempotencyKey, request);
      return new TransferExecutionResult(transfer, false);
    } catch (org.springframework.dao.DataIntegrityViolationException ex) {
      // In case of concurrent request with the same idempotency key
      return transferRepository
          .findByIdempotencyKey(idempotencyKey)
          .map(existing -> handleExisting(existing, networkId, request))
          .orElseThrow(() -> ex);
    }
  }

  private TransferExecutionResult handleExisting(
      TransferEntity existing, String networkId, CreateTransferRequest request) {
    // Verify request payload matches the original execution
    if (!matchesRequest(existing, networkId, request)) {
      throw new IdempotencyConflictException(
          "Idempotency-Key '%s' was already used with a different request payload."
              .formatted(existing.getIdempotencyKey()));
    }

    // If the transfer is in UNKNOWN state, retrying is strictly blocked until resolved
    if (existing.getState() == TransferState.UNKNOWN) {
      throw new ReconciliationRequiredException(
          "Transfer '%s' outcome is UNKNOWN and must be resolved before retrying."
              .formatted(existing.getId()));
    }

    // Replay of an identical request
    List<TransferStageEntity> stages =
        stageRepository.findByTransferIdOrderByIdAsc(existing.getId());
    return new TransferExecutionResult(existing.toDomain(stages), true);
  }

  /** Retrieves an existing transfer and its full stage history by transaction ID. */
  @Transactional(readOnly = true)
  public Transfer getTransfer(String transactionId) {
    TransferEntity entity =
        transferRepository
            .findById(transactionId)
            .orElseThrow(() -> new TransferNotFoundException(transactionId));
    List<TransferStageEntity> stages = stageRepository.findByTransferIdOrderByIdAsc(transactionId);
    return entity.toDomain(stages);
  }

  /**
   * Resolves an ambiguous (UNKNOWN) transfer according to the rules in ADR 0001.
   *
   * @param transactionId unique transaction id
   * @return the resolved transfer in its terminal or updated state
   */
  public Transfer resolveTransfer(String transactionId) {
    Transfer transfer = getTransfer(transactionId);
    if (transfer.getState().isTerminal()) {
      return transfer;
    }
    if (transfer.getState() != TransferState.UNKNOWN) {
      throw new TransferNotResolvableException(
          "Transfer '%s' is in state '%s' and cannot be resolved (only UNKNOWN transfers can be resolved)."
              .formatted(transactionId, transfer.getState()));
    }
    return orchestrator.resolve(transfer);
  }

  private static void validateIdempotencyKey(String key) {
    if (key == null || key.isBlank() || key.length() < 8 || key.length() > 128) {
      throw new MissingIdempotencyKeyException(
          "Missing or invalid Idempotency-Key header: length must be between 8 and 128 characters.");
    }
  }

  private static void validateRequest(String networkId, CreateTransferRequest req) {
    if (networkId == null || networkId.isBlank()) {
      throw new InvalidTransferRequestException("networkId must not be blank.");
    }
    if (req == null) {
      throw new InvalidTransferRequestException("Transfer request body is required.");
    }
    if (req.from() == null
        || req.from().institutionId() == null
        || req.from().accountReference() == null) {
      throw new InvalidTransferRequestException(
          "'from' institutionId and accountReference are required.");
    }
    if (req.to() == null
        || req.to().institutionId() == null
        || req.to().accountReference() == null) {
      throw new InvalidTransferRequestException(
          "'to' institutionId and accountReference are required.");
    }
    if (req.from().institutionId().equals(req.to().institutionId())
        && req.from().accountReference().equals(req.to().accountReference())) {
      throw new InvalidTransferRequestException(
          "Source and destination accounts must not be identical.");
    }
    if (req.money() == null || req.money().amount() == null || req.money().currency() == null) {
      throw new InvalidTransferRequestException("'money' amount and currency are required.");
    }
    try {
      BigDecimal amt = new BigDecimal(req.money().amount());
      if (amt.signum() <= 0) {
        throw new InvalidTransferRequestException("Transfer amount must be strictly positive.");
      }
    } catch (NumberFormatException ex) {
      throw new InvalidTransferRequestException("Invalid decimal amount: " + req.money().amount());
    }
    if (req.reference() != null && req.reference().length() > 140) {
      throw new InvalidTransferRequestException("reference must not exceed 140 characters.");
    }
  }

  private static boolean matchesRequest(
      TransferEntity entity, String networkId, CreateTransferRequest req) {
    if (!entity.getNetworkId().equals(networkId)) {
      return false;
    }
    if (!entity.getFromInstitution().equals(req.from().institutionId())
        || !entity.getFromAccount().equals(req.from().accountReference())) {
      return false;
    }
    if (!entity.getToInstitution().equals(req.to().institutionId())
        || !entity.getToAccount().equals(req.to().accountReference())) {
      return false;
    }
    BigDecimal reqAmount = new BigDecimal(req.money().amount());
    if (entity.getAmount().compareTo(reqAmount) != 0) {
      return false;
    }
    if (!entity.getCurrency().equalsIgnoreCase(req.money().currency())) {
      return false;
    }
    return Objects.equals(entity.getReference(), req.reference());
  }
}
