package uz.bobnoza.wedding.service;

import uz.bobnoza.wedding.dto.common.PageResponse;
import uz.bobnoza.wedding.dto.guest.GuestCreateRequest;
import uz.bobnoza.wedding.dto.guest.GuestResponse;
import uz.bobnoza.wedding.dto.guest.GuestUpdateRequest;
import uz.bobnoza.wedding.dto.guest.PublicInvitationResponse;
import uz.bobnoza.wedding.dto.media.MediaAllowanceResponse;
import uz.bobnoza.wedding.entity.Guest;
import uz.bobnoza.wedding.entity.SeatingTable;
import uz.bobnoza.wedding.exception.ResourceNotFoundException;
import uz.bobnoza.wedding.repository.AdminRepository;
import uz.bobnoza.wedding.repository.GuestRepository;
import uz.bobnoza.wedding.security.AdminPrincipal;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

/**
 * Guest CRUD, always scoped by ownership: every read/write that isn't the
 * public-invitation lookup takes an {@link AdminPrincipal} and filters by
 * its admin_id. That ownership filter is what keeps one admin's guest list
 * invisible to another (see GuestRepository.findByIdAndAdminIdAndDeletedFalse).
 */
@Service
public class GuestService {

    private final GuestRepository guestRepository;
    private final AdminRepository adminRepository;
    private final GuestMediaService guestMediaService;
    private final String invitationBaseUrl;

    public GuestService(GuestRepository guestRepository,
                         AdminRepository adminRepository,
                         GuestMediaService guestMediaService,
                         @Value("${app.invitation.base-url}") String invitationBaseUrl) {
        this.guestRepository = guestRepository;
        this.adminRepository = adminRepository;
        this.guestMediaService = guestMediaService;
        this.invitationBaseUrl = invitationBaseUrl;
    }

    @Transactional(readOnly = true)
    public PageResponse<GuestResponse> listMyGuests(AdminPrincipal caller, Pageable pageable) {
        return PageResponse.from(
                guestRepository.findAllByAdminIdAndDeletedFalseOrderByDisplayNameAsc(caller.getAdminId(), pageable)
                        .map(this::toResponse));
    }

    /** Super-admin-only view of a chosen admin's guests. Access control is enforced at the controller/security layer. */
    @Transactional(readOnly = true)
    public PageResponse<GuestResponse> listGuestsForAdmin(UUID adminId, Pageable pageable) {
        return PageResponse.from(
                guestRepository.findAllByAdminIdAndDeletedFalseOrderByDisplayNameAsc(adminId, pageable)
                        .map(this::toResponse));
    }

    @Transactional(readOnly = true)
    public GuestResponse getMyGuest(AdminPrincipal caller, UUID guestId) {
        return toResponse(requireOwnedGuest(caller, guestId));
    }

    @Transactional
    public GuestResponse createGuest(AdminPrincipal caller, GuestCreateRequest request) {
        boolean isGroup = request.isGroup();
        int partySize = request.partySize() != null ? request.partySize() : 1;
        if (!isGroup) {
            partySize = 1; // defense-in-depth — matches the entity/DB rule
        }

        Guest guest = Guest.builder()
                .admin(adminRepository.getReferenceById(caller.getAdminId()))
                .displayName(request.displayName())
                .group(isGroup)
                .partySize(partySize)
                .groupMembers(request.groupMembers())
                .greetingMessage(request.greetingMessage())
                .language(request.language() != null ? request.language() : "ru")
                // The landing page exists as soon as the guest does — no separate "generate" step.
                .pageGeneratedAt(Instant.now())
                .build();

        return toResponse(guestRepository.save(guest));
    }

    @Transactional
    public GuestResponse updateGuest(AdminPrincipal caller, UUID guestId, GuestUpdateRequest request) {
        Guest guest = requireOwnedGuest(caller, guestId);

        if (request.displayName() != null) {
            guest.setDisplayName(request.displayName());
        }
        if (request.isGroup() != null) {
            guest.setGroup(request.isGroup());
        }
        if (request.partySize() != null) {
            guest.setPartySize(request.partySize());
        }
        if (request.groupMembers() != null) {
            guest.setGroupMembers(request.groupMembers());
        }
        if (request.greetingMessage() != null) {
            guest.setGreetingMessage(request.greetingMessage());
        }
        if (request.language() != null) {
            guest.setLanguage(request.language());
        }
        if (!guest.isGroup()) {
            guest.setPartySize(1); // defense-in-depth — matches ck_guests_group_size
        }
        // Content changed — treat this as a regeneration of the invitation page.
        guest.setPageGeneratedAt(Instant.now());

        return toResponse(guest); // managed entity — change is flushed on transaction commit
    }

    @Transactional
    public void deleteGuest(AdminPrincipal caller, UUID guestId) {
        requireOwnedGuest(caller, guestId).softDelete();
    }

    @Transactional
    public GuestResponse regeneratePage(AdminPrincipal caller, UUID guestId) {
        Guest guest = requireOwnedGuest(caller, guestId);
        guest.setPageGeneratedAt(Instant.now());
        return toResponse(guest);
    }

    /** Resolves a guest by their public landing-page token — no admin/ownership check applies here by design. */
    @Transactional(readOnly = true)
    public UUID resolveGuestIdBySlug(String slug) {
        return guestRepository.findByLandingSlugAndDeletedFalse(slug)
                .map(Guest::getId)
                .orElseThrow(() -> new ResourceNotFoundException("Invitation not found"));
    }

    /** What a guest sees on their own landing page, reached via {@link #resolveGuestIdBySlug}. */
    @Transactional
    public PublicInvitationResponse getPublicInvitation(String slug) {
        Guest guest = guestRepository.findByLandingSlugAndDeletedFalse(slug)
                .orElseThrow(() -> new ResourceNotFoundException("Invitation not found"));

        if (guest.getFirstViewedAt() == null) {
            guest.setFirstViewedAt(Instant.now());
        }

        MediaAllowanceResponse allowance = guestMediaService.getAllowance(guest.getId());
        SeatingTable table = guest.getTable();
        Integer tableNumber = table != null ? table.getTableNumber() : null;
        String tableLabel = table != null ? table.getLabel() : null;

        return new PublicInvitationResponse(
                guest.getDisplayName(),
                guest.isGroup(),
                guest.getGroupMembers(),
                guest.getGreetingMessage(),
                guest.getLanguage(),
                tableNumber,
                tableLabel,
                allowance.photosRemaining(),
                allowance.videosRemaining());
    }

    /**
     * Ownership-checked lookup shared with other services/controllers (e.g. media, seating).
     * super_admin bypasses ownership entirely — it manages guests across every admin,
     * so this is the one place that rule needs to live, since every guest read/write in
     * the app (view, edit, delete, media, table assignment) routes through this method.
     */
    @Transactional(readOnly = true)
    public Guest requireOwnedGuest(AdminPrincipal caller, UUID guestId) {
        if (caller.isSuperAdmin()) {
            return guestRepository.findByIdAndDeletedFalse(guestId)
                    .orElseThrow(() -> new ResourceNotFoundException("Guest not found: " + guestId));
        }
        return guestRepository.findByIdAndAdminIdAndDeletedFalse(guestId, caller.getAdminId())
                .orElseThrow(() -> new ResourceNotFoundException("Guest not found: " + guestId));
        // Deliberately 404, not 403: an admin shouldn't be able to tell the
        // difference between "doesn't exist" and "belongs to someone else".
    }

    private GuestResponse toResponse(Guest guest) {
        SeatingTable table = guest.getTable();
        UUID tableId = table != null ? table.getId() : null;
        Integer tableNumber = table != null ? table.getTableNumber() : null;

        return new GuestResponse(
                guest.getId(),
                guest.getDisplayName(),
                guest.isGroup(),
                guest.getPartySize(),
                guest.getGroupMembers(),
                guest.getGreetingMessage(),
                guest.getLanguage(),
                guest.getLandingSlug(),
                invitationBaseUrl + "/" + guest.getLandingSlug(),
                tableId,
                tableNumber,
                guest.getPageGeneratedAt(),
                guest.getFirstViewedAt(),
                guest.getCreatedAt());
    }
}
