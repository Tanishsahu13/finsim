from decimal import Decimal

from fastapi.testclient import TestClient

from app import app, bank


client = TestClient(app)


def setup_function() -> None:
    bank.__init__()


def payload(tx="tx-1", direction="DEBIT", account="ACC-1", amount="100.00", **extra):
    return {
        "transactionId": tx,
        "direction": direction,
        "accountReference": account,
        "money": {"amount": amount, "currency": "USD"},
        "reference": "TEST",
        "provenance": "SYNTHETIC",
        **extra,
    }


def test_health_and_capabilities():
    assert client.get("/finsim/v1/institution/health").json() == {
        "status": "UP",
        "contractVersions": ["finsim/v1"],
    }
    assert "ACCOUNT_DEBIT" in client.get("/finsim/v1/institution/capabilities").json()["capabilities"]


def test_debit_credit_and_status():
    debit = client.post("/finsim/v1/payments", headers={"Idempotency-Key": "debit-001"}, json=payload())
    credit = client.post(
        "/finsim/v1/payments",
        headers={"Idempotency-Key": "credit-001"},
        json=payload("tx-2", "CREDIT", "ACC-2", "25.00"),
    )
    assert debit.status_code == credit.status_code == 201
    assert debit.json()["status"] == credit.json()["status"] == "COMPLETED"
    assert client.get("/finsim/v1/payments/tx-1?leg=DEBIT").json()["status"] == "COMPLETED"


def test_rejections_are_remembered_and_balances_are_unchanged():
    for key, account, code in [("unknown-1", "NOPE", "AC01"), ("closed-1", "ACC-CLOSED", "AC04")]:
        response = client.post("/finsim/v1/payments", headers={"Idempotency-Key": key}, json=payload(key, account=account))
        assert response.status_code == 422 and response.json()["code"] == code
    response = client.post(
        "/finsim/v1/payments", headers={"Idempotency-Key": "funds-001"}, json=payload("funds", amount="501.00")
    )
    assert response.status_code == 422 and response.json()["code"] == "AM04"
    status = client.get("/finsim/v1/payments/funds?leg=DEBIT")
    assert status.status_code == 200 and status.json()["reasonCode"] == "AM04"


def test_idempotency_replay_and_conflict_do_not_apply_twice():
    first = client.post("/finsim/v1/payments", headers={"Idempotency-Key": "idem-001"}, json=payload())
    replay = client.post("/finsim/v1/payments", headers={"Idempotency-Key": "idem-001"}, json=payload())
    conflict = client.post("/finsim/v1/payments", headers={"Idempotency-Key": "idem-001"}, json=payload(amount="200.00"))
    assert first.status_code == 201 and replay.status_code == 200
    assert replay.json() == first.json()
    assert conflict.status_code == 409 and conflict.json()["code"] == "IDEMPOTENCY_CONFLICT"
    assert bank._balances["ACC-1"] == Decimal("400.00")


def test_validation_and_not_found_errors():
    assert client.post("/finsim/v1/payments", json=payload()).status_code == 400
    invalid = client.post(
        "/finsim/v1/payments", headers={"Idempotency-Key": "invalid-1"}, json=payload(provenance="REAL")
    )
    assert invalid.status_code == 400 and invalid.json()["code"] == "INVALID_REQUEST"
    assert client.get("/finsim/v1/payments/nope?leg=CREDIT").status_code == 404
    assert client.get("/finsim/v1/payments/nope?leg=BAD").status_code == 400


def test_amount_currency_and_key_validation():
    invalid_amount = client.post(
        "/finsim/v1/payments",
        headers={"Idempotency-Key": "amount-01"},
        json=payload(amount="ten dollars"),
    )
    unsupported_currency = client.post(
        "/finsim/v1/payments",
        headers={"Idempotency-Key": "currency-01"},
        json={**payload(), "money": {"amount": "1.00", "currency": "EUR"}},
    )
    short_key = client.post("/finsim/v1/payments", headers={"Idempotency-Key": "short"}, json=payload())
    assert invalid_amount.status_code == 400
    assert unsupported_currency.status_code == 422
    assert unsupported_currency.json()["code"] == "AM03"
    assert short_key.status_code == 400


def test_reversal_restores_balance_once_and_validates_original_debit():
    debit = client.post(
        "/finsim/v1/payments",
        headers={"Idempotency-Key": "reversal-01"},
        json=payload("debit-to-reverse", amount="5.00"),
    )
    reversal = client.post(
        "/finsim/v1/payments",
        headers={"Idempotency-Key": "reversal-02"},
        json=payload("debit-reversal", "CREDIT", amount="5.00", reversalOf="debit-to-reverse"),
    )
    second = client.post(
        "/finsim/v1/payments",
        headers={"Idempotency-Key": "reversal-03"},
        json=payload("second-reversal", "CREDIT", amount="5.00", reversalOf="debit-to-reverse"),
    )
    unknown = client.post(
        "/finsim/v1/payments",
        headers={"Idempotency-Key": "reversal-04"},
        json=payload("unknown-reversal", "CREDIT", amount="5.00", reversalOf="missing-debit"),
    )
    assert debit.status_code == reversal.status_code == 201
    assert second.status_code == 422 and second.json()["code"] == "DUPL"
    assert unknown.status_code == 422 and unknown.json()["code"] == "MS03"
    assert bank._balances["ACC-1"] == Decimal("500.00")


def test_reversal_amount_must_match_completed_debit():
    client.post(
        "/finsim/v1/payments",
        headers={"Idempotency-Key": "reversal-05"},
        json=payload("debit-mismatch", amount="5.00"),
    )
    response = client.post(
        "/finsim/v1/payments",
        headers={"Idempotency-Key": "reversal-06"},
        json=payload("mismatch-reversal", "CREDIT", amount="4.00", reversalOf="debit-mismatch"),
    )
    assert response.status_code == 422 and response.json()["code"] == "MS03"
