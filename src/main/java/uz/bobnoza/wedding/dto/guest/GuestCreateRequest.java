package uz.bobnoza.wedding.dto.guest;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;

import java.util.List;

public record GuestCreateRequest(
        @NotBlank String displayName,
        boolean isGroup,
        @Min(1) Integer partySize,
        List<String> groupMembers,
        String greetingMessage,
        String language
) {}
