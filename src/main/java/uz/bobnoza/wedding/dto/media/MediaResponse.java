package uz.bobnoza.wedding.dto.media;

import java.time.Instant;
import java.util.UUID;

public record MediaResponse(
        UUID id,
        String mediaType,
        String storageKey,
        String originalFilename,
        Instant uploadedAt
) {}
