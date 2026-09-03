package uz.bobnoza.wedding.service;

import uz.bobnoza.wedding.dto.media.MediaAllowanceResponse;
import uz.bobnoza.wedding.dto.media.MediaResponse;
import uz.bobnoza.wedding.entity.Guest;
import uz.bobnoza.wedding.entity.GuestMedia;
import uz.bobnoza.wedding.entity.MediaType;
import uz.bobnoza.wedding.exception.MediaLimitExceededException;
import uz.bobnoza.wedding.exception.ResourceNotFoundException;
import uz.bobnoza.wedding.repository.GuestMediaRepository;
import uz.bobnoza.wedding.repository.GuestRepository;
import uz.bobnoza.wedding.service.storage.MediaStorageService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;
import java.util.UUID;

/**
 * Upload/list/delete for guest photos and videos, and the 15-photo / 4-video
 * ceiling. The count check here is a fast-fail with a clean 409 response;
 * the database trigger (trg_guest_media_limit in schema.sql) is the actual
 * source of truth and the safety net against races between concurrent uploads.
 */
@Service
public class GuestMediaService {

    private final GuestMediaRepository guestMediaRepository;
    private final GuestRepository guestRepository;
    private final MediaStorageService storageService;
    private final int maxPhotos;
    private final int maxVideos;

    public GuestMediaService(GuestMediaRepository guestMediaRepository,
                              GuestRepository guestRepository,
                              MediaStorageService storageService,
                              @Value("${app.media.max-photos-per-guest}") int maxPhotos,
                              @Value("${app.media.max-videos-per-guest}") int maxVideos) {
        this.guestMediaRepository = guestMediaRepository;
        this.guestRepository = guestRepository;
        this.storageService = storageService;
        this.maxPhotos = maxPhotos;
        this.maxVideos = maxVideos;
    }

    @Transactional(readOnly = true)
    public List<MediaResponse> listMedia(UUID guestId) {
        return guestMediaRepository.findAllByGuestIdOrderByMediaTypeAscUploadedAtAsc(guestId).stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public MediaAllowanceResponse getAllowance(UUID guestId) {
        int photosUsed = (int) guestMediaRepository.countByGuestIdAndMediaType(guestId, MediaType.PHOTO);
        int videosUsed = (int) guestMediaRepository.countByGuestIdAndMediaType(guestId, MediaType.VIDEO);
        return new MediaAllowanceResponse(photosUsed, maxPhotos - photosUsed, videosUsed, maxVideos - videosUsed);
    }

    @Transactional
    public MediaResponse uploadMedia(UUID guestId, MediaType mediaType, MultipartFile file) {
        Guest guest = guestRepository.findById(guestId)
                .filter(g -> !g.isDeleted())
                .orElseThrow(() -> new ResourceNotFoundException("Guest not found: " + guestId));

        int max = mediaType == MediaType.PHOTO ? maxPhotos : maxVideos;
        long currentCount = guestMediaRepository.countByGuestIdAndMediaType(guestId, mediaType);
        if (currentCount >= max) {
            throw new MediaLimitExceededException(
                    "This guest already has the maximum of " + max + " " + mediaType.name().toLowerCase() + " uploads");
        }

        String subfolder = mediaType == MediaType.PHOTO ? "photos" : "videos";
        String storageKey;
        try {
            storageKey = storageService.store(file, guestId.toString(), subfolder);
        } catch (IOException e) {
            throw new RuntimeException("Failed to store uploaded file", e);
        }

        GuestMedia media = GuestMedia.builder()
                .guest(guest)
                .mediaType(mediaType)
                .storageKey(storageKey)
                .originalFilename(file.getOriginalFilename() != null ? file.getOriginalFilename() : "upload")
                .mimeType(file.getContentType() != null ? file.getContentType() : "application/octet-stream")
                .sizeBytes(file.getSize())
                .build();

        return toResponse(guestMediaRepository.save(media));
    }

    @Transactional
    public void deleteMedia(UUID guestId, UUID mediaId) {
        GuestMedia media = guestMediaRepository.findByIdAndGuestId(mediaId, guestId)
                .orElseThrow(() -> new ResourceNotFoundException("Media not found: " + mediaId));
        storageService.delete(media.getStorageKey());
        guestMediaRepository.delete(media);
    }

    private MediaResponse toResponse(GuestMedia media) {
        return new MediaResponse(
                media.getId(),
                media.getMediaType().name(),
                media.getStorageKey(),
                media.getOriginalFilename(),
                media.getUploadedAt());
    }
}
