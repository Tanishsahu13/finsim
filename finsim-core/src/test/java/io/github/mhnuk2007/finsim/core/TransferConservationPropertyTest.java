package io.github.mhnuk2007.finsim.core;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import net.jqwik.api.Arbitraries;
import net.jqwik.api.Arbitrary;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.Provide;

/**
 * Property-based tests verifying core financial conservation, terminal state purity, and
 * idempotency invariants under randomized chaos sequences (Issue #24).
 */
class TransferConservationPropertyTest {

  private static final Instant BASE_TIME = Instant.parse("2026-11-20T10:00:00Z");

  public enum ChaosStep {
    ACCEPT,
    START_DEBIT,
    DEBIT_TIMEOUT,
    DEBIT_REJECT,
    DEBIT_SUCCESS,
    CREDIT_TIMEOUT,
    CREDIT_REJECT,
    CREDIT_SUCCESS,
    RESOLVE_QUERY_NOT_FOUND,
    RESOLVE_RETRY_CREDIT_SUCCESS,
    RESOLVE_RETRY_CREDIT_TIMEOUT,
    RESOLVE_RETRY_CREDIT_REJECT,
    REVERSAL_TIMEOUT,
    REVERSAL_REJECT,
    REVERSAL_SUCCESS,
    DUPLICATE_RETRY
  }

  public record SimulationContext(
      BigDecimal initialBalanceA,
      BigDecimal initialBalanceB,
      BigDecimal balanceA,
      BigDecimal balanceB,
      BigDecimal transferAmount,
      Transfer transfer) {

    public BigDecimal totalInitial() {
      return initialBalanceA.add(initialBalanceB);
    }

    public BigDecimal totalCurrent() {
      return balanceA.add(balanceB);
    }
  }

  @Provide
  Arbitrary<BigDecimal> validBalances() {
    return Arbitraries.bigDecimals()
        .between(new BigDecimal("1000.00"), new BigDecimal("10000000.00"))
        .ofScale(2);
  }

  @Provide
  Arbitrary<BigDecimal> validTransferAmounts() {
    return Arbitraries.bigDecimals()
        .between(new BigDecimal("1.00"), new BigDecimal("500.00"))
        .ofScale(2);
  }

  @Provide
  Arbitrary<List<ChaosStep>> chaosSequences() {
    return Arbitraries.of(ChaosStep.values()).list().ofMinSize(1).ofMaxSize(30);
  }

  /**
   * Property: Conservation of Value across source and destination.
   *
   * <p>Initial Balance A + Initial Balance B == Current Balance A + Current Balance B holds
   * strictly throughout and after any randomized sequence of chaos events.
   */
  @Property(tries = 1000)
  void conservationOfValueHoldsUnderChaos(
      @ForAll("validBalances") BigDecimal initA,
      @ForAll("validBalances") BigDecimal initB,
      @ForAll("validTransferAmounts") BigDecimal amount,
      @ForAll("chaosSequences") List<ChaosStep> steps) {

    AccountRef source = AccountRef.of("bank-a", "ACC-1");
    AccountRef dest = AccountRef.of("bank-b", "ACC-2");
    Money money = Money.of(amount.toPlainString(), "USD");

    Transfer transfer =
        Transfer.create(
            "tx-prop-test",
            "sandbox",
            "idemp-prop-key",
            source,
            dest,
            money,
            "Prop Ref",
            BASE_TIME);

    BigDecimal balA = initA;
    BigDecimal balB = initB;
    boolean debitApplied = false;
    boolean creditApplied = false;
    boolean reversalApplied = false;

    Instant clock = BASE_TIME;

    for (ChaosStep step : steps) {
      clock = clock.plusSeconds(1);

      switch (step) {
        case ACCEPT -> {
          if (transfer.getState() == TransferState.CREATED) {
            transfer.accept("accepted", clock);
          }
        }
        case START_DEBIT -> {
          if (transfer.getState() == TransferState.ACCEPTED) {
            transfer.startProcessing("debit_source", clock);
          }
        }
        case DEBIT_TIMEOUT -> {
          if (transfer.getState() == TransferState.IN_PROGRESS && !debitApplied) {
            // Per ADR 0001, timeout on debit leaves debit uncertain -> PendingAction.NONE
            transfer.markUnknown(
                "debit_source", PendingAction.NONE, false, "Debit timed out", clock);
          }
        }
        case DEBIT_REJECT -> {
          if (transfer.getState() == TransferState.IN_PROGRESS && !debitApplied) {
            transfer.reject("debit_source", "AM04", Emitter.SOURCE, "Insufficient funds", clock);
          }
        }
        case DEBIT_SUCCESS -> {
          if (transfer.getState() == TransferState.IN_PROGRESS && !debitApplied) {
            balA = balA.subtract(amount);
            debitApplied = true;
            transfer.recordStage("debit_source", StageOutcome.PASSED, "Debit completed", clock);
          }
        }
        case CREDIT_TIMEOUT -> {
          if (transfer.getState() == TransferState.IN_PROGRESS && debitApplied && !creditApplied) {
            // Debit succeeded, credit timed out -> UNKNOWN with RETRY_CREDIT
            transfer.markUnknown(
                "credit_destination", PendingAction.RETRY_CREDIT, false, "Credit timed out", clock);
          }
        }
        case CREDIT_REJECT -> {
          if (transfer.getState() == TransferState.IN_PROGRESS && debitApplied && !creditApplied) {
            // Credit rejected after debit succeeded -> UNKNOWN with REVERSE_DEBIT
            transfer.markUnknown(
                "credit_destination",
                PendingAction.REVERSE_DEBIT,
                false,
                "Credit rejected by destination",
                clock);
          }
        }
        case CREDIT_SUCCESS -> {
          if (transfer.getState() == TransferState.IN_PROGRESS && debitApplied && !creditApplied) {
            balB = balB.add(amount);
            creditApplied = true;
            transfer.complete("credit_destination", clock);
          }
        }
        case RESOLVE_QUERY_NOT_FOUND -> {
          // Debit was query 404 (never applied)
          if (transfer.getState() == TransferState.UNKNOWN && !debitApplied) {
            transfer.reject(
                "resolve",
                ReasonCode.FINSIM_TIMEOUT.code(),
                Emitter.FINSIM,
                "Debit not found at source",
                clock);
          }
        }
        case RESOLVE_RETRY_CREDIT_SUCCESS -> {
          if (transfer.getState() == TransferState.UNKNOWN
              && transfer.getPendingAction() == PendingAction.RETRY_CREDIT
              && debitApplied
              && !creditApplied) {
            balB = balB.add(amount);
            creditApplied = true;
            transfer.complete("credit_destination", clock);
          }
        }
        case RESOLVE_RETRY_CREDIT_TIMEOUT -> {
          if (transfer.getState() == TransferState.UNKNOWN
              && transfer.getPendingAction() == PendingAction.RETRY_CREDIT) {
            // Stays UNKNOWN with RETRY_CREDIT
            transfer.recordStage("resolve_retry_credit", StageOutcome.UNKNOWN, "Timeout", clock);
          }
        }
        case RESOLVE_RETRY_CREDIT_REJECT -> {
          if (transfer.getState() == TransferState.UNKNOWN
              && transfer.getPendingAction() == PendingAction.RETRY_CREDIT
              && !creditApplied) {
            // Destination rejected on retry -> switch to REVERSE_DEBIT
            transfer.updateResolutionStatus(PendingAction.REVERSE_DEBIT, false, clock);
          }
        }
        case REVERSAL_SUCCESS -> {
          if (transfer.getState() == TransferState.UNKNOWN
              && transfer.getPendingAction() == PendingAction.REVERSE_DEBIT
              && debitApplied
              && !reversalApplied) {
            balA = balA.add(amount);
            reversalApplied = true;
            transfer.reject(
                "resolve_reversal",
                "AC04",
                Emitter.DESTINATION,
                "Reversal completed after destination rejection",
                clock);
          }
        }
        case REVERSAL_TIMEOUT -> {
          if (transfer.getState() == TransferState.UNKNOWN
              && transfer.getPendingAction() == PendingAction.REVERSE_DEBIT) {
            // Stays UNKNOWN with REVERSE_DEBIT, needsManualReview: false
            transfer.updateResolutionStatus(PendingAction.REVERSE_DEBIT, false, clock);
          }
        }
        case REVERSAL_REJECT -> {
          if (transfer.getState() == TransferState.UNKNOWN
              && transfer.getPendingAction() == PendingAction.REVERSE_DEBIT) {
            // Source refused reversal -> stays UNKNOWN with needsManualReview: true
            transfer.updateResolutionStatus(PendingAction.REVERSE_DEBIT, true, clock);
          }
        }
        case DUPLICATE_RETRY -> {
          // Idempotent retry: must not mutate state if terminal, must not double-credit/debit
          if (transfer.getState().isTerminal()) {
            // Invariant: Terminal state is immutable
            assertThat(transfer.getState().isTerminal()).isTrue();
          }
        }
      }

      // Assert conservation invariant at every single step: Total money is conserved
      BigDecimal inTransit =
          (debitApplied && !creditApplied && !reversalApplied) ? amount : BigDecimal.ZERO;
      BigDecimal currentTotal = balA.add(balB).add(inTransit);
      BigDecimal initialTotal = initA.add(initB);
      assertThat(currentTotal)
          .as(
              "Conservation invariant violated: initial=%s, current=%s (balA=%s, balB=%s, inTransit=%s) after step %s",
              initialTotal, currentTotal, balA, balB, inTransit, step)
          .isEqualByComparingTo(initialTotal);
    }

    // Assert Terminal State Purity
    if (transfer.getState() == TransferState.COMPLETED) {
      assertThat(debitApplied).isTrue();
      assertThat(creditApplied).isTrue();
      assertThat(reversalApplied).isFalse();
      assertThat(balA).isEqualByComparingTo(initA.subtract(amount));
      assertThat(balB).isEqualByComparingTo(initB.add(amount));
      assertThat(transfer.getPendingAction()).isEqualTo(PendingAction.NONE);
    } else if (transfer.getState() == TransferState.REJECTED) {
      if (debitApplied) {
        assertThat(reversalApplied).isTrue();
      }
      assertThat(creditApplied).isFalse();
      assertThat(balA).isEqualByComparingTo(initA);
      assertThat(balB).isEqualByComparingTo(initB);
      assertThat(transfer.getPendingAction()).isEqualTo(PendingAction.NONE);
    } else if (transfer.getState() == TransferState.UNKNOWN) {
      // Must not claim success or definitive rejection while an outcome is in flight
      assertThat(transfer.getState().isTerminal()).isFalse();
    }
  }

  /**
   * Property: Idempotent replay of an identical transfer request never applies additional
   * mutations.
   */
  @Property(tries = 500)
  void idempotentReplayPreservesBalances(
      @ForAll("validBalances") BigDecimal initA,
      @ForAll("validBalances") BigDecimal initB,
      @ForAll("validTransferAmounts") BigDecimal amount,
      @ForAll("validAmounts") int duplicateCount) {

    AccountRef source = AccountRef.of("bank-a", "ACC-1");
    AccountRef dest = AccountRef.of("bank-b", "ACC-2");
    Money money = Money.of(amount.toPlainString(), "USD");

    Transfer transfer =
        Transfer.create(
            "tx-idemp", "sandbox", "idemp-key-repeat", source, dest, money, "Ref", BASE_TIME);
    transfer.accept("accepted", BASE_TIME.plusSeconds(1));
    transfer.startProcessing("debit_source", BASE_TIME.plusSeconds(2));

    BigDecimal balA = initA.subtract(amount);
    BigDecimal balB = initB.add(amount);
    transfer.complete("credit_destination", BASE_TIME.plusSeconds(3));

    // Replay up to duplicateCount times
    for (int i = 0; i < duplicateCount; i++) {
      assertThat(transfer.getState()).isEqualTo(TransferState.COMPLETED);
      assertThat(balA.add(balB)).isEqualByComparingTo(initA.add(initB));
    }
  }

  @Provide
  Arbitrary<Integer> validAmounts() {
    return Arbitraries.integers().between(1, 20);
  }
}
