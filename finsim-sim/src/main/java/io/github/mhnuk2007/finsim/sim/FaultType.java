package io.github.mhnuk2007.finsim.sim;

/** Faults FinSim can inject on calls to a participant. */
public enum FaultType {
  DELAY,
  TIMEOUT,
  DUPLICATE_DELIVERY
}
