package uz.bobnoza.wedding.service.storage;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.UUID;

/**
 * Reference implementation that writes to local disk — fine for a single-node
 * deployment or local development. Swap for an S3/GCS-backed implementation
 * of {@link MediaStorageService} for production, without touching any service
 * or controller code above this interface.
 */
@Service
public class LocalFilesystemMediaStorageService implements MediaStorageService {

    private static final String SCRATCH_DIR = ".scratch";

    private final Path baseDir;

    public LocalFilesystemMediaStorageService(@Value("${app.media.storage-dir:./media-storage}") String baseDir) {
        this.baseDir = Path.of(baseDir).toAbsolutePath().normalize();
        try {
            Files.createDirectories(this.baseDir.resolve(SCRATCH_DIR));
        } catch (IOException e) {
            throw new UncheckedIOException("Cannot create media storage directory " + this.baseDir, e);
        }
    }

    @Override
    public String store(MultipartFile file, String guestId, String subfolder) throws IOException {
        String key = newKey(guestId, subfolder, extractExtension(file.getOriginalFilename()));

        Path target = baseDir.resolve(key);
        Files.createDirectories(target.getParent());
        file.transferTo(target);

        return key;
    }

    @Override
    public String importFile(Path source, String guestId, String subfolder, String extension) throws IOException {
        String key = newKey(guestId, subfolder, extension);
        putFile(source, key);
        return key;
    }

    @Override
    public void putFile(Path source, String storageKey) throws IOException {
        Path target = resolve(storageKey);
        Files.createDirectories(target.getParent());
        Files.move(source, target, StandardCopyOption.REPLACE_EXISTING);
    }

    @Override
    public Resource load(String storageKey) throws IOException {
        return new UrlResource(resolve(storageKey).toUri());
    }

    @Override
    public Path localPath(String storageKey) {
        return resolve(storageKey);
    }

    @Override
    public Path scratchDir() {
        return baseDir.resolve(SCRATCH_DIR);
    }

    @Override
    public long usableSpace() {
        try {
            return Files.getFileStore(baseDir).getUsableSpace();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    @Override
    public long totalSpace() {
        try {
            return Files.getFileStore(baseDir).getTotalSpace();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    @Override
    public void delete(String storageKey) {
        if (storageKey == null) return;
        try {
            Files.deleteIfExists(resolve(storageKey));
        } catch (IOException e) {
            throw new RuntimeException("Failed to delete media file: " + storageKey, e);
        }
    }

    private String newKey(String guestId, String subfolder, String extension) {
        return "%s/%s/%s%s".formatted(subfolder, guestId, UUID.randomUUID(), extension);
    }

    /** Keys are always server-generated, but never let one escape the storage directory regardless. */
    private Path resolve(String storageKey) {
        Path path = baseDir.resolve(storageKey).normalize();
        if (!path.startsWith(baseDir)) {
            throw new IllegalArgumentException("Storage key escapes the storage directory: " + storageKey);
        }
        return path;
    }

    private String extractExtension(String originalFilename) {
        if (originalFilename == null) return "";
        int dot = originalFilename.lastIndexOf('.');
        return dot >= 0 ? originalFilename.substring(dot) : "";
    }
}
