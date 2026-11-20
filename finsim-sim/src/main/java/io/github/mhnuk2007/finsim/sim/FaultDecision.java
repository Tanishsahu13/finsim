package io.github.mhnuk2007.finsim.sim;

/** Result of evaluating fault rules for an outbound participant call. */
public record FaultDecision(
    boolean triggered, FaultRule matchedRule, FaultType faultType, int delayMs) {

  private static final FaultDecision NONE = new FaultDecision(false, null, null, 0);

  public static FaultDecision none() {
    return NONE;
  }

  public static FaultDecision delay(FaultRule rule, int delayMs) {
    return new FaultDecision(true, rule, FaultType.DELAY, delayMs);
  }

  public static FaultDecision timeout(FaultRule rule) {
    return new FaultDecision(true, rule, FaultType.TIMEOUT, 0);
  }

  public static FaultDecision duplicateDelivery(FaultRule rule) {
    return new FaultDecision(true, rule, FaultType.DUPLICATE_DELIVERY, 0);
  }
}
