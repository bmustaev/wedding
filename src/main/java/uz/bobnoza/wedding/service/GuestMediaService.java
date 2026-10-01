package uz.bobnoza.wedding.service;

import uz.bobnoza.wedding.config.MediaProperties;
import uz.bobnoza.wedding.dto.media.MediaAllowanceResponse;
import uz.bobnoza.wedding.dto.media.MediaResponse;
import uz.bobnoza.wedding.dto.media.MediaTableGroup;
import uz.bobnoza.wedding.dto.media.StorageStatusResponse;
import uz.bobnoza.wedding.entity.Guest;
import uz.bobnoza.wedding.entity.GuestMedia;
import uz.bobnoza.wedding.entity.Hall;
import uz.bobnoza.wedding.entity.MediaType;
import uz.bobnoza.wedding.entity.MediaVisibility;
import uz.bobnoza.wedding.entity.ProcessingStatus;
import uz.bobnoza.wedding.entity.SeatingTable;
import uz.bobnoza.wedding.entity.TableSide;
import uz.bobnoza.wedding.exception.MediaLimitExceededException;
import uz.bobnoza.wedding.exception.MediaRejectedException;
import uz.bobnoza.wedding.exception.ResourceNotFoundException;
import uz.bobnoza.wedding.repository.GuestMediaRepository;
import uz.bobnoza.wedding.repository.GuestRepository;
import uz.bobnoza.wedding.security.AdminPrincipal;
import uz.bobnoza.wedding.service.media.MediaProbeService;
import uz.bobnoza.wedding.service.media.MediaProcessingQueue;
import uz.bobnoza.wedding.service.media.MediaUrlSigner;
import uz.bobnoza.wedding.service.media.MediaUrlSigner.Variant;
import uz.bobnoza.wedding.service.storage.MediaStorageService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Path;
import java.text.Collator;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;

/**
 * Guest photos and videos: turning a finished upload into a GuestMedia row,
 * the 15-photo / 4-video ceiling and the 60-second video limit, listing and
 * deleting. The checks here are fast-fails with clean 4xx responses; the
 * database (trg_guest_media_limit, ck_guest_media_video_duration in
 * schema.sql) is the actual source of truth and the safety net against races.
 *
 * Who sees what:
 * <ul>
 *   <li>a guest — all of their own media, plus every guest's READY
 *       PUBLIC photos and videos in their own hall (the shared feed). The
 *       uploader picks PUBLIC or PRIVATE per file ({@link MediaVisibility});
 *       PRIVATE ones are seen only by the uploader and the admins;</li>
 *   <li>an admin — every photo and video of the guests they can manage
 *       ({@link AdminPrincipal#canManageGuest}, same rule as the dashboard),
 *       PUBLIC or PRIVATE, including the untouched original for download.</li>
 * </ul>
 */
@Service
public class GuestMediaService {

    /** Which fields a response may carry — see MediaResponse. */
    private enum Audience { OWNER, FEED, ADMIN }

    private static final Comparator<TableSide> SIDE_ORDER =
            Comparator.comparingInt(side -> switch (side) {
                case HEAD -> 0;
                case BRIDE -> 1;
                case GROOM -> 2;
            });

    private final GuestMediaRepository guestMediaRepository;
    private final GuestRepository guestRepository;
    private final MediaStorageService storageService;
    private final MediaProbeService probeService;
    private final MediaProcessingQueue processingQueue;
    private final MediaUrlSigner urlSigner;
    private final MediaProperties properties;

    public GuestMediaService(GuestMediaRepository guestMediaRepository,
                             GuestRepository guestRepository,
                             MediaStorageService storageService,
                             MediaProbeService probeService,
                             MediaProcessingQueue processingQueue,
                             MediaUrlSigner urlSigner,
                             MediaProperties properties) {
        this.guestMediaRepository = guestMediaRepository;
        this.guestRepository = guestRepository;
        this.storageService = storageService;
        this.probeService = probeService;
        this.processingQueue = processingQueue;
        this.urlSigner = urlSigner;
        this.properties = properties;
    }

    /** A guest's own photos and videos, any status — for the guest themselves. */
    @Transactional(readOnly = true)
    public List<MediaResponse> listOwnMedia(UUID guestId) {
        return guestMediaRepository.findAllByGuestIdOrderByMediaTypeAscUploadedAtAsc(guestId).stream()
                .map(media -> toResponse(media, Audience.OWNER, guestId))
                .toList();
    }

    /** One guest's media for an admin who manages that guest (ownership checked by the caller). */
    @Transactional(readOnly = true)
    public List<MediaResponse> listMediaForAdmin(UUID guestId) {
        return guestMediaRepository.findAllByGuestIdOrderByMediaTypeAscUploadedAtAsc(guestId).stream()
                .map(media -> toResponse(media, Audience.ADMIN, null))
                .toList();
    }

    /**
     * Every READY PUBLIC photo and video in the guest's own hall, grouped by
     * table — the guest page's shared feed. PRIVATE media never appears
     * here, not even the viewer's own (that's what their own list is for).
     */
    @Transactional(readOnly = true)
    public List<MediaTableGroup> feed(UUID guestId) {
        Guest viewer = guestRepository.findByIdAndDeletedFalse(guestId)
                .orElseThrow(() -> new ResourceNotFoundException("Invitation not found"));
        List<GuestMedia> media = guestMediaRepository.findAllInHallByVisibility(
                viewer.getHall(), MediaVisibility.PUBLIC, ProcessingStatus.READY);
        return groupByTable(media, m -> toResponse(m, Audience.FEED, guestId));
    }

    /** A guest sharing or un-sharing one of their own uploads; someone else's id is a 404. */
    @Transactional
    public MediaResponse setVisibility(UUID guestId, UUID mediaId, MediaVisibility visibility) {
        GuestMedia media = guestMediaRepository.findByIdAndGuestId(mediaId, guestId)
                .orElseThrow(() -> new ResourceNotFoundException("Media not found: " + mediaId));
        media.setVisibility(visibility);
        return toResponse(media, Audience.OWNER, guestId);
    }

    /**
     * One hall's photos or videos across every guest the caller manages,
     * grouped by table. {@code hall} as in the seating endpoints: absent =
     * the caller's default hall, inaccessible = 404.
     */
    @Transactional(readOnly = true)
    public List<MediaTableGroup> listForAdmin(AdminPrincipal caller, String hall, MediaType mediaType) {
        Hall resolved = HallResolver.resolve(caller, hall);
        List<GuestMedia> media = guestMediaRepository.findAllInHall(resolved, mediaType).stream()
                .filter(m -> caller.canManageGuest(m.getGuest()))
                .toList();
        return groupByTable(media, m -> toResponse(m, Audience.ADMIN, null));
    }

    @Transactional(readOnly = true)
    public MediaAllowanceResponse getAllowance(UUID guestId) {
        int photosUsed = (int) guestMediaRepository.countByGuestIdAndMediaType(guestId, MediaType.PHOTO);
        int videosUsed = (int) guestMediaRepository.countByGuestIdAndMediaType(guestId, MediaType.VIDEO);
        return new MediaAllowanceResponse(photosUsed, properties.maxPhotosPerGuest() - photosUsed,
                videosUsed, properties.maxVideosPerGuest() - videosUsed);
    }

    public StorageStatusResponse storageStatus() {
        return new StorageStatusResponse(storageService.usableSpace(), storageService.totalSpace(),
                properties.minFreeDisk().toBytes());
    }

    /** 409 once the guest has reached the ceiling for this type. */
    void requireRoomFor(UUID guestId, MediaType mediaType, long inFlight) {
        int max = properties.maxCountFor(mediaType);
        if (guestMediaRepository.countByGuestIdAndMediaType(guestId, mediaType) + inFlight >= max) {
            throw new MediaLimitExceededException(
                    "This guest already has the maximum of " + max + " " + mediaType.name().toLowerCase() + " uploads");
        }
    }

    /**
     * Turns a fully received upload (a local file in scratch space) into a
     * stored original + a PROCESSING row, and queues its conversion. The
     * file's real content decides everything: a "photo" libvips can't read,
     * or a video longer than the limit, is refused here even if the browser
     * said otherwise. On success the file has been moved into storage.
     */
    @Transactional
    public MediaResponse ingestUpload(UUID guestId, MediaType mediaType, MediaVisibility visibility, Path file,
                                      String originalFilename, long sizeBytes) {
        Guest guest = guestRepository.findByIdAndDeletedFalse(guestId)
                .orElseThrow(() -> new ResourceNotFoundException("Guest not found: " + guestId));
        requireRoomFor(guestId, mediaType, 0);

        String extension;
        String mimeType;
        Integer durationSeconds = null;
        Integer width;
        Integer height;
        if (mediaType == MediaType.PHOTO) {
            MediaProbeService.PhotoProbe probe = probeService.probePhoto(file);
            extension = probe.format().extension();
            mimeType = probe.format().mimeType();
            width = probe.width();
            height = probe.height();
        } else {
            MediaProbeService.VideoProbe probe = probeService.probeVideo(file);
            if (probe.durationSeconds() > properties.maxVideoSecondsWithTolerance()) {
                throw MediaRejectedException.videoTooLong(properties.maxVideoSeconds(), probe.durationSeconds());
            }
            boolean mov = originalFilename.toLowerCase(Locale.ROOT).endsWith(".mov");
            extension = mov ? ".mov" : ".mp4";
            mimeType = mov ? "video/quicktime" : "video/mp4";
            durationSeconds = Math.min(properties.maxVideoSeconds(), (int) Math.round(probe.durationSeconds()));
            width = probe.displayWidth();
            height = probe.displayHeight();
        }

        String storageKey;
        try {
            storageKey = storageService.importFile(file, guestId.toString(),
                    mediaType == MediaType.PHOTO ? "photos" : "videos", extension);
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to store uploaded file", e);
        }

        GuestMedia media = GuestMedia.builder()
                .guest(guest)
                .mediaType(mediaType)
                .storageKey(storageKey)
                .originalFilename(originalFilename)
                .mimeType(mimeType)
                .sizeBytes(sizeBytes)
                .widthPx(width)
                .heightPx(height)
                .durationSeconds(durationSeconds)
                .visibility(visibility)
                .build();
        try {
            media = guestMediaRepository.saveAndFlush(media);
        } catch (RuntimeException e) {
            storageService.delete(storageKey); // e.g. trg_guest_media_limit lost a race — don't orphan the file
            throw e;
        }
        processingQueue.enqueue(media.getId(), mediaType);
        return toResponse(media, Audience.OWNER, guestId);
    }

    @Transactional
    public void deleteMedia(UUID guestId, UUID mediaId) {
        GuestMedia media = guestMediaRepository.findByIdAndGuestId(mediaId, guestId)
                .orElseThrow(() -> new ResourceNotFoundException("Media not found: " + mediaId));
        guestMediaRepository.delete(media);
        storageService.delete(media.getStorageKey());
        storageService.delete(media.getDisplayKey());
        storageService.delete(media.getThumbKey());
    }

    /** Resolves a signed-URL request to the stored file it names; 404 if the variant doesn't exist (yet). */
    @Transactional(readOnly = true)
    public Optional<MediaFile> loadFile(UUID mediaId, Variant variant) {
        return guestMediaRepository.findById(mediaId).flatMap(media -> {
            boolean photo = media.getMediaType() == MediaType.PHOTO;
            return switch (variant) {
                case ORIGINAL -> Optional.of(new MediaFile(media.getStorageKey(), media.getMimeType(),
                        media.getOriginalFilename()));
                case DISPLAY -> Optional.ofNullable(media.getDisplayKey())
                        .map(key -> new MediaFile(key, photo ? "image/jpeg" : "video/mp4", null));
                case THUMB -> Optional.ofNullable(media.getThumbKey())
                        .map(key -> new MediaFile(key, "image/jpeg", null));
            };
        });
    }

    /** {@code downloadName} is set only for originals, which are served as an attachment. */
    public record MediaFile(String storageKey, String mimeType, String downloadName) {}

    private List<MediaTableGroup> groupByTable(List<GuestMedia> media,
                                               Function<GuestMedia, MediaResponse> mapper) {
        Collator collator = Collator.getInstance(Locale.forLanguageTag("ru"));
        Comparator<GuestMedia> order = Comparator
                .comparing((GuestMedia m) -> m.getGuest().getTable(), Comparator.nullsLast(
                        Comparator.comparing(SeatingTable::getSide, SIDE_ORDER)
                                .thenComparing(SeatingTable::getTableNumber, Comparator.nullsFirst(Comparator.naturalOrder()))))
                .thenComparing(m -> m.getGuest().getDisplayName(), collator)
                .thenComparing(m -> m.getGuest().getId())
                .thenComparing(GuestMedia::getUploadedAt);

        Map<UUID, List<GuestMedia>> byTable = new LinkedHashMap<>();
        Map<UUID, SeatingTable> tables = new LinkedHashMap<>();
        List<GuestMedia> noTable = new ArrayList<>();
        for (GuestMedia m : media.stream().sorted(order).toList()) {
            SeatingTable table = m.getGuest().getTable();
            if (table == null) {
                noTable.add(m);
            } else {
                tables.putIfAbsent(table.getId(), table);
                byTable.computeIfAbsent(table.getId(), id -> new ArrayList<>()).add(m);
            }
        }

        List<MediaTableGroup> groups = new ArrayList<>();
        byTable.forEach((tableId, items) -> {
            SeatingTable table = tables.get(tableId);
            groups.add(new MediaTableGroup(table.getLabel(), table.getSide().name(), table.getTableNumber(),
                    items.stream().map(mapper).toList()));
        });
        if (!noTable.isEmpty()) {
            groups.add(new MediaTableGroup(null, null, null, noTable.stream().map(mapper).toList()));
        }
        return groups;
    }

    /** {@code viewerGuestId}: the guest looking (OWNER/FEED), to mark their own items; null for admins. */
    private MediaResponse toResponse(GuestMedia media, Audience audience, UUID viewerGuestId) {
        Guest guest = media.getGuest();
        boolean own = viewerGuestId != null && viewerGuestId.equals(guest.getId());
        boolean ready = media.getProcessingStatus() == ProcessingStatus.READY;
        boolean admin = audience == Audience.ADMIN;
        boolean showPrivate = admin || own;

        MediaProcessingQueue.Progress progress = media.getProcessingStatus() == ProcessingStatus.PROCESSING && showPrivate
                ? processingQueue.progressOf(media.getId(), media.getMediaType())
                : new MediaProcessingQueue.Progress(null, null);
        SeatingTable table = audience == Audience.OWNER ? null : guest.getTable();

        return new MediaResponse(
                media.getId(),
                media.getMediaType().name(),
                media.getVisibility().name(),
                media.getProcessingStatus().name(),
                progress.percent(),
                progress.queuePosition(),
                admin ? media.getProcessingError() : null,
                showPrivate ? media.getOriginalFilename() : null,
                showPrivate ? media.getSizeBytes() : null,
                media.getDurationSeconds(),
                media.getWidthPx(),
                media.getHeightPx(),
                media.getUploadedAt(),
                admin ? guest.getId() : null,
                audience == Audience.OWNER ? null : guest.getDisplayName(),
                table != null ? table.getLabel() : null,
                own,
                ready ? urlSigner.url(media.getId(), Variant.DISPLAY) : null,
                ready ? urlSigner.url(media.getId(), Variant.THUMB) : null,
                admin ? urlSigner.url(media.getId(), Variant.ORIGINAL) : null);
    }
}
