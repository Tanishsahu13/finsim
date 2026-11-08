package io.github.mhnuk2007.finsim.core;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class TransferTest {

  private static final AccountRef SOURCE = AccountRef.of("bank-a", "ACC-1");
  private static final AccountRef DEST = AccountRef.of("bank-b", "ACC-2");
  private static final Money AMOUNT = Money.of("100.00", "USD");

  private Transfer createStandardTransfer() {
    return Transfer.create(
        "tx-123", "sandbox", "idemp-key-12345", SOURCE, DEST, AMOUNT, "Payment reference");
  }

  @Nested
  @DisplayName("Creation and Invariant Validation")
  class CreationTests {

    @Test
    @DisplayName("Creates transfer with valid parameters and initial CREATED state")
    void validCreation() {
      Instant now = Instant.now();
      Transfer transfer =
          Transfer.create("tx-1", "sandbox", "idemp-key-001", SOURCE, DEST, AMOUNT, "Ref 123", now);

      assertThat(transfer.getId()).isEqualTo("tx-1");
      assertThat(transfer.getNetworkId()).isEqualTo("sandbox");
      assertThat(transfer.getIdempotencyKey()).isEqualTo("idemp-key-001");
      assertThat(transfer.getFrom()).isEqualTo(SOURCE);
      assertThat(transfer.getTo()).isEqualTo(DEST);
      assertThat(transfer.getMoney()).isEqualTo(AMOUNT);
      assertThat(transfer.getReference()).isEqualTo("Ref 123");
      assertThat(transfer.getState()).isEqualTo(TransferState.CREATED);
      assertThat(transfer.getCurrentStage()).isEqualTo("created");
      assertThat(transfer.getProvenance()).isEqualTo(Provenance.SYNTHETIC);
      assertThat(transfer.getPendingAction()).isEqualTo(PendingAction.NONE);
      assertThat(transfer.isNeedsManualReview()).isFalse();
      assertThat(transfer.getReasonCode()).isNull();
      assertThat(transfer.getEmittedBy()).isNull();
      assertThat(transfer.getCreatedAt()).isEqualTo(now);
      assertThat(transfer.getUpdatedAt()).isEqualTo(now);
      assertThat(transfer.getVersion()).isZero();

      // Check initial stage entry
      List<StageEntry> stages = transfer.getStageHistory();
      assertThat(stages).hasSize(1);
      assertThat(stages.get(0).stage()).isEqualTo("created");
      assertThat(stages.get(0).outcome()).isEqualTo(StageOutcome.PASSED);
      assertThat(stages.get(0).at()).isEqualTo(now);
    }

    @Test
    @DisplayName("Rejects null or blank id")
    void rejectsInvalidId() {
      assertThatThrownBy(
              () -> Transfer.create(null, "sandbox", "idemp-key-001", SOURCE, DEST, AMOUNT, "Ref"))
          .isInstanceOf(NullPointerException.class);
      assertThatThrownBy(
              () -> Transfer.create("  ", "sandbox", "idemp-key-001", SOURCE, DEST, AMOUNT, "Ref"))
          .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("Rejects null or blank networkId")
    void rejectsInvalidNetworkId() {
      assertThatThrownBy(
              () -> Transfer.create("tx-1", null, "idemp-key-001", SOURCE, DEST, AMOUNT, "Ref"))
          .isInstanceOf(NullPointerException.class);
      assertThatThrownBy(
              () -> Transfer.create("tx-1", " ", "idemp-key-001", SOURCE, DEST, AMOUNT, "Ref"))
          .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("Rejects idempotency key with length outside 8..128")
    void rejectsInvalidIdempotencyKey() {
      assertThatThrownBy(
              () -> Transfer.create("tx-1", "sandbox", null, SOURCE, DEST, AMOUNT, "Ref"))
          .isInstanceOf(NullPointerException.class);
      assertThatThrownBy(
              () -> Transfer.create("tx-1", "sandbox", "short", SOURCE, DEST, AMOUNT, "Ref"))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining("between 8 and 128");
      assertThatThrownBy(
              () ->
                  Transfer.create("tx-1", "sandbox", "a".repeat(129), SOURCE, DEST, AMOUNT, "Ref"))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining("between 8 and 128");
    }

    @Test
    @DisplayName("Rejects null or identical accounts")
    void rejectsInvalidAccounts() {
      assertThatThrownBy(
              () -> Transfer.create("tx-1", "sandbox", "idemp-key-001", null, DEST, AMOUNT, "Ref"))
          .isInstanceOf(NullPointerException.class);
      assertThatThrownBy(
              () ->
                  Transfer.create("tx-1", "sandbox", "idemp-key-001", SOURCE, null, AMOUNT, "Ref"))
          .isInstanceOf(NullPointerException.class);

      AccountRef sameAccount = AccountRef.of("bank-a", "ACC-1");
      assertThatThrownBy(
              () ->
                  Transfer.create(
                      "tx-1", "sandbox", "idemp-key-001", sameAccount, sameAccount, AMOUNT, "Ref"))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining("must not be identical");
    }

    @Test
    @DisplayName("Rejects zero or negative amount")
    void rejectsZeroOrNegativeAmount() {
      assertThatThrownBy(
              () ->
                  Transfer.create(
                      "tx-1",
                      "sandbox",
                      "idemp-key-001",
                      SOURCE,
                      DEST,
                      Money.of("0.00", "USD"),
                      "Ref"))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining("strictly positive");
    }

    @Test
    @DisplayName("Rejects reference exceeding 140 characters")
    void rejectsLongReference() {
      assertThatThrownBy(
              () ->
                  Transfer.create(
                      "tx-1", "sandbox", "idemp-key-001", SOURCE, DEST, AMOUNT, "a".repeat(141)))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining("reference must not exceed 140 characters");
    }
  }

  @Nested
  @DisplayName("Append-Only Stage History")
  class StageHistoryTests {

    @Test
    @DisplayName("Stage history cannot be modified through the getter")
    void unmodifiableStageHistory() {
      Transfer transfer = createStandardTransfer();
      List<StageEntry> history = transfer.getStageHistory();

      assertThatThrownBy(
              () -> history.add(StageEntry.of("hacked", StageOutcome.PASSED, Instant.now())))
          .isInstanceOf(UnsupportedOperationException.class);
      assertThatThrownBy(history::clear).isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    @DisplayName("Stage history appends entries in sequential order")
    void appendOnlySequence() {
      Transfer transfer = createStandardTransfer();
      Instant t1 = Instant.parse("2026-11-08T10:00:00Z");
      Instant t2 = Instant.parse("2026-11-08T10:00:01Z");
      Instant t3 = Instant.parse("2026-11-08T10:00:02Z");

      transfer.accept("validation", t1);
      transfer.startProcessing("debit_source", t2);
      transfer.recordStage("debit_source", StageOutcome.PASSED, "Source debited", t3);

      List<StageEntry> stages = transfer.getStageHistory();
      assertThat(stages).hasSize(4);
      assertThat(stages.get(0).stage()).isEqualTo("created");
      assertThat(stages.get(1).stage()).isEqualTo("validation");
      assertThat(stages.get(1).at()).isEqualTo(t1);
      assertThat(stages.get(2).stage()).isEqualTo("debit_source");
      assertThat(stages.get(2).at()).isEqualTo(t2);
      assertThat(stages.get(3).stage()).isEqualTo("debit_source");
      assertThat(stages.get(3).details()).isEqualTo("Source debited");
      assertThat(stages.get(3).at()).isEqualTo(t3);
      assertThat(transfer.getCurrentStage()).isEqualTo("debit_source");
      assertThat(transfer.getUpdatedAt()).isEqualTo(t3);
    }
  }

  @Nested
  @DisplayName("Allowed State Transitions")
  class AllowedTransitionsTests {

    @Test
    @DisplayName("Happy path: CREATED -> ACCEPTED -> IN_PROGRESS -> COMPLETED")
    void happyPath() {
      Transfer transfer = createStandardTransfer();

      transfer.accept("network_validation");
      assertThat(transfer.getState()).isEqualTo(TransferState.ACCEPTED);

      transfer.startProcessing("debit_source");
      assertThat(transfer.getState()).isEqualTo(TransferState.IN_PROGRESS);

      transfer.complete("credit_destination");
      assertThat(transfer.getState()).isEqualTo(TransferState.COMPLETED);
      assertThat(transfer.getState().isTerminal()).isTrue();
      assertThat(transfer.getPendingAction()).isEqualTo(PendingAction.NONE);
    }

    @Test
    @DisplayName("Direct rejection: CREATED -> REJECTED")
    void directRejection() {
      Transfer transfer = createStandardTransfer();

      transfer.reject(
          "validation",
          ReasonCode.CAPABILITY_NOT_SUPPORTED.code(),
          Emitter.FINSIM,
          "Lacks capability");
      assertThat(transfer.getState()).isEqualTo(TransferState.REJECTED);
      assertThat(transfer.getState().isTerminal()).isTrue();
      assertThat(transfer.getReasonCode()).isEqualTo(ReasonCode.CAPABILITY_NOT_SUPPORTED.code());
      assertThat(transfer.getEmittedBy()).isEqualTo(Emitter.FINSIM);
    }

    @Test
    @DisplayName("Routing rejection: CREATED -> ACCEPTED -> REJECTED")
    void preRoutingRejection() {
      Transfer transfer = createStandardTransfer();

      transfer.accept("validation");
      transfer.reject("route_check", ReasonCode.MS03.code(), Emitter.FINSIM, "Routing failed");
      assertThat(transfer.getState()).isEqualTo(TransferState.REJECTED);
    }

    @Test
    @DisplayName("In-progress rejection: CREATED -> ACCEPTED -> IN_PROGRESS -> REJECTED")
    void inProgressRejection() {
      Transfer transfer = createStandardTransfer();

      transfer.accept("validation");
      transfer.startProcessing("debit_source");
      transfer.reject("debit_source", ReasonCode.AM04.code(), Emitter.SOURCE, "Insufficient funds");

      assertThat(transfer.getState()).isEqualTo(TransferState.REJECTED);
      assertThat(transfer.getReasonCode()).isEqualTo("AM04");
      assertThat(transfer.getEmittedBy()).isEqualTo(Emitter.SOURCE);
    }

    @Test
    @DisplayName("Timeout and resolution: IN_PROGRESS -> UNKNOWN -> COMPLETED")
    void unknownToCompleted() {
      Transfer transfer = createStandardTransfer();

      transfer.accept("validation");
      transfer.startProcessing("credit_destination");
      transfer.markUnknown(
          "credit_destination", PendingAction.RETRY_CREDIT, false, "Timeout on credit");

      assertThat(transfer.getState()).isEqualTo(TransferState.UNKNOWN);
      assertThat(transfer.getPendingAction()).isEqualTo(PendingAction.RETRY_CREDIT);
      assertThat(transfer.isNeedsManualReview()).isFalse();

      // Query determines credit actually completed
      transfer.complete("resolve_query");
      assertThat(transfer.getState()).isEqualTo(TransferState.COMPLETED);
      assertThat(transfer.getPendingAction()).isEqualTo(PendingAction.NONE);
    }

    @Test
    @DisplayName("Timeout and resolution: IN_PROGRESS -> UNKNOWN -> REJECTED")
    void unknownToRejected() {
      Transfer transfer = createStandardTransfer();

      transfer.accept("validation");
      transfer.startProcessing("debit_source");
      transfer.markUnknown("debit_source", PendingAction.NONE, false, "Timeout on debit");

      // Query determines debit was never applied (404)
      transfer.reject("resolve_query", ReasonCode.FINSIM_TIMEOUT.code(), Emitter.FINSIM);
      assertThat(transfer.getState()).isEqualTo(TransferState.REJECTED);
      assertThat(transfer.getReasonCode()).isEqualTo(ReasonCode.FINSIM_TIMEOUT.code());
      assertThat(transfer.getEmittedBy()).isEqualTo(Emitter.FINSIM);
    }

    @Test
    @DisplayName("Resolution recovery flow: UNKNOWN -> IN_PROGRESS -> COMPLETED")
    void unknownToInProgressToCompleted() {
      Transfer transfer = createStandardTransfer();

      transfer.accept("validation");
      transfer.startProcessing("credit_destination");
      transfer.markUnknown("credit_destination", PendingAction.RETRY_CREDIT);

      // Resolve starts retry
      transfer.startProcessing("retry_credit");
      assertThat(transfer.getState()).isEqualTo(TransferState.IN_PROGRESS);

      // Retry succeeds
      transfer.complete("retry_credit");
      assertThat(transfer.getState()).isEqualTo(TransferState.COMPLETED);
    }

    @Test
    @DisplayName("Resolution reversal flow: UNKNOWN -> IN_PROGRESS -> REJECTED")
    void unknownToInProgressToRejected() {
      Transfer transfer = createStandardTransfer();

      transfer.accept("validation");
      transfer.startProcessing("credit_destination");
      transfer.markUnknown("credit_destination", PendingAction.REVERSE_DEBIT);

      // Reversal starts
      transfer.startProcessing("reverse_debit");
      assertThat(transfer.getState()).isEqualTo(TransferState.IN_PROGRESS);

      // Reversal completes, transfer is definitively rejected with destination's reason code
      transfer.reject(
          "reverse_debit",
          ReasonCode.AC04.code(),
          Emitter.DESTINATION,
          "Account closed, debit reversed");
      assertThat(transfer.getState()).isEqualTo(TransferState.REJECTED);
      assertThat(transfer.getReasonCode()).isEqualTo("AC04");
      assertThat(transfer.getEmittedBy()).isEqualTo(Emitter.DESTINATION);
    }

    @Test
    @DisplayName("Resolution re-timeout: IN_PROGRESS -> UNKNOWN -> IN_PROGRESS -> UNKNOWN")
    void resolutionReTimeoutLoop() {
      Transfer transfer = createStandardTransfer();

      transfer.accept("validation");
      transfer.startProcessing("credit_destination");
      transfer.markUnknown("credit_destination", PendingAction.RETRY_CREDIT);

      // Re-send credit
      transfer.startProcessing("retry_credit");
      assertThat(transfer.getState()).isEqualTo(TransferState.IN_PROGRESS);

      // Re-send credit times out again
      transfer.markUnknown("retry_credit", PendingAction.RETRY_CREDIT, false, "Second timeout");
      assertThat(transfer.getState()).isEqualTo(TransferState.UNKNOWN);
    }
  }

  @Nested
  @DisplayName("Forbidden State Transitions")
  class ForbiddenTransitionsTests {

    @Test
    @DisplayName("Cannot transition out of terminal state COMPLETED")
    void cannotTransitionFromCompleted() {
      Transfer transfer = createStandardTransfer();
      transfer.accept("v");
      transfer.startProcessing("d");
      transfer.complete("c");

      assertThatThrownBy(() -> transfer.accept("v"))
          .isInstanceOf(IllegalStateTransitionException.class)
          .hasMessageContaining("COMPLETED to ACCEPTED");

      assertThatThrownBy(() -> transfer.startProcessing("d"))
          .isInstanceOf(IllegalStateTransitionException.class)
          .hasMessageContaining("COMPLETED to IN_PROGRESS");

      assertThatThrownBy(() -> transfer.complete("c"))
          .isInstanceOf(IllegalStateTransitionException.class)
          .hasMessageContaining("COMPLETED to COMPLETED");

      assertThatThrownBy(() -> transfer.reject("r", ReasonCode.MS03.code(), Emitter.FINSIM))
          .isInstanceOf(IllegalStateTransitionException.class)
          .hasMessageContaining("COMPLETED to REJECTED");

      assertThatThrownBy(() -> transfer.markUnknown("u", PendingAction.NONE))
          .isInstanceOf(IllegalStateTransitionException.class)
          .hasMessageContaining("COMPLETED to UNKNOWN");
    }

    @Test
    @DisplayName("Cannot transition out of terminal state REJECTED")
    void cannotTransitionFromRejected() {
      Transfer transfer = createStandardTransfer();
      transfer.reject("v", ReasonCode.AC01.code(), Emitter.SOURCE);

      assertThatThrownBy(() -> transfer.accept("v"))
          .isInstanceOf(IllegalStateTransitionException.class)
          .hasMessageContaining("REJECTED to ACCEPTED");

      assertThatThrownBy(() -> transfer.startProcessing("d"))
          .isInstanceOf(IllegalStateTransitionException.class)
          .hasMessageContaining("REJECTED to IN_PROGRESS");

      assertThatThrownBy(() -> transfer.complete("c"))
          .isInstanceOf(IllegalStateTransitionException.class)
          .hasMessageContaining("REJECTED to COMPLETED");

      assertThatThrownBy(() -> transfer.reject("r", ReasonCode.MS03.code(), Emitter.FINSIM))
          .isInstanceOf(IllegalStateTransitionException.class)
          .hasMessageContaining("REJECTED to REJECTED");

      assertThatThrownBy(() -> transfer.markUnknown("u", PendingAction.NONE))
          .isInstanceOf(IllegalStateTransitionException.class)
          .hasMessageContaining("REJECTED to UNKNOWN");
    }

    @Test
    @DisplayName("Cannot jump from CREATED to COMPLETED or UNKNOWN")
    void forbiddenFromCreated() {
      Transfer transfer = createStandardTransfer();

      assertThatThrownBy(() -> transfer.complete("c"))
          .isInstanceOf(IllegalStateTransitionException.class)
          .hasMessageContaining("CREATED to COMPLETED");

      assertThatThrownBy(() -> transfer.markUnknown("u", PendingAction.NONE))
          .isInstanceOf(IllegalStateTransitionException.class)
          .hasMessageContaining("CREATED to UNKNOWN");

      assertThatThrownBy(() -> transfer.startProcessing("p"))
          .isInstanceOf(IllegalStateTransitionException.class)
          .hasMessageContaining("CREATED to IN_PROGRESS");
    }

    @Test
    @DisplayName("Cannot jump from ACCEPTED to COMPLETED or UNKNOWN")
    void forbiddenFromAccepted() {
      Transfer transfer = createStandardTransfer();
      transfer.accept("v");

      assertThatThrownBy(() -> transfer.complete("c"))
          .isInstanceOf(IllegalStateTransitionException.class)
          .hasMessageContaining("ACCEPTED to COMPLETED");

      assertThatThrownBy(() -> transfer.markUnknown("u", PendingAction.NONE))
          .isInstanceOf(IllegalStateTransitionException.class)
          .hasMessageContaining("ACCEPTED to UNKNOWN");

      assertThatThrownBy(() -> transfer.accept("v2"))
          .isInstanceOf(IllegalStateTransitionException.class)
          .hasMessageContaining("ACCEPTED to ACCEPTED");
    }

    @Test
    @DisplayName("Cannot move backward from IN_PROGRESS to ACCEPTED")
    void cannotMoveBackwardFromInProgress() {
      Transfer transfer = createStandardTransfer();
      transfer.accept("v");
      transfer.startProcessing("d");

      assertThatThrownBy(() -> transfer.accept("v"))
          .isInstanceOf(IllegalStateTransitionException.class)
          .hasMessageContaining("IN_PROGRESS to ACCEPTED");

      assertThatThrownBy(() -> transfer.startProcessing("d2"))
          .isInstanceOf(IllegalStateTransitionException.class)
          .hasMessageContaining("IN_PROGRESS to IN_PROGRESS");
    }

    @Test
    @DisplayName("Cannot move backward from UNKNOWN to ACCEPTED")
    void cannotMoveBackwardFromUnknown() {
      Transfer transfer = createStandardTransfer();
      transfer.accept("v");
      transfer.startProcessing("d");
      transfer.markUnknown("d", PendingAction.NONE);

      assertThatThrownBy(() -> transfer.accept("v"))
          .isInstanceOf(IllegalStateTransitionException.class)
          .hasMessageContaining("UNKNOWN to ACCEPTED");
    }
  }

  @Nested
  @DisplayName("Rejection and Resolution Validation")
  class RejectionAndResolutionTests {

    @Test
    @DisplayName("Reject requires non-null, non-blank reasonCode and non-null emittedBy")
    void rejectValidation() {
      Transfer transfer = createStandardTransfer();

      assertThatThrownBy(() -> transfer.reject("stage", null, Emitter.SOURCE))
          .isInstanceOf(NullPointerException.class)
          .hasMessageContaining("reasonCode");

      assertThatThrownBy(() -> transfer.reject("stage", "   ", Emitter.SOURCE))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining("reasonCode must not be blank");

      assertThatThrownBy(() -> transfer.reject("stage", "AC01", null))
          .isInstanceOf(NullPointerException.class)
          .hasMessageContaining("emittedBy");
    }

    @Test
    @DisplayName("Resolution status can be updated while UNKNOWN")
    void updateResolutionStatusWhileUnknown() {
      Transfer transfer = createStandardTransfer();
      transfer.accept("v");
      transfer.startProcessing("credit");
      transfer.markUnknown("credit", PendingAction.RETRY_CREDIT, false);

      transfer.updateResolutionStatus(PendingAction.REVERSE_DEBIT, true);
      assertThat(transfer.getPendingAction()).isEqualTo(PendingAction.REVERSE_DEBIT);
      assertThat(transfer.isNeedsManualReview()).isTrue();
    }

    @Test
    @DisplayName("Updating resolution status throws when not in UNKNOWN state")
    void updateResolutionStatusThrowsWhenNotUnknown() {
      Transfer transfer = createStandardTransfer();
      assertThatThrownBy(() -> transfer.updateResolutionStatus(PendingAction.RETRY_CREDIT, false))
          .isInstanceOf(IllegalStateException.class)
          .hasMessageContaining("not in UNKNOWN state");
    }
  }

  @Nested
  @DisplayName("Reconstitution Tests")
  class ReconstitutionTests {

    @Test
    @DisplayName("Reconstitutes existing transfer with all historical fields without throwing")
    void reconstituteExistingTransfer() {
      Instant created = Instant.parse("2026-11-08T09:00:00Z");
      Instant updated = Instant.parse("2026-11-08T09:05:00Z");
      List<StageEntry> stages =
          List.of(
              StageEntry.of("created", StageOutcome.PASSED, created),
              StageEntry.of("validation", StageOutcome.PASSED, created.plusSeconds(10)),
              StageEntry.of("debit", StageOutcome.PASSED, created.plusSeconds(30)),
              StageEntry.of("credit", StageOutcome.UNKNOWN, updated, "Timeout"));

      Transfer transfer =
          Transfer.reconstitute(
              "tx-persisted",
              "sandbox",
              "idemp-key-persisted",
              TransferState.UNKNOWN,
              "credit",
              SOURCE,
              DEST,
              AMOUNT,
              "Persisted transfer",
              null,
              null,
              PendingAction.RETRY_CREDIT,
              true,
              Provenance.SYNTHETIC,
              stages,
              created,
              updated,
              4L);

      assertThat(transfer.getId()).isEqualTo("tx-persisted");
      assertThat(transfer.getState()).isEqualTo(TransferState.UNKNOWN);
      assertThat(transfer.getCurrentStage()).isEqualTo("credit");
      assertThat(transfer.getPendingAction()).isEqualTo(PendingAction.RETRY_CREDIT);
      assertThat(transfer.isNeedsManualReview()).isTrue();
      assertThat(transfer.getStageHistory()).hasSize(4);
      assertThat(transfer.getVersion()).isEqualTo(4L);
      assertThat(transfer.getCreatedAt()).isEqualTo(created);
      assertThat(transfer.getUpdatedAt()).isEqualTo(updated);
    }
  }
}
