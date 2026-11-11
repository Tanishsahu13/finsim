package io.github.mhnuk2007.finsim;

import static org.assertj.core.api.Assertions.assertThat;

import io.swagger.v3.oas.models.OpenAPI;
import org.junit.jupiter.api.Test;

class OpenApiConfigurationTests {

  @Test
  void exposesExpectedApiMetadata() {
    OpenAPI openApi = new OpenApiConfiguration().finSimOpenApi();

    assertThat(openApi.getInfo().getTitle()).isEqualTo("FinSim API");
    assertThat(openApi.getInfo().getVersion()).isEqualTo("v1");
    assertThat(openApi.getInfo().getDescription())
        .isEqualTo(
            "REST API for managing FinSim institutions, networks, and simulation platform resources.");
  }
}
