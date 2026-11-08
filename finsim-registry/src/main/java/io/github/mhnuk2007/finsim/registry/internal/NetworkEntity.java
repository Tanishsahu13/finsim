package io.github.mhnuk2007.finsim.registry.internal;

import io.github.mhnuk2007.finsim.registry.Network;
import io.github.mhnuk2007.finsim.registry.Registry;
import io.github.mhnuk2007.finsim.registry.Scheme;
import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
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
@Table(name = "registry_networks")
public class NetworkEntity {

  @Id
  @Column(name = "network_id")
  private String id;

  @Column(nullable = false)
  private String name;

  @Column(nullable = false)
  private String provenance = "SYNTHETIC";

  @ElementCollection(fetch = FetchType.EAGER)
  @CollectionTable(
      name = "registry_network_currencies",
      joinColumns = @JoinColumn(name = "network_id"))
  @Column(name = "currency", nullable = false)
  private Set<String> currencies = new HashSet<>();

  @ElementCollection(fetch = FetchType.EAGER)
  @CollectionTable(
      name = "registry_network_members",
      joinColumns = @JoinColumn(name = "network_id"))
  @Column(name = "institution_id", nullable = false)
  private Set<String> memberIds = new HashSet<>();

  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;

  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt;

  @Version private Long version;

  protected NetworkEntity() {}

  public static NetworkEntity create(String id, String name, Set<String> currencies) {
    NetworkEntity entity = new NetworkEntity();
    entity.id = id;
    entity.name = name == null || name.isBlank() ? id : name.trim();
    entity.currencies = new HashSet<>(currencies);
    return entity;
  }

  public void addMember(String institutionId) {
    memberIds.add(institutionId);
  }

  public Network toDomain() {
    return new Network(
        id,
        name,
        Registry.sorted(currencies),
        Registry.sorted(memberIds),
        Scheme.SANDBOX_V1,
        provenance);
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
