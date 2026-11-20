package io.github.mhnuk2007.finsim.faults.web;

import io.github.mhnuk2007.finsim.faults.FaultExceptions.FaultNotFoundException;
import io.github.mhnuk2007.finsim.faults.FaultExceptions.InvalidFaultRuleException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice(basePackageClasses = FaultController.class)
public class FaultExceptionHandler {

  public record FaultErrorResponse(String code, String message) {}

  @ExceptionHandler({IllegalArgumentException.class, InvalidFaultRuleException.class})
  public ResponseEntity<FaultErrorResponse> handleBadRequest(Exception ex) {
    return ResponseEntity.status(HttpStatus.BAD_REQUEST)
        .body(new FaultErrorResponse("BAD_REQUEST", ex.getMessage()));
  }

  @ExceptionHandler(FaultNotFoundException.class)
  public ResponseEntity<FaultErrorResponse> handleNotFound(FaultNotFoundException ex) {
    return ResponseEntity.status(HttpStatus.NOT_FOUND)
        .body(new FaultErrorResponse("NOT_FOUND", ex.getMessage()));
  }
}
