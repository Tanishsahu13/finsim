package io.github.mhnuk2007.finsim.faults.web;

import io.github.mhnuk2007.finsim.faults.FaultExceptions.FaultNotFoundException;
import io.github.mhnuk2007.finsim.faults.FaultRuleDto;
import io.github.mhnuk2007.finsim.faults.FaultService;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/faults")
public class FaultController {

  private final FaultService faultService;

  public FaultController(FaultService faultService) {
    this.faultService = faultService;
  }

  @PostMapping
  @ResponseStatus(HttpStatus.CREATED)
  public FaultRuleDto addFault(@RequestBody FaultRuleDto request) {
    return faultService.register(request);
  }

  @GetMapping
  public List<FaultRuleDto> listFaults() {
    return faultService.listActive();
  }

  @DeleteMapping("/{faultId}")
  public ResponseEntity<Void> removeFault(@PathVariable String faultId) {
    boolean deleted = faultService.delete(faultId);
    if (!deleted) {
      throw new FaultNotFoundException(faultId);
    }
    return ResponseEntity.noContent().build();
  }
}
