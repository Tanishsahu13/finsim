package io.github.mhnuk2007.finsim.cli;

import static org.assertj.core.api.Assertions.assertThat;

import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class ParticipantVerifierTest {
  private HttpServer server;

  @AfterEach
  void stop() {
    if (server != null) server.stop(0);
  }

  @Test
  void conformingParticipantPassesWithoutDuplicateEffects() throws Exception {
    AtomicInteger effects = new AtomicInteger();
    Map<String, String> keys = new HashMap<>();
    Map<String, Map<String, Object>> legs = new HashMap<>();
    server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
    server.createContext(
        "/",
        exchange -> {
          String path = exchange.getRequestURI().getPath();
          int status = 200;
          Object body = Map.of("code", "INVALID_REQUEST", "message", "Unexpected request");
          if (path.endsWith("/health"))
            body = Map.of("status", "UP", "contractVersions", List.of("finsim/v1"));
          else if (path.endsWith("/capabilities"))
            body =
                Map.of(
                    "institutionType",
                    "BANK",
                    "capabilities",
                    List.of("ACCOUNT_DEBIT", "ACCOUNT_CREDIT", "STATUS_QUERY"),
                    "currencies",
                    List.of("USD"));
          else if (exchange.getRequestMethod().equals("POST")) {
            String raw =
                new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
            @SuppressWarnings("unchecked")
            Map<String, Object> request = (Map<String, Object>) ContractJson.parse(raw);
            @SuppressWarnings("unchecked")
            Map<String, Object> money = (Map<String, Object>) request.get("money");
            String key = exchange.getRequestHeaders().getFirst("Idempotency-Key");
            String id = (String) request.get("transactionId");
            String direction = (String) request.get("direction");
            String code = null;
            if (keys.containsKey(key)) {
              if (!keys.get(key).equals(raw)) {
                status = 409;
                code = "IDEMPOTENCY_CONFLICT";
              } else {
                body = legs.get(id + direction);
                status = ((Map<?, ?>) body).containsKey("code") ? 422 : 200;
              }
            } else {
              keys.put(key, raw);
              if (money.get("currency").equals("XXX")) code = "AM03";
              else if (money.get("amount").equals("0.00")) code = "AM02";
              else if (request.get("accountReference").equals("ACC-UNKNOWN")) code = "AC01";
              else if (request.get("accountReference").equals("ACC-CLOSED")) code = "AC04";
              else if (money.get("amount").equals("999999999.00")) code = "AM04";
              if (code == null) {
                status = 201;
                effects.incrementAndGet();
                body =
                    Map.of(
                        "transactionId",
                        id,
                        "direction",
                        direction,
                        "status",
                        "COMPLETED",
                        "participantReference",
                        id,
                        "provenance",
                        "SYNTHETIC");
                legs.put(id + direction, (Map<String, Object>) body);
              } else {
                status = 422;
                body = Map.of("code", code, "message", "Rejected");
                legs.put(
                    id + direction,
                    Map.of(
                        "transactionId",
                        id,
                        "direction",
                        direction,
                        "status",
                        "REJECTED",
                        "reasonCode",
                        code,
                        "provenance",
                        "SYNTHETIC"));
              }
            }
            if (code != null && status == 409) body = Map.of("code", code, "message", "Conflict");
          } else {
            String id = path.substring(path.lastIndexOf('/') + 1);
            String direction = exchange.getRequestURI().getQuery().substring(4);
            body = legs.get(id + direction);
            if (body == null) {
              status = 404;
              body = Map.of("code", "LEG_NOT_FOUND", "message", "Absent");
            }
          }
          byte[] bytes = ContractJson.write(body).getBytes(StandardCharsets.UTF_8);
          exchange.getResponseHeaders().add("Content-Type", "application/json");
          exchange.sendResponseHeaders(status, bytes.length);
          exchange.getResponseBody().write(bytes);
          exchange.close();
        });
    server.start();
    var result =
        new ParticipantVerifier("http://localhost:" + server.getAddress().getPort(), 5000, false)
            .run();
    assertThat(result.phases()).hasSize(7);
    assertThat(result.passed()).isTrue();
    assertThat(effects).hasValue(2);
    assertThat(ContractJson.parse(ParticipantVerifier.json(result))).isInstanceOf(Map.class);
  }

  @Test
  void badUrlAndMalformedJsonAreRejected() throws Exception {
    assertThat(ParticipantVerifier.validUrl("file:///tmp/bank")).isFalse();
    assertThat(ParticipantVerifier.validUrl("http://user:pass@localhost:9001")).isFalse();
    assertThat(ParticipantVerifier.validUrl("http://localhost:9001")).isTrue();
    org.assertj.core.api.Assertions.assertThatThrownBy(() -> ContractJson.parse("{bad"))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void brokenHealthProducesFailureAndContinuesOtherPhases() throws Exception {
    server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
    server.createContext(
        "/",
        exchange -> {
          byte[] bytes = "{\"status\":\"DOWN\"}".getBytes(StandardCharsets.UTF_8);
          exchange.sendResponseHeaders(200, bytes.length);
          exchange.getResponseBody().write(bytes);
          exchange.close();
        });
    server.start();
    var result =
        new ParticipantVerifier("http://localhost:" + server.getAddress().getPort(), 5000, false)
            .run();
    assertThat(result.passed()).isFalse();
    assertThat(result.phases()).hasSize(7);
    assertThat(result.phases().get(0).details())
        .anySatisfy(s -> assertThat(s).contains("status=UP"));
  }

  @Test
  void verboseGetShowsEmptyRequestBody() throws Exception {
    server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
    server.createContext(
        "/",
        exchange -> {
          byte[] bytes = "{\"status\":\"UP\"}".getBytes(StandardCharsets.UTF_8);
          exchange.sendResponseHeaders(200, bytes.length);
          exchange.getResponseBody().write(bytes);
          exchange.close();
        });
    server.start();
    var result =
        new ParticipantVerifier("http://localhost:" + server.getAddress().getPort(), 5000, true)
            .run();
    assertThat(result.phases().get(0).details())
        .anySatisfy(s -> assertThat(s).contains(" request={} response=200 "));
  }

  @Test
  void unreachableParticipantReportsConnectionFailure() {
    var result = new ParticipantVerifier("http://localhost:1", 100, false).run();
    assertThat(result.passed()).isFalse();
    assertThat(result.phases()).hasSize(7);
    assertThat(result.phases().get(0).details())
        .anySatisfy(
            s -> assertThat(s).contains("GET http://localhost:1/finsim/v1/institution/health"));
  }

  @Test
  void slowParticipantReportsTimeout() throws Exception {
    server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
    server.createContext(
        "/",
        exchange -> {
          try {
            Thread.sleep(250);
          } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
          }
          exchange.close();
        });
    server.start();
    var result =
        new ParticipantVerifier("http://localhost:" + server.getAddress().getPort(), 20, false)
            .run();
    assertThat(result.passed()).isFalse();
    assertThat(result.phases().get(0).details())
        .anySatisfy(s -> assertThat(s.toLowerCase()).contains("timed out"));
  }
}
