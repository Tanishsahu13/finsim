# Sample bank (Python/FastAPI)

This is a standalone in-memory institution implementing the FinSim `finsim/v1` participant contract.

## Requirements and setup

Python 3.11 or newer is required.

```bash
cd samples/bank-python
python3 -m venv .venv
source .venv/bin/activate
python -m pip install -r requirements.txt
```

## Run

The default port is `9003`; override it with `PORT`:

```bash
python -m uvicorn app:app --host 0.0.0.0 --port "${PORT:-9003}"
# or: PORT=9010 python app.py
```

Seeded accounts are `ACC-1` (USD 500.00), `ACC-2` (USD 100.00), and `ACC-CLOSED` (closed).

## Try it

```bash
curl http://localhost:9003/finsim/v1/institution/health
curl http://localhost:9003/finsim/v1/institution/capabilities

curl -i -X POST http://localhost:9003/finsim/v1/payments \
  -H 'Content-Type: application/json' \
  -H 'Idempotency-Key: demo-key-0001' \
  -d '{"transactionId":"tx-1","direction":"DEBIT","accountReference":"ACC-1","money":{"amount":"100.00","currency":"USD"},"reference":"DEMO-001","provenance":"SYNTHETIC"}'

curl 'http://localhost:9003/finsim/v1/payments/tx-1?leg=DEBIT'
```

## Tests

```bash
pytest
```

The implementation uses `decimal.Decimal` for all monetary calculations. Repeating an identical request with the same idempotency key returns `200` and does not apply the leg again; changing the request with that key returns `409 IDEMPOTENCY_CONFLICT`. Business rejections are returned with `422` and remain available through the status endpoint.
