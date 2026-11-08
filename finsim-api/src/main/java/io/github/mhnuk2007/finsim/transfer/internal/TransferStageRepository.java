package io.github.mhnuk2007.finsim.transfer.internal;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TransferStageRepository extends JpaRepository<TransferStageEntity, Long> {

  List<TransferStageEntity> findByTransferIdOrderByIdAsc(String transferId);
}
