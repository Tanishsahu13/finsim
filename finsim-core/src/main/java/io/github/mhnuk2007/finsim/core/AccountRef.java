package io.github.mhnuk2007.finsim.core;

import java.util.Objects;

/**
 * Identifies an account at a specific institution.
 *
 * @param institutionId the unique identifier of the institution (e.g. bank-a)
 * @param accountReference the institution-local account reference (e.g. ACC-1)
 */
public record AccountRef(String institutionId, String accountReference) {

  public AccountRef {
    Objects.requireNonNull(institutionId, "institutionId must not be null");
    Objects.requireNonNull(accountReference, "accountReference must not be null");
    if (institutionId.isBlank()) {
      throw new IllegalArgumentException("institutionId must not be blank");
    }
    if (accountReference.isBlank()) {
      throw new IllegalArgumentException("accountReference must not be blank");
    }
  }

  public static AccountRef of(String institutionId, String accountReference) {
    return new AccountRef(institutionId, accountReference);
  }
}
