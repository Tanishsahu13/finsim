package io.github.mhnuk2007.finsim.core;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.EnumSet;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

class TransferStateTest {

  private static final Map<TransferState, Set<TransferState>> EXPECTED_ALLOWED =
      Map.of(
          TransferState.CREATED, EnumSet.of(TransferState.ACCEPTED, TransferState.REJECTED),
          TransferState.ACCEPTED, EnumSet.of(TransferState.IN_PROGRESS, TransferState.REJECTED),
          TransferState.IN_PROGRESS,
              EnumSet.of(TransferState.COMPLETED, TransferState.REJECTED, TransferState.UNKNOWN),
          TransferState.COMPLETED, EnumSet.noneOf(TransferState.class),
          TransferState.REJECTED, EnumSet.noneOf(TransferState.class),
          TransferState.UNKNOWN,
              EnumSet.of(
                  TransferState.IN_PROGRESS, TransferState.COMPLETED, TransferState.REJECTED));

  @Test
  @DisplayName("All 36 transition pairs strictly match the state machine matrix")
  void testAll36Transitions() {
    for (TransferState from : TransferState.values()) {
      Set<TransferState> allowedTargets = EXPECTED_ALLOWED.get(from);
      for (TransferState to : TransferState.values()) {
        boolean expected = allowedTargets.contains(to);
        assertThat(from.canTransitionTo(to))
            .as("Transition from %s to %s should be %s", from, to, expected)
            .isEqualTo(expected);
      }
    }
  }

  @Test
  @DisplayName("Self transitions are always forbidden")
  void selfTransitionsAreForbidden() {
    for (TransferState state : TransferState.values()) {
      assertThat(state.canTransitionTo(state))
          .as("Self transition for %s must be forbidden", state)
          .isFalse();
    }
  }

  @Test
  @DisplayName("Transition to null is always false")
  void transitionToNullIsFalse() {
    for (TransferState state : TransferState.values()) {
      assertThat(state.canTransitionTo(null)).isFalse();
    }
  }

  @ParameterizedTest
  @EnumSource(TransferState.class)
  @DisplayName("Terminal states are exactly COMPLETED and REJECTED")
  void terminalStateCheck(TransferState state) {
    boolean expectedTerminal =
        (state == TransferState.COMPLETED || state == TransferState.REJECTED);
    assertThat(state.isTerminal()).isEqualTo(expectedTerminal);
  }
}
