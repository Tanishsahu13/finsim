import { afterEach, describe, expect, it } from "vitest";
import { buildApp } from "../src/server.js";

describe("participant HTTP contract", () => {
  const apps: Awaited<ReturnType<typeof buildApp>>[] = [];
  afterEach(async () => { for (const app of apps.splice(0)) await app.close(); });
  const body = (overrides: Record<string, unknown> = {}) => ({ transactionId: "http-1", direction: "DEBIT", accountReference: "ACC-1", money: { amount: "1.00", currency: "USD" }, reference: "TEST", provenance: "SYNTHETIC", ...overrides });

  it("serves health, capabilities, payment, and status routes", async () => {
    const app = buildApp(); apps.push(app);
    expect((await app.inject({ method: "GET", url: "/finsim/v1/institution/health" })).json()).toMatchObject({ status: "UP" });
    expect((await app.inject({ method: "GET", url: "/finsim/v1/institution/capabilities" })).json().capabilities).toContain("STATUS_QUERY");
    const created = await app.inject({ method: "POST", url: "/finsim/v1/payments", headers: { "Idempotency-Key": "http-001" }, payload: body() });
    expect(created.statusCode).toBe(201);
    expect((await app.inject({ method: "GET", url: "/finsim/v1/payments/http-1?leg=DEBIT" })).statusCode).toBe(200);
  });

  it("returns contract errors for invalid requests and missing legs", async () => {
    const app = buildApp(); apps.push(app);
    expect((await app.inject({ method: "POST", url: "/finsim/v1/payments", payload: body() })).json()).toMatchObject({ code: "MISSING_IDEMPOTENCY_KEY" });
    expect((await app.inject({ method: "GET", url: "/finsim/v1/payments/missing" })).json()).toMatchObject({ code: "INVALID_REQUEST" });
    expect((await app.inject({ method: "GET", url: "/finsim/v1/payments/missing?leg=CREDIT" })).json()).toMatchObject({ code: "LEG_NOT_FOUND" });
    expect((await app.inject({ method: "POST", url: "/finsim/v1/payments", headers: { "Idempotency-Key": "http-002", "content-type": "application/json" }, payload: "{not-json" })).json()).toMatchObject({ code: "INVALID_REQUEST" });
    expect((await app.inject({ method: "POST", url: "/finsim/v1/payments", headers: { "Idempotency-Key": "http-003" }, payload: { ...body(), extra: true } })).json()).toMatchObject({ code: "INVALID_REQUEST" });
  });
});
