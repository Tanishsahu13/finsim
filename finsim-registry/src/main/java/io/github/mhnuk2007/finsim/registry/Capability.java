package io.github.mhnuk2007.finsim.registry;

/** Operations an institution can declare. FinSim routes only to declared capabilities. */
public enum Capability {
  ACCOUNT_DEBIT,
  ACCOUNT_CREDIT,
  STATUS_QUERY
}
