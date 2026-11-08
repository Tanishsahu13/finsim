package io.github.mhnuk2007.finsim.samples.bank;

import io.github.mhnuk2007.finsim.samples.bank.Contract.ErrorBody;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

/** Malformed input gets the contract's error shape instead of a framework default. */
@RestControllerAdvice
class ApiExceptionHandler {

  @ExceptionHandler({
    HttpMessageNotReadableException.class,
    MissingServletRequestParameterException.class,
    MethodArgumentTypeMismatchException.class
  })
  ResponseEntity<ErrorBody> badRequest(Exception e) {
    return ResponseEntity.badRequest()
        .body(new ErrorBody("INVALID_REQUEST", "Malformed or incomplete request."));
  }
}
