package uz.bobnoza.wedding.dto.guest;

import java.util.List;

/** What a guest sees when they open their own landing page — no admin-only fields. */
public record PublicInvitationResponse(
        String displayName,
        boolean isGroup,
        List<String> groupMembers,
        String greetingMessage,
        String language,
        Integer tableNumber,
        int photosRemaining,
        int videosRemaining
) {}
