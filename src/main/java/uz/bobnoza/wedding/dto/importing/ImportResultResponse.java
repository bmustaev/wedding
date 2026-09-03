package uz.bobnoza.wedding.dto.importing;

import java.util.List;
import java.util.UUID;

public record ImportResultResponse(
        UUID batchId,
        String filename,
        String status,
        int totalRows,
        int successRows,
        int errorRows,
        List<ImportRowResult> rows
) {}
