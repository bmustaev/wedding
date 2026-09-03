package uz.bobnoza.wedding.controller;

import uz.bobnoza.wedding.dto.seating.CreateTableRequest;
import uz.bobnoza.wedding.dto.seating.HallViewResponse;
import uz.bobnoza.wedding.dto.seating.SeatingChartEntryResponse;
import uz.bobnoza.wedding.dto.seating.TableOccupancyResponse;
import uz.bobnoza.wedding.dto.seating.TableResponse;
import uz.bobnoza.wedding.security.AdminPrincipal;
import uz.bobnoza.wedding.service.SeatingService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/** Table-centric seating views and table CRUD. Per-guest assignment lives on GuestController (PUT/DELETE .../table). */
@RestController
@RequestMapping("/api/seating")
public class SeatingController {

    private final SeatingService seatingService;

    public SeatingController(SeatingService seatingService) {
        this.seatingService = seatingService;
    }

    /** Seat counts only, no names — safe for any authenticated admin. */
    @GetMapping("/occupancy")
    public List<TableOccupancyResponse> occupancy() {
        return seatingService.listOccupancy();
    }

    /** Own guests shown by name; every other admin's guest anonymized to a seat count. */
    @GetMapping("/chart")
    public List<SeatingChartEntryResponse> chart(@AuthenticationPrincipal AdminPrincipal caller) {
        return seatingService.getSeatingChart(caller);
    }

    /** Everything the hall-map page needs in one call: head/bride/groom tables plus the caller's unassigned guests. */
    @GetMapping("/hall")
    public HallViewResponse hall(@AuthenticationPrincipal AdminPrincipal caller) {
        return seatingService.getHallView(caller);
    }

    /** Adds a table on the caller's own side. Number is auto-assigned (next available for that side). */
    @PostMapping("/tables")
    public ResponseEntity<TableResponse> createTable(
            @AuthenticationPrincipal AdminPrincipal caller,
            @Valid @RequestBody CreateTableRequest request) {
        TableResponse created = seatingService.createTable(caller, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    /** Removes a table on the caller's own side. Must be empty first (409 otherwise). Head table can't be removed. */
    @DeleteMapping("/tables/{tableId}")
    public ResponseEntity<Void> deleteTable(@AuthenticationPrincipal AdminPrincipal caller, @PathVariable UUID tableId) {
        seatingService.deleteTable(caller, tableId);
        return ResponseEntity.noContent().build();
    }
}
