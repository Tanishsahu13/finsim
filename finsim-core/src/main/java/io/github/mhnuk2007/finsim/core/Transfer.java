package io.github.mhnuk2007.finsim.core;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * The Transfer aggregate root.
 *
 * <p>Enforces state machine invariants, idempotency key validity, positive money amounts, distinct
 * source and destination accounts, and an append-only stage history.
 */
public class Transfer {

  private static final int MIN_IDEMPOTENCY_KEY_LENGTH = 8;
  private static final int MAX_IDEMPOTENCY_KEY_LENGTH = 128;
  private static final int MAX_REFERENCE_LENGTH = 140;

  private final String id;
  private final String networkId;
  private final String idempotencyKey;
  private final AccountRef from;
  private final AccountRef to;
  private final Money money;
  private final String reference;
  private final Provenance provenance;
  private final Instant createdAt;

  private TransferState state;
  private String currentStage;
  private String reasonCode;
  private Emitter emittedBy;
  private PendingAction pendingAction;
  private boolean needsManualReview;
  private final List<StageEntry> stageHistory;
  private Instant updatedAt;
  private long version;

  private Transfer(
      String id,
      String networkId,
      String idempotencyKey,
      TransferState state,
      String currentStage,
      AccountRef from,
      AccountRef to,
      Money money,
      String reference,
      String reasonCode,
      Emitter emittedBy,
      PendingAction pendingAction,
      boolean needsManualReview,
      Provenance provenance,
      List<StageEntry> stageHistory,
      Instant createdAt,
      Instant updatedAt,
      long version) {
    this.id = id;
    this.networkId = networkId;
    this.idempotencyKey = idempotencyKey;
    this.state = state;
    this.currentStage = currentStage;
    this.from = from;
    this.to = to;
    this.money = money;
    this.reference = reference;
    this.reasonCode = reasonCode;
    this.emittedBy = emittedBy;
    this.pendingAction = pendingAction != null ? pendingAction : PendingAction.NONE;
    this.needsManualReview = needsManualReview;
    this.provenance = provenance != null ? provenance : Provenance.SYNTHETIC;
    this.stageHistory = new ArrayList<>(stageHistory != null ? stageHistory : List.of());
    this.createdAt = createdAt;
    this.updatedAt = updatedAt;
    this.version = version;
  }

  /** Creates a new Transfer in the {@link TransferState#CREATED} state. */
  public static Transfer create(
      String id,
      String networkId,
      String idempotencyKey,
      AccountRef from,
      AccountRef to,
      Money money,
      String reference,
      Instant createdAt) {
    validateCreationParameters(id, networkId, idempotencyKey, from, to, money, reference);

    Instant creationTime = createdAt != null ? createdAt : Instant.now();
    StageEntry initialStage =
        StageEntry.of("created", StageOutcome.PASSED, creationTime, "Transfer created");
    List<StageEntry> initialHistory = List.of(initialStage);

    return new Transfer(
        id,
        networkId,
        idempotencyKey,
        TransferState.CREATED,
        "created",
        from,
        to,
        money,
        reference,
        null,
        null,
        PendingAction.NONE,
        false,
        Provenance.SYNTHETIC,
        initialHistory,
        creationTime,
        creationTime,
        0L);
  }

  /** Creates a new Transfer using the current system time as {@code createdAt}. */
  public static Transfer create(
      String id,
      String networkId,
      String idempotencyKey,
      AccountRef from,
      AccountRef to,
      Money money,
      String reference) {
    return create(id, networkId, idempotencyKey, from, to, money, reference, Instant.now());
  }

  /**
   * Reconstitutes an existing Transfer from persistence storage without re-running creation or
   * state-transition checks.
   */
  public static Transfer reconstitute(
      String id,
      String networkId,
      String idempotencyKey,
      TransferState state,
      String currentStage,
      AccountRef from,
      AccountRef to,
      Money money,
      String reference,
      String reasonCode,
      Emitter emittedBy,
      PendingAction pendingAction,
      boolean needsManualReview,
      Provenance provenance,
      List<StageEntry> stageHistory,
      Instant createdAt,
      Instant updatedAt,
      long version) {
    Objects.requireNonNull(id, "id must not be null");
    Objects.requireNonNull(networkId, "networkId must not be null");
    Objects.requireNonNull(idempotencyKey, "idempotencyKey must not be null");
    Objects.requireNonNull(state, "state must not be null");
    Objects.requireNonNull(from, "from must not be null");
    Objects.requireNonNull(to, "to must not be null");
    Objects.requireNonNull(money, "money must not be null");
    Objects.requireNonNull(createdAt, "createdAt must not be null");
    Objects.requireNonNull(updatedAt, "updatedAt must not be null");

    return new Transfer(
        id,
        networkId,
        idempotencyKey,
        state,
        currentStage,
        from,
        to,
        money,
        reference,
        reasonCode,
        emittedBy,
        pendingAction,
        needsManualReview,
        provenance,
        stageHistory,
        createdAt,
        updatedAt,
        version);
  }

  private static void validateCreationParameters(
      String id,
      String networkId,
      String idempotencyKey,
      AccountRef from,
      AccountRef to,
      Money money,
      String reference) {
    Objects.requireNonNull(id, "id must not be null");
    if (id.isBlank()) {
      throw new IllegalArgumentException("id must not be blank");
    }
    Objects.requireNonNull(networkId, "networkId must not be null");
    if (networkId.isBlank()) {
      throw new IllegalArgumentException("networkId must not be blank");
    }
    Objects.requireNonNull(idempotencyKey, "idempotencyKey must not be null");
    if (idempotencyKey.length() < MIN_IDEMPOTENCY_KEY_LENGTH
        || idempotencyKey.length() > MAX_IDEMPOTENCY_KEY_LENGTH) {
      throw new IllegalArgumentException(
          "idempotencyKey length must be between %d and %d characters: length=%d"
              .formatted(
                  MIN_IDEMPOTENCY_KEY_LENGTH, MAX_IDEMPOTENCY_KEY_LENGTH, idempotencyKey.length()));
    }
    Objects.requireNonNull(from, "from account must not be null");
    Objects.requireNonNull(to, "to account must not be null");
    if (from.equals(to)) {
      throw new IllegalArgumentException(
          "source and destination accounts must not be identical: %s".formatted(from));
    }
    Objects.requireNonNull(money, "money must not be null");
    if (money.amount().signum() <= 0) {
      throw new IllegalArgumentException(
          "transfer amount must be strictly positive: %s".formatted(money.amount()));
    }
    if (reference != null && reference.length() > MAX_REFERENCE_LENGTH) {
      throw new IllegalArgumentException(
          "reference must not exceed %d characters: length=%d"
              .formatted(MAX_REFERENCE_LENGTH, reference.length()));
    }
  }

  /** Accepts this transfer for network processing. Transitions {@code CREATED -> ACCEPTED}. */
  public synchronized void accept(String stage, Instant at) {
    transitionTo(TransferState.ACCEPTED);
    Instant timestamp = at != null ? at : Instant.now();
    this.currentStage = stage;
    this.updatedAt = timestamp;
    this.stageHistory.add(StageEntry.of(stage, StageOutcome.PASSED, timestamp));
  }

  public synchronized void accept(String stage) {
    accept(stage, Instant.now());
  }

  /**
   * Marks this transfer as currently executing payment legs. Transitions to {@code IN_PROGRESS}.
   */
  public synchronized void startProcessing(String stage, Instant at) {
    transitionTo(TransferState.IN_PROGRESS);
    Instant timestamp = at != null ? at : Instant.now();
    this.currentStage = stage;
    this.updatedAt = timestamp;
    this.stageHistory.add(StageEntry.of(stage, StageOutcome.PASSED, timestamp));
  }

  public synchronized void startProcessing(String stage) {
    startProcessing(stage, Instant.now());
  }

  /** Completes the transfer. Transitions to terminal state {@code COMPLETED}. */
  public synchronized void complete(String stage, Instant at) {
    transitionTo(TransferState.COMPLETED);
    Instant timestamp = at != null ? at : Instant.now();
    this.currentStage = stage;
    this.pendingAction = PendingAction.NONE;
    this.updatedAt = timestamp;
    this.stageHistory.add(StageEntry.of(stage, StageOutcome.PASSED, timestamp));
  }

  public synchronized void complete(String stage) {
    complete(stage, Instant.now());
  }

  /**
   * Rejects the transfer. Transitions to terminal state {@code REJECTED}.
   *
   * @param stage stage name
   * @param reasonCode rejection reason code (must not be null or blank)
   * @param emittedBy who rejected the transfer (must not be null)
   * @param details optional additional details
   * @param at timestamp
   */
  public synchronized void reject(
      String stage, String reasonCode, Emitter emittedBy, String details, Instant at) {
    Objects.requireNonNull(reasonCode, "reasonCode must not be null for REJECTED state");
    if (reasonCode.isBlank()) {
      throw new IllegalArgumentException("reasonCode must not be blank for REJECTED state");
    }
    Objects.requireNonNull(emittedBy, "emittedBy must not be null for REJECTED state");

    transitionTo(TransferState.REJECTED);
    Instant timestamp = at != null ? at : Instant.now();
    this.currentStage = stage;
    this.reasonCode = reasonCode;
    this.emittedBy = emittedBy;
    this.pendingAction = PendingAction.NONE;
    this.updatedAt = timestamp;
    this.stageHistory.add(
        StageEntry.of(
            stage, StageOutcome.FAILED, timestamp, details != null ? details : reasonCode));
  }

  public synchronized void reject(
      String stage, String reasonCode, Emitter emittedBy, String details) {
    reject(stage, reasonCode, emittedBy, details, Instant.now());
  }

  public synchronized void reject(String stage, String reasonCode, Emitter emittedBy) {
    reject(stage, reasonCode, emittedBy, null, Instant.now());
  }

  /** Marks the transfer outcome as ambiguous due to a timeout. Transitions to {@code UNKNOWN}. */
  public synchronized void markUnknown(
      String stage,
      PendingAction pendingAction,
      boolean needsManualReview,
      String details,
      Instant at) {
    transitionTo(TransferState.UNKNOWN);
    Instant timestamp = at != null ? at : Instant.now();
    this.currentStage = stage;
    this.pendingAction = pendingAction != null ? pendingAction : PendingAction.NONE;
    this.needsManualReview = needsManualReview;
    this.updatedAt = timestamp;
    this.stageHistory.add(StageEntry.of(stage, StageOutcome.UNKNOWN, timestamp, details));
  }

  public synchronized void markUnknown(
      String stage, PendingAction pendingAction, boolean needsManualReview, String details) {
    markUnknown(stage, pendingAction, needsManualReview, details, Instant.now());
  }

  public synchronized void markUnknown(
      String stage, PendingAction pendingAction, boolean needsManualReview) {
    markUnknown(stage, pendingAction, needsManualReview, null, Instant.now());
  }

  public synchronized void markUnknown(String stage, PendingAction pendingAction) {
    markUnknown(stage, pendingAction, false, null, Instant.now());
  }

  /**
   * Updates resolution status (pending action and manual review flag) while in the {@code UNKNOWN}
   * state.
   */
  public synchronized void updateResolutionStatus(
      PendingAction pendingAction, boolean needsManualReview, Instant at) {
    if (this.state != TransferState.UNKNOWN) {
      throw new IllegalStateException(
          "Cannot update resolution status when transfer is not in UNKNOWN state: current state is "
              + this.state);
    }
    this.pendingAction = pendingAction != null ? pendingAction : PendingAction.NONE;
    this.needsManualReview = needsManualReview;
    this.updatedAt = at != null ? at : Instant.now();
  }

  public synchronized void updateResolutionStatus(
      PendingAction pendingAction, boolean needsManualReview) {
    updateResolutionStatus(pendingAction, needsManualReview, Instant.now());
  }

  /** Appends an entry to the stage history without altering the transfer state. */
  public synchronized void recordStage(
      String stage, StageOutcome outcome, String details, Instant at) {
    Objects.requireNonNull(stage, "stage must not be null");
    Objects.requireNonNull(outcome, "outcome must not be null");
    Instant timestamp = at != null ? at : Instant.now();
    this.currentStage = stage;
    this.updatedAt = timestamp;
    this.stageHistory.add(StageEntry.of(stage, outcome, timestamp, details));
  }

  public synchronized void recordStage(String stage, StageOutcome outcome, String details) {
    recordStage(stage, outcome, details, Instant.now());
  }

  /** Performs and validates state transition. */
  private void transitionTo(TransferState targetState) {
    if (!this.state.canTransitionTo(targetState)) {
      throw new IllegalStateTransitionException(this.id, this.state, targetState);
    }
    this.state = targetState;
  }

  // Getters

  public String getId() {
    return id;
  }

  public String getNetworkId() {
    return networkId;
  }

  public String getIdempotencyKey() {
    return idempotencyKey;
  }

  public synchronized TransferState getState() {
    return state;
  }

  public synchronized String getCurrentStage() {
    return currentStage;
  }

  public AccountRef getFrom() {
    return from;
  }

  public AccountRef getTo() {
    return to;
  }

  public Money getMoney() {
    return money;
  }

  public String getReference() {
    return reference;
  }

  public synchronized String getReasonCode() {
    return reasonCode;
  }

  public synchronized Emitter getEmittedBy() {
    return emittedBy;
  }

  public synchronized PendingAction getPendingAction() {
    return pendingAction;
  }

  public synchronized boolean isNeedsManualReview() {
    return needsManualReview;
  }

  /** Returns an unmodifiable view of the append-only stage history. */
  public synchronized List<StageEntry> getStageHistory() {
    return Collections.unmodifiableList(new ArrayList<>(stageHistory));
  }

  public Provenance getProvenance() {
    return provenance;
  }

  public Instant getCreatedAt() {
    return createdAt;
  }

  public synchronized Instant getUpdatedAt() {
    return updatedAt;
  }

  public synchronized long getVersion() {
    return version;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (!(o instanceof Transfer other)) return false;
    return Objects.equals(id, other.id);
  }

  @Override
  public int hashCode() {
    return Objects.hash(id);
  }

  @Override
  public synchronized String toString() {
    return "Transfer{"
        + "id='"
        + id
        + '\''
        + ", networkId='"
        + networkId
        + '\''
        + ", state="
        + state
        + ", currentStage='"
        + currentStage
        + '\''
        + ", from="
        + from
        + ", to="
        + to
        + ", money="
        + money
        + ", reasonCode='"
        + reasonCode
        + '\''
        + ", emittedBy="
        + emittedBy
        + ", pendingAction="
        + pendingAction
        + ", needsManualReview="
        + needsManualReview
        + ", stages="
        + stageHistory.size()
        + '}';
  }
}
