package uz.bobnoza.wedding.repository;

import uz.bobnoza.wedding.entity.SeatingTable;
import uz.bobnoza.wedding.entity.TableSide;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface SeatingTableRepository extends JpaRepository<SeatingTable, UUID> {

    List<SeatingTable> findAllByOrderBySideAscTableNumberAsc();

    List<SeatingTable> findAllBySideOrderByTableNumberAsc(TableSide side);

    Optional<SeatingTable> findBySideAndTableNumber(TableSide side, Integer tableNumber);

    /** Next number to use when an admin adds a new table on their side (1 if they have none yet). */
    @Query("select coalesce(max(t.tableNumber), 0) + 1 from SeatingTable t where t.side = :side")
    int nextTableNumberForSide(@Param("side") TableSide side);
}
