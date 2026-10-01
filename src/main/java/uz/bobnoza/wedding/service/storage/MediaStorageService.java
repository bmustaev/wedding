package uz.bobnoza.wedding.service.storage;

import org.springframework.core.io.Resource;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Path;

/**
 * Persists uploaded file bytes. The database only ever stores the returned
 * key (see GuestMedia.storageKey) — never the bytes themselves.
 */
public interface MediaStorageService {

    /** Stores the file under a fresh, unique key and returns that key. */
    String store(MultipartFile file, String guestId, String subfolder) throws IOException;

    /**
     * Moves an already-written local file (e.g. a finished chunked upload in
     * {@link #scratchDir()}) into storage under a fresh, unique key ending in
     * {@code extension} (".heic", ".mov", …) and returns that key.
     */
    String importFile(Path source, String guestId, String subfolder, String extension) throws IOException;

    /** Moves a local file into storage at exactly {@code storageKey} (derived copies sit next to their original). */
    void putFile(Path source, String storageKey) throws IOException;

    /** Loads the bytes back out for a key previously returned by {@link #store}. */
    Resource load(String storageKey) throws IOException;

    /**
     * A local path the external converters (ffmpeg, libvips) can read the
     * stored file from. An object-storage implementation would download to a
     * local cache here.
     */
    Path localPath(String storageKey);

    /**
     * Working space for in-progress uploads and conversions — on the same
     * volume as stored files, so {@link #importFile}/{@link #putFile} are a
     * rename rather than a second full copy of a 400 MB video.
     */
    Path scratchDir();

    /** Bytes still free on the storage volume — uploads are refused below app.media.min-free-disk. */
    long usableSpace();

    long totalSpace();

    /** No-op for a null key, so callers can pass optional derived keys straight through. */
    void delete(String storageKey);
}
