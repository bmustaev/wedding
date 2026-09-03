package uz.bobnoza.wedding.dto.seating;

import java.util.UUID;

/**
 * A caller's own guest with no table yet — the draggable "roster" entries in the hall view.
 * ownerUsername is null for a regular admin (always their own guest, redundant to show) and
 * populated only when the caller is super_admin, viewing a mixed list from every admin.
 */
public record UnassignedGuestResponse(
        UUID id,
        String displayName,
        int partySize,
        boolean isGroup,
        String ownerUsername
) {}
