package uz.bobnoza.wedding.dto.seating;

import java.util.UUID;

public record TableOccupancyResponse(
        UUID tableId,
        String side,
        Integer tableNumber,
        String label,
        int capacity,
        int seatsTaken,
        int seatsLeft
) {}
