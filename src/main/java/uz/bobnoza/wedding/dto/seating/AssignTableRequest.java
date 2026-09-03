package uz.bobnoza.wedding.dto.seating;

import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record AssignTableRequest(
        @NotNull UUID tableId
) {}
