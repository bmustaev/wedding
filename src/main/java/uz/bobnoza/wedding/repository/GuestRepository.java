package uz.bobnoza.wedding.repository;

import uz.bobnoza.wedding.entity.Guest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface GuestRepository extends JpaRepository<Guest, UUID> {

    /** Primary access path for an admin's own guest list — always filter by admin_id. */
    Page<Guest> findAllByAdminIdAndDeletedFalseOrderByDisplayNameAsc(UUID adminId, Pageable pageable);

    /** Ownership-checked single lookup — returns empty if the guest belongs to a different admin. */
    Optional<Guest> findByIdAndAdminIdAndDeletedFalse(UUID id, UUID adminId);

    /** Unrestricted lookup — only for super_admin, which has no ownership scope to filter by. */
    Optional<Guest> findByIdAndDeletedFalse(UUID id);

    /** Every unassigned guest across every admin — only for super_admin's hall view (a regular admin sees only their own). */
    @Query("select g from Guest g left join fetch g.admin where g.table is null and g.deleted = false order by g.displayName asc")
    List<Guest> findAllUnassignedAcrossAllAdmins();

    /** Public landing-page resolution by unguessable token — no admin scoping needed. */
    Optional<Guest> findByLandingSlugAndDeletedFalse(String landingSlug);

    List<Guest> findAllByTableIdAndDeletedFalse(UUID tableId);

    long countByAdminIdAndDeletedFalse(UUID adminId);

    @Query("select coalesce(sum(g.partySize), 0) from Guest g " +
           "where g.table.id = :tableId and g.deleted = false and g.id <> :excludeGuestId")
    int sumPartySizeAtTableExcluding(@Param("tableId") UUID tableId, @Param("excludeGuestId") UUID excludeGuestId);
}
