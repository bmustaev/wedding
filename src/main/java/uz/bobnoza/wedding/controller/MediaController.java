package uz.bobnoza.wedding.controller;

import uz.bobnoza.wedding.dto.media.MediaAllowanceResponse;
import uz.bobnoza.wedding.dto.media.MediaResponse;
import uz.bobnoza.wedding.entity.MediaType;
import uz.bobnoza.wedding.security.AdminPrincipal;
import uz.bobnoza.wedding.service.GuestMediaService;
import uz.bobnoza.wedding.service.GuestService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
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
 * Admin-side media management for a guest they own (e.g. removing
 * inappropriate content). Guest-side self-upload is PublicInvitationController,
 * reached via the landing_slug link instead of a JWT.
 */
@RestController
@RequestMapping("/api/guests/{guestId}/media")
public class MediaController {

    private final GuestService guestService;
    private final GuestMediaService guestMediaService;

    public MediaController(GuestService guestService, GuestMediaService guestMediaService) {
        this.guestService = guestService;
        this.guestMediaService = guestMediaService;
    }

    @GetMapping
    public List<MediaResponse> list(@AuthenticationPrincipal AdminPrincipal caller, @PathVariable UUID guestId) {
        guestService.requireOwnedGuest(caller, guestId);
        return guestMediaService.listMedia(guestId);
    }

    @GetMapping("/allowance")
    public MediaAllowanceResponse allowance(@AuthenticationPrincipal AdminPrincipal caller, @PathVariable UUID guestId) {
        guestService.requireOwnedGuest(caller, guestId);
        return guestMediaService.getAllowance(guestId);
    }

    @PostMapping("/photos")
    public ResponseEntity<MediaResponse> uploadPhoto(
            @AuthenticationPrincipal AdminPrincipal caller,
            @PathVariable UUID guestId,
            @RequestParam("file") MultipartFile file) {
        guestService.requireOwnedGuest(caller, guestId);
        MediaResponse response = guestMediaService.uploadMedia(guestId, MediaType.PHOTO, file);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @PostMapping("/videos")
    public ResponseEntity<MediaResponse> uploadVideo(
            @AuthenticationPrincipal AdminPrincipal caller,
            @PathVariable UUID guestId,
            @RequestParam("file") MultipartFile file) {
        guestService.requireOwnedGuest(caller, guestId);
        MediaResponse response = guestMediaService.uploadMedia(guestId, MediaType.VIDEO, file);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @DeleteMapping("/{mediaId}")
    public ResponseEntity<Void> delete(
            @AuthenticationPrincipal AdminPrincipal caller,
            @PathVariable UUID guestId,
            @PathVariable UUID mediaId) {
        guestService.requireOwnedGuest(caller, guestId);
        guestMediaService.deleteMedia(guestId, mediaId);
        return ResponseEntity.noContent().build();
    }
}
