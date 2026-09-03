package uz.bobnoza.wedding.dto.seating;

import java.util.UUID;

/** One guest placed at a table in the hall view — anonymized the same way the seating chart already is. */
public record HallGuestEntry(
        UUID guestId,
        String displayName,
        int partySize,
        boolean ownGuest
) {}
