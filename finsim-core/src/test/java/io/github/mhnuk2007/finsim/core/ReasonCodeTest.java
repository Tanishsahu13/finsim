package io.github.mhnuk2007.finsim.core;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class ReasonCodeTest {

  @Test
  @DisplayName("ISO 20022 reason codes are correctly classified")
  void testIso20022Codes() {
    assertThat(ReasonCode.AC01.isIso20022()).isTrue();
    assertThat(ReasonCode.AC04.isIso20022()).isTrue();
    assertThat(ReasonCode.AM02.isIso20022()).isTrue();
    assertThat(ReasonCode.AM04.isIso20022()).isTrue();
    assertThat(ReasonCode.DUPL.isIso20022()).isTrue();
    assertThat(ReasonCode.MS03.isIso20022()).isTrue();
  }

  @Test
  @DisplayName("FinSim specific reason codes are not classified as ISO 20022")
  void testFinSimSpecificCodes() {
    assertThat(ReasonCode.FINSIM_TIMEOUT.isIso20022()).isFalse();
    assertThat(ReasonCode.RECONCILIATION_REQUIRED.isIso20022()).isFalse();
    assertThat(ReasonCode.IDEMPOTENCY_CONFLICT.isIso20022()).isFalse();
    assertThat(ReasonCode.CAPABILITY_NOT_SUPPORTED.isIso20022()).isFalse();
    assertThat(ReasonCode.INVALID_ACCOUNT.isIso20022()).isFalse();
  }

  @ParameterizedTest
  @ValueSource(
      strings = {
        "AC01",
        "AC04",
        "AM02",
        "AM04",
        "DUPL",
        "MS03",
        "FINSIM_TIMEOUT",
        "RECONCILIATION_REQUIRED"
      })
  @DisplayName("Known reason codes can be looked up by string")
  void testFindKnownCodes(String code) {
    assertThat(ReasonCode.find(code)).isPresent();
    assertThat(ReasonCode.isKnown(code)).isTrue();
  }

  @Test
  @DisplayName("Unknown codes return empty and false")
  void testUnknownCodes() {
    assertThat(ReasonCode.find("UNKNOWN_CODE")).isEmpty();
    assertThat(ReasonCode.find(null)).isEmpty();
    assertThat(ReasonCode.isKnown("UNKNOWN_CODE")).isFalse();
    assertThat(ReasonCode.isKnown(null)).isFalse();
  }
}
