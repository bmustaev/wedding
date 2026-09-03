package uz.bobnoza.wedding.dto.seating;

import java.util.List;
import java.util.UUID;

public record HallTableResponse(
        UUID id,
        String side,
        Integer tableNumber,
        String label,
        int capacity,
        int seatsLeft,
        List<HallGuestEntry> guests
) {}
