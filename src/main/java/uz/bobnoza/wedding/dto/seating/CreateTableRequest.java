package uz.bobnoza.wedding.dto.seating;

import jakarta.validation.constraints.Min;

/**
 * side is required only for a super_admin caller (who has no side of their
 * own) — "BRIDE" or "GROOM". A regular admin's own side is used regardless
 * of what's sent here, so they can't create a table on the other side by
 * passing a mismatched value. hall is optional ("TASHKENT" by default, or
 * "SAMARKAND" — groom side only).
 */
public record CreateTableRequest(
        @Min(1) Integer capacity,
        String side,
        String hall
) {}
