package io.github.mhnuk2007.finsim.registry;

import java.util.Set;

/** A controlled environment in which member institutions can transact. */
public record Network(
    String id,
    String name,
    Set<String> currencies,
    Set<String> memberIds,
    Scheme scheme,
    String provenance) {}
