package io.github.mhnuk2007.finsim.sim;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class FaultTypeTest {

  @Test
  void milestoneOneFaultsExist() {
    assertThat(FaultType.values())
        .containsExactly(FaultType.DELAY, FaultType.TIMEOUT, FaultType.DUPLICATE_DELIVERY);
  }
}
