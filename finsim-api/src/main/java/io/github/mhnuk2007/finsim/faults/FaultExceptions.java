package io.github.mhnuk2007.finsim.faults;

public final class FaultExceptions {

  private FaultExceptions() {}

  public static class FaultNotFoundException extends RuntimeException {
    public FaultNotFoundException(String faultId) {
      super("Fault rule not found: " + faultId);
    }
  }

  public static class InvalidFaultRuleException extends RuntimeException {
    public InvalidFaultRuleException(String message) {
      super(message);
    }
  }
}
