package io.github.mhnuk2007.finsim.registry;

import java.util.Set;

/** A registered institution. {@code provenance} is always SYNTHETIC. */
public record Institution(
    String id,
    String name,
    InstitutionType type,
    String baseUrl,
    Set<Capability> capabilities,
    InstitutionStatus status,
    String provenance) {}
