package uz.bobnoza.wedding.repository;

import uz.bobnoza.wedding.entity.AdminSide;
import uz.bobnoza.wedding.entity.Guest;
import uz.bobnoza.wedding.entity.Hall;
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

    /**
     * A hall admin's guest list: their own guests plus every guest their side
     * has in their hall — the list form of AdminPrincipal#canManageGuest.
     */
    @Query("select g from Guest g where g.deleted = false " +
           "and (g.admin.id = :adminId or (g.hall = :hall and g.admin.side = :side)) order by g.displayName asc")
    Page<Guest> findAllManagedByHallAdmin(@Param("adminId") UUID adminId, @Param("hall") Hall hall,
                                          @Param("side") AdminSide side, Pageable pageable);

    /** Unscoped lookup — callers must check AdminPrincipal#canManageGuest on the result. */
    Optional<Guest> findByIdAndDeletedFalse(UUID id);

    /** Every unassigned guest of one hall across every admin — only for super_admin's hall view (a regular admin sees only their own). */
    @Query("select g from Guest g left join fetch g.admin " +
           "where g.table is null and g.deleted = false and g.hall = :hall order by g.displayName asc")
    List<Guest> findAllUnassignedAcrossAllAdmins(@Param("hall") Hall hall);

    /** Public landing-page resolution by unguessable token — no admin scoping needed. */
    Optional<Guest> findByLandingSlugAndDeletedFalse(String landingSlug);

    List<Guest> findAllByTableIdAndDeletedFalse(UUID tableId);

    long countByAdminIdAndDeletedFalse(UUID adminId);

    @Query("select coalesce(sum(g.partySize), 0) from Guest g " +
           "where g.table.id = :tableId and g.deleted = false and g.id <> :excludeGuestId")
    int sumPartySizeAtTableExcluding(@Param("tableId") UUID tableId, @Param("excludeGuestId") UUID excludeGuestId);
}
