package io.github.mhnuk2007.finsim.registry;

import io.github.mhnuk2007.finsim.registry.RegistryExceptions.CapabilityNotSupported;
import io.github.mhnuk2007.finsim.registry.RegistryExceptions.InstitutionAlreadyExists;
import io.github.mhnuk2007.finsim.registry.RegistryExceptions.InstitutionNotFound;
import io.github.mhnuk2007.finsim.registry.RegistryExceptions.InvalidInstitution;
import io.github.mhnuk2007.finsim.registry.RegistryExceptions.NetworkNotFound;
import io.github.mhnuk2007.finsim.registry.RegistryExceptions.NotAMember;
import io.github.mhnuk2007.finsim.registry.internal.BaseUrlValidator;
import io.github.mhnuk2007.finsim.registry.internal.InstitutionEntity;
import io.github.mhnuk2007.finsim.registry.internal.InstitutionRepository;
import io.github.mhnuk2007.finsim.registry.internal.NetworkEntity;
import io.github.mhnuk2007.finsim.registry.internal.NetworkRepository;
import io.github.mhnuk2007.finsim.registry.internal.RegistryProperties;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Pattern;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** The registry's public entry point: institutions, networks, membership, routing checks. */
@Service
@Transactional
public class Registry {

  private static final Pattern SLUG = Pattern.compile("[a-z0-9][a-z0-9-]{1,62}");
  private static final Pattern CURRENCY = Pattern.compile("[A-Z]{3}");

  private final InstitutionRepository institutions;
  private final NetworkRepository networks;
  private final RegistryProperties properties;

  public Registry(
      InstitutionRepository institutions,
      NetworkRepository networks,
      RegistryProperties properties) {
    this.institutions = institutions;
    this.networks = networks;
    this.properties = properties;
  }

  /**
   * Registers an institution. Idempotent by id: an identical registration returns the existing
   * institution ({@code created = false}); a different definition for an existing id is a conflict.
   */
  public RegistrationResult register(RegisterInstitution command) {
    validate(command);
    Optional<InstitutionEntity> existing = institutions.findById(command.id());
    if (existing.isPresent()) {
      if (existing.get().sameDefinitionAs(command)) {
        return new RegistrationResult(existing.get().toDomain(), false);
      }
      throw new InstitutionAlreadyExists(command.id());
    }
    InstitutionEntity saved = institutions.save(InstitutionEntity.from(command));
    return new RegistrationResult(saved.toDomain(), true);
  }

  @Transactional(readOnly = true)
  public List<Institution> institutions() {
    return institutions.findAll(Sort.by("id")).stream().map(InstitutionEntity::toDomain).toList();
  }

  @Transactional(readOnly = true)
  public Institution institution(String id) {
    return institutions.findById(id).orElseThrow(() -> new InstitutionNotFound(id)).toDomain();
  }

  /** Creates the network if it does not exist yet; otherwise returns the existing one. */
  public Network ensureNetwork(String id, String name, Set<String> currencies) {
    if (id == null || !SLUG.matcher(id).matches()) {
      throw new InvalidInstitution("Network id must be lowercase letters, digits, and hyphens.");
    }
    if (currencies == null || currencies.isEmpty()) {
      throw new InvalidInstitution("A network needs at least one currency.");
    }
    for (String currency : currencies) {
      if (currency == null || !CURRENCY.matcher(currency).matches()) {
        throw new InvalidInstitution("Currency must be three uppercase letters: " + currency);
      }
    }
    return networks
        .findById(id)
        .orElseGet(() -> networks.save(NetworkEntity.create(id, name, currencies)))
        .toDomain();
  }

  @Transactional(readOnly = true)
  public List<Network> networks() {
    return networks.findAll(Sort.by("id")).stream().map(NetworkEntity::toDomain).toList();
  }

  @Transactional(readOnly = true)
  public Network network(String id) {
    return networks.findById(id).orElseThrow(() -> new NetworkNotFound(id)).toDomain();
  }

  /** Adds an institution to a network. Idempotent. */
  public Network addMember(String networkId, String institutionId) {
    NetworkEntity network =
        networks.findById(networkId).orElseThrow(() -> new NetworkNotFound(networkId));
    if (!institutions.existsById(institutionId)) {
      throw new InstitutionNotFound(institutionId);
    }
    network.addMember(institutionId);
    return networks.save(network).toDomain();
  }

  /**
   * Routing check: the institution must be an active member of the network and must have declared
   * the capability. FinSim never routes a call to an institution that hasn't declared it.
   */
  @Transactional(readOnly = true)
  public Institution requireRoutable(
      String networkId, String institutionId, Capability capability) {
    Network network = network(networkId);
    if (!network.memberIds().contains(institutionId)) {
      throw new NotAMember(networkId, institutionId);
    }
    Institution institution = institution(institutionId);
    if (institution.status() != InstitutionStatus.ACTIVE
        || !institution.capabilities().contains(capability)) {
      throw new CapabilityNotSupported(institutionId, capability);
    }
    return institution;
  }

  // ---------------------------------------------------------------------------------------------

  private void validate(RegisterInstitution c) {
    if (c == null) {
      throw new InvalidInstitution("Registration is required.");
    }
    if (c.id() == null || !SLUG.matcher(c.id()).matches()) {
      throw new InvalidInstitution(
          "id must be 2 to 63 characters: lowercase letters, digits, and hyphens, starting with a letter or digit.");
    }
    if (c.name() == null || c.name().isBlank() || c.name().length() > 100) {
      throw new InvalidInstitution("name is required (up to 100 characters).");
    }
    if (c.type() == null) {
      throw new InvalidInstitution("type is required.");
    }
    if (c.capabilities() == null || c.capabilities().isEmpty()) {
      throw new InvalidInstitution("At least one capability must be declared.");
    }
    BaseUrlValidator.validate(c.baseUrl(), properties.allowPrivateAddresses());
  }

  /** Sorted copy, used by entities when building public records. */
  public static <T extends Comparable<T>> Set<T> sorted(Set<T> values) {
    return java.util.Collections.unmodifiableSet(new TreeSet<>(values));
  }
}
