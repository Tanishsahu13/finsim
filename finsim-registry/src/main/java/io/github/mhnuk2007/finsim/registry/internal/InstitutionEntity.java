package io.github.mhnuk2007.finsim.registry.internal;

import io.github.mhnuk2007.finsim.registry.Capability;
import io.github.mhnuk2007.finsim.registry.Institution;
import io.github.mhnuk2007.finsim.registry.InstitutionStatus;
import io.github.mhnuk2007.finsim.registry.InstitutionType;
import io.github.mhnuk2007.finsim.registry.RegisterInstitution;
import io.github.mhnuk2007.finsim.registry.Registry;
import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.util.HashSet;
import java.util.Set;

@Entity
@Table(name = "registry_institutions")
public class InstitutionEntity {

  @Id
  @Column(name = "institution_id")
  private String id;

  @Column(nullable = false)
  private String name;

  @Enumerated(EnumType.STRING)
  @Column(name = "institution_type", nullable = false)
  private InstitutionType type;

  @Column(name = "base_url", nullable = false)
  private String baseUrl;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false)
  private InstitutionStatus status;

  @Column(nullable = false)
  private String provenance = "SYNTHETIC";

  @ElementCollection(fetch = FetchType.EAGER)
  @CollectionTable(
      name = "registry_institution_capabilities",
      joinColumns = @JoinColumn(name = "institution_id"))
  @Enumerated(EnumType.STRING)
  @Column(name = "capability", nullable = false)
  private Set<Capability> capabilities = new HashSet<>();

  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;

  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt;

  @Version private Long version;

  protected InstitutionEntity() {}

  public static InstitutionEntity from(RegisterInstitution command) {
    InstitutionEntity entity = new InstitutionEntity();
    entity.id = command.id();
    entity.name = command.name().trim();
    entity.type = command.type();
    entity.baseUrl = command.baseUrl().trim();
    entity.status = InstitutionStatus.ACTIVE;
    entity.capabilities = new HashSet<>(command.capabilities());
    return entity;
  }

  public boolean sameDefinitionAs(RegisterInstitution command) {
    return name.equals(command.name().trim())
        && type == command.type()
        && baseUrl.equals(command.baseUrl().trim())
        && capabilities.equals(command.capabilities());
  }

  public Institution toDomain() {
    return new Institution(
        id, name, type, baseUrl, Registry.sorted(capabilities), status, provenance);
  }

  @PrePersist
  void onCreate() {
    createdAt = Instant.now();
    updatedAt = createdAt;
  }

  @PreUpdate
  void onUpdate() {
    updatedAt = Instant.now();
  }
}
