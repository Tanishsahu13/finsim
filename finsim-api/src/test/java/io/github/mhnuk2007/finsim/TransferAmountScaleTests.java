package io.github.mhnuk2007.finsim;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.mhnuk2007.finsim.core.AccountRef;
import io.github.mhnuk2007.finsim.core.Money;
import io.github.mhnuk2007.finsim.core.Transfer;
import io.github.mhnuk2007.finsim.transfer.internal.TransferEntity;
import io.github.mhnuk2007.finsim.transfer.internal.TransferRepository;
import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import javax.sql.DataSource;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

@Import(TestcontainersConfiguration.class)
@SpringBootTest
class TransferAmountScaleTests {

  @Autowired private DataSource dataSource;

  @Autowired private TransferRepository transferRepository;

  @Test
  @DisplayName("transfers.amount is NUMERIC(22, 8) after migrations")
  void amountColumnIsNumeric22Scale8() throws Exception {
    try (Connection connection = dataSource.getConnection()) {
      DatabaseMetaData metaData = connection.getMetaData();
      try (ResultSet rs = metaData.getColumns(null, null, "transfers", "amount")) {
        assertThat(rs.next()).isTrue();
        assertThat(rs.getString("TYPE_NAME")).isEqualToIgnoringCase("numeric");
        assertThat(rs.getInt("COLUMN_SIZE")).isEqualTo(22);
        assertThat(rs.getInt("DECIMAL_DIGITS")).isEqualTo(8);
      }
    }
  }

  @Test
  @DisplayName("Amounts with 4, 5 and 8 decimal places survive a database round-trip")
  void amountsKeepFullPrecisionAfterRoundTrip() throws Exception {
    for (String amount : List.of("10.1234", "10.12345", "10.12345678")) {
      Transfer transfer =
          Transfer.create(
              "scale-" + UUID.randomUUID(),
              "sandbox",
              "key-" + UUID.randomUUID(),
              new AccountRef("bank-a", "ACC-1"),
              new AccountRef("bank-b", "ACC-2"),
              Money.of(amount, "USD"),
              "scale test",
              Instant.now());
      TransferEntity saved = transferRepository.save(TransferEntity.from(transfer));

      BigDecimal stored;
      try (Connection connection = dataSource.getConnection();
          PreparedStatement ps =
              connection.prepareStatement("SELECT amount FROM transfers WHERE id = ?")) {
        ps.setString(1, saved.getId());
        try (ResultSet rs = ps.executeQuery()) {
          assertThat(rs.next()).isTrue();
          stored = rs.getBigDecimal(1);
        }
      }
      assertThat(stored).isEqualByComparingTo(new BigDecimal(amount));

      TransferEntity reloaded = transferRepository.findById(saved.getId()).orElseThrow();
      assertThat(reloaded.toDomain(List.of()).getMoney().amount())
          .isEqualByComparingTo(new BigDecimal(amount));
    }
  }
}
