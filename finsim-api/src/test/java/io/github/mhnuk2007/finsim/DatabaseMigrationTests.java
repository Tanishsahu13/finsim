package io.github.mhnuk2007.finsim;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.List;
import javax.sql.DataSource;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@SpringBootTest
@Testcontainers(disabledWithoutDocker = true)
class DatabaseMigrationTests {

  @Container @ServiceConnection
  static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:17-alpine");

  @Autowired private DataSource dataSource;

  @Test
  @DisplayName("Flyway migrations run and create baseline tables with common columns")
  void flywayMigrationsCreateBaselineTables() throws Exception {
    try (Connection connection = dataSource.getConnection()) {
      DatabaseMetaData metaData = connection.getMetaData();

      List<String> tables = new ArrayList<>();
      try (ResultSet rs = metaData.getTables(null, null, "%", new String[] {"TABLE"})) {
        while (rs.next()) {
          tables.add(rs.getString("TABLE_NAME").toLowerCase());
        }
      }

      assertThat(tables)
          .contains(
              "institutions",
              "transfers",
              "transfer_stages",
              "fault_rules",
              "flyway_schema_history");

      // Verify common columns exist on primary entity tables: id, created_at, updated_at, version
      for (String table : List.of("institutions", "transfers", "fault_rules")) {
        List<String> columns = new ArrayList<>();
        try (ResultSet rs = metaData.getColumns(null, null, table, "%")) {
          while (rs.next()) {
            columns.add(rs.getString("COLUMN_NAME").toLowerCase());
          }
        }
        assertThat(columns)
            .as("Table %s must contain common baseline columns", table)
            .contains("id", "created_at", "updated_at", "version");
      }
    }
  }
}
