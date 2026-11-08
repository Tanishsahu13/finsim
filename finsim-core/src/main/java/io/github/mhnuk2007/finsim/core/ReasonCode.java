package io.github.mhnuk2007.finsim.core;

import java.util.Arrays;
import java.util.Collections;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Rejection reason codes for transfer legs and network rejection.
 *
 * <p>Every rejection of a transfer must carry a reason code and indicate which entity emitted it
 * (FINSIM, SOURCE, or DESTINATION).
 *
 * <p>API-level HTTP errors (such as {@code IDEMPOTENCY_CONFLICT} or {@code
 * RECONCILIATION_REQUIRED}) belong to {@link ApiErrorCode}, not transfer rejection.
 */
public enum ReasonCode {

  // ISO 20022 reason codes
  AC01("AC01", "Incorrect account number", true),
  AC04("AC04", "Closed account number", true),
  AM02("AM02", "Transaction amount outside allowed limits", true),
  AM04("AM04", "Insufficient funds", true),
  DUPL("DUPL", "Duplicate payment", true),
  MS03("MS03", "Not specified reason agent generated", true),

  // FinSim-specific transfer rejection codes
  FINSIM_TIMEOUT("FINSIM_TIMEOUT", "FinSim outbound call timed out", false),
  CAPABILITY_NOT_SUPPORTED(
      "CAPABILITY_NOT_SUPPORTED", "Institution lacks declared capability for leg", false),
  ROUTING_ERROR("ROUTING_ERROR", "Institution or currency not routable on network", false),
  INVALID_ACCOUNT(
      "INVALID_ACCOUNT", "Source and destination accounts must not be identical", false);

  private static final Map<String, ReasonCode> BY_CODE =
      Collections.unmodifiableMap(
          Arrays.stream(values()).collect(Collectors.toMap(ReasonCode::code, Function.identity())));

  private final String code;
  private final String description;
  private final boolean iso20022;

  ReasonCode(String code, String description, boolean iso20022) {
    this.code = code;
    this.description = description;
    this.iso20022 = iso20022;
  }

  public String code() {
    return code;
  }

  public String description() {
    return description;
  }

  public boolean isIso20022() {
    return iso20022;
  }

  public boolean isRejectionReason() {
    return true;
  }

  /**
   * Look up a {@link ReasonCode} by its code string.
   *
   * @param code the code string (e.g. "AC04" or "FINSIM_TIMEOUT")
   * @return optional containing the matching enum value, or empty if unknown
   */
  public static Optional<ReasonCode> find(String code) {
    if (code == null) {
      return Optional.empty();
    }
    return Optional.ofNullable(BY_CODE.get(code));
  }

  /**
   * Checks whether the given code string is a known registered rejection reason code.
   *
   * @param code the code string
   * @return {@code true} if registered
   */
  public static boolean isKnown(String code) {
    return code != null && BY_CODE.containsKey(code);
  }
}
