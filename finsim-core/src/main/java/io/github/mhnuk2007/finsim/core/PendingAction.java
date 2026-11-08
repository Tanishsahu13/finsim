package io.github.mhnuk2007.finsim.core;

/**
 * Action that FinSim will attempt during resolution while in the {@code UNKNOWN} state.
 *
 * <p>See ADR 0001 for details on the resolution decision table.
 */
public enum PendingAction {
  NONE,
  RETRY_CREDIT,
  REVERSE_DEBIT
}
