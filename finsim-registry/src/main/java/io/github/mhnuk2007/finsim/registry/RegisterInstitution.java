package io.github.mhnuk2007.finsim.registry;

import java.util.Set;

/** Command to register an institution. */
public record RegisterInstitution(
    String id, String name, InstitutionType type, String baseUrl, Set<Capability> capabilities) {}
