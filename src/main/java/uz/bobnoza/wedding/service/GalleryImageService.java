package uz.bobnoza.wedding.service;

import uz.bobnoza.wedding.dto.gallery.GalleryImageFile;
import uz.bobnoza.wedding.dto.gallery.GalleryImageResponse;
import uz.bobnoza.wedding.entity.GalleryImage;
import uz.bobnoza.wedding.exception.ResourceNotFoundException;
import uz.bobnoza.wedding.repository.GalleryImageRepository;
import uz.bobnoza.wedding.service.storage.MediaStorageService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;
import java.util.UUID;

/**
 * The invitation page's "about us" gallery: an ordered list of photos with a
 * caption per supported language. Managed exclusively by the super admin —
 * see SuperAdminGalleryController (access enforced by SecurityConfig, same
 * as the rest of /api/super-admin/**) — and read publicly by every guest's
 * invitation page via PublicGalleryController.
 */
@Service
public class GalleryImageService {

    private static final String SUBFOLDER = "gallery";
    private static final String OWNER_SEGMENT = "site";

    private final GalleryImageRepository galleryImageRepository;
    private final MediaStorageService storageService;

    public GalleryImageService(GalleryImageRepository galleryImageRepository, MediaStorageService storageService) {
        this.galleryImageRepository = galleryImageRepository;
        this.storageService = storageService;
    }

    @Transactional(readOnly = true)
    public List<GalleryImageResponse> list() {
        return galleryImageRepository.findAllByOrderByDisplayOrderAsc().stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional
    public GalleryImageResponse create(MultipartFile file, String captionRu, String captionUz, String captionEn) {
        String storageKey;
        try {
            storageKey = storageService.store(file, OWNER_SEGMENT, SUBFOLDER);
        } catch (IOException e) {
            throw new RuntimeException("Failed to store uploaded file", e);
        }

        GalleryImage image = GalleryImage.builder()
                .storageKey(storageKey)
                .mimeType(file.getContentType() != null ? file.getContentType() : "application/octet-stream")
                .captionRu(captionRu)
                .captionUz(captionUz)
                .captionEn(captionEn)
                .displayOrder(galleryImageRepository.findMaxDisplayOrder() + 1)
                .build();

        return toResponse(galleryImageRepository.save(image));
    }

    @Transactional
    public GalleryImageResponse updateCaptions(UUID id, String captionRu, String captionUz, String captionEn) {
        GalleryImage image = getOrThrow(id);
        image.setCaptionRu(captionRu);
        image.setCaptionUz(captionUz);
        image.setCaptionEn(captionEn);
        return toResponse(image);
    }

    /** No-ops at the ends of the list rather than erroring — the buttons are disabled there anyway. */
    @Transactional
    public void moveUp(UUID id) {
        move(id, -1);
    }

    @Transactional
    public void moveDown(UUID id) {
        move(id, 1);
    }

    private void move(UUID id, int direction) {
        List<GalleryImage> images = galleryImageRepository.findAllByOrderByDisplayOrderAsc();
        int index = -1;
        for (int i = 0; i < images.size(); i++) {
            if (images.get(i).getId().equals(id)) {
                index = i;
                break;
            }
        }
        if (index < 0) {
            throw new ResourceNotFoundException("Gallery image not found: " + id);
        }
        int targetIndex = index + direction;
        if (targetIndex < 0 || targetIndex >= images.size()) {
            return;
        }
        GalleryImage a = images.get(index);
        GalleryImage b = images.get(targetIndex);
        int aOrder = a.getDisplayOrder();
        a.setDisplayOrder(b.getDisplayOrder());
        b.setDisplayOrder(aOrder);
    }

    @Transactional
    public void delete(UUID id) {
        GalleryImage image = getOrThrow(id);
        storageService.delete(image.getStorageKey());
        galleryImageRepository.delete(image);
    }

    @Transactional(readOnly = true)
    public GalleryImageFile loadFile(UUID id) {
        GalleryImage image = getOrThrow(id);
        try {
            return new GalleryImageFile(storageService.load(image.getStorageKey()), image.getMimeType());
        } catch (IOException e) {
            throw new RuntimeException("Failed to load gallery image file: " + id, e);
        }
    }

    private GalleryImage getOrThrow(UUID id) {
        return galleryImageRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Gallery image not found: " + id));
    }

    private GalleryImageResponse toResponse(GalleryImage image) {
        return new GalleryImageResponse(
                image.getId(),
                "/api/public/gallery-images/" + image.getId() + "/file",
                image.getCaptionRu(),
                image.getCaptionUz(),
                image.getCaptionEn(),
                image.getDisplayOrder());
    }
}
