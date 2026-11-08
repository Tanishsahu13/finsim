package io.github.mhnuk2007.finsim;

import static org.assertj.core.api.Assertions.assertThat;

import com.jayway.jsonpath.JsonPath;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpRequest.BodyPublishers;
import java.net.http.HttpResponse;
import java.net.http.HttpResponse.BodyHandlers;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(TestcontainersConfiguration.class)
class RegistryApiTests {

  @Value("${local.server.port}")
  int port;

  private final HttpClient http = HttpClient.newHttpClient();

  private HttpResponse<String> get(String path) throws Exception {
    return http.send(
        HttpRequest.newBuilder(URI.create("http://localhost:" + port + path)).GET().build(),
        BodyHandlers.ofString());
  }

  private HttpResponse<String> post(String path, String json) throws Exception {
    return http.send(
        HttpRequest.newBuilder(URI.create("http://localhost:" + port + path))
            .header("Content-Type", "application/json")
            .POST(BodyPublishers.ofString(json))
            .build(),
        BodyHandlers.ofString());
  }

  private static String uniqueId() {
    return "t" + UUID.randomUUID().toString().replace("-", "").substring(0, 12);
  }

  private static String institutionJson(String id, String baseUrl, String capabilities) {
    return """
        {"id":"%s","name":"Test bank","type":"BANK","baseUrl":"%s","capabilities":%s}"""
        .formatted(id, baseUrl, capabilities);
  }

  private static final String ALL = "[\"ACCOUNT_DEBIT\",\"ACCOUNT_CREDIT\",\"STATUS_QUERY\"]";

  @Test
  void registerThenReplayThenConflict() throws Exception {
    String id = uniqueId();
    String body = institutionJson(id, "http://localhost:9500", ALL);

    HttpResponse<String> first = post("/api/v1/institutions", body);
    HttpResponse<String> replay = post("/api/v1/institutions", body);
    HttpResponse<String> conflict =
        post("/api/v1/institutions", institutionJson(id, "http://localhost:9501", ALL));

    assertThat(first.statusCode()).isEqualTo(201);
    assertThat((String) JsonPath.read(first.body(), "$.provenance")).isEqualTo("SYNTHETIC");
    assertThat((String) JsonPath.read(first.body(), "$.status")).isEqualTo("ACTIVE");
    assertThat(replay.statusCode()).isEqualTo(200);
    assertThat(replay.body()).isEqualTo(first.body());
    assertThat(conflict.statusCode()).isEqualTo(409);
    assertThat((String) JsonPath.read(conflict.body(), "$.code"))
        .isEqualTo("INSTITUTION_ALREADY_EXISTS");
  }

  @Test
  void aRegisteredInstitutionCanBeFetchedAndListed() throws Exception {
    String id = uniqueId();
    post("/api/v1/institutions", institutionJson(id, "http://localhost:9500", ALL));

    HttpResponse<String> one = get("/api/v1/institutions/" + id);
    HttpResponse<String> all = get("/api/v1/institutions");

    assertThat(one.statusCode()).isEqualTo(200);
    assertThat((String) JsonPath.read(one.body(), "$.id")).isEqualTo(id);
    assertThat(all.statusCode()).isEqualTo(200);
    List<String> ids = JsonPath.read(all.body(), "$[*].id");
    assertThat(ids).contains(id, "bank-a", "bank-b");
  }

  @Test
  void anUnknownInstitutionIs404() throws Exception {
    HttpResponse<String> response = get("/api/v1/institutions/no-such-bank");

    assertThat(response.statusCode()).isEqualTo(404);
    assertThat((String) JsonPath.read(response.body(), "$.code"))
        .isEqualTo("INSTITUTION_NOT_FOUND");
  }

  @Test
  void aMetadataAddressAndAMalformedBodyAreBadRequests() throws Exception {
    HttpResponse<String> metadata =
        post("/api/v1/institutions", institutionJson(uniqueId(), "http://169.254.169.254", ALL));
    HttpResponse<String> malformed = post("/api/v1/institutions", "{ nope");

    assertThat(metadata.statusCode()).isEqualTo(400);
    assertThat((String) JsonPath.read(metadata.body(), "$.code")).isEqualTo("INVALID_INSTITUTION");
    assertThat(malformed.statusCode()).isEqualTo(400);
    assertThat((String) JsonPath.read(malformed.body(), "$.code")).isEqualTo("INVALID_REQUEST");
  }

  @Test
  void theSandboxNetworkListsBothSampleBanks() throws Exception {
    HttpResponse<String> response = get("/api/v1/networks/sandbox");

    assertThat(response.statusCode()).isEqualTo(200);
    List<String> members = JsonPath.read(response.body(), "$.memberIds");
    List<String> currencies = JsonPath.read(response.body(), "$.currencies");
    assertThat(members).contains("bank-a", "bank-b");
    assertThat(currencies).containsExactly("USD");
    assertThat((String) JsonPath.read(response.body(), "$.scheme.workflow"))
        .isEqualTo("wf.transfer.v1");
    assertThat((String) JsonPath.read(response.body(), "$.provenance")).isEqualTo("SYNTHETIC");
  }

  @Test
  void anInstitutionCanJoinTheNetwork() throws Exception {
    String id = uniqueId();
    post("/api/v1/institutions", institutionJson(id, "http://localhost:9500", ALL));

    HttpResponse<String> joined =
        post("/api/v1/networks/sandbox/members", "{\"institutionId\":\"" + id + "\"}");
    HttpResponse<String> unknown =
        post("/api/v1/networks/sandbox/members", "{\"institutionId\":\"ghost-bank\"}");
    HttpResponse<String> noNetwork =
        post("/api/v1/networks/nope/members", "{\"institutionId\":\"" + id + "\"}");

    assertThat(joined.statusCode()).isEqualTo(200);
    List<String> members = JsonPath.read(joined.body(), "$.memberIds");
    assertThat(members).contains(id);
    assertThat(unknown.statusCode()).isEqualTo(404);
    assertThat(noNetwork.statusCode()).isEqualTo(404);
    assertThat((String) JsonPath.read(noNetwork.body(), "$.code")).isEqualTo("NETWORK_NOT_FOUND");
  }
}
