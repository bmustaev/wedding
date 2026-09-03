package uz.bobnoza.wedding.dto.media;

public record MediaAllowanceResponse(
        int photosUsed,
        int photosRemaining,
        int videosUsed,
        int videosRemaining
) {}
