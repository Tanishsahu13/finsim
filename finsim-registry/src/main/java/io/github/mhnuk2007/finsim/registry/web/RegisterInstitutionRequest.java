package io.github.mhnuk2007.finsim.registry.web;

import io.github.mhnuk2007.finsim.registry.Capability;
import io.github.mhnuk2007.finsim.registry.InstitutionType;
import io.github.mhnuk2007.finsim.registry.RegisterInstitution;
import java.util.Set;

public record RegisterInstitutionRequest(
    String id, String name, InstitutionType type, String baseUrl, Set<Capability> capabilities) {

  RegisterInstitution toCommand() {
    return new RegisterInstitution(id, name, type, baseUrl, capabilities);
  }
}
