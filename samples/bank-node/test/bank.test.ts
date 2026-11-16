import { describe, expect, it } from "vitest";
import { Bank } from "../src/bank.js";
import type { PaymentLegRequest } from "../src/types.js";

const leg = (transactionId: string, direction: "DEBIT" | "CREDIT" = "DEBIT", amount = "100.00", extra: Partial<PaymentLegRequest> = {}): PaymentLegRequest => ({
  transactionId, direction, accountReference: "ACC-1", money: { amount, currency: "USD" }, reference: "TEST", provenance: "SYNTHETIC", ...extra,
});

describe("Bank ledger", () => {
  it("applies Decimal debit and credit legs", () => {
    const bank = new Bank();
    expect(bank.apply("debit-001", leg("d", "DEBIT", "100.25")).status).toBe(201);
    expect(bank.apply("credit-001", leg("c", "CREDIT", "0.25", { accountReference: "ACC-2" })).status).toBe(201);
  });

  it("rejects business errors and remembers them", () => {
    const bank = new Bank();
    const rejected = bank.apply("funds-001", leg("funds", "DEBIT", "999999999.00"));
    expect(rejected).toMatchObject({ status: 422, body: { code: "AM04" } });
    expect(bank.status("funds", "DEBIT")).toMatchObject({ status: "REJECTED", reasonCode: "AM04" });
    expect(bank.apply("funds-001", leg("funds", "DEBIT", "999999999.00")).status).toBe(422);
    expect(bank.apply("unknown-1", leg("unknown", "CREDIT", "1.00", { accountReference: "NOPE" })).body).toMatchObject({ code: "AC01" });
    expect(bank.apply("closed-1", leg("closed", "CREDIT", "1.00", { accountReference: "ACC-CLOSED" })).body).toMatchObject({ code: "AC04" });
    expect(bank.apply("zero-001", leg("zero", "DEBIT", "0.00")).body).toMatchObject({ code: "AM02" });
  });

  it("enforces idempotency without double application", () => {
    const bank = new Bank();
    const first = bank.apply("idem-001", leg("idem", "DEBIT", "100.00"));
    const replay = bank.apply("idem-001", leg("idem", "DEBIT", "100.00"));
    const conflict = bank.apply("idem-001", leg("idem", "DEBIT", "200.00"));
    expect(replay).toEqual({ status: 200, body: first.body });
    expect(conflict).toMatchObject({ status: 409, body: { code: "IDEMPOTENCY_CONFLICT" } });
  });

  it("supports a single valid reversal", () => {
    const bank = new Bank();
    expect(bank.apply("rev-0001", leg("debit", "DEBIT", "5.00")).status).toBe(201);
    expect(bank.apply("rev-0002", leg("reversal", "CREDIT", "5.00", { reversalOf: "debit" })).status).toBe(201);
    expect(bank.apply("rev-0003", leg("reversal-2", "CREDIT", "5.00", { reversalOf: "debit" }))).toMatchObject({ status: 422, body: { code: "DUPL" } });
    expect(bank.apply("rev-0004", leg("unknown-reversal", "CREDIT", "5.00", { reversalOf: "missing" }))).toMatchObject({ status: 422, body: { code: "MS03" } });
  });

  it.each([
    ["short", "short", "MISSING_IDEMPOTENCY_KEY"],
    ["amount", "amount-001", "INVALID_REQUEST"],
    ["currency", "currency-001", "AM03"],
  ])("handles %s validation", (_name, key, code) => {
    const bank = new Bank();
    const request = _name === "amount" ? leg("bad", "DEBIT", "ten") : _name === "currency" ? leg("bad", "DEBIT", "1.00", { money: { amount: "1.00", currency: "EUR" } }) : leg("bad");
    expect(bank.apply(key === "short" ? key : key, request).body).toMatchObject({ code });
  });

  it("rejects invalid body types, missing fields, and unknown fields", () => {
    const bank = new Bank();
    expect(bank.apply("shape-001", { ...leg("shape"), reference: undefined }).body).toMatchObject({ code: "INVALID_REQUEST" });
    expect(bank.apply("shape-002", { ...leg("shape"), transactionId: 123 }).body).toMatchObject({ code: "INVALID_REQUEST" });
    expect(bank.apply("shape-003", { ...leg("shape"), extra: true }).body).toMatchObject({ code: "INVALID_REQUEST" });
    expect(bank.apply("shape-004", { ...leg("shape"), provenance: "REAL" }).body).toMatchObject({ code: "INVALID_REQUEST" });
    expect(bank.apply("shape-005", { ...leg("shape"), money: { amount: "1.00", currency: "USD", extra: true } }).body).toMatchObject({ code: "INVALID_REQUEST" });
  });

  it("serializes same-key concurrent calls into one financial effect", async () => {
    const bank = new Bank();
    const results = await Promise.all(Array.from({ length: 20 }, () => Promise.resolve(bank.apply("concurrent-1", leg("concurrent", "DEBIT", "100.00")))));
    expect(results.filter((result) => result.status === 201)).toHaveLength(1);
    expect(results.filter((result) => result.status === 200)).toHaveLength(19);
    expect(bank.balanceOf("ACC-1")?.toFixed(2)).toBe("900.00");
  });
});
