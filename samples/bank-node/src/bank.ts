import Decimal from "decimal.js";
import type { ErrorBody, Outcome, PaymentLegRequest, PaymentLegResult, Direction } from "./types.js";

type StoredResponse = { fingerprint: string; outcome: Outcome };
type LegRecord = { request: PaymentLegRequest; result: PaymentLegResult };

const error = (code: string, message: string): ErrorBody => ({ code, message });

function stable(value: unknown): unknown {
  if (Array.isArray(value)) return value.map(stable);
  if (value && typeof value === "object") {
    return Object.fromEntries(Object.entries(value).sort(([a], [b]) => a.localeCompare(b)).map(([k, v]) => [k, stable(v)]));
  }
  return value;
}

function fingerprint(request: PaymentLegRequest): string {
  return JSON.stringify(stable(request));
}

export class Bank {
  private balances = new Map<string, Decimal>([
    ["ACC-1", new Decimal("1000.00")],
    ["ACC-2", new Decimal("250.00")],
    ["ACC-CLOSED", new Decimal("0.00")],
  ]);
  private readonly closedAccounts = new Set(["ACC-CLOSED"]);
  private readonly idempotency = new Map<string, StoredResponse>();
  private readonly legs = new Map<string, LegRecord>();
  private readonly reversedDebits = new Set<string>();
  private sequence = 0;

  apply(key: string | undefined, request: unknown): Outcome {
    if (!key || key.length < 8 || key.length > 128) {
      return { status: 400, body: error("MISSING_IDEMPOTENCY_KEY", "The Idempotency-Key header is required (8 to 128 characters).") };
    }
    const validation = this.validate(request);
    if (typeof validation === "string") return { status: 400, body: error("INVALID_REQUEST", validation) };
    const validRequest = validation;

    const bodyFingerprint = fingerprint(validRequest);
    const previous = this.idempotency.get(key);
    if (previous) {
      if (previous.fingerprint !== bodyFingerprint) {
        return { status: 409, body: error("IDEMPOTENCY_CONFLICT", "This Idempotency-Key was used with a different request.") };
      }
      return { status: previous.outcome.status === 201 ? 200 : previous.outcome.status, body: previous.outcome.body };
    }

    const outcome = this.process(validRequest);
    this.idempotency.set(key, { fingerprint: bodyFingerprint, outcome });
    return outcome;
  }

  status(transactionId: string, direction: Direction): PaymentLegResult | undefined {
    return this.legs.get(this.legKey(transactionId, direction))?.result;
  }

  balanceOf(account: string): Decimal | undefined {
    return this.balances.get(account);
  }

  private process(request: PaymentLegRequest): Outcome {
    const amount = new Decimal(request.money.amount);
    const key = this.legKey(request.transactionId, request.direction);
    if (this.legs.has(key)) return this.reject(request, "DUPL", "A leg with this transactionId and direction already exists.", false);
    if (request.money.currency !== "USD") return this.reject(request, "AM03", "Currency not supported by this institution.", true);
    if (amount.lte(0)) return this.reject(request, "AM02", "Amount must be greater than zero.", true);
    const balance = this.balances.get(request.accountReference);
    if (balance === undefined) return this.reject(request, "AC01", "Account does not exist.", true);
    if (this.closedAccounts.has(request.accountReference)) return this.reject(request, "AC04", "Account is closed.", true);

    if (request.reversalOf) {
      const original = this.legs.get(this.legKey(request.reversalOf, "DEBIT"));
      const matches = original?.result.status === "COMPLETED"
        && original.request.accountReference === request.accountReference
        && new Decimal(original.request.money.amount).equals(amount);
      if (!matches) return this.reject(request, "MS03", "reversalOf does not match a completed debit.", true);
      if (this.reversedDebits.has(request.reversalOf)) return this.reject(request, "DUPL", "That debit has already been reversed.", true);
    }

    if (request.direction === "DEBIT" && balance.lt(amount)) return this.reject(request, "AM04", "Insufficient funds.", true);
    this.balances.set(request.accountReference, request.direction === "DEBIT" ? balance.minus(amount) : balance.plus(amount));
    if (request.reversalOf) this.reversedDebits.add(request.reversalOf);
    const result: PaymentLegResult = {
      transactionId: request.transactionId,
      direction: request.direction,
      status: "COMPLETED",
      participantReference: `bank-node-${++this.sequence}`,
      provenance: "SYNTHETIC",
    };
    this.legs.set(key, { request, result });
    return { status: 201, body: result };
  }

  private reject(request: PaymentLegRequest, code: string, message: string, remember: boolean): Outcome {
    if (remember) {
      const result: PaymentLegResult = {
        transactionId: request.transactionId,
        direction: request.direction,
        status: "REJECTED",
        reasonCode: code,
        participantReference: `bank-node-${++this.sequence}`,
        provenance: "SYNTHETIC",
      };
      this.legs.set(this.legKey(request.transactionId, request.direction), { request, result });
    }
    return { status: 422, body: error(code, message) };
  }

  private validate(request: unknown): PaymentLegRequest | string {
    if (!request || typeof request !== "object" || Array.isArray(request)) return "Request body is required.";
    const body = request as Record<string, unknown>;
    const allowed = new Set(["transactionId", "direction", "accountReference", "money", "reference", "reversalOf", "provenance"]);
    if (Object.keys(body).some((key) => !allowed.has(key))) return "Request contains unknown fields.";
    if (typeof body.transactionId !== "string" || !body.transactionId || body.transactionId.length > 128) return "transactionId is required (up to 128 characters).";
    if (body.direction !== "DEBIT" && body.direction !== "CREDIT") return "direction is required (DEBIT or CREDIT).";
    if (typeof body.accountReference !== "string" || !body.accountReference) return "accountReference is required.";
    if (typeof body.reference !== "string" || body.reference.length > 140) return "reference is required (up to 140 characters).";
    if (!body.money || typeof body.money !== "object" || Array.isArray(body.money)) return "money is required.";
    const money = body.money as Record<string, unknown>;
    if (Object.keys(money).some((key) => key !== "amount" && key !== "currency")) return "money contains unknown fields.";
    if (typeof money.amount !== "string" || !/^\d+(\.\d{1,8})?$/.test(money.amount)) return "money.amount must be a decimal string.";
    if (typeof money.currency !== "string" || !/^[A-Z]{3}$/.test(money.currency)) return "money.currency must be three uppercase letters.";
    if (body.reversalOf !== undefined && typeof body.reversalOf !== "string") return "reversalOf must be a string.";
    if (body.provenance !== "SYNTHETIC") return "provenance must be SYNTHETIC.";
    if (body.reversalOf && body.direction !== "CREDIT") return "reversalOf is only valid on a CREDIT leg.";
    return body as unknown as PaymentLegRequest;
  }

  private legKey(transactionId: string, direction: Direction): string { return `${transactionId}\u0000${direction}`; }
}
