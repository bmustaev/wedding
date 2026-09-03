package uz.bobnoza.wedding.dto.admin;

import java.time.Instant;
import java.util.UUID;

public record AdminSummaryResponse(
        UUID id,
        String username,
        String role,
        String side,
        boolean active,
        long guestCount,
        Instant createdAt
) {}
