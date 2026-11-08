package io.github.mhnuk2007.finsim.cli;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpRequest.BodyPublishers;
import java.net.http.HttpResponse.BodyHandlers;
import java.time.Duration;

/** Minimal HTTP client for FinSim's administration API. */
final class FinSimClient {

  record Response(int status, String body) {
    boolean ok() {
      return status >= 200 && status < 300;
    }
  }

  private final HttpClient http = HttpClient.newHttpClient();
  private final String baseUrl;

  FinSimClient(String baseUrl) {
    this.baseUrl = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
  }

  String baseUrl() {
    return baseUrl;
  }

  Response get(String path) throws IOException, InterruptedException {
    HttpRequest request =
        HttpRequest.newBuilder(URI.create(baseUrl + path))
            .timeout(Duration.ofSeconds(10))
            .header("Accept", "application/json")
            .GET()
            .build();
    return send(request);
  }

  Response post(String path, String json) throws IOException, InterruptedException {
    HttpRequest request =
        HttpRequest.newBuilder(URI.create(baseUrl + path))
            .timeout(Duration.ofSeconds(10))
            .header("Content-Type", "application/json")
            .header("Accept", "application/json")
            .POST(BodyPublishers.ofString(json))
            .build();
    return send(request);
  }

  private Response send(HttpRequest request) throws IOException, InterruptedException {
    var response = http.send(request, BodyHandlers.ofString());
    return new Response(response.statusCode(), response.body());
  }
}
