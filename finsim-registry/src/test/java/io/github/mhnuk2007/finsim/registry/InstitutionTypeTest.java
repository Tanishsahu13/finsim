package io.github.mhnuk2007.finsim.registry;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class InstitutionTypeTest {

  @Test
  void milestoneOneTypesExist() {
    assertThat(InstitutionType.values())
        .contains(InstitutionType.BANK, InstitutionType.MERCHANT, InstitutionType.CUSTOM);
  }
}
