package io.github.mhnuk2007.finsim.registry;

/** Base type for registry failures. Each carries a stable, machine-readable code. */
public abstract class RegistryException extends RuntimeException {

  private final String code;

  protected RegistryException(String code, String message) {
    super(message);
    this.code = code;
  }

  public String code() {
    return code;
  }
}
