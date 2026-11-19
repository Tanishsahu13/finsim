import { afterEach, describe, expect, it } from "vitest";
import { buildApp } from "../src/server.js";

describe("participant HTTP contract", () => {
  const apps: Awaited<ReturnType<typeof buildApp>>[] = [];
  afterEach(async () => { for (const app of apps.splice(0)) await app.close(); });
  const body = (overrides: Record<string, unknown> = {}) => ({ transactionId: "http-1", direction: "DEBIT", accountReference: "ACC-1", money: { amount: "1.00", currency: "USD" }, reference: "TEST", provenance: "SYNTHETIC", ...overrides });

  it.each(["GET", "POST"] as const)("returns generic errors for %s unknown routes", async (method) => {
    const app = buildApp(); apps.push(app);
    const response = await app.inject({ method, url: "/invalid-endpoint" });
    expect(response.statusCode).toBe(404);
    expect(response.json()).toEqual({ code: "NOT_FOUND", message: "Endpoint not found." });
  });

  it("returns a generic error for favicon requests", async () => {
    const app = buildApp(); apps.push(app);
    const response = await app.inject({ method: "GET", url: "/favicon.ico" });
    expect(response.statusCode).toBe(404);
    expect(response.json()).toEqual({ code: "NOT_FOUND", message: "Endpoint not found." });
  });

  it("keeps thrown HTTP 404 errors separate from missing payment legs", async () => {
    const app = buildApp(); apps.push(app);
    app.get("/test-http-error", async () => { throw Object.assign(new Error("Not found"), { statusCode: 404 }); });
    const response = await app.inject({ method: "GET", url: "/test-http-error" });
    expect(response.statusCode).toBe(404);
    expect(response.json()).toEqual({ code: "NOT_FOUND", message: "Endpoint not found." });
  });

  it("serves health, capabilities, payment, and status routes", async () => {
    const app = buildApp(); apps.push(app);
    expect((await app.inject({ method: "GET", url: "/finsim/v1/institution/health" })).json()).toMatchObject({ status: "UP" });
    expect((await app.inject({ method: "GET", url: "/finsim/v1/institution/capabilities" })).json().capabilities).toContain("STATUS_QUERY");
    const created = await app.inject({ method: "POST", url: "/finsim/v1/payments", headers: { "Idempotency-Key": "http-001" }, payload: body() });
    expect(created.statusCode).toBe(201);
    const status = await app.inject({ method: "GET", url: "/finsim/v1/payments/http-1?leg=DEBIT" });
    expect(status.statusCode).toBe(200);
    expect(status.json()).toEqual(created.json());
  });

  it("returns contract errors for invalid requests and missing legs", async () => {
    const app = buildApp(); apps.push(app);
    expect((await app.inject({ method: "POST", url: "/finsim/v1/payments", payload: body() })).json()).toMatchObject({ code: "MISSING_IDEMPOTENCY_KEY" });
    expect((await app.inject({ method: "GET", url: "/finsim/v1/payments/missing" })).json()).toMatchObject({ code: "INVALID_REQUEST" });
    const missing = await app.inject({ method: "GET", url: "/finsim/v1/payments/missing?leg=CREDIT" });
    expect(missing.statusCode).toBe(404);
    expect(missing.json()).toEqual({ code: "LEG_NOT_FOUND", message: "This institution has no record of that leg." });
    const invalidLeg = await app.inject({ method: "GET", url: "/finsim/v1/payments/missing?leg=BAD" });
    expect(invalidLeg.statusCode).toBe(400);
    expect(invalidLeg.json()).toMatchObject({ code: "INVALID_REQUEST" });
    expect((await app.inject({ method: "POST", url: "/finsim/v1/payments", headers: { "Idempotency-Key": "http-002", "content-type": "application/json" }, payload: "{not-json" })).json()).toMatchObject({ code: "INVALID_REQUEST" });
    expect((await app.inject({ method: "POST", url: "/finsim/v1/payments", headers: { "Idempotency-Key": "http-003" }, payload: { ...body(), extra: true } })).json()).toMatchObject({ code: "INVALID_REQUEST" });
  });
});
