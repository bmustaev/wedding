package uz.bobnoza.wedding.dto.guest;

import jakarta.validation.constraints.Min;

import java.util.List;

public record GuestUpdateRequest(
        String displayName,
        Boolean isGroup,
        @Min(1) Integer partySize,
        List<String> groupMembers,
        String greetingMessage,
        String language
) {}
