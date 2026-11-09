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
    assertThat(ReasonCode.AC06.isIso20022()).isTrue();
    assertThat(ReasonCode.AG01.isIso20022()).isTrue();
    assertThat(ReasonCode.AM02.isIso20022()).isTrue();
    assertThat(ReasonCode.AM04.isIso20022()).isTrue();
    assertThat(ReasonCode.BE04.isIso20022()).isTrue();
    assertThat(ReasonCode.DUPL.isIso20022()).isTrue();
    assertThat(ReasonCode.MS03.isIso20022()).isTrue();
    assertThat(ReasonCode.RC01.isIso20022()).isTrue();
    assertThat(ReasonCode.RR01.isIso20022()).isTrue();
    assertThat(ReasonCode.RR04.isIso20022()).isTrue();
  }

  @Test
  @DisplayName("FinSim specific reason codes are not classified as ISO 20022")
  void testFinSimSpecificCodes() {
    assertThat(ReasonCode.FINSIM_TIMEOUT.isIso20022()).isFalse();
    assertThat(ReasonCode.CAPABILITY_NOT_SUPPORTED.isIso20022()).isFalse();
    assertThat(ReasonCode.ROUTING_ERROR.isIso20022()).isFalse();
    assertThat(ReasonCode.INVALID_ACCOUNT.isIso20022()).isFalse();
  }

  @Test
  @DisplayName("All ReasonCodes are valid transfer rejection reasons")
  void allReasonCodesAreRejectionReasons() {
    for (ReasonCode rc : ReasonCode.values()) {
      assertThat(rc.isRejectionReason()).isTrue();
    }
  }

  @ParameterizedTest
  @ValueSource(
      strings = {
        "AC01",
        "AC04",
        "AC06",
        "AG01",
        "AM02",
        "AM04",
        "BE04",
        "DUPL",
        "MS03",
        "RC01",
        "RR01",
        "RR04",
        "FINSIM_TIMEOUT",
        "CAPABILITY_NOT_SUPPORTED"
      })
  @DisplayName("Known rejection reason codes can be looked up by string")
  void testFindKnownCodes(String code) {
    assertThat(ReasonCode.find(code)).isPresent();
    assertThat(ReasonCode.isKnown(code)).isTrue();
  }

  @Test
  @DisplayName("API error codes are not part of ReasonCode registry")
  void apiErrorCodesAreSeparate() {
    assertThat(ReasonCode.find("IDEMPOTENCY_CONFLICT")).isEmpty();
    assertThat(ReasonCode.find("RECONCILIATION_REQUIRED")).isEmpty();
    assertThat(ApiErrorCode.find("IDEMPOTENCY_CONFLICT")).isPresent();
    assertThat(ApiErrorCode.find("RECONCILIATION_REQUIRED")).isPresent();
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
