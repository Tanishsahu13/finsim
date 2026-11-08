package io.github.mhnuk2007.finsim.core;

/** Thrown when an illegal state transition is attempted on a {@link Transfer}. */
public class IllegalStateTransitionException extends IllegalStateException {

  private final String transferId;
  private final TransferState from;
  private final TransferState to;

  public IllegalStateTransitionException(String transferId, TransferState from, TransferState to) {
    super(
        "Cannot transition transfer '%s' from %s to %s%s"
            .formatted(
                transferId,
                from,
                to,
                from.isTerminal() ? ": " + from + " is a terminal state" : ""));
    this.transferId = transferId;
    this.from = from;
    this.to = to;
  }

  public IllegalStateTransitionException(
      String transferId, TransferState from, TransferState to, String message) {
    super(
        "Cannot transition transfer '%s' from %s to %s: %s"
            .formatted(transferId, from, to, message));
    this.transferId = transferId;
    this.from = from;
    this.to = to;
  }

  public String getTransferId() {
    return transferId;
  }

  public TransferState getFrom() {
    return from;
  }

  public TransferState getTo() {
    return to;
  }
}
