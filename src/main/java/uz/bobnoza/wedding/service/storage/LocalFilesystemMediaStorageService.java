package uz.bobnoza.wedding.service.storage;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;

/**
 * Reference implementation that writes to local disk — fine for a single-node
 * deployment or local development. Swap for an S3/GCS-backed implementation
 * of {@link MediaStorageService} for production, without touching any service
 * or controller code above this interface.
 */
@Service
public class LocalFilesystemMediaStorageService implements MediaStorageService {

    private final Path baseDir;

    public LocalFilesystemMediaStorageService(@Value("${app.media.storage-dir:./media-storage}") String baseDir) {
        this.baseDir = Path.of(baseDir);
    }

    @Override
    public String store(MultipartFile file, String guestId, String subfolder) throws IOException {
        String extension = extractExtension(file.getOriginalFilename());
        String key = "%s/%s/%s%s".formatted(subfolder, guestId, UUID.randomUUID(), extension);

        Path target = baseDir.resolve(key);
        Files.createDirectories(target.getParent());
        file.transferTo(target);

        return key;
    }

    @Override
    public void delete(String storageKey) {
        try {
            Files.deleteIfExists(baseDir.resolve(storageKey));
        } catch (IOException e) {
            throw new RuntimeException("Failed to delete media file: " + storageKey, e);
        }
    }

    private String extractExtension(String originalFilename) {
        if (originalFilename == null) return "";
        int dot = originalFilename.lastIndexOf('.');
        return dot >= 0 ? originalFilename.substring(dot) : "";
    }
}
