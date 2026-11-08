package io.github.mhnuk2007.finsim.core;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class MoneyTest {

  @Test
  void addsAmountsInTheSameCurrency() {
    assertThat(Money.of("10.50", "USD").plus(Money.of("0.25", "USD")))
        .isEqualTo(Money.of("10.75", "USD"));
  }

  @Test
  void rejectsCurrencyMismatch() {
    assertThatThrownBy(() -> Money.of("1.00", "USD").plus(Money.of("1.00", "EUR")))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("currency mismatch");
  }

  @Test
  void rejectsNegativeAmount() {
    assertThatThrownBy(() -> Money.of("-0.01", "USD")).isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void rejectsMalformedCurrency() {
    assertThatThrownBy(() -> Money.of("1.00", "usd")).isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void subtractionCannotGoNegative() {
    assertThatThrownBy(() -> Money.of("1.00", "USD").minus(Money.of("2.00", "USD")))
        .isInstanceOf(IllegalArgumentException.class);
  }
}
