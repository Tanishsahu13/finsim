package io.github.mhnuk2007.finsim.registry.internal;

import io.github.mhnuk2007.finsim.registry.RegisterInstitution;
import io.github.mhnuk2007.finsim.registry.Registry;
import io.github.mhnuk2007.finsim.registry.RegistryException;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

/**
 * Creates the seeded sandbox network and its institutions at startup (all SYNTHETIC). Idempotent:
 * safe to run on every start. An existing institution with a different definition is left alone.
 */
@Component
class RegistrySeeder implements ApplicationRunner {

  private static final Logger log = LoggerFactory.getLogger(RegistrySeeder.class);

  private final Registry registry;
  private final RegistryProperties properties;

  RegistrySeeder(Registry registry, RegistryProperties properties) {
    this.registry = registry;
    this.properties = properties;
  }

  @Override
  public void run(ApplicationArguments args) {
    RegistryProperties.Seed seed = properties.seed();
    if (!seed.enabled()) {
      return;
    }
    registry.ensureNetwork(seed.networkId(), seed.networkName(), Set.copyOf(seed.currencies()));
    for (RegistryProperties.SeedInstitution s : seed.institutions()) {
      try {
        registry.register(
            new RegisterInstitution(s.id(), s.name(), s.type(), s.baseUrl(), s.capabilities()));
        registry.addMember(seed.networkId(), s.id());
      } catch (RegistryException e) {
        log.warn("Seed skipped for institution '{}': {}", s.id(), e.getMessage());
      }
    }
    log.info(
        "Seeded network '{}' with {} synthetic institution(s).",
        seed.networkId(),
        seed.institutions().size());
  }
}
