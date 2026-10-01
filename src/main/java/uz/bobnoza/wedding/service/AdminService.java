package uz.bobnoza.wedding.service;

import uz.bobnoza.wedding.dto.admin.AdminSummaryResponse;
import uz.bobnoza.wedding.dto.admin.CreateAdminRequest;
import uz.bobnoza.wedding.entity.Admin;
import uz.bobnoza.wedding.entity.AdminRole;
import uz.bobnoza.wedding.entity.AdminSide;
import uz.bobnoza.wedding.entity.Hall;
import uz.bobnoza.wedding.exception.ResourceNotFoundException;
import uz.bobnoza.wedding.repository.AdminRepository;
import uz.bobnoza.wedding.repository.GuestRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;

import java.util.List;
import java.util.UUID;

/** Super-admin-only operations: managing admin accounts. */
@Service
public class AdminService {

    private final AdminRepository adminRepository;
    private final GuestRepository guestRepository;
    private final PasswordEncoder passwordEncoder;

    public AdminService(AdminRepository adminRepository, GuestRepository guestRepository,
                         PasswordEncoder passwordEncoder) {
        this.adminRepository = adminRepository;
        this.guestRepository = guestRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Transactional(readOnly = true)
    public List<AdminSummaryResponse> listAdmins() {
        return adminRepository.findAll().stream()
                .map(this::toSummary)
                .toList();
    }

    @Transactional
    public AdminSummaryResponse createAdmin(CreateAdminRequest request, UUID creatorId) {
        if (adminRepository.existsByUsername(request.username())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Username is already taken");
        }

        AdminSide side;
        try {
            side = AdminSide.valueOf(request.side().toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "side must be BRIDE or GROOM");
        }

        Hall hall = null;
        if (request.hall() != null && !request.hall().isBlank()) {
            try {
                hall = Hall.valueOf(request.hall().strip().toUpperCase());
            } catch (IllegalArgumentException e) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "hall must be TASHKENT or SAMARKAND");
            }
            if (!hall.isOpenTo(side)) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "The " + hall.name() + " hall has no " + side.name() + " side");
            }
        }

        Admin creator = adminRepository.findById(creatorId)
                .orElseThrow(() -> new ResourceNotFoundException("Creating admin not found"));

        Admin admin = Admin.builder()
                .username(request.username())
                .passwordHash(passwordEncoder.encode(request.password()))
                .role(AdminRole.ADMIN)
                .side(side)
                .hall(hall)
                .active(true)
                .createdBy(creator)
                .build();

        return toSummary(adminRepository.save(admin));
    }

    @Transactional
    public AdminSummaryResponse setActive(UUID adminId, boolean active) {
        Admin admin = adminRepository.findById(adminId)
                .orElseThrow(() -> new ResourceNotFoundException("Admin not found: " + adminId));
        admin.setActive(active);
        return toSummary(adminRepository.save(admin));
    }

    private AdminSummaryResponse toSummary(Admin admin) {
        long guestCount = guestRepository.countByAdminIdAndDeletedFalse(admin.getId());
        String side = admin.getSide() != null ? admin.getSide().name() : null;
        String hall = admin.getHall() != null ? admin.getHall().name() : null;
        return new AdminSummaryResponse(
                admin.getId(),
                admin.getUsername(),
                admin.getRole().name(),
                side,
                hall,
                admin.isActive(),
                guestCount,
                admin.getCreatedAt());
    }
}
