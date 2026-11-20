package io.github.mhnuk2007.finsim.faults;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.github.mhnuk2007.finsim.sim.FaultType;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record FaultRuleDto(
    String faultId,
    String target,
    FaultType type,
    Integer delayMs,
    Double probability,
    Integer times,
    Long seed) {

  public FaultRuleDto {
    if (probability == null) {
      probability = 1.0;
    }
  }

  public static FaultRuleDto of(
      String faultId,
      String target,
      FaultType type,
      Integer delayMs,
      Double probability,
      Integer times,
      Long seed) {
    return new FaultRuleDto(faultId, target, type, delayMs, probability, times, seed);
  }
}
