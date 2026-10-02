package uz.bobnoza.wedding.dto.media;

import java.time.Instant;
import java.util.UUID;

/**
 * One photo or video. Which fields are filled depends on who's asking (see
 * GuestMediaService): guests never get {@code guestId}, {@code originalUrl}
 * or {@code processingError}, and only see other guests' PUBLIC photos and
 * videos (never their PRIVATE ones, or file names). {@code fileUrl}/{@code thumbUrl} are signed,
 * expiring links (API.md, "Media files") and are null until
 * {@code status} is READY. While PROCESSING, the uploader also gets either
 * {@code processingPercent} (converting now) or {@code queuePosition}
 * (0 = next in line). {@code questId}/{@code questTitle}: the quest this
 * upload completes, if any.
 */
public record MediaResponse(
        UUID id,
        String mediaType,
        String visibility,
        String status,
        Integer processingPercent,
        Integer queuePosition,
        String processingError,
        String originalFilename,
        Long sizeBytes,
        Integer durationSeconds,
        Integer widthPx,
        Integer heightPx,
        Instant uploadedAt,
        UUID guestId,
        String guestName,
        String tableLabel,
        boolean own,
        String fileUrl,
        String thumbUrl,
        String originalUrl,
        UUID questId,
        String questTitle
) {}
