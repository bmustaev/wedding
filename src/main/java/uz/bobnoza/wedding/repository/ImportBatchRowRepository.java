package uz.bobnoza.wedding.repository;

import uz.bobnoza.wedding.entity.ImportBatchRow;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface ImportBatchRowRepository extends JpaRepository<ImportBatchRow, UUID> {

    List<ImportBatchRow> findAllByBatchIdOrderByRowNumberAsc(UUID batchId);

    long countByBatchIdAndGuestIsNotNull(UUID batchId);

    long countByBatchIdAndGuestIsNull(UUID batchId);
}
