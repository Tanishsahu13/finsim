package io.github.mhnuk2007.finsim.samples.bank;

import io.github.mhnuk2007.finsim.samples.bank.Contract.Direction;
import io.github.mhnuk2007.finsim.samples.bank.Contract.ErrorBody;
import io.github.mhnuk2007.finsim.samples.bank.Contract.LegStatus;
import io.github.mhnuk2007.finsim.samples.bank.Contract.PaymentLegRequest;
import io.github.mhnuk2007.finsim.samples.bank.Contract.PaymentLegResult;
import java.math.BigDecimal;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.regex.Pattern;
import org.springframework.stereotype.Service;

/**
 * The bank's in-memory ledger and the contract rules.
 *
 * <p>What this sample demonstrates for institution authors:
 *
 * <ul>
 *   <li>Idempotency: same key and same body returns the original result (200); same key and a
 *       different body returns 409; a missing key returns 400.
 *   <li>Business rejections return 422 with an ISO 20022-style reason code and are remembered, so
 *       the leg can be queried later as REJECTED.
 *   <li>Every applied leg can be queried by transactionId, which is how FinSim resolves UNKNOWN
 *       transfers. A leg that never arrived is "not found".
 *   <li>A reversal is an ordinary CREDIT with its own transactionId and {@code reversalOf} set.
 * </ul>
 */
@Service
public class BankService {

  /** HTTP status plus response body. */
  public record Outcome(int status, Object body) {}

  private record LegRecord(
      String transactionId,
      Direction direction,
      String accountReference,
      BigDecimal amount,
      LegStatus status,
      String reasonCode,
      String participantReference) {

    PaymentLegResult toResult() {
      return new PaymentLegResult(
          transactionId, direction, status, reasonCode, participantReference, "SYNTHETIC");
    }
  }

  private record Stored(PaymentLegRequest request, Outcome outcome) {}

  private static final Pattern CURRENCY = Pattern.compile("[A-Z]{3}");

  private final BankProperties props;
  private final Map<String, BigDecimal> balances = new TreeMap<>();
  private final Map<String, Stored> byIdempotencyKey = new HashMap<>();
  private final Map<String, LegRecord> legs = new HashMap<>();
  private final Set<String> reversedDebits = new HashSet<>();
  private long sequence = 0;

  public BankService(BankProperties props) {
    this.props = props;
    this.balances.putAll(props.accounts());
  }

  /** Applies one leg. Safe to call repeatedly with the same key. */
  public synchronized Outcome applyLeg(String idempotencyKey, PaymentLegRequest request) {
    if (idempotencyKey == null || idempotencyKey.length() < 8 || idempotencyKey.length() > 128) {
      return badRequest(
          "MISSING_IDEMPOTENCY_KEY",
          "The Idempotency-Key header is required (8 to 128 characters).");
    }
    String problem = validate(request);
    if (problem != null) {
      return badRequest("INVALID_REQUEST", problem);
    }

    Stored previous = byIdempotencyKey.get(idempotencyKey);
    if (previous != null) {
      if (!previous.request().equals(request)) {
        return new Outcome(
            409,
            new ErrorBody(
                "IDEMPOTENCY_CONFLICT", "This Idempotency-Key was used with a different request."));
      }
      int status = previous.outcome().status() == 201 ? 200 : previous.outcome().status();
      return new Outcome(status, previous.outcome().body());
    }

    Outcome outcome = process(request);
    byIdempotencyKey.put(idempotencyKey, new Stored(request, outcome));
    return outcome;
  }

  /** Status of a leg, or empty if this bank never received it. */
  public synchronized Optional<PaymentLegResult> status(String transactionId, Direction direction) {
    return Optional.ofNullable(legs.get(legKey(transactionId, direction))).map(LegRecord::toResult);
  }

  public synchronized BigDecimal balanceOf(String account) {
    return balances.get(account);
  }

  /** Demo helper: current balances as plain strings. Not part of the contract. */
  public synchronized Map<String, String> balances() {
    Map<String, String> view = new LinkedHashMap<>();
    balances.forEach((account, amount) -> view.put(account, amount.toPlainString()));
    return view;
  }

  public BankProperties properties() {
    return props;
  }

  // ---------------------------------------------------------------------------------------------

  private Outcome process(PaymentLegRequest r) {
    BigDecimal amount = new BigDecimal(r.money().amount());
    String account = r.accountReference();

    if (legs.containsKey(legKey(r.transactionId(), r.direction()))) {
      return reject(
          r, amount, "DUPL", "A leg with this transactionId and direction already exists.", false);
    }
    if (!props.currencies().contains(r.money().currency())) {
      return reject(r, amount, "AM03", "Currency not supported by this institution.", true);
    }
    if (amount.signum() <= 0) {
      return reject(r, amount, "AM02", "Amount must be greater than zero.", true);
    }
    if (!balances.containsKey(account)) {
      return reject(r, amount, "AC01", "Account does not exist.", true);
    }
    if (props.closedAccounts().contains(account)) {
      return reject(r, amount, "AC04", "Account is closed.", true);
    }

    if (r.reversalOf() != null) {
      LegRecord original = legs.get(legKey(r.reversalOf(), Direction.DEBIT));
      boolean matches =
          original != null
              && original.status() == LegStatus.COMPLETED
              && original.accountReference().equals(account)
              && original.amount().compareTo(amount) == 0;
      if (!matches) {
        return reject(r, amount, "MS03", "reversalOf does not match a completed debit.", true);
      }
      if (reversedDebits.contains(r.reversalOf())) {
        return reject(r, amount, "DUPL", "That debit has already been reversed.", true);
      }
    }

    if (r.direction() == Direction.DEBIT) {
      if (balances.get(account).compareTo(amount) < 0) {
        return reject(r, amount, "AM04", "Insufficient funds.", true);
      }
      balances.merge(account, amount.negate(), BigDecimal::add);
    } else {
      balances.merge(account, amount, BigDecimal::add);
    }
    if (r.reversalOf() != null) {
      reversedDebits.add(r.reversalOf());
    }

    LegRecord done =
        new LegRecord(
            r.transactionId(),
            r.direction(),
            account,
            amount,
            LegStatus.COMPLETED,
            null,
            nextReference());
    legs.put(legKey(r.transactionId(), r.direction()), done);
    return new Outcome(201, done.toResult());
  }

  private Outcome reject(
      PaymentLegRequest r, BigDecimal amount, String code, String message, boolean remember) {
    if (remember) {
      legs.put(
          legKey(r.transactionId(), r.direction()),
          new LegRecord(
              r.transactionId(),
              r.direction(),
              r.accountReference(),
              amount,
              LegStatus.REJECTED,
              code,
              nextReference()));
    }
    return new Outcome(422, new ErrorBody(code, message));
  }

  private static String validate(PaymentLegRequest r) {
    if (r == null) {
      return "Request body is required.";
    }
    if (isBlank(r.transactionId()) || r.transactionId().length() > 128) {
      return "transactionId is required (up to 128 characters).";
    }
    if (r.direction() == null) {
      return "direction is required (DEBIT or CREDIT).";
    }
    if (isBlank(r.accountReference())) {
      return "accountReference is required.";
    }
    if (r.money() == null || isBlank(r.money().amount()) || isBlank(r.money().currency())) {
      return "money.amount and money.currency are required.";
    }
    try {
      new BigDecimal(r.money().amount());
    } catch (NumberFormatException e) {
      return "money.amount must be a decimal string.";
    }
    if (!CURRENCY.matcher(r.money().currency()).matches()) {
      return "money.currency must be three uppercase letters.";
    }
    if (!"SYNTHETIC".equals(r.provenance())) {
      return "provenance must be SYNTHETIC.";
    }
    if (r.reversalOf() != null && r.direction() != Direction.CREDIT) {
      return "reversalOf is only valid on a CREDIT leg.";
    }
    return null;
  }

  private static boolean isBlank(String s) {
    return s == null || s.isBlank();
  }

  private static Outcome badRequest(String code, String message) {
    return new Outcome(400, new ErrorBody(code, message));
  }

  private static String legKey(String transactionId, Direction direction) {
    return transactionId + "|" + direction;
  }

  private String nextReference() {
    sequence++;
    return "%s-%06d".formatted(props.institutionId(), sequence);
  }
}
