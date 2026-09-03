package uz.bobnoza.wedding.repository;

import uz.bobnoza.wedding.entity.ImportBatch;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ImportBatchRepository extends JpaRepository<ImportBatch, UUID> {

    List<ImportBatch> findAllByAdminIdOrderByCreatedAtDesc(UUID adminId);

    Optional<ImportBatch> findByIdAndAdminId(UUID id, UUID adminId);
}
