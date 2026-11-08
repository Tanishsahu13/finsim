package io.github.mhnuk2007.finsim.core;

import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

/**
 * The discrete states in the lifecycle of a {@link Transfer}.
 *
 * <p>{@code COMPLETED} and {@code REJECTED} are terminal. {@code UNKNOWN} is not terminal: it
 * indicates ambiguous outbound leg outcome and must be resolved before the transfer can proceed or
 * be retried.
 */
public enum TransferState {
  CREATED,
  ACCEPTED,
  IN_PROGRESS,
  COMPLETED,
  REJECTED,
  UNKNOWN;

  private static final Map<TransferState, Set<TransferState>> ALLOWED_TRANSITIONS =
      Map.of(
          CREATED, EnumSet.of(ACCEPTED, REJECTED),
          ACCEPTED, EnumSet.of(IN_PROGRESS, REJECTED),
          IN_PROGRESS, EnumSet.of(COMPLETED, REJECTED, UNKNOWN),
          COMPLETED, EnumSet.noneOf(TransferState.class),
          REJECTED, EnumSet.noneOf(TransferState.class),
          UNKNOWN, EnumSet.of(IN_PROGRESS, COMPLETED, REJECTED));

  /**
   * Returns {@code true} if this is a terminal state from which no further transitions are allowed.
   */
  public boolean isTerminal() {
    return this == COMPLETED || this == REJECTED;
  }

  /**
   * Returns {@code true} if transitioning from this state to the given {@code target} state is
   * allowed.
   *
   * @param target the target state
   * @return {@code true} if allowed, {@code false} otherwise
   */
  public boolean canTransitionTo(TransferState target) {
    if (target == null || target == this) {
      return false;
    }
    Set<TransferState> allowed = ALLOWED_TRANSITIONS.get(this);
    return allowed != null && allowed.contains(target);
  }
}
