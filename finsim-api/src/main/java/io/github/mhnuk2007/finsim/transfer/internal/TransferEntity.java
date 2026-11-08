package io.github.mhnuk2007.finsim.transfer.internal;

import io.github.mhnuk2007.finsim.core.AccountRef;
import io.github.mhnuk2007.finsim.core.Emitter;
import io.github.mhnuk2007.finsim.core.Money;
import io.github.mhnuk2007.finsim.core.PendingAction;
import io.github.mhnuk2007.finsim.core.Provenance;
import io.github.mhnuk2007.finsim.core.StageEntry;
import io.github.mhnuk2007.finsim.core.StageOutcome;
import io.github.mhnuk2007.finsim.core.Transfer;
import io.github.mhnuk2007.finsim.core.TransferState;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

@Entity
@Table(name = "transfers")
public class TransferEntity {

  @Id
  @Column(name = "id", length = 64)
  private String id;

  @Column(name = "network_id", nullable = false, length = 64)
  private String networkId;

  @Column(name = "idempotency_key", nullable = false, unique = true, length = 128)
  private String idempotencyKey;

  @Enumerated(EnumType.STRING)
  @Column(name = "state", nullable = false, length = 32)
  private TransferState state;

  @Column(name = "current_stage", length = 64)
  private String currentStage;

  @Column(name = "from_institution", nullable = false, length = 64)
  private String fromInstitution;

  @Column(name = "from_account", nullable = false, length = 64)
  private String fromAccount;

  @Column(name = "to_institution", nullable = false, length = 64)
  private String toInstitution;

  @Column(name = "to_account", nullable = false, length = 64)
  private String toAccount;

  @Column(name = "amount", nullable = false, precision = 18, scale = 4)
  private BigDecimal amount;

  @Column(name = "currency", nullable = false, length = 3)
  private String currency;

  @Column(name = "reference", length = 140)
  private String reference;

  @Column(name = "reason_code", length = 32)
  private String reasonCode;

  @Enumerated(EnumType.STRING)
  @Column(name = "emitted_by", length = 32)
  private Emitter emittedBy;

  @Enumerated(EnumType.STRING)
  @Column(name = "pending_action", length = 32)
  private PendingAction pendingAction;

  @Column(name = "needs_manual_review", nullable = false)
  private boolean needsManualReview;

  @Column(name = "provenance", nullable = false, length = 32)
  private String provenance;

  @Version
  @Column(name = "version", nullable = false)
  private Long version;

  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;

  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt;

  protected TransferEntity() {}

  public static TransferEntity from(Transfer transfer) {
    TransferEntity entity = new TransferEntity();
    entity.id = transfer.getId();
    entity.networkId = transfer.getNetworkId();
    entity.idempotencyKey = transfer.getIdempotencyKey();
    entity.state = transfer.getState();
    entity.currentStage = transfer.getCurrentStage();
    entity.fromInstitution = transfer.getFrom().institutionId();
    entity.fromAccount = transfer.getFrom().accountReference();
    entity.toInstitution = transfer.getTo().institutionId();
    entity.toAccount = transfer.getTo().accountReference();
    entity.amount = transfer.getMoney().amount();
    entity.currency = transfer.getMoney().currency();
    entity.reference = transfer.getReference();
    entity.reasonCode = transfer.getReasonCode();
    entity.emittedBy = transfer.getEmittedBy();
    entity.pendingAction = transfer.getPendingAction();
    entity.needsManualReview = transfer.isNeedsManualReview();
    entity.provenance = transfer.getProvenance().name();
    entity.version = null; // null signals a new entity to Spring Data JPA / Hibernate
    entity.createdAt = transfer.getCreatedAt();
    entity.updatedAt = transfer.getUpdatedAt();
    return entity;
  }

  public void updateFrom(Transfer transfer) {
    this.state = transfer.getState();
    this.currentStage = transfer.getCurrentStage();
    this.reasonCode = transfer.getReasonCode();
    this.emittedBy = transfer.getEmittedBy();
    this.pendingAction = transfer.getPendingAction();
    this.needsManualReview = transfer.isNeedsManualReview();
    this.updatedAt = transfer.getUpdatedAt();
  }

  public Transfer toDomain(List<TransferStageEntity> stageEntities) {
    List<StageEntry> history =
        stageEntities != null
            ? stageEntities.stream()
                .map(
                    s ->
                        StageEntry.of(
                            s.getStage(),
                            mapOutcome(s.getState()),
                            s.getCreatedAt(),
                            s.getDetails()))
                .toList()
            : List.of();

    return Transfer.reconstitute(
        id,
        networkId,
        idempotencyKey,
        state,
        currentStage,
        new AccountRef(fromInstitution, fromAccount),
        new AccountRef(toInstitution, toAccount),
        Money.of(amount.toPlainString(), currency),
        reference,
        reasonCode,
        emittedBy,
        pendingAction,
        needsManualReview,
        Provenance.valueOf(provenance),
        history,
        createdAt,
        updatedAt,
        version != null ? version : 0L);
  }

  private static StageOutcome mapOutcome(String stateName) {
    if ("COMPLETED".equals(stateName)
        || "ACCEPTED".equals(stateName)
        || "IN_PROGRESS".equals(stateName)
        || "CREATED".equals(stateName)
        || "PASSED".equals(stateName)) {
      return StageOutcome.PASSED;
    }
    if ("UNKNOWN".equals(stateName)) {
      return StageOutcome.UNKNOWN;
    }
    return StageOutcome.FAILED;
  }

  // Getters

  public String getId() {
    return id;
  }

  public String getNetworkId() {
    return networkId;
  }

  public String getIdempotencyKey() {
    return idempotencyKey;
  }

  public TransferState getState() {
    return state;
  }

  public String getCurrentStage() {
    return currentStage;
  }

  public String getFromInstitution() {
    return fromInstitution;
  }

  public String getFromAccount() {
    return fromAccount;
  }

  public String getToInstitution() {
    return toInstitution;
  }

  public String getToAccount() {
    return toAccount;
  }

  public BigDecimal getAmount() {
    return amount;
  }

  public String getCurrency() {
    return currency;
  }

  public String getReference() {
    return reference;
  }

  public String getReasonCode() {
    return reasonCode;
  }

  public Emitter getEmittedBy() {
    return emittedBy;
  }

  public PendingAction getPendingAction() {
    return pendingAction;
  }

  public boolean isNeedsManualReview() {
    return needsManualReview;
  }

  public String getProvenance() {
    return provenance;
  }

  public Long getVersion() {
    return version;
  }

  public Instant getCreatedAt() {
    return createdAt;
  }

  public Instant getUpdatedAt() {
    return updatedAt;
  }
}
