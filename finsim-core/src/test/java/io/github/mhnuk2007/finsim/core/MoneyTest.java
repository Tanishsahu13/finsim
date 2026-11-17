package io.github.mhnuk2007.finsim.core;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import net.jqwik.api.Arbitraries;
import net.jqwik.api.Arbitrary;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.Provide;
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

  @Provide
  Arbitrary<String> validAmounts() {
    return Arbitraries.integers()
        .between(0, 4)
        .filter(scale -> scale == 0 || scale == 2 || scale == 3 || scale == 4)
        .flatMap(
            scale ->
                Arbitraries.bigDecimals()
                    .between(BigDecimal.ZERO, new BigDecimal("1000000"))
                    .ofScale(scale))
        .map(BigDecimal::toPlainString);
  }

  @Property(tries = 1000)
  void preservesExactDecimalValue(@ForAll("validAmounts") String amount) {

    BigDecimal original = new BigDecimal(amount);

    Money money = Money.of(amount, "USD");

    assertThat(money.amount().compareTo(original)).isZero();
  }

  @Property(tries = 1000)
  void preservesDecimalScale(@ForAll("validAmounts") String amount) {

    BigDecimal original = new BigDecimal(amount);

    Money money = Money.of(amount, "USD");

    assertThat(money.amount().scale()).isEqualTo(original.scale());
  }

  @Property(tries = 1000)
  void additionIsCommutative(
      @ForAll("validAmounts") String first, @ForAll("validAmounts") String second) {

    Money a = Money.of(first, "USD");
    Money b = Money.of(second, "USD");

    assertThat(a.plus(b)).isEqualTo(b.plus(a));
  }

  @Property(tries = 1000)
  void addingZeroPreservesOriginalAmount(@ForAll("validAmounts") String amount) {

    Money money = Money.of(amount, "USD");
    Money zero = Money.of("0", "USD");

    Money result = money.plus(zero);

    assertThat(result.amount().compareTo(money.amount())).isZero();
    assertThat(result.currency()).isEqualTo(money.currency());
  }

  @Provide
  Arbitrary<String> differentCurrencies() {
    return Arbitraries.of("USD", "EUR", "INR", "GBP", "JPY")
        .flatMap(
            first ->
                Arbitraries.of("USD", "EUR", "INR", "GBP", "JPY")
                    .filter(second -> !first.equals(second))
                    .map(second -> first + "," + second));
  }

  @Property(tries = 1000)
  void rejectsAdditionBetweenDifferentCurrencies(@ForAll("differentCurrencies") String currencies) {

    String[] codes = currencies.split(",");

    Money first = Money.of("100.00", codes[0]);
    Money second = Money.of("50.00", codes[1]);

    assertThatThrownBy(() -> first.plus(second))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("currency mismatch");
  }

  @Provide
  Arbitrary<String> negativeAmounts() {
    return Arbitraries.bigDecimals()
        .between(new BigDecimal("-1000000"), new BigDecimal("-0.0001"))
        .ofScale(4)
        .map(BigDecimal::toPlainString);
  }

  @Property(tries = 1000)
  void rejectsNegativeAmounts(@ForAll("negativeAmounts") String amount) {

    assertThatThrownBy(() -> Money.of(amount, "USD")).isInstanceOf(IllegalArgumentException.class);
  }
}
