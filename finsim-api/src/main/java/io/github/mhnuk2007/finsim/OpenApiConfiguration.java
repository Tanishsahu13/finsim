package io.github.mhnuk2007.finsim;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** OpenAPI metadata for the platform REST API shown in Swagger UI. */
@Configuration
public class OpenApiConfiguration {

  @Bean
  public OpenAPI finSimOpenApi() {
    return new OpenAPI()
        .info(
            new Info()
                .title("FinSim API")
                .version("v1")
                .description(
                    "REST API for managing FinSim institutions, networks, and simulation platform resources."));
  }
}
