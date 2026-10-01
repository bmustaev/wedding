package uz.bobnoza.wedding.controller;

import uz.bobnoza.wedding.dto.guest.PublicInvitationResponse;
import uz.bobnoza.wedding.dto.media.MediaResponse;
import uz.bobnoza.wedding.dto.media.MediaTableGroup;
import uz.bobnoza.wedding.dto.media.StartUploadRequest;
import uz.bobnoza.wedding.dto.media.UpdateMediaVisibilityRequest;
import uz.bobnoza.wedding.dto.media.UploadSessionResponse;
import uz.bobnoza.wedding.entity.MediaVisibility;
import uz.bobnoza.wedding.service.GuestMediaService;
import uz.bobnoza.wedding.service.GuestService;
import uz.bobnoza.wedding.service.MediaUploadService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.util.List;
import java.util.UUID;

/**
 * Guest-facing endpoints, reached by clicking the link on the invitation
 * landing page. No login exists for guests — the unguessable landing_slug
 * in the URL *is* the credential (see SecurityConfig: "/api/public/**" is
 * permitAll). Never accept a raw guest UUID here, only the slug.
 */
@RestController
@RequestMapping("/api/public/invitations")
public class PublicInvitationController {

    private final GuestService guestService;
    private final GuestMediaService guestMediaService;
    private final MediaUploadService mediaUploadService;

    public PublicInvitationController(GuestService guestService, GuestMediaService guestMediaService,
                                      MediaUploadService mediaUploadService) {
        this.guestService = guestService;
        this.guestMediaService = guestMediaService;
        this.mediaUploadService = mediaUploadService;
    }

    @GetMapping("/{slug}")
    public PublicInvitationResponse getInvitation(@PathVariable String slug) {
        return guestService.getPublicInvitation(slug);
    }

    /** The guest's own photos and videos, including ones still converting. */
    @GetMapping("/{slug}/media")
    public List<MediaResponse> listMedia(@PathVariable String slug) {
        return guestMediaService.listOwnMedia(guestService.resolveGuestIdBySlug(slug));
    }

    /** Everyone's PUBLIC photos and videos in this guest's hall, grouped by table. Never PRIVATE ones. */
    @GetMapping("/{slug}/feed")
    public List<MediaTableGroup> feed(@PathVariable String slug) {
        return guestMediaService.feed(guestService.resolveGuestIdBySlug(slug));
    }

    // --- chunked upload (see MediaUploadService) -------------------------

    @PostMapping("/{slug}/media/uploads")
    public ResponseEntity<UploadSessionResponse> startUpload(@PathVariable String slug,
                                                             @Valid @RequestBody StartUploadRequest request) {
        UUID guestId = guestService.resolveGuestIdBySlug(slug);
        return ResponseEntity.status(HttpStatus.CREATED).body(mediaUploadService.start(guestId, request));
    }

    @GetMapping("/{slug}/media/uploads/{uploadId}")
    public UploadSessionResponse uploadStatus(@PathVariable String slug, @PathVariable UUID uploadId) {
        return mediaUploadService.status(guestService.resolveGuestIdBySlug(slug), uploadId);
    }

    /** Raw bytes (application/octet-stream) of one chunk, starting at {@code offset}. */
    @PutMapping("/{slug}/media/uploads/{uploadId}")
    public UploadSessionResponse uploadChunk(@PathVariable String slug, @PathVariable UUID uploadId,
                                             @RequestParam long offset, HttpServletRequest request)
            throws IOException {
        UUID guestId = guestService.resolveGuestIdBySlug(slug);
        return mediaUploadService.appendChunk(guestId, uploadId, offset, request.getInputStream());
    }

    @PostMapping("/{slug}/media/uploads/{uploadId}/complete")
    public ResponseEntity<MediaResponse> completeUpload(@PathVariable String slug, @PathVariable UUID uploadId) {
        UUID guestId = guestService.resolveGuestIdBySlug(slug);
        return ResponseEntity.status(HttpStatus.CREATED).body(mediaUploadService.complete(guestId, uploadId));
    }

    @DeleteMapping("/{slug}/media/uploads/{uploadId}")
    public ResponseEntity<Void> cancelUpload(@PathVariable String slug, @PathVariable UUID uploadId) {
        mediaUploadService.cancel(guestService.resolveGuestIdBySlug(slug), uploadId);
        return ResponseEntity.noContent().build();
    }

    /** A guest sharing (PUBLIC) or hiding (PRIVATE) one of their own uploads — scoped like delete below. */
    @PatchMapping("/{slug}/media/{mediaId}")
    public MediaResponse updateMedia(@PathVariable String slug, @PathVariable UUID mediaId,
                                     @Valid @RequestBody UpdateMediaVisibilityRequest request) {
        UUID guestId = guestService.resolveGuestIdBySlug(slug);
        return guestMediaService.setVisibility(guestId, mediaId, MediaVisibility.valueOf(request.visibility()));
    }

    /**
     * A guest can delete their own upload (e.g. wrong photo), scoped by
     * resolving mediaId only within *their* slug's guestId — never a bare
     * mediaId lookup, or one guest could delete another's media by ID guess.
     */
    @DeleteMapping("/{slug}/media/{mediaId}")
    public ResponseEntity<Void> deleteMedia(@PathVariable String slug, @PathVariable UUID mediaId) {
        UUID guestId = guestService.resolveGuestIdBySlug(slug);
        guestMediaService.deleteMedia(guestId, mediaId);
        return ResponseEntity.noContent().build();
    }
}
