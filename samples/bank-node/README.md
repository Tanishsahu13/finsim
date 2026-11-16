# Sample bank (Node.js/TypeScript)

Standalone in-memory institution implementing the FinSim `finsim/v1` participant contract. It does not depend on any FinSim Java module. State is synthetic and resets when the process exits.

## Requirements and setup

Node.js 20 or newer is required.

```bash
cd samples/bank-node
npm ci
```

## Run

The default port is `9012`; override it with `PORT`:

```bash
npm start
PORT=9010 npm start
```

Seeded accounts are `ACC-1` (USD 1000.00), `ACC-2` (USD 250.00), and `ACC-CLOSED` (closed).

## Try it

```bash
curl http://localhost:9012/finsim/v1/institution/health
curl http://localhost:9012/finsim/v1/institution/capabilities

curl -i -X POST http://localhost:9012/finsim/v1/payments \
  -H 'Content-Type: application/json' \
  -H 'Idempotency-Key: demo-key-0001' \
  -d '{"transactionId":"tx-1","direction":"DEBIT","accountReference":"ACC-1","money":{"amount":"100.00","currency":"USD"},"reference":"DEMO-001","provenance":"SYNTHETIC"}'

curl 'http://localhost:9012/finsim/v1/payments/tx-1?leg=DEBIT'
```

## Tests and conformance

```bash
npm test
# with the bank running in another terminal:
../../scripts/conformance.sh http://localhost:9012
```

Money uses `decimal.js`; JavaScript floating-point numbers are never used for monetary calculations. Idempotency, rejection memory, status lookup, and reversal rules follow the shared contract conformance suite.
