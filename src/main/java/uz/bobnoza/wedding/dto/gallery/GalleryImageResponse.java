package uz.bobnoza.wedding.dto.gallery;

import java.util.UUID;

public record GalleryImageResponse(
        UUID id,
        String imageUrl,
        String captionRu,
        String captionUz,
        String captionEn,
        int displayOrder
) {}
