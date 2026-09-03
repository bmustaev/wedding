package uz.bobnoza.wedding.controller;

import uz.bobnoza.wedding.dto.common.PageResponse;
import uz.bobnoza.wedding.dto.guest.GuestCreateRequest;
import uz.bobnoza.wedding.dto.guest.GuestResponse;
import uz.bobnoza.wedding.dto.guest.GuestUpdateRequest;
import uz.bobnoza.wedding.dto.seating.AssignTableRequest;
import uz.bobnoza.wedding.security.AdminPrincipal;
import uz.bobnoza.wedding.service.GuestService;
import uz.bobnoza.wedding.service.SeatingService;
import jakarta.validation.Valid;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * An admin's own guest list. Every method here is scoped to the calling
 * admin via GuestService — an admin can never see or touch another admin's
 * guests through this controller, regardless of the ID in the URL.
 */
@RestController
@RequestMapping("/api/guests")
public class GuestController {

    private final GuestService guestService;
    private final SeatingService seatingService;

    public GuestController(GuestService guestService, SeatingService seatingService) {
        this.guestService = guestService;
        this.seatingService = seatingService;
    }

    @GetMapping
    public PageResponse<GuestResponse> listMyGuests(
            @AuthenticationPrincipal AdminPrincipal caller,
            @PageableDefault(size = 50) Pageable pageable) {
        return guestService.listMyGuests(caller, pageable);
    }

    @PostMapping
    public ResponseEntity<GuestResponse> createGuest(
            @AuthenticationPrincipal AdminPrincipal caller,
            @Valid @RequestBody GuestCreateRequest request) {
        GuestResponse created = guestService.createGuest(caller, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    @GetMapping("/{guestId}")
    public GuestResponse getGuest(@AuthenticationPrincipal AdminPrincipal caller, @PathVariable UUID guestId) {
        return guestService.getMyGuest(caller, guestId);
    }

    @PatchMapping("/{guestId}")
    public GuestResponse updateGuest(
            @AuthenticationPrincipal AdminPrincipal caller,
            @PathVariable UUID guestId,
            @Valid @RequestBody GuestUpdateRequest request) {
        return guestService.updateGuest(caller, guestId, request);
    }

    @DeleteMapping("/{guestId}")
    public ResponseEntity<Void> deleteGuest(@AuthenticationPrincipal AdminPrincipal caller, @PathVariable UUID guestId) {
        guestService.deleteGuest(caller, guestId);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{guestId}/regenerate-page")
    public GuestResponse regeneratePage(@AuthenticationPrincipal AdminPrincipal caller, @PathVariable UUID guestId) {
        return guestService.regeneratePage(caller, guestId);
    }

    @PutMapping("/{guestId}/table")
    public GuestResponse assignTable(
            @AuthenticationPrincipal AdminPrincipal caller,
            @PathVariable UUID guestId,
            @Valid @RequestBody AssignTableRequest request) {
        seatingService.assignGuestToTable(caller, guestId, request.tableId());
        return guestService.getMyGuest(caller, guestId);
    }

    @DeleteMapping("/{guestId}/table")
    public GuestResponse unassignTable(@AuthenticationPrincipal AdminPrincipal caller, @PathVariable UUID guestId) {
        seatingService.unassignGuestFromTable(caller, guestId);
        return guestService.getMyGuest(caller, guestId);
    }
}
