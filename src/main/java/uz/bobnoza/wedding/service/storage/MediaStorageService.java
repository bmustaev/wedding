package uz.bobnoza.wedding.service.storage;

import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;

/**
 * Persists uploaded file bytes. The database only ever stores the returned
 * key (see GuestMedia.storageKey) — never the bytes themselves.
 */
public interface MediaStorageService {

    /** Stores the file under a fresh, unique key and returns that key. */
    String store(MultipartFile file, String guestId, String subfolder) throws IOException;

    void delete(String storageKey);
}
