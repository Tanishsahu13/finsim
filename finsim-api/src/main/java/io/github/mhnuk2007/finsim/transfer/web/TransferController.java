package io.github.mhnuk2007.finsim.transfer.web;

import io.github.mhnuk2007.finsim.core.Transfer;
import io.github.mhnuk2007.finsim.transfer.TransferDtos.CreateTransferRequest;
import io.github.mhnuk2007.finsim.transfer.TransferDtos.TransferResponse;
import io.github.mhnuk2007.finsim.transfer.TransferService;
import io.github.mhnuk2007.finsim.transfer.TransferService.TransferExecutionResult;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Tag(name = "Transfers", description = "Transfer orchestration across institutions")
public class TransferController {

  private final TransferService transferService;

  public TransferController(TransferService transferService) {
    this.transferService = transferService;
  }

  @PostMapping("/api/v1/networks/{networkId}/transfers")
  @Operation(
      summary = "Create a transfer",
      description = "Executes or replays an idempotent transfer on a network")
  public ResponseEntity<TransferResponse> createTransfer(
      @Parameter(description = "Network identifier") @PathVariable("networkId") String networkId,
      @Parameter(description = "Client idempotency key")
          @RequestHeader(value = "Idempotency-Key", required = false)
          String idempotencyKey,
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
  @Operation(summary = "Get a transfer", description = "Returns the current transfer state")
  public ResponseEntity<TransferResponse> getTransfer(
      @Parameter(description = "Transfer transaction identifier") @PathVariable("transactionId")
          String transactionId) {
    Transfer transfer = transferService.getTransfer(transactionId);
    return ResponseEntity.ok(TransferResponse.from(transfer));
  }

  @PostMapping("/api/v1/transfers/{transactionId}/resolve")
  @Operation(
      summary = "Resolve a transfer",
      description = "Attempts to resolve a transfer in a non-terminal state")
  public ResponseEntity<TransferResponse> resolveTransfer(
      @Parameter(description = "Transfer transaction identifier") @PathVariable("transactionId")
          String transactionId) {
    Transfer resolved = transferService.resolveTransfer(transactionId);
    return ResponseEntity.ok(TransferResponse.from(resolved));
  }
}
