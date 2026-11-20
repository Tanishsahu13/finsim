package io.github.mhnuk2007.finsim.cli;

import java.io.IOException;
import java.util.List;
import java.util.concurrent.Callable;
import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Mixin;
import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine.Option;
import picocli.CommandLine.Spec;

/** The {@code finsim} command line. */
@Command(
    name = "finsim",
    mixinStandardHelpOptions = true,
    version = "finsim 0.1.0-SNAPSHOT",
    description = "Simulate financial networks: connect services, run transfers, inject faults.",
    subcommands = {
      FinSimCli.Register.class,
      FinSimCli.Institutions.class,
      FinSimCli.Networks.class,
      FinSimCli.Join.class,
      FinSimCli.Verify.class,
      FinSimCli.Transfer.class,
      FinSimCli.Trace.class,
      FinSimCli.Fault.class
    })
public class FinSimCli implements Runnable {

  static final int EXIT_FAILED = 1;
  static final int EXIT_NOT_IMPLEMENTED = 2;

  public static void main(String[] args) {
    System.exit(new CommandLine(new FinSimCli()).execute(args));
  }

  @Override
  public void run() {
    CommandLine.usage(this, System.out);
  }

  /** Where FinSim is running. */
  static class Connection {
    @Option(
        names = "--finsim",
        description = "FinSim base URL (env FINSIM_URL). Default: ${DEFAULT-VALUE}",
        defaultValue = "${env:FINSIM_URL:-http://localhost:8080}")
    String finsimUrl;

    FinSimClient client() {
      return new FinSimClient(finsimUrl);
    }
  }

  /**
   * Runs a request, prints the JSON on success or the error on failure, and picks the exit code.
   */
  abstract static class ApiCommand implements Callable<Integer> {
    @Mixin Connection connection = new Connection();
    @Spec CommandSpec spec;

    @Option(
        names = {"--json", "-j"},
        description = "Output raw JSON")
    boolean jsonMode;

    abstract FinSimClient.Response execute(FinSimClient client)
        throws IOException, InterruptedException;

    @Override
    public Integer call() {
      FinSimClient client = connection.client();
      try {
        FinSimClient.Response response = execute(client);
        if (response.ok()) {
          printSuccess(response.body());
          return 0;
        }
        spec.commandLine()
            .getErr()
            .println("FinSim answered HTTP " + response.status() + ": " + response.body());
        return EXIT_FAILED;
      } catch (IOException e) {
        spec.commandLine()
            .getErr()
            .println(
                "Cannot reach FinSim at "
                    + client.baseUrl()
                    + ". Is it running? ("
                    + e.getMessage()
                    + ")");
        return EXIT_FAILED;
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
        return EXIT_FAILED;
      }
    }

    /**
     * Prints a successful response. JSON mode pins the machine-readable contract: the raw payload
     * only, with nothing else on stdout. The default output is the same payload today (the CLI has
     * no table formatting yet); when human-readable formatting is added it must branch on {@code
     * jsonMode} and keep this path raw.
     */
    private void printSuccess(String body) {
      spec.commandLine().getOut().println(body);
    }
  }

  @Command(
      name = "register",
      mixinStandardHelpOptions = true,
      description = "Register an institution with FinSim (idempotent by id).")
  static class Register extends ApiCommand {
    @Option(names = "--id", required = true, description = "Short id, for example bank-a.")
    String id;

    @Option(names = "--name", required = true, description = "Display name.")
    String name;

    @Option(
        names = "--type",
        defaultValue = "BANK",
        description = "BANK, MERCHANT, PAYMENT_GATEWAY, or CUSTOM. Default: ${DEFAULT-VALUE}")
    String type;

    @Option(
        names = "--base-url",
        required = true,
        description = "Where the institution serves the finsim/v1 contract.")
    String baseUrl;

    @Option(
        names = "--capabilities",
        required = true,
        split = ",",
        description = "Comma-separated: ACCOUNT_DEBIT, ACCOUNT_CREDIT, STATUS_QUERY.")
    List<String> capabilities;

    static String body(
        String id, String name, String type, String baseUrl, List<String> capabilities) {
      List<String> trimmedCapabilities =
          capabilities == null
              ? List.of()
              : capabilities.stream().map(String::trim).filter(s -> !s.isEmpty()).toList();
      return "{\"id\":"
          + Json.quote(id)
          + ",\"name\":"
          + Json.quote(name)
          + ",\"type\":"
          + Json.quote(type)
          + ",\"baseUrl\":"
          + Json.quote(baseUrl)
          + ",\"capabilities\":"
          + Json.array(trimmedCapabilities)
          + "}";
    }

    @Override
    FinSimClient.Response execute(FinSimClient client) throws IOException, InterruptedException {
      return client.post("/api/v1/institutions", body(id, name, type, baseUrl, capabilities));
    }
  }

  @Command(
      name = "institutions",
      mixinStandardHelpOptions = true,
      description = "List registered institutions, or show one with --id.")
  static class Institutions extends ApiCommand {
    @Option(names = "--id", description = "Show a single institution.")
    String id;

    @Override
    FinSimClient.Response execute(FinSimClient client) throws IOException, InterruptedException {
      return client.get(id == null ? "/api/v1/institutions" : "/api/v1/institutions/" + id);
    }
  }

  @Command(
      name = "networks",
      mixinStandardHelpOptions = true,
      description = "List networks, or show one with --id.")
  static class Networks extends ApiCommand {
    @Option(names = "--id", description = "Show a single network.")
    String id;

    @Override
    FinSimClient.Response execute(FinSimClient client) throws IOException, InterruptedException {
      return client.get(id == null ? "/api/v1/networks" : "/api/v1/networks/" + id);
    }
  }

  @Command(
      name = "join",
      mixinStandardHelpOptions = true,
      description = "Add a registered institution to a network.")
  static class Join extends ApiCommand {
    @Option(names = "--network", required = true, description = "Network id, for example sandbox.")
    String network;

    @Option(names = "--institution", required = true, description = "Institution id.")
    String institution;

    @Override
    FinSimClient.Response execute(FinSimClient client) throws IOException, InterruptedException {
      return client.post(
          "/api/v1/networks/" + network + "/members",
          "{\"institutionId\":" + Json.quote(institution) + "}");
    }
  }

  /** Runs the public participant contract checks. */
  @Command(
      name = "verify",
      mixinStandardHelpOptions = true,
      description = "Check an institution against the finsim/v1 contract.")
  static class Verify implements Callable<Integer> {
    @Spec CommandSpec spec;

    @Option(names = "--url", required = true, description = "Participant base URL.")
    String url;

    @Option(names = "--verbose", description = "Show sanitized HTTP exchanges.")
    boolean verbose;

    @Option(
        names = {"--json", "-j"},
        description = "Output machine-readable results.")
    boolean json;

    @Option(
        names = "--timeout-ms",
        defaultValue = "5000",
        description = "Request timeout in milliseconds.")
    int timeoutMs;

    @Override
    public Integer call() {
      if (timeoutMs <= 0 || !ParticipantVerifier.validUrl(url)) {
        spec.commandLine().getErr().println("Invalid --url or --timeout-ms (must be positive).");
        return CommandLine.ExitCode.USAGE;
      }
      if (!json)
        spec.commandLine()
            .getOut()
            .println("Warning: successful checks can change synthetic account balances.");
      var result = new ParticipantVerifier(url, timeoutMs, verbose).run();
      if (json) {
        spec.commandLine().getOut().println(ParticipantVerifier.json(result));
        if (!result.passed())
          spec.commandLine().getErr().println("Participant verification failed.");
      } else {
        spec.commandLine()
            .getOut()
            .println("FinSim Participant Conformance Verification\nTarget: " + url);
        for (var phase : result.phases()) {
          spec.commandLine().getOut().println("[" + phase.status() + "] " + phase.name());
          for (String detail : phase.details()) spec.commandLine().getOut().println("  " + detail);
        }
        spec.commandLine()
            .getOut()
            .println(
                result.passed()
                    ? "All 7 conformance phases PASSED."
                    : "Participant verification FAILED.");
      }
      return result.passed() ? 0 : EXIT_FAILED;
    }
  }

  /** Placeholder: arrives with milestone 1, issue 19. */
  @Command(
      name = "transfer",
      mixinStandardHelpOptions = true,
      description = "Start a transfer between two institutions.")
  static class Transfer extends NotImplemented {}

  /** Placeholder: arrives with milestone 1, issue 19. */
  @Command(
      name = "trace",
      mixinStandardHelpOptions = true,
      description = "Show every stage, call, and fault for a transfer.")
  static class Trace extends NotImplemented {}

  /** Placeholder: arrives with milestone 1, issue 19. */
  @Command(
      name = "fault",
      mixinStandardHelpOptions = true,
      description = "Add or remove fault rules.")
  static class Fault extends NotImplemented {}

  abstract static class NotImplemented implements Callable<Integer> {
    @Override
    public Integer call() {
      System.err.println("Not implemented yet. See docs/milestone-1-issues.md.");
      return EXIT_NOT_IMPLEMENTED;
    }
  }
}
