package io.github.mhnuk2007.finsim.cli;

import java.util.concurrent.Callable;
import picocli.CommandLine;
import picocli.CommandLine.Command;

/** The {@code finsim} command line. Subcommands arrive with milestone 1. */
@Command(
    name = "finsim",
    mixinStandardHelpOptions = true,
    version = "finsim 0.1.0-SNAPSHOT",
    description = "Simulate financial networks: connect services, run transfers, inject faults.",
    subcommands = {
      FinSimCli.Verify.class,
      FinSimCli.Transfer.class,
      FinSimCli.Trace.class,
      FinSimCli.Fault.class
    })
public class FinSimCli implements Runnable {

  static final int EXIT_NOT_IMPLEMENTED = 2;

  public static void main(String[] args) {
    System.exit(new CommandLine(new FinSimCli()).execute(args));
  }

  @Override
  public void run() {
    CommandLine.usage(this, System.out);
  }

  /** Placeholder: contract checks arrive with milestone 1, issue 20. */
  @Command(name = "verify", description = "Check an institution against the finsim/v1 contract.")
  static class Verify extends NotImplemented {}

  /** Placeholder: arrives with milestone 1, issue 19. */
  @Command(name = "transfer", description = "Start a transfer between two institutions.")
  static class Transfer extends NotImplemented {}

  /** Placeholder: arrives with milestone 1, issue 19. */
  @Command(name = "trace", description = "Show every stage, call, and fault for a transfer.")
  static class Trace extends NotImplemented {}

  /** Placeholder: arrives with milestone 1, issue 19. */
  @Command(name = "fault", description = "Add or remove fault rules.")
  static class Fault extends NotImplemented {}

  abstract static class NotImplemented implements Callable<Integer> {
    @Override
    public Integer call() {
      System.err.println("Not implemented yet. See docs/milestone-1-issues.md.");
      return EXIT_NOT_IMPLEMENTED;
    }
  }
}
