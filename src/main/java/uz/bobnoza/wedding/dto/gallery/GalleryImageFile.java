package uz.bobnoza.wedding.dto.gallery;

import org.springframework.core.io.Resource;

public record GalleryImageFile(Resource resource, String mimeType) {}
