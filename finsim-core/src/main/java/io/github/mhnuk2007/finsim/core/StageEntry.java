package io.github.mhnuk2007.finsim.core;

import java.time.Instant;
import java.util.Objects;

/**
 * An immutable entry in the append-only stage history of a transfer.
 *
 * @param stage the name of the stage (e.g. "created", "debit_source", "credit_destination")
 * @param outcome the outcome of the stage
 * @param at the timestamp when the stage occurred
 * @param details optional extra details or reason description
 */
public record StageEntry(String stage, StageOutcome outcome, Instant at, String details) {

  public StageEntry {
    Objects.requireNonNull(stage, "stage must not be null");
    Objects.requireNonNull(outcome, "outcome must not be null");
    Objects.requireNonNull(at, "at must not be null");
    if (stage.isBlank()) {
      throw new IllegalArgumentException("stage must not be blank");
    }
  }

  public StageEntry(String stage, StageOutcome outcome, Instant at) {
    this(stage, outcome, at, null);
  }

  public static StageEntry of(String stage, StageOutcome outcome, Instant at, String details) {
    return new StageEntry(stage, outcome, at, details);
  }

  public static StageEntry of(String stage, StageOutcome outcome, Instant at) {
    return new StageEntry(stage, outcome, at, null);
  }
}
