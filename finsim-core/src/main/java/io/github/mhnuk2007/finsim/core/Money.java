package io.github.mhnuk2007.finsim.core;

import java.math.BigDecimal;
import java.util.Objects;
import java.util.regex.Pattern;

/**
 * An amount of money in one currency.
 *
 * <p>Money is never a floating-point number: the amount is an exact decimal. All simulated money in
 * FinSim is synthetic.
 */
public record Money(BigDecimal amount, String currency) {

  private static final Pattern CURRENCY = Pattern.compile("[A-Z]{3}");
  private static final int MAX_SCALE = 8;

  public Money {
    Objects.requireNonNull(amount, "amount");
    Objects.requireNonNull(currency, "currency");
    if (!CURRENCY.matcher(currency).matches()) {
      throw new IllegalArgumentException("currency must be three uppercase letters: " + currency);
    }
    if (amount.signum() < 0) {
      throw new IllegalArgumentException("amount must not be negative: " + amount);
    }
    if (amount.scale() > MAX_SCALE) {
      throw new IllegalArgumentException("amount has more than " + MAX_SCALE + " decimal places");
    }
  }

  public static Money of(String amount, String currency) {
    return new Money(new BigDecimal(amount), currency);
  }

  public Money plus(Money other) {
    requireSameCurrency(other);
    return new Money(amount.add(other.amount), currency);
  }

  public Money minus(Money other) {
    requireSameCurrency(other);
    return new Money(amount.subtract(other.amount), currency);
  }

  private void requireSameCurrency(Money other) {
    if (!currency.equals(other.currency)) {
      throw new IllegalArgumentException(
          "currency mismatch: " + currency + " vs " + other.currency);
    }
  }
}
