package io.github.mhnuk2007.finsim.samples.bank;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/** Seed data and behavior switches for the sample bank. All data is synthetic. */
@ConfigurationProperties(prefix = "bank")
public record BankProperties(
    @DefaultValue("bank-a") String institutionId,
    @DefaultValue("USD") List<String> currencies,
    Map<String, BigDecimal> accounts,
    Set<String> closedAccounts) {

  public BankProperties {
    currencies = currencies == null ? List.of("USD") : List.copyOf(currencies);
    accounts = accounts == null ? Map.of() : Map.copyOf(accounts);
    closedAccounts = closedAccounts == null ? Set.of() : Set.copyOf(closedAccounts);
  }
}
