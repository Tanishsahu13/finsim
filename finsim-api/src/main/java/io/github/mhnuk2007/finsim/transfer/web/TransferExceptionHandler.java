package io.github.mhnuk2007.finsim.transfer.web;

import io.github.mhnuk2007.finsim.transfer.TransferDtos.ErrorResponse;
import io.github.mhnuk2007.finsim.transfer.TransferExceptions.IdempotencyConflictException;
import io.github.mhnuk2007.finsim.transfer.TransferExceptions.InvalidTransferRequestException;
import io.github.mhnuk2007.finsim.transfer.TransferExceptions.MissingIdempotencyKeyException;
import io.github.mhnuk2007.finsim.transfer.TransferExceptions.ReconciliationRequiredException;
import io.github.mhnuk2007.finsim.transfer.TransferExceptions.TransferNotFoundException;
import io.github.mhnuk2007.finsim.transfer.TransferExceptions.TransferNotResolvableException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice(basePackageClasses = TransferController.class)
public class TransferExceptionHandler {

  @ExceptionHandler(MissingIdempotencyKeyException.class)
  public ResponseEntity<ErrorResponse> handleMissingIdempotencyKey(
      MissingIdempotencyKeyException ex) {
    return ResponseEntity.status(HttpStatus.BAD_REQUEST)
        .body(new ErrorResponse(ex.code(), ex.getMessage()));
  }

  @ExceptionHandler(InvalidTransferRequestException.class)
  public ResponseEntity<ErrorResponse> handleInvalidTransferRequest(
      InvalidTransferRequestException ex) {
    return ResponseEntity.status(HttpStatus.BAD_REQUEST)
        .body(new ErrorResponse(ex.code(), ex.getMessage()));
  }

  @ExceptionHandler(IdempotencyConflictException.class)
  public ResponseEntity<ErrorResponse> handleIdempotencyConflict(IdempotencyConflictException ex) {
    return ResponseEntity.status(HttpStatus.CONFLICT)
        .body(new ErrorResponse(ex.code(), ex.getMessage()));
  }

  @ExceptionHandler(ReconciliationRequiredException.class)
  public ResponseEntity<ErrorResponse> handleReconciliationRequired(
      ReconciliationRequiredException ex) {
    return ResponseEntity.status(HttpStatus.CONFLICT)
        .body(new ErrorResponse(ex.code(), ex.getMessage()));
  }

  @ExceptionHandler(TransferNotResolvableException.class)
  public ResponseEntity<ErrorResponse> handleTransferNotResolvable(
      TransferNotResolvableException ex) {
    return ResponseEntity.status(HttpStatus.CONFLICT)
        .body(new ErrorResponse(ex.code(), ex.getMessage()));
  }

  @ExceptionHandler(TransferNotFoundException.class)
  public ResponseEntity<ErrorResponse> handleNotFound(TransferNotFoundException ex) {
    return ResponseEntity.status(HttpStatus.NOT_FOUND)
        .body(new ErrorResponse(ex.code(), ex.getMessage()));
  }
}
