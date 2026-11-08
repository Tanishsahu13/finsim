package io.github.mhnuk2007.finsim.cli;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import picocli.CommandLine;

class FinSimCliTest {

  @Test
  void helpExitsCleanly() {
    assertThat(new CommandLine(new FinSimCli()).execute("--help")).isZero();
  }

  @Test
  void plannedSubcommandsReturnNotImplementedUntilBuilt() {
    assertThat(new CommandLine(new FinSimCli()).execute("verify"))
        .isEqualTo(FinSimCli.EXIT_NOT_IMPLEMENTED);
  }
}
