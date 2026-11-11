package io.github.mhnuk2007.finsim.cli;

import static org.assertj.core.api.Assertions.assertThat;

import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import picocli.CommandLine;

class FinSimCliTest {

  private HttpServer server;
  private final AtomicReference<String> lastMethod = new AtomicReference<>();
  private final AtomicReference<String> lastPath = new AtomicReference<>();
  private final AtomicReference<String> lastBody = new AtomicReference<>();

  @AfterEach
  void stopServer() {
    if (server != null) {
      server.stop(0);
    }
  }

  /** Starts a stub FinSim that answers every request with the given status and body. */
  private String stubFinSim(int status, String responseBody) throws IOException {
    server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
    server.createContext(
        "/",
        exchange -> {
          lastMethod.set(exchange.getRequestMethod());
          lastPath.set(exchange.getRequestURI().getPath());
          lastBody.set(
              new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
          byte[] bytes = responseBody.getBytes(StandardCharsets.UTF_8);
          exchange.getResponseHeaders().add("Content-Type", "application/json");
          exchange.sendResponseHeaders(status, bytes.length);
          exchange.getResponseBody().write(bytes);
          exchange.close();
        });
    server.start();
    return "http://localhost:" + server.getAddress().getPort();
  }

  private record Result(int exit, String out, String err) {}

  private static Result run(String... args) {
    StringWriter out = new StringWriter();
    StringWriter err = new StringWriter();
    CommandLine cli = new CommandLine(new FinSimCli());
    cli.setOut(new PrintWriter(out));
    cli.setErr(new PrintWriter(err));
    int exit = cli.execute(args);
    return new Result(exit, out.toString(), err.toString());
  }

  @Test
  void helpExitsCleanly() {
    assertThat(run("--help").exit()).isZero();
  }

  @Test
  void plannedSubcommandsReturnNotImplementedUntilBuilt() {
    assertThat(run("verify").exit()).isEqualTo(FinSimCli.EXIT_NOT_IMPLEMENTED);
  }

  @Test
  void registerSendsTheInstitutionAndPrintsTheResponse() throws Exception {
    String url = stubFinSim(201, "{\"id\":\"my-bank\"}");

    Result result =
        run(
            "register",
            "--finsim",
            url,
            "--id",
            "my-bank",
            "--name",
            "My \"Bank\"",
            "--base-url",
            "http://localhost:9100",
            "--capabilities",
            "ACCOUNT_DEBIT,ACCOUNT_CREDIT,STATUS_QUERY");

    assertThat(result.exit()).isZero();
    assertThat(result.out()).contains("my-bank");
    assertThat(lastMethod.get()).isEqualTo("POST");
    assertThat(lastPath.get()).isEqualTo("/api/v1/institutions");
    assertThat(lastBody.get())
        .contains("\"id\":\"my-bank\"")
        .contains("\"name\":\"My \\\"Bank\\\"\"")
        .contains("\"type\":\"BANK\"")
        .contains("\"capabilities\":[\"ACCOUNT_DEBIT\",\"ACCOUNT_CREDIT\",\"STATUS_QUERY\"]");
  }

  @Test
  void aFailureResponseGivesAReadableErrorAndANonZeroExit() throws Exception {
    String url = stubFinSim(409, "{\"code\":\"INSTITUTION_ALREADY_EXISTS\"}");

    Result result =
        run(
            "register",
            "--finsim",
            url,
            "--id",
            "my-bank",
            "--name",
            "x",
            "--base-url",
            "http://localhost:9100",
            "--capabilities",
            "STATUS_QUERY");

    assertThat(result.exit()).isEqualTo(FinSimCli.EXIT_FAILED);
    assertThat(result.err()).contains("409").contains("INSTITUTION_ALREADY_EXISTS");
  }

  @Test
  void listingInstitutionsAndNetworksUsesTheRightPaths() throws Exception {
    String url = stubFinSim(200, "[]");

    assertThat(run("institutions", "--finsim", url).exit()).isZero();
    assertThat(lastPath.get()).isEqualTo("/api/v1/institutions");

    assertThat(run("institutions", "--finsim", url, "--id", "bank-a").exit()).isZero();
    assertThat(lastPath.get()).isEqualTo("/api/v1/institutions/bank-a");

    assertThat(run("networks", "--finsim", url, "--id", "sandbox").exit()).isZero();
    assertThat(lastPath.get()).isEqualTo("/api/v1/networks/sandbox");
  }

  @Test
  void joinPostsTheMember() throws Exception {
    String url = stubFinSim(200, "{}");

    Result result = run("join", "--finsim", url, "--network", "sandbox", "--institution", "bank-a");

    assertThat(result.exit()).isZero();
    assertThat(lastPath.get()).isEqualTo("/api/v1/networks/sandbox/members");
    assertThat(lastBody.get()).isEqualTo("{\"institutionId\":\"bank-a\"}");
  }

  @Test
  void anUnreachableFinSimIsAClearError() {
    Result result = run("institutions", "--finsim", "http://localhost:1");

    assertThat(result.exit()).isEqualTo(FinSimCli.EXIT_FAILED);
    assertThat(result.err()).contains("Cannot reach FinSim");
  }

  @Test
  void jsonFlagPrintsRawJsonWithoutExtraText() throws Exception {
    String body = "[{\"id\":\"bank-a\",\"name\":\"Bank A\"}]";
    String url = stubFinSim(200, body);

    Result result = run("institutions", "--json", "--finsim", url);

    assertThat(result.exit()).isZero();
    assertThat(result.out().strip()).isEqualTo(body);
    assertThat(result.err()).isEmpty();
  }

  @Test
  void shortJsonFlagBehavesLikeLongForm() throws Exception {
    String body = "[{\"id\":\"bank-a\"}]";
    String url = stubFinSim(200, body);

    Result result = run("institutions", "-j", "--finsim", url);

    assertThat(result.exit()).isZero();
    assertThat(result.out().strip()).isEqualTo(body);
    assertThat(result.err()).isEmpty();
  }

  @Test
  void defaultOutputIsUnchangedWithoutJsonFlag() throws Exception {
    String body = "[{\"id\":\"bank-a\"}]";
    String url = stubFinSim(200, body);

    Result result = run("institutions", "--finsim", url);

    assertThat(result.exit()).isZero();
    assertThat(result.out().strip()).isEqualTo(body);
  }

  @Test
  void jsonFlagKeepsErrorsOnStderr() throws Exception {
    String url = stubFinSim(409, "{\"code\":\"INSTITUTION_ALREADY_EXISTS\"}");

    Result result = run("institutions", "--json", "--finsim", url);

    assertThat(result.exit()).isEqualTo(FinSimCli.EXIT_FAILED);
    assertThat(result.out()).isEmpty();
    assertThat(result.err()).contains("409").contains("INSTITUTION_ALREADY_EXISTS");
  }

  @Test
  void jsonQuotingEscapesSpecialCharacters() {
    assertThat(Json.quote("a\"b\\c\nd")).isEqualTo("\"a\\\"b\\\\c\\nd\"");
    assertThat(Json.array(List.of("X", "Y"))).isEqualTo("[\"X\",\"Y\"]");
  }
}
