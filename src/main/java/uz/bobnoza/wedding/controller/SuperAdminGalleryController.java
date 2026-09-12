package uz.bobnoza.wedding.controller;

import uz.bobnoza.wedding.dto.gallery.GalleryImageResponse;
import uz.bobnoza.wedding.dto.gallery.UpdateGalleryImageCaptionsRequest;
import uz.bobnoza.wedding.service.GalleryImageService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.UUID;

/**
 * Super-admin-only. Access is enforced at the security-filter-chain level
 * (SecurityConfig: "/api/super-admin/**" requires ROLE_SUPER_ADMIN) — see
 * SuperAdminController for the same note. Manages the invitation page's
 * "about us" gallery; guests read it back via PublicGalleryController.
 */
@RestController
@RequestMapping("/api/super-admin/gallery-images")
public class SuperAdminGalleryController {

    private final GalleryImageService galleryImageService;

    public SuperAdminGalleryController(GalleryImageService galleryImageService) {
        this.galleryImageService = galleryImageService;
    }

    @GetMapping
    public List<GalleryImageResponse> list() {
        return galleryImageService.list();
    }

    @PostMapping
    public ResponseEntity<GalleryImageResponse> create(
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "captionRu", defaultValue = "") String captionRu,
            @RequestParam(value = "captionUz", defaultValue = "") String captionUz,
            @RequestParam(value = "captionEn", defaultValue = "") String captionEn) {
        GalleryImageResponse created = galleryImageService.create(file, captionRu, captionUz, captionEn);
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    @PatchMapping("/{id}")
    public GalleryImageResponse updateCaptions(@PathVariable UUID id,
                                                @Valid @RequestBody UpdateGalleryImageCaptionsRequest request) {
        return galleryImageService.updateCaptions(id, request.captionRu(), request.captionUz(), request.captionEn());
    }

    @PatchMapping("/{id}/move-up")
    public ResponseEntity<Void> moveUp(@PathVariable UUID id) {
        galleryImageService.moveUp(id);
        return ResponseEntity.noContent().build();
    }

    @PatchMapping("/{id}/move-down")
    public ResponseEntity<Void> moveDown(@PathVariable UUID id) {
        galleryImageService.moveDown(id);
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable UUID id) {
        galleryImageService.delete(id);
        return ResponseEntity.noContent().build();
    }
}
