package uz.bobnoza.wedding.dto.seating;

import java.util.UUID;

/**
 * A caller's own guest with no table yet — the draggable "roster" entries in the hall view.
 * ownerUsername is null for the caller's own guests (redundant to show) and names the admin
 * who added the guest otherwise — super_admin's mixed list from every admin, or a hall admin's
 * view of their side's guests added by someone else.
 */
public record UnassignedGuestResponse(
        UUID id,
        String displayName,
        int partySize,
        boolean isGroup,
        String ownerUsername,
        String invitationUrl
) {}
