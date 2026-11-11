package io.github.mhnuk2007.finsim;

import static org.assertj.core.api.Assertions.assertThat;

import com.jayway.jsonpath.JsonPath;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpResponse.BodyHandlers;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(TestcontainersConfiguration.class)
class OpenApiDocumentationTests {

  @Value("${local.server.port}")
  int port;

  private final HttpClient http =
      HttpClient.newBuilder().followRedirects(HttpClient.Redirect.NEVER).build();

  private HttpResponse<String> get(String path) throws Exception {
    return http.send(
        HttpRequest.newBuilder(URI.create("http://localhost:" + port + path)).GET().build(),
        BodyHandlers.ofString());
  }

  @Test
  void exposesOpenApiDocumentWithMetadata() throws Exception {
    HttpResponse<String> response = get("/v3/api-docs");

    assertThat(response.statusCode()).isEqualTo(200);
    assertThat(JsonPath.<String>read(response.body(), "$.info.title")).isEqualTo("FinSim API");
    assertThat(JsonPath.<String>read(response.body(), "$.info.version")).isEqualTo("v1");
    assertThat(
            JsonPath.<Object>read(
                response.body(), "$.paths['/api/v1/networks/{networkId}/transfers']"))
        .isNotNull();
  }

  @Test
  void redirectsSwaggerUiToInteractivePage() throws Exception {
    HttpResponse<String> response = get("/swagger-ui.html");

    assertThat(response.statusCode()).isIn(200, 302);
    if (response.statusCode() == 302) {
      assertThat(response.headers().firstValue("Location").orElse("")).contains("/swagger-ui/");
    }
  }
}
