package uz.bobnoza.wedding.dto.seating;

import java.util.UUID;

public record TableResponse(
        UUID id,
        String side,
        Integer tableNumber,
        String label,
        int capacity,
        int seatsLeft
) {}
