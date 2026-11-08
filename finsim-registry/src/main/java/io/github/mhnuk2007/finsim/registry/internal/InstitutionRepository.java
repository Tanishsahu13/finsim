package io.github.mhnuk2007.finsim.registry.internal;

import org.springframework.data.jpa.repository.JpaRepository;

/** Internal: other modules must go through {@code Registry}. */
public interface InstitutionRepository extends JpaRepository<InstitutionEntity, String> {}
