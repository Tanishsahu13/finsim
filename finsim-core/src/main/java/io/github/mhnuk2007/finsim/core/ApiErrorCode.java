package io.github.mhnuk2007.finsim.core;

import java.util.Arrays;
import java.util.Collections;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * API-level error codes used in HTTP responses (e.g. 400 Bad Request, 404 Not Found, 409 Conflict).
 *
 * <p>These are distinct from {@link ReasonCode}, which represent domain rejection reasons for a
 * financial transfer.
 */
public enum ApiErrorCode {
  BAD_REQUEST("BAD_REQUEST", "Invalid or missing fields, or a missing Idempotency-Key"),
  IDEMPOTENCY_CONFLICT(
      "IDEMPOTENCY_CONFLICT", "The Idempotency-Key was used with a different request body"),
  RECONCILIATION_REQUIRED(
      "RECONCILIATION_REQUIRED",
      "Transfer is in UNKNOWN state and must be resolved before retry"),
  NOT_FOUND("NOT_FOUND", "The requested resource was not found");

  private static final Map<String, ApiErrorCode> BY_CODE =
      Collections.unmodifiableMap(
          Arrays.stream(values())
              .collect(Collectors.toMap(ApiErrorCode::code, Function.identity())));

  private final String code;
  private final String description;

  ApiErrorCode(String code, String description) {
    this.code = code;
    this.description = description;
  }

  public String code() {
    return code;
  }

  public String description() {
    return description;
  }

  public static Optional<ApiErrorCode> find(String code) {
    if (code == null) {
      return Optional.empty();
    }
    return Optional.ofNullable(BY_CODE.get(code));
  }
}

