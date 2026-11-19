import Fastify, { type FastifyError, type FastifyInstance } from "fastify";
import type { PaymentLegRequest, Direction, ErrorBody } from "./types.js";
import { Bank } from "./bank.js";

const invalid = (message = "Malformed or incomplete request."): ErrorBody => ({ code: "INVALID_REQUEST", message });
const notFound: ErrorBody = { code: "LEG_NOT_FOUND", message: "This institution has no record of that leg." };
const endpointNotFound: ErrorBody = { code: "NOT_FOUND", message: "Endpoint not found." };

export function buildApp(bank = new Bank()): FastifyInstance {
  const app = Fastify({ logger: true });
  app.setNotFoundHandler((_request, reply) => reply.code(404).send(endpointNotFound));
  app.setErrorHandler((err: FastifyError, _request, reply) => {
    if (err.statusCode === 404) return reply.code(404).send(endpointNotFound);
    if (err.statusCode === 400) return reply.code(400).send(invalid());
    app.log.error(err);
    return reply.code(500).send({ code: "INTERNAL_ERROR", message: "Internal server error." });
  });

  app.get("/finsim/v1/institution/health", async () => ({ status: "UP", contractVersions: ["finsim/v1"] }));
  app.get("/finsim/v1/institution/capabilities", async () => ({
    institutionType: "BANK",
    capabilities: ["ACCOUNT_DEBIT", "ACCOUNT_CREDIT", "STATUS_QUERY"],
    currencies: ["USD"],
  }));
  app.post<{ Headers: { "idempotency-key"?: string }; Body: PaymentLegRequest }>("/finsim/v1/payments", async (request, reply) => {
    const outcome = bank.apply(request.headers["idempotency-key"], request.body);
    return reply.code(outcome.status).send(outcome.body);
  });
  app.get<{ Params: { transactionId: string }; Querystring: { leg?: string } }>("/finsim/v1/payments/:transactionId", async (request, reply) => {
    if (request.query.leg !== "DEBIT" && request.query.leg !== "CREDIT") return reply.code(400).send(invalid());
    const result = bank.status(request.params.transactionId, request.query.leg as Direction);
    return result ? reply.code(200).send(result) : reply.code(404).send(notFound);
  });
  return app;
}

if (import.meta.url === `file://${process.argv[1]}`) {
  const port = Number.parseInt(process.env.PORT ?? "9012", 10);
  buildApp().listen({ host: "0.0.0.0", port }).catch((error) => { console.error(error); process.exit(1); });
}
