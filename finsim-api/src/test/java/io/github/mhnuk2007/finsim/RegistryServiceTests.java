package io.github.mhnuk2007.finsim;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.mhnuk2007.finsim.registry.Capability;
import io.github.mhnuk2007.finsim.registry.InstitutionType;
import io.github.mhnuk2007.finsim.registry.RegisterInstitution;
import io.github.mhnuk2007.finsim.registry.Registry;
import io.github.mhnuk2007.finsim.registry.RegistryExceptions.CapabilityNotSupported;
import io.github.mhnuk2007.finsim.registry.RegistryExceptions.InstitutionAlreadyExists;
import io.github.mhnuk2007.finsim.registry.RegistryExceptions.InvalidInstitution;
import io.github.mhnuk2007.finsim.registry.RegistryExceptions.NotAMember;
import io.github.mhnuk2007.finsim.registry.Scheme;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

@SpringBootTest
@Import(TestcontainersConfiguration.class)
class RegistryServiceTests {

  @Autowired Registry registry;

  private static String uniqueId() {
    return "t" + UUID.randomUUID().toString().replace("-", "").substring(0, 12);
  }

  private static RegisterInstitution bank(String id, Set<Capability> capabilities) {
    return new RegisterInstitution(
        id, "Test bank", InstitutionType.BANK, "http://localhost:9999", capabilities);
  }

  @Test
  void theSandboxNetworkIsSeededWithBothSampleBanks() {
    var sandbox = registry.network("sandbox");

    assertThat(sandbox.memberIds()).contains("bank-a", "bank-b");
    assertThat(sandbox.currencies()).containsExactly("USD");
    assertThat(sandbox.scheme()).isEqualTo(Scheme.SANDBOX_V1);
    assertThat(sandbox.provenance()).isEqualTo("SYNTHETIC");
  }

  @Test
  void registeringTwiceIsIdempotentAndAConflictingDefinitionIsRejected() {
    String id = uniqueId();
    Set<Capability> all =
        Set.of(Capability.ACCOUNT_DEBIT, Capability.ACCOUNT_CREDIT, Capability.STATUS_QUERY);

    var first = registry.register(bank(id, all));
    var replay = registry.register(bank(id, all));

    assertThat(first.created()).isTrue();
    assertThat(replay.created()).isFalse();
    assertThat(replay.institution()).isEqualTo(first.institution());
    assertThatThrownBy(() -> registry.register(bank(id, Set.of(Capability.ACCOUNT_CREDIT))))
        .isInstanceOf(InstitutionAlreadyExists.class);
  }

  @Test
  void invalidRegistrationsAreRejected() {
    Set<Capability> all = Set.of(Capability.ACCOUNT_CREDIT);

    assertThatThrownBy(() -> registry.register(bank("Bad ID!", all)))
        .isInstanceOf(InvalidInstitution.class);
    assertThatThrownBy(() -> registry.register(bank(uniqueId(), Set.of())))
        .isInstanceOf(InvalidInstitution.class);
    assertThatThrownBy(
            () ->
                registry.register(
                    new RegisterInstitution(
                        uniqueId(), "x", InstitutionType.BANK, "http://169.254.169.254", all)))
        .isInstanceOf(InvalidInstitution.class);
  }

  @Test
  void routingRequiresMembershipAndADeclaredCapability() {
    String id = uniqueId();
    registry.register(bank(id, Set.of(Capability.ACCOUNT_CREDIT)));

    assertThatThrownBy(() -> registry.requireRoutable("sandbox", id, Capability.ACCOUNT_CREDIT))
        .isInstanceOf(NotAMember.class);

    registry.addMember("sandbox", id);

    assertThat(registry.requireRoutable("sandbox", id, Capability.ACCOUNT_CREDIT).id())
        .isEqualTo(id);
    assertThatThrownBy(() -> registry.requireRoutable("sandbox", id, Capability.ACCOUNT_DEBIT))
        .isInstanceOf(CapabilityNotSupported.class)
        .hasMessageContaining("ACCOUNT_DEBIT");
  }

  @Test
  void seededBanksAreRoutableForDebitAndCredit() {
    assertThat(registry.requireRoutable("sandbox", "bank-a", Capability.ACCOUNT_DEBIT)).isNotNull();
    assertThat(registry.requireRoutable("sandbox", "bank-b", Capability.ACCOUNT_CREDIT))
        .isNotNull();
  }

  @Test
  void addingAMemberTwiceIsHarmless() {
    String id = uniqueId();
    registry.register(bank(id, Set.of(Capability.ACCOUNT_CREDIT)));

    registry.addMember("sandbox", id);
    var again = registry.addMember("sandbox", id);

    assertThat(again.memberIds().stream().filter(id::equals).count()).isEqualTo(1);
  }
}
