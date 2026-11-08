package io.github.mhnuk2007.finsim.registry.web;

import io.github.mhnuk2007.finsim.registry.Institution;
import io.github.mhnuk2007.finsim.registry.RegistrationResult;
import io.github.mhnuk2007.finsim.registry.Registry;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Administration API for institutions. Registration is idempotent by {@code id}: an identical
 * request returns 200 with the existing institution; a different definition returns 409.
 */
@RestController
@RequestMapping("/api/v1/institutions")
class InstitutionController {

  private final Registry registry;

  InstitutionController(Registry registry) {
    this.registry = registry;
  }

  @PostMapping
  ResponseEntity<Institution> register(@RequestBody RegisterInstitutionRequest request) {
    RegistrationResult result = registry.register(request.toCommand());
    return ResponseEntity.status(result.created() ? 201 : 200).body(result.institution());
  }

  @GetMapping
  List<Institution> list() {
    return registry.institutions();
  }

  @GetMapping("/{id}")
  Institution get(@PathVariable("id") String id) {
    return registry.institution(id);
  }
}
