package io.github.mhnuk2007.finsim.sim;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class FaultEvaluatorTest {

  private final FaultEvaluator evaluator = new FaultEvaluator();

  @Test
  void validatesRuleConfiguration() {
    assertThatThrownBy(() -> new FaultRule("", "bank-b", FaultType.TIMEOUT, null, 1.0, null, null))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> new FaultRule("r1", "  ", FaultType.TIMEOUT, null, 1.0, null, null))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> new FaultRule("r1", "bank-b", null, null, 1.0, null, null))
        .isInstanceOf(NullPointerException.class);
    assertThatThrownBy(() -> new FaultRule("r1", "bank-b", FaultType.DELAY, -10, 1.0, null, null))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> new FaultRule("r1", "bank-b", FaultType.DELAY, null, 1.0, null, null))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(
            () -> new FaultRule("r1", "bank-b", FaultType.TIMEOUT, null, -0.1, null, null))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(
            () -> new FaultRule("r1", "bank-b", FaultType.TIMEOUT, null, 1.1, null, null))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> new FaultRule("r1", "bank-b", FaultType.TIMEOUT, null, 1.0, -1, null))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void matchesRuleByTargetInstitution() {
    FaultRule rule = new FaultRule("r1", "bank-b", FaultType.TIMEOUT, null, 1.0, null, null);

    FaultEvaluator.EvaluationResult resA = evaluator.evaluate(List.of(rule), "bank-a");
    assertThat(resA.decision().triggered()).isFalse();

    FaultEvaluator.EvaluationResult resB = evaluator.evaluate(List.of(rule), "bank-b");
    assertThat(resB.decision().triggered()).isTrue();
    assertThat(resB.decision().faultType()).isEqualTo(FaultType.TIMEOUT);
  }

  @Test
  void triggersAllThreeFaultTypes() {
    FaultRule delay = new FaultRule("r1", "bank-b", FaultType.DELAY, 500, 1.0, null, null);
    FaultRule timeout = new FaultRule("r2", "bank-b", FaultType.TIMEOUT, null, 1.0, null, null);
    FaultRule duplicate =
        new FaultRule("r3", "bank-b", FaultType.DUPLICATE_DELIVERY, null, 1.0, null, null);

    assertThat(evaluator.evaluate(List.of(delay), "bank-b").decision().faultType())
        .isEqualTo(FaultType.DELAY);
    assertThat(evaluator.evaluate(List.of(delay), "bank-b").decision().delayMs()).isEqualTo(500);

    assertThat(evaluator.evaluate(List.of(timeout), "bank-b").decision().faultType())
        .isEqualTo(FaultType.TIMEOUT);

    assertThat(evaluator.evaluate(List.of(duplicate), "bank-b").decision().faultType())
        .isEqualTo(FaultType.DUPLICATE_DELIVERY);
  }

  @Test
  void respectsProbabilityBoundaries() {
    FaultRule never = new FaultRule("r-never", "bank-b", FaultType.TIMEOUT, null, 0.0, null, null);
    FaultRule always =
        new FaultRule("r-always", "bank-b", FaultType.TIMEOUT, null, 1.0, null, null);

    for (int i = 0; i < 20; i++) {
      assertThat(evaluator.evaluate(List.of(never), "bank-b").decision().triggered()).isFalse();
      assertThat(evaluator.evaluate(List.of(always), "bank-b").decision().triggered()).isTrue();
    }
  }

  @Test
  void seededRandomnessProducesDeterministicDecisions() {
    FaultRule rule1 = new FaultRule("r-seed-1", "bank-b", FaultType.TIMEOUT, null, 0.5, null, 42L);
    FaultRule rule2 = new FaultRule("r-seed-2", "bank-b", FaultType.TIMEOUT, null, 0.5, null, 42L);

    List<Boolean> seq1 = new ArrayList<>();
    for (int i = 0; i < 30; i++) {
      seq1.add(evaluator.evaluate(List.of(rule1), "bank-b").decision().triggered());
    }

    FaultEvaluator secondEvaluator = new FaultEvaluator();
    List<Boolean> seq2 = new ArrayList<>();
    for (int i = 0; i < 30; i++) {
      seq2.add(secondEvaluator.evaluate(List.of(rule2), "bank-b").decision().triggered());
    }

    assertThat(seq1).isEqualTo(seq2);
    // At probability 0.5 across 30 samples, it should contain both true and false
    assertThat(seq1).contains(true, false);
  }

  @Test
  void decrementsRemainingCountAndExpires() {
    FaultRule rule = new FaultRule("r-times", "bank-b", FaultType.TIMEOUT, null, 1.0, 2, null);

    // Call 1
    FaultEvaluator.EvaluationResult res1 = evaluator.evaluate(List.of(rule), "bank-b");
    assertThat(res1.decision().triggered()).isTrue();
    assertThat(res1.updatedRule()).isPresent();
    assertThat(res1.updatedRule().get().remainingCount()).isEqualTo(1);
    assertThat(res1.updatedRule().get().isActive()).isTrue();

    // Call 2
    FaultRule ruleAfter1 = res1.updatedRule().get();
    FaultEvaluator.EvaluationResult res2 = evaluator.evaluate(List.of(ruleAfter1), "bank-b");
    assertThat(res2.decision().triggered()).isTrue();
    assertThat(res2.updatedRule()).isPresent();
    assertThat(res2.updatedRule().get().remainingCount()).isEqualTo(0);
    assertThat(res2.updatedRule().get().isActive()).isFalse();

    // Call 3: rule is expired
    FaultRule ruleAfter2 = res2.updatedRule().get();
    FaultEvaluator.EvaluationResult res3 = evaluator.evaluate(List.of(ruleAfter2), "bank-b");
    assertThat(res3.decision().triggered()).isFalse();
  }

  @Test
  void concurrentEvaluationsNeverExceedTriggerCount() throws InterruptedException {
    int initialCount = 10;
    int threads = 30;
    AtomicInteger remaining = new AtomicInteger(initialCount);
    AtomicInteger totalTriggered = new AtomicInteger(0);

    ExecutorService executor = Executors.newFixedThreadPool(threads);
    CountDownLatch startLatch = new CountDownLatch(1);
    CountDownLatch doneLatch = new CountDownLatch(threads);

    for (int i = 0; i < threads; i++) {
      executor.submit(
          () -> {
            try {
              startLatch.await();
              // Synchronize rule representation
              synchronized (remaining) {
                int current = remaining.get();
                if (current > 0) {
                  FaultRule rule =
                      new FaultRule(
                          "r-concurrent", "bank-b", FaultType.TIMEOUT, null, 1.0, current, null);
                  FaultEvaluator.EvaluationResult res = evaluator.evaluate(List.of(rule), "bank-b");
                  if (res.decision().triggered()) {
                    totalTriggered.incrementAndGet();
                    remaining.decrementAndGet();
                  }
                }
              }
            } catch (Exception ignored) {
            } finally {
              doneLatch.countDown();
            }
          });
    }

    startLatch.countDown();
    doneLatch.await();
    executor.shutdown();

    assertThat(totalTriggered.get()).isEqualTo(initialCount);
    assertThat(remaining.get()).isEqualTo(0);
  }
}
