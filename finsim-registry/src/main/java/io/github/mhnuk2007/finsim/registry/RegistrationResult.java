package io.github.mhnuk2007.finsim.registry;

/** {@code created} is false when an identical registration already existed (idempotent replay). */
public record RegistrationResult(Institution institution, boolean created) {}
