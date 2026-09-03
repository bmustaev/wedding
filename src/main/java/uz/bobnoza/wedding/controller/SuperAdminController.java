package uz.bobnoza.wedding.controller;

import uz.bobnoza.wedding.dto.admin.AdminSummaryResponse;
import uz.bobnoza.wedding.dto.admin.CreateAdminRequest;
import uz.bobnoza.wedding.dto.common.PageResponse;
import uz.bobnoza.wedding.dto.guest.GuestResponse;
import uz.bobnoza.wedding.security.AdminPrincipal;
import uz.bobnoza.wedding.service.AdminService;
import uz.bobnoza.wedding.service.GuestService;
import jakarta.validation.Valid;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/**
 * Super-admin-only. Access is enforced at the security-filter-chain level
 * (SecurityConfig: "/api/super-admin/**" requires ROLE_SUPER_ADMIN) — a
 * regular admin's JWT will be rejected with 403 before any code here runs.
 */
@RestController
@RequestMapping("/api/super-admin")
public class SuperAdminController {

    private final AdminService adminService;
    private final GuestService guestService;

    public SuperAdminController(AdminService adminService, GuestService guestService) {
        this.adminService = adminService;
        this.guestService = guestService;
    }

    @GetMapping("/admins")
    public List<AdminSummaryResponse> listAdmins() {
        return adminService.listAdmins();
    }

    @PostMapping("/admins")
    public ResponseEntity<AdminSummaryResponse> createAdmin(
            @AuthenticationPrincipal AdminPrincipal caller,
            @Valid @RequestBody CreateAdminRequest request) {
        AdminSummaryResponse created = adminService.createAdmin(request, caller.getAdminId());
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    @PatchMapping("/admins/{adminId}/active")
    public AdminSummaryResponse setActive(@PathVariable UUID adminId, @RequestParam boolean active) {
        return adminService.setActive(adminId, active);
    }

    /** "When clicked into, shows that admin's guest list" — the super-admin drill-down. */
    @GetMapping("/admins/{adminId}/guests")
    public PageResponse<GuestResponse> getAdminGuests(
            @PathVariable UUID adminId,
            @PageableDefault(size = 50) Pageable pageable) {
        return guestService.listGuestsForAdmin(adminId, pageable);
    }
}
