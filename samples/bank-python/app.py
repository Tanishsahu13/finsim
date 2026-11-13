"""Standalone in-memory FinSim participant bank."""

from __future__ import annotations

import json
import os
import re
from decimal import Decimal, InvalidOperation
from enum import Enum
from threading import RLock
from typing import Any

from fastapi import FastAPI, Header, Path, Query, Request, Response
from fastapi.exceptions import RequestValidationError
from fastapi.responses import JSONResponse
from pydantic import BaseModel, ConfigDict, Field
from starlette.exceptions import HTTPException as StarletteHTTPException


class Direction(str, Enum):
    DEBIT = "DEBIT"
    CREDIT = "CREDIT"


class LegStatus(str, Enum):
    ACCEPTED = "ACCEPTED"
    COMPLETED = "COMPLETED"
    REJECTED = "REJECTED"


class Money(BaseModel):
    model_config = ConfigDict(extra="forbid")

    amount: str
    currency: str


class PaymentLegRequest(BaseModel):
    model_config = ConfigDict(extra="forbid")

    transactionId: str
    direction: Direction
    accountReference: str
    money: Money
    reference: str = Field(max_length=140)
    reversalOf: str | None = None
    provenance: str


class PaymentLegResult(BaseModel):
    transactionId: str
    direction: Direction
    status: LegStatus
    reasonCode: str | None = None
    participantReference: str | None = None
    provenance: str = "SYNTHETIC"


class ErrorBody(BaseModel):
    code: str
    message: str


class Health(BaseModel):
    status: str
    contractVersions: list[str]


class Capabilities(BaseModel):
    institutionType: str
    capabilities: list[str]
    currencies: list[str]


class LegRecord:
    def __init__(
        self,
        request: PaymentLegRequest,
        status: LegStatus,
        participant_reference: str,
        reason_code: str | None = None,
    ) -> None:
        self.request = request
        self.status = status
        self.participant_reference = participant_reference
        self.reason_code = reason_code

    def result(self) -> PaymentLegResult:
        return PaymentLegResult(
            transactionId=self.request.transactionId,
            direction=self.request.direction,
            status=self.status,
            reasonCode=self.reason_code,
            participantReference=self.participant_reference,
        )


class Bank:
    def __init__(self) -> None:
        self._balances = {
            "ACC-1": Decimal("500.00"),
            "ACC-2": Decimal("100.00"),
            "ACC-CLOSED": Decimal("0.00"),
        }
        self._closed_accounts = {"ACC-CLOSED"}
        self._idempotency: dict[str, tuple[str, int, dict[str, Any]]] = {}
        self._legs: dict[tuple[str, Direction], LegRecord] = {}
        self._reversed_debits: set[str] = set()
        self._sequence = 0
        self._lock = RLock()

    def apply(self, key: str | None, request: PaymentLegRequest) -> tuple[int, dict[str, Any]]:
        if key is None or not 8 <= len(key) <= 128:
            return 400, ErrorBody(
                code="MISSING_IDEMPOTENCY_KEY",
                message="The Idempotency-Key header is required (8 to 128 characters).",
            ).model_dump()

        validation_error = self._validate(request)
        if validation_error is not None:
            return 400, ErrorBody(code="INVALID_REQUEST", message=validation_error).model_dump()

        fingerprint = self._fingerprint(request)
        with self._lock:
            previous = self._idempotency.get(key)
            if previous is not None:
                previous_fingerprint, previous_status, previous_body = previous
                if previous_fingerprint != fingerprint:
                    return 409, ErrorBody(
                        code="IDEMPOTENCY_CONFLICT",
                        message="This Idempotency-Key was used with a different request.",
                    ).model_dump()
                return (200 if previous_status == 201 else previous_status), previous_body

            status, body = self._process(request)
            self._idempotency[key] = (fingerprint, status, body)
            return status, body

    def status(self, transaction_id: str, direction: Direction) -> PaymentLegResult | None:
        with self._lock:
            record = self._legs.get((transaction_id, direction))
            return record.result() if record else None

    def _process(self, request: PaymentLegRequest) -> tuple[int, dict[str, Any]]:
        amount = Decimal(request.money.amount)
        leg_key = (request.transactionId, request.direction)
        if leg_key in self._legs:
            return self._reject(request, amount, "DUPL", "A leg with this transactionId and direction already exists.", False)
        if request.money.currency != "USD":
            return self._reject(request, amount, "AM03", "Currency not supported by this institution.", True)
        if amount <= 0:
            return self._reject(request, amount, "AM02", "Amount must be greater than zero.", True)
        account = request.accountReference
        if account not in self._balances:
            return self._reject(request, amount, "AC01", "Account does not exist.", True)
        if account in self._closed_accounts:
            return self._reject(request, amount, "AC04", "Account is closed.", True)
        if request.reversalOf is not None:
            original = self._legs.get((request.reversalOf, Direction.DEBIT))
            matches = (
                original is not None
                and original.status is LegStatus.COMPLETED
                and original.request.accountReference == account
                and Decimal(original.request.money.amount) == amount
            )
            if not matches:
                return self._reject(
                    request,
                    amount,
                    "MS03",
                    "reversalOf does not match a completed debit.",
                    True,
                )
            if request.reversalOf in self._reversed_debits:
                return self._reject(
                    request,
                    amount,
                    "DUPL",
                    "That debit has already been reversed.",
                    True,
                )
        if request.direction is Direction.DEBIT and self._balances[account] < amount:
            return self._reject(request, amount, "AM04", "Insufficient funds.", True)

        if request.direction is Direction.DEBIT:
            self._balances[account] -= amount
        else:
            self._balances[account] += amount
        if request.reversalOf is not None:
            self._reversed_debits.add(request.reversalOf)
        self._sequence += 1
        record = LegRecord(request, LegStatus.COMPLETED, f"bank-python-{self._sequence}")
        self._legs[leg_key] = record
        return 201, record.result().model_dump(mode="json")

    def _reject(
        self,
        request: PaymentLegRequest,
        _amount: Decimal,
        code: str,
        message: str,
        remember: bool,
    ) -> tuple[int, dict[str, Any]]:
        self._sequence += 1
        if remember:
            self._legs[(request.transactionId, request.direction)] = LegRecord(
                request, LegStatus.REJECTED, f"bank-python-{self._sequence}", code
            )
        return 422, ErrorBody(code=code, message=message).model_dump()

    @staticmethod
    def _validate(request: PaymentLegRequest) -> str | None:
        if not request.transactionId or len(request.transactionId) > 128:
            return "transactionId is required (up to 128 characters)."
        if not request.accountReference:
            return "accountReference is required."
        if not request.money.amount or not re.fullmatch(r"\d+(\.\d{1,8})?", request.money.amount):
            return "money.amount must be a decimal string."
        if not re.fullmatch(r"[A-Z]{3}", request.money.currency):
            return "money.currency must be three uppercase letters."
        try:
            Decimal(request.money.amount)
        except InvalidOperation:
            return "money.amount must be a decimal string."
        if request.provenance != "SYNTHETIC":
            return "provenance must be SYNTHETIC."
        if request.reversalOf is not None and request.direction is not Direction.CREDIT:
            return "reversalOf is only valid on a CREDIT leg."
        return None

    @staticmethod
    def _fingerprint(request: PaymentLegRequest) -> str:
        return json.dumps(request.model_dump(mode="json"), sort_keys=True, separators=(",", ":"))


app = FastAPI(title="FinSim Python Sample Bank")
bank = Bank()


def contract_error(code: str, message: str, status_code: int = 400) -> JSONResponse:
    return JSONResponse(status_code=status_code, content=ErrorBody(code=code, message=message).model_dump())


@app.exception_handler(RequestValidationError)
async def validation_exception_handler(_request: Request, exc: RequestValidationError) -> JSONResponse:
    return contract_error("INVALID_REQUEST", "Request contains invalid or missing fields.")


@app.exception_handler(StarletteHTTPException)
async def http_exception_handler(_request: Request, exc: StarletteHTTPException) -> JSONResponse:
    if exc.status_code == 404:
        return contract_error("LEG_NOT_FOUND", "This institution has no record of that leg.", 404)
    return contract_error("INVALID_REQUEST", str(exc.detail), exc.status_code)


@app.get("/finsim/v1/institution/health", response_model=Health)
def health() -> Health:
    return Health(status="UP", contractVersions=["finsim/v1"])


@app.get("/finsim/v1/institution/capabilities", response_model=Capabilities)
def capabilities() -> Capabilities:
    return Capabilities(
        institutionType="BANK",
        capabilities=["ACCOUNT_DEBIT", "ACCOUNT_CREDIT", "STATUS_QUERY"],
        currencies=["USD"],
    )


@app.post("/finsim/v1/payments")
def create_payment(request: PaymentLegRequest, idempotency_key: str | None = Header(default=None, alias="Idempotency-Key")) -> Response:
    status, body = bank.apply(idempotency_key, request)
    return JSONResponse(status_code=status, content=body)


@app.get("/finsim/v1/payments/{transactionId}")
def get_payment(
    transaction_id: str = Path(alias="transactionId"),
    leg: Direction = Query(...),
) -> Response:
    result = bank.status(transaction_id, leg)
    if result is None:
        return contract_error("LEG_NOT_FOUND", "This institution has no record of that leg.", 404)
    return JSONResponse(status_code=200, content=result.model_dump(mode="json"))


if __name__ == "__main__":
    import uvicorn

    uvicorn.run(app, host="0.0.0.0", port=int(os.environ.get("PORT", "9003")))
