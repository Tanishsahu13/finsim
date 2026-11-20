package io.github.mhnuk2007.finsim.faults.internal;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface FaultRuleRepository extends JpaRepository<FaultRuleEntity, String> {

  List<FaultRuleEntity> findByTargetInstitutionIgnoreCase(String targetInstitution);
}
