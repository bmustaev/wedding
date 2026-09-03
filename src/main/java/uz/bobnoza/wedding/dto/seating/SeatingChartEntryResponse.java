package uz.bobnoza.wedding.dto.seating;

import java.util.UUID;

/**
 * One guest's placement as seen by a particular admin: own guests show real
 * names; every other admin's guest is anonymized to a seat count only.
 */
public record SeatingChartEntryResponse(
        UUID tableId,
        String side,
        Integer tableNumber,
        String label,
        int capacity,
        int seatsLeft,
        UUID guestId,
        String displayName,
        String invitationUrl,
        Integer partySize,
        boolean ownGuest
) {}
