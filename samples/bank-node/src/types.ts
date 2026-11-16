export type Direction = "DEBIT" | "CREDIT";
export type LegStatus = "ACCEPTED" | "COMPLETED" | "REJECTED";

export interface Money {
  amount: string;
  currency: string;
}

export interface PaymentLegRequest {
  transactionId: string;
  direction: Direction;
  accountReference: string;
  money: Money;
  reference: string;
  reversalOf?: string;
  provenance: "SYNTHETIC";
}

export interface PaymentLegResult {
  transactionId: string;
  direction: Direction;
  status: LegStatus;
  reasonCode?: string;
  participantReference?: string;
  provenance: "SYNTHETIC";
}

export interface ErrorBody {
  code: string;
  message: string;
}

export interface Outcome {
  status: number;
  body: PaymentLegResult | ErrorBody;
}
