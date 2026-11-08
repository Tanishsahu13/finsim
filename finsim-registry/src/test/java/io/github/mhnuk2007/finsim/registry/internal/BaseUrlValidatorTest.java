package io.github.mhnuk2007.finsim.registry.internal;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.mhnuk2007.finsim.registry.RegistryExceptions.InvalidInstitution;
import org.junit.jupiter.api.Test;

class BaseUrlValidatorTest {

  @Test
  void acceptsLocalAndPublicUrlsWhenPrivateAddressesAreAllowed() {
    assertThatCode(() -> BaseUrlValidator.validate("http://localhost:9001", true))
        .doesNotThrowAnyException();
    assertThatCode(() -> BaseUrlValidator.validate("http://127.0.0.1:9001", true))
        .doesNotThrowAnyException();
    assertThatCode(() -> BaseUrlValidator.validate("http://bank-a:9001", true))
        .doesNotThrowAnyException();
    assertThatCode(() -> BaseUrlValidator.validate("https://bank.example.com/finsim", true))
        .doesNotThrowAnyException();
  }

  @Test
  void alwaysRejectsTheCloudMetadataAndLinkLocalAddresses() {
    assertThatThrownBy(
            () -> BaseUrlValidator.validate("http://169.254.169.254/latest/meta-data", true))
        .isInstanceOf(InvalidInstitution.class);
    assertThatThrownBy(() -> BaseUrlValidator.validate("http://169.254.1.1", false))
        .isInstanceOf(InvalidInstitution.class);
    assertThatThrownBy(() -> BaseUrlValidator.validate("http://[fe80::1]:8080", true))
        .isInstanceOf(InvalidInstitution.class);
    assertThatThrownBy(() -> BaseUrlValidator.validate("http://0.0.0.0:8080", true))
        .isInstanceOf(InvalidInstitution.class);
  }

  @Test
  void rejectsPrivateAndLoopbackAddressesInStrictMode() {
    assertThatThrownBy(() -> BaseUrlValidator.validate("http://localhost:9001", false))
        .isInstanceOf(InvalidInstitution.class);
    assertThatThrownBy(() -> BaseUrlValidator.validate("http://127.0.0.1", false))
        .isInstanceOf(InvalidInstitution.class);
    assertThatThrownBy(() -> BaseUrlValidator.validate("http://10.0.0.5", false))
        .isInstanceOf(InvalidInstitution.class);
    assertThatThrownBy(() -> BaseUrlValidator.validate("http://192.168.1.10", false))
        .isInstanceOf(InvalidInstitution.class);
    assertThatThrownBy(() -> BaseUrlValidator.validate("http://172.16.0.1", false))
        .isInstanceOf(InvalidInstitution.class);
    assertThatThrownBy(() -> BaseUrlValidator.validate("http://[::1]:8080", false))
        .isInstanceOf(InvalidInstitution.class);
    assertThatThrownBy(() -> BaseUrlValidator.validate("http://[fd00::1]:8080", false))
        .isInstanceOf(InvalidInstitution.class);
    assertThatCode(() -> BaseUrlValidator.validate("https://bank.example.com", false))
        .doesNotThrowAnyException();
  }

  @Test
  void rejectsMalformedUrls() {
    assertThatThrownBy(() -> BaseUrlValidator.validate(null, true))
        .isInstanceOf(InvalidInstitution.class);
    assertThatThrownBy(() -> BaseUrlValidator.validate("  ", true))
        .isInstanceOf(InvalidInstitution.class);
    assertThatThrownBy(() -> BaseUrlValidator.validate("ftp://host", true))
        .isInstanceOf(InvalidInstitution.class);
    assertThatThrownBy(() -> BaseUrlValidator.validate("file:///etc/passwd", true))
        .isInstanceOf(InvalidInstitution.class);
    assertThatThrownBy(() -> BaseUrlValidator.validate("http://user:pass@host", true))
        .isInstanceOf(InvalidInstitution.class);
    assertThatThrownBy(() -> BaseUrlValidator.validate("http://host#frag", true))
        .isInstanceOf(InvalidInstitution.class);
    assertThatThrownBy(() -> BaseUrlValidator.validate("not a url", true))
        .isInstanceOf(InvalidInstitution.class);
    assertThatThrownBy(() -> BaseUrlValidator.validate("http://", true))
        .isInstanceOf(InvalidInstitution.class);
  }
}
