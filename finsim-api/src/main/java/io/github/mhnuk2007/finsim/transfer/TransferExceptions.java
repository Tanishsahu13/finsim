package io.github.mhnuk2007.finsim.transfer;

public final class TransferExceptions {

  private TransferExceptions() {}

  public static class TransferException extends RuntimeException {
    private final String code;

    public TransferException(String code, String message) {
      super(message);
      this.code = code;
    }

    public String code() {
      return code;
    }
  }

  public static class MissingIdempotencyKeyException extends TransferException {
    public MissingIdempotencyKeyException(String message) {
      super("MISSING_IDEMPOTENCY_KEY", message);
    }
  }

  public static class IdempotencyConflictException extends TransferException {
    public IdempotencyConflictException(String message) {
      super("IDEMPOTENCY_CONFLICT", message);
    }
  }

  public static class ReconciliationRequiredException extends TransferException {
    public ReconciliationRequiredException(String message) {
      super("RECONCILIATION_REQUIRED", message);
    }
  }

  public static class TransferNotFoundException extends TransferException {
    public TransferNotFoundException(String id) {
      super("NOT_FOUND", "Transfer not found: " + id);
    }
  }

  public static class InvalidTransferRequestException extends TransferException {
    public InvalidTransferRequestException(String message) {
      super("INVALID_REQUEST", message);
    }
  }
}
