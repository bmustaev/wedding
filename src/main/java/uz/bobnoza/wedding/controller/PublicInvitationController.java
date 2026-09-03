package uz.bobnoza.wedding.controller;

import uz.bobnoza.wedding.dto.guest.PublicInvitationResponse;
import uz.bobnoza.wedding.dto.media.MediaResponse;
import uz.bobnoza.wedding.entity.MediaType;
import uz.bobnoza.wedding.service.GuestMediaService;
import uz.bobnoza.wedding.service.GuestService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

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

    public PublicInvitationController(GuestService guestService, GuestMediaService guestMediaService) {
        this.guestService = guestService;
        this.guestMediaService = guestMediaService;
    }

    @GetMapping("/{slug}")
    public PublicInvitationResponse getInvitation(@PathVariable String slug) {
        return guestService.getPublicInvitation(slug);
    }

    @GetMapping("/{slug}/media")
    public List<MediaResponse> listMedia(@PathVariable String slug) {
        UUID guestId = guestService.resolveGuestIdBySlug(slug);
        return guestMediaService.listMedia(guestId);
    }

    @PostMapping("/{slug}/media/photos")
    public ResponseEntity<MediaResponse> uploadPhoto(@PathVariable String slug, @RequestParam("file") MultipartFile file) {
        UUID guestId = guestService.resolveGuestIdBySlug(slug);
        MediaResponse response = guestMediaService.uploadMedia(guestId, MediaType.PHOTO, file);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @PostMapping("/{slug}/media/videos")
    public ResponseEntity<MediaResponse> uploadVideo(@PathVariable String slug, @RequestParam("file") MultipartFile file) {
        UUID guestId = guestService.resolveGuestIdBySlug(slug);
        MediaResponse response = guestMediaService.uploadMedia(guestId, MediaType.VIDEO, file);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
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
