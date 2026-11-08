package io.github.mhnuk2007.finsim;

import org.junit.jupiter.api.Test;
import org.springframework.modulith.core.ApplicationModules;

/** Fails the build if a module reaches into another module's internals or modules form a cycle. */
class ModularityTests {

  @Test
  void modulesAreWellFormed() {
    ApplicationModules.of(FinSimApplication.class).verify();
  }
}
