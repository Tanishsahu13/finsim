package io.github.mhnuk2007.finsim.faults.internal;

import io.github.mhnuk2007.finsim.sim.FaultRule;
import io.github.mhnuk2007.finsim.sim.FaultType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;

@Entity
@Table(name = "fault_rules")
public class FaultRuleEntity {

  @Id
  @Column(name = "id", length = 64)
  private String id;

  @Column(name = "target_institution", nullable = false, length = 64)
  private String targetInstitution;

  @Enumerated(EnumType.STRING)
  @Column(name = "fault_type", nullable = false, length = 32)
  private FaultType faultType;

  @Column(name = "delay_ms")
  private Integer delayMs;

  @Column(name = "probability", nullable = false)
  private double probability = 1.0;

  @Column(name = "remaining_count")
  private Integer remainingCount;

  @Column(name = "seed")
  private Long seed;

  @Version
  @Column(name = "version", nullable = false)
  private long version;

  @Column(name = "created_at", nullable = false)
  private Instant createdAt;

  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt;

  protected FaultRuleEntity() {}

  public FaultRuleEntity(
      String id,
      String targetInstitution,
      FaultType faultType,
      Integer delayMs,
      double probability,
      Integer remainingCount,
      Long seed,
      Instant now) {
    this.id = id;
    this.targetInstitution = targetInstitution;
    this.faultType = faultType;
    this.delayMs = delayMs;
    this.probability = probability;
    this.remainingCount = remainingCount;
    this.seed = seed;
    this.createdAt = now;
    this.updatedAt = now;
  }

  public String getId() {
    return id;
  }

  public String getTargetInstitution() {
    return targetInstitution;
  }

  public FaultType getFaultType() {
    return faultType;
  }

  public Integer getDelayMs() {
    return delayMs;
  }

  public double getProbability() {
    return probability;
  }

  public Integer getRemainingCount() {
    return remainingCount;
  }

  public void setRemainingCount(Integer remainingCount) {
    this.remainingCount = remainingCount;
    this.updatedAt = Instant.now();
  }

  public Long getSeed() {
    return seed;
  }

  public long getVersion() {
    return version;
  }

  public Instant getCreatedAt() {
    return createdAt;
  }

  public Instant getUpdatedAt() {
    return updatedAt;
  }

  public boolean isActive() {
    return remainingCount == null || remainingCount > 0;
  }

  public FaultRule toDomain() {
    return new FaultRule(
        id, targetInstitution, faultType, delayMs, probability, remainingCount, seed);
  }
}
