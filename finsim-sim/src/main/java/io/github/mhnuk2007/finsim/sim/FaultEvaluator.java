package io.github.mhnuk2007.finsim.sim;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/** Framework-independent engine for evaluating fault rules against outbound participant calls. */
public class FaultEvaluator {

  private final Map<String, DeterministicRandom> prngs = new ConcurrentHashMap<>();

  public record EvaluationResult(
      FaultDecision decision, Optional<FaultRule> originalRule, Optional<FaultRule> updatedRule) {

    public static EvaluationResult none() {
      return new EvaluationResult(FaultDecision.none(), Optional.empty(), Optional.empty());
    }
  }

  /**
   * Evaluates the first active matching rule for the given target institution.
   *
   * @param rules the configured active rules to evaluate
   * @param targetInstitution the destination institution slug
   * @return the evaluation result containing the decision and updated rule if triggered
   */
  public synchronized EvaluationResult evaluate(List<FaultRule> rules, String targetInstitution) {
    Objects.requireNonNull(rules, "rules must not be null");
    Objects.requireNonNull(targetInstitution, "targetInstitution must not be null");

    for (FaultRule rule : rules) {
      if (!rule.target().equalsIgnoreCase(targetInstitution)) {
        continue;
      }
      if (!rule.isActive()) {
        continue;
      }

      if (shouldTrigger(rule)) {
        FaultDecision decision = toDecision(rule);
        FaultRule updatedRule;
        if (rule.remainingCount() != null) {
          updatedRule = rule.withRemainingCount(Math.max(0, rule.remainingCount() - 1));
        } else {
          updatedRule = rule;
        }
        return new EvaluationResult(decision, Optional.of(rule), Optional.of(updatedRule));
      }
    }

    return EvaluationResult.none();
  }

  private boolean shouldTrigger(FaultRule rule) {
    if (rule.probability() >= 1.0) {
      return true;
    }
    if (rule.probability() <= 0.0) {
      return false;
    }

    DeterministicRandom prng =
        prngs.computeIfAbsent(rule.faultId(), id -> new DeterministicRandom(rule.seed()));
    return prng.nextDouble() < rule.probability();
  }

  private FaultDecision toDecision(FaultRule rule) {
    return switch (rule.type()) {
      case DELAY -> FaultDecision.delay(rule, rule.delayMs() != null ? rule.delayMs() : 0);
      case TIMEOUT -> FaultDecision.timeout(rule);
      case DUPLICATE_DELIVERY -> FaultDecision.duplicateDelivery(rule);
    };
  }

  public void resetPrng(String faultId) {
    prngs.remove(faultId);
  }

  public void resetAll() {
    prngs.clear();
  }
}
