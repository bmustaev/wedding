package uz.bobnoza.wedding.dto.admin;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * role: "ADMIN" (default when absent), "DJ" or "BANKER".
 * side: "BRIDE" or "GROOM" — required for an admin, ignored for a DJ or banker.
 * hall: optional "TASHKENT" / "SAMARKAND" for an admin — makes a hall admin,
 * limited to that hall but managing every guest their side has there.
 * Omitted: every hall open to their side, own guests only. Required for a
 * DJ (the one hall whose playlist they work) and a banker (whose bank table).
 */
public record CreateAdminRequest(
        @NotBlank @Size(min = 3, max = 64) String username,
        @NotBlank @Size(min = 8, max = 128) String password,
        String side,
        String hall,
        @Pattern(regexp = "ADMIN|DJ|BANKER", message = "must be ADMIN, DJ or BANKER") String role
) {}
