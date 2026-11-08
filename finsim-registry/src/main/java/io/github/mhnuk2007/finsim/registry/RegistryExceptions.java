package io.github.mhnuk2007.finsim.registry;

/** The concrete registry failures. */
public final class RegistryExceptions {

  private RegistryExceptions() {}

  public static class InvalidInstitution extends RegistryException {
    public InvalidInstitution(String message) {
      super("INVALID_INSTITUTION", message);
    }
  }

  public static class InstitutionNotFound extends RegistryException {
    public InstitutionNotFound(String id) {
      super("INSTITUTION_NOT_FOUND", "No institution with id '" + id + "'.");
    }
  }

  public static class InstitutionAlreadyExists extends RegistryException {
    public InstitutionAlreadyExists(String id) {
      super(
          "INSTITUTION_ALREADY_EXISTS",
          "An institution with id '" + id + "' already exists with a different definition.");
    }
  }

  public static class NetworkNotFound extends RegistryException {
    public NetworkNotFound(String id) {
      super("NETWORK_NOT_FOUND", "No network with id '" + id + "'.");
    }
  }

  public static class NotAMember extends RegistryException {
    public NotAMember(String networkId, String institutionId) {
      super(
          "NOT_A_MEMBER",
          "Institution '" + institutionId + "' is not a member of network '" + networkId + "'.");
    }
  }

  public static class CapabilityNotSupported extends RegistryException {
    public CapabilityNotSupported(String institutionId, Capability capability) {
      super(
          "CAPABILITY_NOT_SUPPORTED",
          "Institution '" + institutionId + "' has not declared capability " + capability + ".");
    }
  }
}
