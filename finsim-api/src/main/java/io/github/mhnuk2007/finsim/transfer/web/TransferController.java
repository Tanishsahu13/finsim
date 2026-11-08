package io.github.mhnuk2007.finsim.transfer.web;

import io.github.mhnuk2007.finsim.core.Transfer;
import io.github.mhnuk2007.finsim.transfer.TransferDtos.CreateTransferRequest;
import io.github.mhnuk2007.finsim.transfer.TransferDtos.TransferResponse;
import io.github.mhnuk2007.finsim.transfer.TransferService;
import io.github.mhnuk2007.finsim.transfer.TransferService.TransferExecutionResult;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class TransferController {

  private final TransferService transferService;

  public TransferController(TransferService transferService) {
    this.transferService = transferService;
  }

  @PostMapping("/api/v1/networks/{networkId}/transfers")
  public ResponseEntity<TransferResponse> createTransfer(
      @PathVariable("networkId") String networkId,
      @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
      @RequestBody CreateTransferRequest request) {

    TransferExecutionResult result =
        transferService.executeTransfer(networkId, idempotencyKey, request);
    TransferResponse response = TransferResponse.from(result.transfer());

    if (result.isReplay()) {
      return ResponseEntity.ok(response);
    }
    return ResponseEntity.status(HttpStatus.CREATED).body(response);
  }

  @GetMapping("/api/v1/transfers/{transactionId}")
  public ResponseEntity<TransferResponse> getTransfer(
      @PathVariable("transactionId") String transactionId) {
    Transfer transfer = transferService.getTransfer(transactionId);
    return ResponseEntity.ok(TransferResponse.from(transfer));
  }
}
