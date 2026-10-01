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
import org.springframework.web.bind.annotation.RequestParam;
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

    /** Seat counts only, no names — every table in every hall the caller can access. */
    @GetMapping("/occupancy")
    public List<TableOccupancyResponse> occupancy(@AuthenticationPrincipal AdminPrincipal caller) {
        return seatingService.listOccupancy(caller);
    }

    /** Own guests shown by name; every other admin's guest anonymized to a seat count. */
    @GetMapping("/chart")
    public List<SeatingChartEntryResponse> chart(@AuthenticationPrincipal AdminPrincipal caller) {
        return seatingService.getSeatingChart(caller);
    }

    /**
     * Everything the hall-map page needs for one hall in one call: head/bride/groom tables plus the
     * caller's unassigned guests invited to that hall. hall defaults to TASHKENT; SAMARKAND is 404 for the bride side.
     */
    @GetMapping("/hall")
    public HallViewResponse hall(@AuthenticationPrincipal AdminPrincipal caller,
                                 @RequestParam(required = false) String hall) {
        return seatingService.getHallView(caller, hall);
    }

    /** Adds a table on the caller's own side of a hall. Number is auto-assigned (next available for that hall and side). */
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
