package uz.bobnoza.wedding.dto.importing;

import java.util.UUID;

public record ImportRowResult(
        int rowNumber,
        String rawLine,
        UUID guestId,
        String errorMessage
) {}
