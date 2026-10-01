package uz.bobnoza.wedding.service;

import uz.bobnoza.wedding.dto.seating.CreateTableRequest;
import uz.bobnoza.wedding.dto.seating.HallGuestEntry;
import uz.bobnoza.wedding.dto.seating.HallTableResponse;
import uz.bobnoza.wedding.dto.seating.HallViewResponse;
import uz.bobnoza.wedding.dto.seating.SeatingChartEntryResponse;
import uz.bobnoza.wedding.dto.seating.TableOccupancyResponse;
import uz.bobnoza.wedding.dto.seating.TableResponse;
import uz.bobnoza.wedding.dto.seating.UnassignedGuestResponse;
import uz.bobnoza.wedding.entity.AdminSide;
import uz.bobnoza.wedding.entity.Guest;
import uz.bobnoza.wedding.entity.Hall;
import uz.bobnoza.wedding.entity.SeatingTable;
import uz.bobnoza.wedding.entity.TableSide;
import uz.bobnoza.wedding.exception.CapacityExceededException;
import uz.bobnoza.wedding.exception.ForbiddenOperationException;
import uz.bobnoza.wedding.exception.ResourceNotFoundException;
import uz.bobnoza.wedding.exception.TableNotEmptyException;
import uz.bobnoza.wedding.repository.GuestRepository;
import uz.bobnoza.wedding.repository.SeatingTableRepository;
import uz.bobnoza.wedding.security.AdminPrincipal;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Pageable;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Table occupancy, the isolation-aware seating chart, the hall-map view,
 * and table CRUD.
 *
 * The occupancy view and the get_seating_chart_for_admin stored procedure
 * (schema.sql) are called directly via JdbcTemplate rather than reimplemented
 * in JPQL — that SQL was validated live against MariaDB (overbooking
 * rejected, cross-admin names correctly anonymized, side/label correct),
 * and duplicating the same logic in Java would only create a second place
 * for the two to drift apart.
 *
 * Table assignment/unassignment and table create/delete go through JPA,
 * since those are normal entity mutations — the database trigger
 * (trg_guests_table_capacity_*) is still the final authority on capacity;
 * the checks here just give a clean 4xx instead of a raw constraint error.
 *
 * super_admin bypasses every ownership/side restriction in this class —
 * see the isSuperAdmin() branches below. It's the one role explicitly
 * meant to manage guests and tables across both sides without limitation.
 *
 * Every table stands in one {@link Hall}. A hall the caller can't access
 * (the bride side and Samarkand) is filtered out of every listing, and any
 * direct reference to one of its tables is a 404, as if it didn't exist.
 */
@Service
public class SeatingService {

    private final JdbcTemplate jdbcTemplate;
    private final GuestRepository guestRepository;
    private final SeatingTableRepository seatingTableRepository;
    private final String invitationBaseUrl;

    public SeatingService(JdbcTemplate jdbcTemplate,
                           GuestRepository guestRepository,
                           SeatingTableRepository seatingTableRepository,
                           @Value("${app.invitation.base-url}") String invitationBaseUrl) {
        this.jdbcTemplate = jdbcTemplate;
        this.guestRepository = guestRepository;
        this.seatingTableRepository = seatingTableRepository;
        this.invitationBaseUrl = invitationBaseUrl;
    }

    @Transactional(readOnly = true)
    public List<TableOccupancyResponse> listOccupancy(AdminPrincipal caller) {
        return jdbcTemplate.query(
                "SELECT table_id, hall, side, table_number, label, capacity, seats_taken, seats_left FROM v_table_occupancy",
                (rs, rowNum) -> new TableOccupancyResponse(
                        UUID.fromString(rs.getString("table_id")),
                        rs.getString("hall").toUpperCase(),
                        rs.getString("side").toUpperCase(),
                        (Integer) rs.getObject("table_number"),
                        rs.getString("label"),
                        rs.getInt("capacity"),
                        rs.getInt("seats_taken"),
                        rs.getInt("seats_left")))
                .stream()
                .filter(t -> caller.canAccessHall(Hall.valueOf(t.hall())))
                .toList();
    }

    @Transactional(readOnly = true)
    public List<SeatingChartEntryResponse> getSeatingChart(AdminPrincipal caller) {
        return queryChartRows(caller.getAdminId()).stream()
                .filter(row -> caller.canAccessHall(Hall.valueOf(row.hall())))
                .toList();
    }

    /**
     * Everything the hall-map page needs for one hall in one call: the head
     * table (Tashkent only), every bride table, every groom table (each with its seated guests, isolation
     * rules already applied — super_admin sees every guest by name, since
     * the stored procedure grants it the same visibility as an owner), plus
     * the unassigned-guest roster: the caller's own guests, or — for
     * super_admin — every admin's unassigned guests, since it has no side
     * or ownership of its own to scope that list by. Only guests invited to
     * this hall are listed as unassigned — they can't be seated anywhere else.
     */
    @Transactional(readOnly = true)
    public HallViewResponse getHallView(AdminPrincipal caller, String requestedHall) {
        Hall hall = HallResolver.resolve(caller, requestedHall);
        List<SeatingChartEntryResponse> rows = queryChartRows(caller.getAdminId());

        Map<UUID, HallTableBuilder> byTable = new LinkedHashMap<>();
        for (SeatingChartEntryResponse row : rows) {
            if (!row.hall().equals(hall.name())) {
                continue;
            }
            HallTableBuilder builder = byTable.computeIfAbsent(row.tableId(), id -> new HallTableBuilder(row));
            if (row.guestId() != null) {
                builder.guests.add(new HallGuestEntry(row.guestId(), row.displayName(), row.partySize(), row.ownGuest(), row.invitationUrl()));
            }
        }

        HallTableResponse headTable = null;
        List<HallTableResponse> brideTables = new ArrayList<>();
        List<HallTableResponse> groomTables = new ArrayList<>();

        for (HallTableBuilder b : byTable.values()) {
            HallTableResponse table = b.build();
            switch (b.side) {
                case "HEAD" -> headTable = table;
                case "BRIDE" -> brideTables.add(table);
                case "GROOM" -> groomTables.add(table);
                default -> { /* unreachable — side is always one of the three */ }
            }
        }

        List<UnassignedGuestResponse> unassigned = caller.isSuperAdmin()
                ? guestRepository.findAllUnassignedAcrossAllAdmins(hall).stream()
                        .map(g -> new UnassignedGuestResponse(
                                g.getId(), g.getDisplayName(), g.getPartySize(), g.isGroup(), g.getAdmin().getUsername(),
                                invitationBaseUrl + "/" + g.getLandingSlug()))
                        .toList()
                : guestRepository.findAllByAdminIdAndDeletedFalseOrderByDisplayNameAsc(caller.getAdminId(), Pageable.unpaged())
                        .stream()
                        .filter(g -> g.getTable() == null && g.getHall() == hall)
                        .map(g -> new UnassignedGuestResponse(g.getId(), g.getDisplayName(), g.getPartySize(), g.isGroup(), null,
                                invitationBaseUrl + "/" + g.getLandingSlug()))
                        .toList();

        return new HallViewResponse(hall.name(), headTable, brideTables, groomTables, unassigned);
    }

    @Transactional
    public void assignGuestToTable(AdminPrincipal caller, UUID guestId, UUID tableId) {
        Guest guest = resolveGuestForAction(caller, guestId);
        SeatingTable table = requireAccessibleTable(caller, tableId);

        requireOwnSideOrHead(caller, table);
        if (table.getHall() != guest.getHall()) {
            throw new ForbiddenOperationException(
                    "This guest is invited to the " + guest.getHall().name() + " hall and can only be seated at its tables");
        }

        int seatsTakenByOthers = guestRepository.sumPartySizeAtTableExcluding(tableId, guestId);
        int seatsLeft = table.getCapacity() - seatsTakenByOthers;
        if (guest.getPartySize() > seatsLeft) {
            throw new CapacityExceededException(
                    "Table " + table.getLabel() + " has " + seatsLeft + " seat(s) left, but this guest needs "
                            + guest.getPartySize());
        }

        guest.setTable(table);
    }

    @Transactional
    public void unassignGuestFromTable(AdminPrincipal caller, UUID guestId) {
        Guest guest = resolveGuestForAction(caller, guestId);
        guest.setTable(null);
    }

    // -----------------------------------------------------------------
    // Table CRUD — each admin manages tables on their own side only.
    // super_admin manages either side, and must say which one (no side of its own).
    // -----------------------------------------------------------------

    @Transactional
    public TableResponse createTable(AdminPrincipal caller, CreateTableRequest request) {
        Hall hall = HallResolver.resolve(caller, request.hall());
        TableSide side = resolveSideForTableAction(caller, request.side());
        if (!hall.isOpenTo(AdminSide.valueOf(side.name()))) {
            throw new ForbiddenOperationException("The " + hall.name() + " hall has no " + side.name() + "-side tables");
        }
        int nextNumber = seatingTableRepository.nextTableNumberForSide(hall, side);

        SeatingTable table = SeatingTable.builder()
                .hall(hall)
                .side(side)
                .tableNumber(nextNumber)
                .capacity(request.capacity() != null ? request.capacity() : 12)
                .build();

        return toTableResponse(seatingTableRepository.save(table), 0);
    }

    @Transactional
    public void deleteTable(AdminPrincipal caller, UUID tableId) {
        SeatingTable table = requireAccessibleTable(caller, tableId);

        if (table.getSide() == TableSide.HEAD) {
            throw new ForbiddenOperationException("The head table can't be removed");
        }
        if (!caller.isSuperAdmin()) {
            TableSide callerSide = requireCallerSide(caller);
            if (table.getSide() != callerSide) {
                throw new ForbiddenOperationException("You can only remove tables on your own side");
            }
        }

        List<Guest> seated = guestRepository.findAllByTableIdAndDeletedFalse(tableId);
        if (!seated.isEmpty()) {
            throw new TableNotEmptyException(
                    "Move the " + seated.size() + " guest(s) at " + table.getLabel() + " to another table first");
        }

        seatingTableRepository.delete(table);
    }

    // -----------------------------------------------------------------
    // Helpers
    // -----------------------------------------------------------------

    /** Resolves the guest for an assign/unassign action — any guest for super_admin, own guest only otherwise. */
    private Guest resolveGuestForAction(AdminPrincipal caller, UUID guestId) {
        if (caller.isSuperAdmin()) {
            return guestRepository.findByIdAndDeletedFalse(guestId)
                    .orElseThrow(() -> new ResourceNotFoundException("Guest not found: " + guestId));
        }
        return guestRepository.findByIdAndAdminIdAndDeletedFalse(guestId, caller.getAdminId())
                .orElseThrow(() -> new ResourceNotFoundException("Guest not found: " + guestId));
    }

    /** 404 for a table in a hall the caller can't access, same as for one that doesn't exist. */
    private SeatingTable requireAccessibleTable(AdminPrincipal caller, UUID tableId) {
        return seatingTableRepository.findById(tableId)
                .filter(table -> caller.canAccessHall(table.getHall()))
                .orElseThrow(() -> new ResourceNotFoundException("Table not found: " + tableId));
    }

    private void requireOwnSideOrHead(AdminPrincipal caller, SeatingTable table) {
        if (caller.isSuperAdmin() || table.getSide() == TableSide.HEAD) {
            return; // super_admin: any side. Anyone: head table is open for their own guests.
        }
        TableSide callerSide = requireCallerSide(caller);
        if (table.getSide() != callerSide) {
            throw new ForbiddenOperationException(
                    "You can only seat your own guests at tables on your own side");
        }
    }

    private TableSide requireCallerSide(AdminPrincipal caller) {
        if (caller.getSide() == null) {
            throw new ForbiddenOperationException("This action requires a bride- or groom-side admin account");
        }
        return TableSide.valueOf(caller.getSide().name());
    }

    /** super_admin must specify a side explicitly (BRIDE/GROOM); everyone else always uses their own. */
    private TableSide resolveSideForTableAction(AdminPrincipal caller, String requestedSide) {
        if (!caller.isSuperAdmin()) {
            return requireCallerSide(caller);
        }
        if (requestedSide == null) {
            throw new ForbiddenOperationException("side (BRIDE or GROOM) is required when creating a table as super admin");
        }
        try {
            TableSide side = TableSide.valueOf(requestedSide.toUpperCase());
            if (side == TableSide.HEAD) {
                throw new ForbiddenOperationException("Only one head table exists and it can't be recreated");
            }
            return side;
        } catch (IllegalArgumentException e) {
            throw new ForbiddenOperationException("side must be BRIDE or GROOM");
        }
    }

    private List<SeatingChartEntryResponse> queryChartRows(UUID adminId) {
        return jdbcTemplate.query(
                "{call get_seating_chart_for_admin(?)}",
                ps -> ps.setString(1, adminId.toString()),
                (rs, rowNum) -> {
                    String guestIdStr = rs.getString("guest_id");
                    Object partySizeObj = rs.getObject("party_size");
                    String landingSlug = rs.getString("landing_slug");
                    return new SeatingChartEntryResponse(
                            UUID.fromString(rs.getString("table_id")),
                            rs.getString("hall").toUpperCase(),
                            rs.getString("side").toUpperCase(),
                            (Integer) rs.getObject("table_number"),
                            rs.getString("label"),
                            rs.getInt("capacity"),
                            rs.getInt("seats_left"),
                            guestIdStr != null ? UUID.fromString(guestIdStr) : null,
                            rs.getString("display_name"),
                            landingSlug != null ? invitationBaseUrl + "/" + landingSlug : null,
                            partySizeObj != null ? rs.getInt("party_size") : null,
                            rs.getBoolean("is_own_guest"));
                });
    }

    private TableResponse toTableResponse(SeatingTable table, int seatsTaken) {
        return new TableResponse(
                table.getId(),
                table.getHall().name(),
                table.getSide().name(),
                table.getTableNumber(),
                table.getLabel(),
                table.getCapacity(),
                table.getCapacity() - seatsTaken);
    }

    /** Accumulates one hall-view table's guest list while walking the chart rows. */
    private static final class HallTableBuilder {
        final UUID id;
        final String side;
        final Integer tableNumber;
        final String label;
        final int capacity;
        final int seatsLeft;
        final List<HallGuestEntry> guests = new ArrayList<>();

        HallTableBuilder(SeatingChartEntryResponse firstRow) {
            this.id = firstRow.tableId();
            this.side = firstRow.side();
            this.tableNumber = firstRow.tableNumber();
            this.label = firstRow.label();
            this.capacity = firstRow.capacity();
            this.seatsLeft = firstRow.seatsLeft();
        }

        HallTableResponse build() {
            return new HallTableResponse(id, side, tableNumber, label, capacity, seatsLeft, guests);
        }
    }
}
