package uz.bobnoza.wedding.dto.admin;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** side: "BRIDE" or "GROOM" — every regular admin created this way needs one. */
public record CreateAdminRequest(
        @NotBlank @Size(min = 3, max = 64) String username,
        @NotBlank @Size(min = 8, max = 128) String password,
        @NotNull String side
) {}
