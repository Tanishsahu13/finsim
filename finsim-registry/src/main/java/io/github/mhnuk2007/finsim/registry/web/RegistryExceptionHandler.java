package io.github.mhnuk2007.finsim.registry.web;

import io.github.mhnuk2007.finsim.registry.RegistryException;
import io.github.mhnuk2007.finsim.registry.RegistryExceptions.CapabilityNotSupported;
import io.github.mhnuk2007.finsim.registry.RegistryExceptions.InstitutionAlreadyExists;
import io.github.mhnuk2007.finsim.registry.RegistryExceptions.InstitutionNotFound;
import io.github.mhnuk2007.finsim.registry.RegistryExceptions.InvalidInstitution;
import io.github.mhnuk2007.finsim.registry.RegistryExceptions.NetworkNotFound;
import io.github.mhnuk2007.finsim.registry.RegistryExceptions.NotAMember;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/** Every registry failure gets the same error shape: a stable code and a readable message. */
@RestControllerAdvice(basePackageClasses = RegistryExceptionHandler.class)
class RegistryExceptionHandler {

  @ExceptionHandler(RegistryException.class)
  ResponseEntity<ErrorBody> registryFailure(RegistryException e) {
    return ResponseEntity.status(statusOf(e)).body(new ErrorBody(e.code(), e.getMessage()));
  }

  @ExceptionHandler(HttpMessageNotReadableException.class)
  ResponseEntity<ErrorBody> malformed(HttpMessageNotReadableException e) {
    return ResponseEntity.badRequest()
        .body(new ErrorBody("INVALID_REQUEST", "Malformed or incomplete request body."));
  }

  private static int statusOf(RegistryException e) {
    if (e instanceof InvalidInstitution) {
      return 400;
    }
    if (e instanceof InstitutionNotFound || e instanceof NetworkNotFound) {
      return 404;
    }
    if (e instanceof InstitutionAlreadyExists) {
      return 409;
    }
    if (e instanceof NotAMember || e instanceof CapabilityNotSupported) {
      return 422;
    }
    return 500;
  }
}
