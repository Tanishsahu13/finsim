package io.github.mhnuk2007.finsim.registry.internal;

import io.github.mhnuk2007.finsim.registry.Capability;
import io.github.mhnuk2007.finsim.registry.InstitutionType;
import java.util.List;
import java.util.Set;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/** Settings under {@code finsim.registry}. */
@ConfigurationProperties(prefix = "finsim.registry")
public record RegistryProperties(
    @DefaultValue("true") boolean allowPrivateAddresses, @DefaultValue Seed seed) {

  public RegistryProperties {
    if (seed == null) {
      seed = new Seed(true, "sandbox", "Sandbox", List.of("USD"), List.of());
    }
  }

  /** Synthetic data created at startup so a fresh install has something to talk to. */
  public record Seed(
      @DefaultValue("true") boolean enabled,
      @DefaultValue("sandbox") String networkId,
      @DefaultValue("Sandbox") String networkName,
      @DefaultValue("USD") List<String> currencies,
      List<SeedInstitution> institutions) {

    public Seed {
      currencies = currencies == null ? List.of("USD") : List.copyOf(currencies);
      institutions = institutions == null ? List.of() : List.copyOf(institutions);
    }
  }

  public record SeedInstitution(
      String id, String name, InstitutionType type, String baseUrl, Set<Capability> capabilities) {}
}
