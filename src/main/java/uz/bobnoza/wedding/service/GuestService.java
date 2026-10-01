package uz.bobnoza.wedding.service;

import uz.bobnoza.wedding.dto.common.PageResponse;
import uz.bobnoza.wedding.dto.guest.GuestCreateRequest;
import uz.bobnoza.wedding.dto.guest.GuestResponse;
import uz.bobnoza.wedding.dto.guest.GuestUpdateRequest;
import uz.bobnoza.wedding.dto.guest.PublicInvitationResponse;
import uz.bobnoza.wedding.dto.media.MediaAllowanceResponse;
import uz.bobnoza.wedding.entity.AdminSide;
import uz.bobnoza.wedding.entity.Guest;
import uz.bobnoza.wedding.entity.Hall;
import uz.bobnoza.wedding.entity.SeatingTable;
import uz.bobnoza.wedding.exception.ForbiddenOperationException;
import uz.bobnoza.wedding.exception.ResourceNotFoundException;
import uz.bobnoza.wedding.repository.AdminRepository;
import uz.bobnoza.wedding.repository.GuestRepository;
import uz.bobnoza.wedding.security.AdminPrincipal;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

/**
 * Guest CRUD, always scoped by ownership: every read/write that isn't the
 * public-invitation lookup takes an {@link AdminPrincipal} and filters by
 * its admin_id. That ownership filter is what keeps one admin's guest list
 * invisible to another (see AdminPrincipal#canManageGuest). The one widening:
 * a hall admin also manages every guest their side has in their hall.
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
        return PageResponse.from(findManagedGuests(caller, pageable).map(this::toResponse));
    }

    /** The caller's guest list: their own guests, plus — for a hall admin — every guest their side has in their hall. */
    @Transactional(readOnly = true)
    public Page<Guest> findManagedGuests(AdminPrincipal caller, Pageable pageable) {
        if (caller.getHall() != null) {
            return guestRepository.findAllManagedByHallAdmin(caller.getAdminId(), caller.getHall(), caller.getSide(), pageable);
        }
        return guestRepository.findAllByAdminIdAndDeletedFalseOrderByDisplayNameAsc(caller.getAdminId(), pageable);
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
                .hall(HallResolver.resolve(caller, request.hall()))
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
        if (request.hall() != null) {
            Hall hall = HallResolver.resolve(caller, request.hall());
            AdminSide ownerSide = guest.getAdmin().getSide();
            if (ownerSide != null && !hall.isOpenTo(ownerSide)) {
                // Only reachable by super_admin editing a side admin's guest (e.g. a bride-side guest into Samarkand).
                throw new ForbiddenOperationException("This guest's side isn't invited to the " + hall.name() + " hall");
            }
            if (hall != guest.getHall()) {
                guest.setHall(hall);
                guest.setTable(null); // their old table is in the other hall — they need re-seating there
            }
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

    /**
     * The hall a slug's invitation is for, without touching first_viewed_at —
     * used to pick link-preview meta tags, which messenger crawlers fetch
     * before (or without) the guest ever opening the link.
     */
    @Transactional(readOnly = true)
    public Optional<Hall> findHallBySlug(String slug) {
        return guestRepository.findByLandingSlugAndDeletedFalse(slug).map(Guest::getHall);
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
                guest.getHall().name(),
                tableNumber,
                tableLabel,
                allowance.photosRemaining(),
                allowance.videosRemaining());
    }

    /**
     * Ownership-checked lookup shared with other services/controllers (e.g. media, seating).
     * Who may manage which guest (super_admin: everyone's; hall admin: their side's in
     * their hall) is decided by {@link AdminPrincipal#canManageGuest} — every guest
     * read/write in the app (view, edit, delete, media, table assignment) routes through it.
     */
    @Transactional(readOnly = true)
    public Guest requireOwnedGuest(AdminPrincipal caller, UUID guestId) {
        return guestRepository.findByIdAndDeletedFalse(guestId)
                .filter(caller::canManageGuest)
                .orElseThrow(() -> new ResourceNotFoundException("Guest not found: " + guestId));
        // Deliberately 404, not 403: an admin shouldn't be able to tell the
        // difference between "doesn't exist" and "belongs to someone else".
    }

    private GuestResponse toResponse(Guest guest) {
        SeatingTable table = guest.getTable();
        UUID tableId = table != null ? table.getId() : null;
        Integer tableNumber = table != null ? table.getTableNumber() : null;
        String tableLabel = table != null ? table.getLabel() : null;

        return new GuestResponse(
                guest.getId(),
                guest.getDisplayName(),
                guest.isGroup(),
                guest.getPartySize(),
                guest.getGroupMembers(),
                guest.getGreetingMessage(),
                guest.getLanguage(),
                guest.getHall().name(),
                guest.getLandingSlug(),
                invitationBaseUrl + "/" + guest.getLandingSlug(),
                tableId,
                tableNumber,
                tableLabel,
                guest.getPageGeneratedAt(),
                guest.getFirstViewedAt(),
                guest.getCreatedAt());
    }
}
