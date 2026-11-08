package io.github.mhnuk2007.finsim.registry;

/**
 * The rules a network applies. Milestone 1 has exactly one hardcoded scheme: USD only, one workflow
 * (debit, then credit). Schemes become configurable later; see docs/roadmap.md.
 */
public record Scheme(String id, String workflow) {

  public static final Scheme SANDBOX_V1 = new Scheme("sandbox-v1", "wf.transfer.v1");
}
