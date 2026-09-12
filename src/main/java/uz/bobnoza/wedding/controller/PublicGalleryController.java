package uz.bobnoza.wedding.controller;

import uz.bobnoza.wedding.dto.gallery.GalleryImageFile;
import uz.bobnoza.wedding.dto.gallery.GalleryImageResponse;
import uz.bobnoza.wedding.service.GalleryImageService;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.core.io.Resource;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/**
 * Guest-facing: the invitation page's "about us" gallery. No login — same
 * "/api/public/**" permitAll rule as PublicInvitationController, but this
 * isn't scoped to a guest's slug since the gallery is site-wide, not
 * per-guest.
 */
@RestController
@RequestMapping("/api/public/gallery-images")
public class PublicGalleryController {

    private final GalleryImageService galleryImageService;

    public PublicGalleryController(GalleryImageService galleryImageService) {
        this.galleryImageService = galleryImageService;
    }

    @GetMapping
    public List<GalleryImageResponse> list() {
        return galleryImageService.list();
    }

    @GetMapping("/{id}/file")
    public ResponseEntity<Resource> file(@PathVariable UUID id) {
        GalleryImageFile file = galleryImageService.loadFile(id);
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(file.mimeType()))
                .body(file.resource());
    }
}
