package uz.bobnoza.wedding.dto.auth;

import java.util.UUID;

public record LoginResponse(
        String token,
        UUID adminId,
        String username,
        String role,
        String side
) {}
