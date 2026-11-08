package io.github.mhnuk2007.finsim.samples.bank;

import io.github.mhnuk2007.finsim.samples.bank.BankService.Outcome;
import io.github.mhnuk2007.finsim.samples.bank.Contract.Capabilities;
import io.github.mhnuk2007.finsim.samples.bank.Contract.Direction;
import io.github.mhnuk2007.finsim.samples.bank.Contract.ErrorBody;
import io.github.mhnuk2007.finsim.samples.bank.Contract.Health;
import io.github.mhnuk2007.finsim.samples.bank.Contract.PaymentLegRequest;
import java.util.List;
import java.util.Map;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** The finsim/v1 participant contract, plus one clearly separate demo endpoint. */
@RestController
class ContractController {

  private final BankService bank;

  ContractController(BankService bank) {
    this.bank = bank;
  }

  @GetMapping("/finsim/v1/institution/health")
  Health health() {
    return new Health("UP", List.of("finsim/v1"));
  }

  @GetMapping("/finsim/v1/institution/capabilities")
  Capabilities capabilities() {
    return new Capabilities(
        "BANK",
        List.of("ACCOUNT_DEBIT", "ACCOUNT_CREDIT", "STATUS_QUERY"),
        bank.properties().currencies());
  }

  @PostMapping("/finsim/v1/payments")
  ResponseEntity<Object> createLeg(
      @RequestHeader(name = "Idempotency-Key", required = false) String idempotencyKey,
      @RequestBody PaymentLegRequest request) {
    Outcome outcome = bank.applyLeg(idempotencyKey, request);
    return ResponseEntity.status(outcome.status()).body(outcome.body());
  }

  @GetMapping("/finsim/v1/payments/{transactionId}")
  ResponseEntity<Object> getLeg(
      @PathVariable("transactionId") String transactionId, @RequestParam("leg") Direction leg) {
    return bank.status(transactionId, leg)
        .<ResponseEntity<Object>>map(ResponseEntity::ok)
        .orElseGet(
            () ->
                ResponseEntity.status(404)
                    .body(
                        new ErrorBody(
                            "LEG_NOT_FOUND", "This institution has no record of that leg.")));
  }

  /** Demo helper, NOT part of the contract: shows balances so you can watch money move. */
  @GetMapping("/sample/accounts")
  Map<String, String> accounts() {
    return bank.balances();
  }
}
