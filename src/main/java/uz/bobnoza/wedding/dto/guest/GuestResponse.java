package uz.bobnoza.wedding.dto.guest;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record GuestResponse(
        UUID id,
        String displayName,
        boolean isGroup,
        int partySize,
        List<String> groupMembers,
        String greetingMessage,
        String language,
        String landingSlug,
        String invitationUrl,
        UUID tableId,
        Integer tableNumber,
        Instant pageGeneratedAt,
        Instant firstViewedAt,
        Instant createdAt
) {}
