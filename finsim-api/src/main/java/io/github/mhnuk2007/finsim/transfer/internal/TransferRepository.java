package io.github.mhnuk2007.finsim.transfer.internal;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TransferRepository extends JpaRepository<TransferEntity, String> {

  Optional<TransferEntity> findByIdempotencyKey(String idempotencyKey);
}
