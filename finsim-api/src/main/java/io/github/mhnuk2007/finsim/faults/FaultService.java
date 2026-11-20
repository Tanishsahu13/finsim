package io.github.mhnuk2007.finsim.faults;

import io.github.mhnuk2007.finsim.faults.internal.FaultRuleEntity;
import io.github.mhnuk2007.finsim.faults.internal.FaultRuleRepository;
import io.github.mhnuk2007.finsim.sim.FaultDecision;
import io.github.mhnuk2007.finsim.sim.FaultEvaluator;
import io.github.mhnuk2007.finsim.sim.FaultRule;
import io.github.mhnuk2007.finsim.sim.FaultType;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class FaultService {

  private final FaultRuleRepository repository;
  private final FaultEvaluator evaluator;
  private final Clock clock;

  @Autowired
  public FaultService(FaultRuleRepository repository, Clock clock) {
    this(repository, new FaultEvaluator(), clock);
  }

  public FaultService(FaultRuleRepository repository) {
    this(repository, new FaultEvaluator(), Clock.systemUTC());
  }

  public FaultService(FaultRuleRepository repository, FaultEvaluator evaluator, Clock clock) {
    this.repository = repository;
    this.evaluator = evaluator;
    this.clock = clock;
  }

  @Transactional
  public FaultRuleDto register(FaultRuleDto request) {
    validate(request);

    String id =
        request.faultId() != null && !request.faultId().isBlank()
            ? request.faultId()
            : UUID.randomUUID().toString();

    Instant now = Instant.now(clock);
    FaultRuleEntity entity =
        new FaultRuleEntity(
            id,
            request.target().trim(),
            request.type(),
            request.type() == FaultType.DELAY ? request.delayMs() : null,
            request.probability() != null ? request.probability() : 1.0,
            request.times(),
            request.seed(),
            now);

    FaultRuleEntity saved = repository.save(entity);
    return toDto(saved);
  }

  @Transactional(readOnly = true)
  public List<FaultRuleDto> listActive() {
    return repository.findAll().stream()
        .filter(FaultRuleEntity::isActive)
        .map(this::toDto)
        .toList();
  }

  @Transactional
  public boolean delete(String faultId) {
    Optional<FaultRuleEntity> existing = repository.findById(faultId);
    if (existing.isPresent()) {
      repository.delete(existing.get());
      evaluator.resetPrng(faultId);
      return true;
    }
    return false;
  }

  @Transactional
  public void clearAll() {
    repository.deleteAll();
    evaluator.resetAll();
  }

  @Transactional
  public FaultDecision evaluateOutboundCall(String targetInstitution) {
    if (targetInstitution == null || targetInstitution.isBlank()) {
      return FaultDecision.none();
    }

    List<FaultRuleEntity> entities =
        repository.findByTargetInstitutionIgnoreCase(targetInstitution.trim());
    List<FaultRule> activeRules =
        entities.stream().filter(FaultRuleEntity::isActive).map(FaultRuleEntity::toDomain).toList();

    if (activeRules.isEmpty()) {
      return FaultDecision.none();
    }

    FaultEvaluator.EvaluationResult result =
        evaluator.evaluate(activeRules, targetInstitution.trim());
    if (result.decision().triggered() && result.updatedRule().isPresent()) {
      FaultRule updated = result.updatedRule().get();
      // Update persistent entity trigger count if limited
      if (updated.remainingCount() != null) {
        entities.stream()
            .filter(e -> e.getId().equals(updated.faultId()))
            .findFirst()
            .ifPresent(
                e -> {
                  e.setRemainingCount(updated.remainingCount(), Instant.now(clock));
                  repository.save(e);
                });
      }
    }

    return result.decision();
  }

  private void validate(FaultRuleDto dto) {
    if (dto == null) {
      throw new IllegalArgumentException("Fault rule body is required.");
    }
    if (dto.target() == null || dto.target().isBlank()) {
      throw new IllegalArgumentException("Target institution is required.");
    }
    if (dto.type() == null) {
      throw new IllegalArgumentException("Fault type is required.");
    }
    if (dto.type() == FaultType.DELAY) {
      if (dto.delayMs() == null || dto.delayMs() < 0) {
        throw new IllegalArgumentException("delayMs must be non-negative for DELAY fault type.");
      }
    }
    if (dto.probability() != null && (dto.probability() < 0.0 || dto.probability() > 1.0)) {
      throw new IllegalArgumentException("probability must be between 0.0 and 1.0 inclusive.");
    }
    if (dto.times() != null && dto.times() <= 0) {
      throw new IllegalArgumentException("times must be greater than 0 if specified.");
    }
  }

  private FaultRuleDto toDto(FaultRuleEntity entity) {
    return new FaultRuleDto(
        entity.getId(),
        entity.getTargetInstitution(),
        entity.getFaultType(),
        entity.getDelayMs(),
        entity.getProbability(),
        entity.getRemainingCount(),
        entity.getSeed());
  }
}
