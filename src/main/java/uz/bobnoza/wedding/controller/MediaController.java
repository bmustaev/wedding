package uz.bobnoza.wedding.controller;

import uz.bobnoza.wedding.dto.media.MediaAllowanceResponse;
import uz.bobnoza.wedding.dto.media.MediaResponse;
import uz.bobnoza.wedding.dto.media.StartUploadRequest;
import uz.bobnoza.wedding.dto.media.UploadSessionResponse;
import uz.bobnoza.wedding.security.AdminPrincipal;
import uz.bobnoza.wedding.service.GuestMediaService;
import uz.bobnoza.wedding.service.GuestService;
import uz.bobnoza.wedding.service.MediaUploadService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
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
 * Admin-side media management for one guest they manage (e.g. removing
 * inappropriate content, uploading on a guest's behalf). Guest-side
 * self-upload is PublicInvitationController, reached via the landing_slug
 * link instead of a JWT; the hall-wide view is MediaLibraryController.
 */
@RestController
@RequestMapping("/api/guests/{guestId}/media")
public class MediaController {

    private final GuestService guestService;
    private final GuestMediaService guestMediaService;
    private final MediaUploadService mediaUploadService;

    public MediaController(GuestService guestService, GuestMediaService guestMediaService,
                           MediaUploadService mediaUploadService) {
        this.guestService = guestService;
        this.guestMediaService = guestMediaService;
        this.mediaUploadService = mediaUploadService;
    }

    @GetMapping
    public List<MediaResponse> list(@AuthenticationPrincipal AdminPrincipal caller, @PathVariable UUID guestId) {
        guestService.requireOwnedGuest(caller, guestId);
        return guestMediaService.listMediaForAdmin(guestId);
    }

    @GetMapping("/allowance")
    public MediaAllowanceResponse allowance(@AuthenticationPrincipal AdminPrincipal caller, @PathVariable UUID guestId) {
        guestService.requireOwnedGuest(caller, guestId);
        return guestMediaService.getAllowance(guestId);
    }

    // --- chunked upload (see MediaUploadService) -------------------------

    @PostMapping("/uploads")
    public ResponseEntity<UploadSessionResponse> startUpload(@AuthenticationPrincipal AdminPrincipal caller,
                                                             @PathVariable UUID guestId,
                                                             @Valid @RequestBody StartUploadRequest request) {
        guestService.requireOwnedGuest(caller, guestId);
        return ResponseEntity.status(HttpStatus.CREATED).body(mediaUploadService.start(guestId, request));
    }

    @GetMapping("/uploads/{uploadId}")
    public UploadSessionResponse uploadStatus(@AuthenticationPrincipal AdminPrincipal caller,
                                              @PathVariable UUID guestId, @PathVariable UUID uploadId) {
        guestService.requireOwnedGuest(caller, guestId);
        return mediaUploadService.status(guestId, uploadId);
    }

    @PutMapping("/uploads/{uploadId}")
    public UploadSessionResponse uploadChunk(@AuthenticationPrincipal AdminPrincipal caller,
                                             @PathVariable UUID guestId, @PathVariable UUID uploadId,
                                             @RequestParam long offset, HttpServletRequest request)
            throws IOException {
        guestService.requireOwnedGuest(caller, guestId);
        return mediaUploadService.appendChunk(guestId, uploadId, offset, request.getInputStream());
    }

    @PostMapping("/uploads/{uploadId}/complete")
    public ResponseEntity<MediaResponse> completeUpload(@AuthenticationPrincipal AdminPrincipal caller,
                                                        @PathVariable UUID guestId, @PathVariable UUID uploadId) {
        guestService.requireOwnedGuest(caller, guestId);
        return ResponseEntity.status(HttpStatus.CREATED).body(mediaUploadService.complete(guestId, uploadId));
    }

    @DeleteMapping("/uploads/{uploadId}")
    public ResponseEntity<Void> cancelUpload(@AuthenticationPrincipal AdminPrincipal caller,
                                             @PathVariable UUID guestId, @PathVariable UUID uploadId) {
        guestService.requireOwnedGuest(caller, guestId);
        mediaUploadService.cancel(guestId, uploadId);
        return ResponseEntity.noContent().build();
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
