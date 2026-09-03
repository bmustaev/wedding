package uz.bobnoza.wedding.service;

import uz.bobnoza.wedding.dto.importing.ImportResultResponse;
import uz.bobnoza.wedding.dto.importing.ImportRowResult;
import uz.bobnoza.wedding.entity.Admin;
import uz.bobnoza.wedding.entity.Guest;
import uz.bobnoza.wedding.entity.ImportBatch;
import uz.bobnoza.wedding.entity.ImportBatchRow;
import uz.bobnoza.wedding.entity.ImportStatus;
import uz.bobnoza.wedding.exception.ResourceNotFoundException;
import uz.bobnoza.wedding.repository.AdminRepository;
import uz.bobnoza.wedding.repository.GuestRepository;
import uz.bobnoza.wedding.repository.ImportBatchRepository;
import uz.bobnoza.wedding.repository.ImportBatchRowRepository;
import uz.bobnoza.wedding.security.AdminPrincipal;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

/**
 * Parses an uploaded .txt guest list. Expected line format, one guest per line:
 *
 *   DisplayName
 *   DisplayName;PartySize
 *   DisplayName;PartySize;Member One,Member Two,Member Three
 *
 * The first form is a single guest (party size 1). The others create a group
 * when PartySize > 1 — e.g. "The Miller Family;4;Tom,Ann,Lucy,Ben". Blank
 * lines are skipped and not counted as rows. A line that fails to parse
 * doesn't fail the whole batch — it's recorded with its error message and
 * the rest of the file keeps processing.
 */
@Service
public class ImportService {

    private final ImportBatchRepository importBatchRepository;
    private final ImportBatchRowRepository importBatchRowRepository;
    private final GuestRepository guestRepository;
    private final AdminRepository adminRepository;

    public ImportService(ImportBatchRepository importBatchRepository,
                          ImportBatchRowRepository importBatchRowRepository,
                          GuestRepository guestRepository,
                          AdminRepository adminRepository) {
        this.importBatchRepository = importBatchRepository;
        this.importBatchRowRepository = importBatchRowRepository;
        this.guestRepository = guestRepository;
        this.adminRepository = adminRepository;
    }

    @Transactional
    public ImportResultResponse importFile(AdminPrincipal caller, MultipartFile file) {
        Admin adminRef = adminRepository.getReferenceById(caller.getAdminId());

        ImportBatch batch = ImportBatch.builder()
                .admin(adminRef)
                .filename(file.getOriginalFilename() != null ? file.getOriginalFilename() : "upload.txt")
                .status(ImportStatus.PENDING)
                .build();
        batch = importBatchRepository.save(batch);

        List<String> lines;
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(file.getInputStream(), StandardCharsets.UTF_8))) {
            lines = reader.lines().toList();
        } catch (IOException e) {
            throw new RuntimeException("Failed to read uploaded file", e);
        }

        List<ImportRowResult> rowResults = new ArrayList<>();
        int rowNumber = 0;
        int successCount = 0;
        int errorCount = 0;

        for (String rawLine : lines) {
            rowNumber++;
            String trimmed = rawLine.strip();
            if (trimmed.isEmpty()) {
                rowNumber--; // blank lines don't count as a row
                continue;
            }

            try {
                ParsedLine parsed = parseLine(trimmed);

                Guest guest = Guest.builder()
                        .admin(adminRef)
                        .displayName(parsed.displayName())
                        .group(parsed.isGroup())
                        .partySize(parsed.partySize())
                        .groupMembers(parsed.members())
                        .pageGeneratedAt(Instant.now())
                        .build();
                guest = guestRepository.save(guest);

                importBatchRowRepository.save(ImportBatchRow.builder()
                        .batch(batch)
                        .rowNumber(rowNumber)
                        .rawLine(rawLine)
                        .guest(guest)
                        .build());

                rowResults.add(new ImportRowResult(rowNumber, rawLine, guest.getId(), null));
                successCount++;

            } catch (Exception e) {
                importBatchRowRepository.save(ImportBatchRow.builder()
                        .batch(batch)
                        .rowNumber(rowNumber)
                        .rawLine(rawLine)
                        .errorMessage(e.getMessage())
                        .build());

                rowResults.add(new ImportRowResult(rowNumber, rawLine, null, e.getMessage()));
                errorCount++;
            }
        }

        batch.setTotalRows(successCount + errorCount);
        batch.setSuccessRows(successCount);
        batch.setErrorRows(errorCount);
        batch.setStatus(ImportStatus.COMPLETED);
        batch.setImportedAt(Instant.now());

        return new ImportResultResponse(
                batch.getId(), batch.getFilename(), batch.getStatus().name(),
                batch.getTotalRows(), batch.getSuccessRows(), batch.getErrorRows(), rowResults);
    }

    @Transactional(readOnly = true)
    public List<ImportResultResponse> listBatches(AdminPrincipal caller) {
        return importBatchRepository.findAllByAdminIdOrderByCreatedAtDesc(caller.getAdminId()).stream()
                .map(b -> new ImportResultResponse(
                        b.getId(), b.getFilename(), b.getStatus().name(),
                        b.getTotalRows(), b.getSuccessRows(), b.getErrorRows(), List.of()))
                .toList();
    }

    @Transactional(readOnly = true)
    public ImportResultResponse getBatch(AdminPrincipal caller, UUID batchId) {
        ImportBatch batch = importBatchRepository.findByIdAndAdminId(batchId, caller.getAdminId())
                .orElseThrow(() -> new ResourceNotFoundException("Import batch not found: " + batchId));

        List<ImportRowResult> rows = importBatchRowRepository.findAllByBatchIdOrderByRowNumberAsc(batchId).stream()
                .map(r -> new ImportRowResult(
                        r.getRowNumber(), r.getRawLine(),
                        r.getGuest() != null ? r.getGuest().getId() : null,
                        r.getErrorMessage()))
                .toList();

        return new ImportResultResponse(
                batch.getId(), batch.getFilename(), batch.getStatus().name(),
                batch.getTotalRows(), batch.getSuccessRows(), batch.getErrorRows(), rows);
    }

    private record ParsedLine(String displayName, boolean isGroup, int partySize, List<String> members) {}

    private ParsedLine parseLine(String line) {
        String[] parts = line.split(";", -1);

        if (parts.length == 1) {
            String name = parts[0].strip();
            if (name.isEmpty()) {
                throw new IllegalArgumentException("Guest name is required");
            }
            return new ParsedLine(name, false, 1, null);
        }

        if (parts.length != 2 && parts.length != 3) {
            throw new IllegalArgumentException(
                    "Expected 'Name' or 'Name;PartySize' or 'Name;PartySize;Member1,Member2,...' "
                            + "but got " + parts.length + " field(s)");
        }

        String name = parts[0].strip();
        if (name.isEmpty()) {
            throw new IllegalArgumentException("Guest name is required");
        }

        int partySize;
        try {
            partySize = Integer.parseInt(parts[1].strip());
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Party size must be a whole number, got '" + parts[1].strip() + "'");
        }
        if (partySize < 1) {
            throw new IllegalArgumentException("Party size must be at least 1");
        }

        List<String> members = null;
        if (parts.length == 3 && !parts[2].isBlank()) {
            members = Arrays.stream(parts[2].split(","))
                    .map(String::strip)
                    .filter(s -> !s.isEmpty())
                    .toList();
        }

        return new ParsedLine(name, partySize > 1, partySize, members);
    }
}
