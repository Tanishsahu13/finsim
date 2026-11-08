package io.github.mhnuk2007.finsim.transfer.internal;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

@Entity
@Table(name = "transfer_stages")
public class TransferStageEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(name = "transfer_id", nullable = false, length = 64)
  private String transferId;

  @Column(name = "stage", nullable = false, length = 64)
  private String stage;

  @Column(name = "state", nullable = false, length = 32)
  private String state;

  @Column(name = "details")
  private String details;

  @Column(name = "created_at", nullable = false)
  private Instant createdAt;

  protected TransferStageEntity() {}

  public static TransferStageEntity of(
      String transferId, String stage, String state, String details, Instant createdAt) {
    TransferStageEntity entity = new TransferStageEntity();
    entity.transferId = transferId;
    entity.stage = stage;
    entity.state = state;
    entity.details = details;
    entity.createdAt = createdAt;
    return entity;
  }

  public Long getId() {
    return id;
  }

  public String getTransferId() {
    return transferId;
  }

  public String getStage() {
    return stage;
  }

  public String getState() {
    return state;
  }

  public String getDetails() {
    return details;
  }

  public Instant getCreatedAt() {
    return createdAt;
  }
}
