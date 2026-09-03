package uz.bobnoza.wedding.dto.seating;

import java.util.List;

public record HallViewResponse(
        HallTableResponse headTable,
        List<HallTableResponse> brideTables,
        List<HallTableResponse> groomTables,
        List<UnassignedGuestResponse> unassignedGuests
) {}
