package uz.bobnoza.wedding.dto.guest;

import java.util.List;

/**
 * What a guest sees when they open their own landing page — no admin-only fields.
 * tableLabel is the side-qualified display form guests actually recognize, e.g.
 * "1D" (bride) / "1B" (groom) / "Head Table" — see {@link uz.bobnoza.wedding.entity.SeatingTable#getLabel()}.
 * Null iff tableNumber is null.
 */
public record PublicInvitationResponse(
        String displayName,
        boolean isGroup,
        List<String> groupMembers,
        String greetingMessage,
        String language,
        Integer tableNumber,
        String tableLabel,
        int photosRemaining,
        int videosRemaining
) {}
