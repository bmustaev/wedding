package uz.bobnoza.wedding.dto.admin;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * side: "BRIDE" or "GROOM" — every regular admin created this way needs one.
 * hall: optional "TASHKENT" / "SAMARKAND" — makes a hall admin, limited to that
 * hall but managing every guest their side has there. Omitted: every hall open
 * to their side, own guests only.
 */
public record CreateAdminRequest(
        @NotBlank @Size(min = 3, max = 64) String username,
        @NotBlank @Size(min = 8, max = 128) String password,
        @NotNull String side,
        String hall
) {}
