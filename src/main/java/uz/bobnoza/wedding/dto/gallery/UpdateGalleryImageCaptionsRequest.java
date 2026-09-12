package uz.bobnoza.wedding.dto.gallery;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record UpdateGalleryImageCaptionsRequest(
        @NotNull @Size(max = 255) String captionRu,
        @NotNull @Size(max = 255) String captionUz,
        @NotNull @Size(max = 255) String captionEn
) {}
