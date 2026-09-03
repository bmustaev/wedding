package uz.bobnoza.wedding.controller;

import uz.bobnoza.wedding.dto.importing.ImportResultResponse;
import uz.bobnoza.wedding.security.AdminPrincipal;
import uz.bobnoza.wedding.service.ImportService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.UUID;

/** Bulk guest-list upload from a .txt file — see ImportService for the expected line format. */
@RestController
@RequestMapping("/api/imports")
public class ImportController {

    private final ImportService importService;

    public ImportController(ImportService importService) {
        this.importService = importService;
    }

    @PostMapping
    public ResponseEntity<ImportResultResponse> importGuests(
            @AuthenticationPrincipal AdminPrincipal caller,
            @RequestParam("file") MultipartFile file) {
        ImportResultResponse result = importService.importFile(caller, file);
        return ResponseEntity.status(HttpStatus.CREATED).body(result);
    }

    @GetMapping
    public List<ImportResultResponse> listBatches(@AuthenticationPrincipal AdminPrincipal caller) {
        return importService.listBatches(caller);
    }

    @GetMapping("/{batchId}")
    public ImportResultResponse getBatch(@AuthenticationPrincipal AdminPrincipal caller, @PathVariable UUID batchId) {
        return importService.getBatch(caller, batchId);
    }
}
