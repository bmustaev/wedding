package uz.bobnoza.wedding.security;

import uz.bobnoza.wedding.entity.Admin;
import uz.bobnoza.wedding.entity.AdminRole;
import uz.bobnoza.wedding.entity.AdminSide;
import uz.bobnoza.wedding.entity.Guest;
import uz.bobnoza.wedding.entity.Hall;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

/**
 * Spring Security identity wrapping an {@link Admin}. Kept separate from the
 * entity so persistence concerns and security concerns don't bleed together.
 */
public class AdminPrincipal implements UserDetails {

    private final Admin admin;

    public AdminPrincipal(Admin admin) {
        this.admin = admin;
    }

    public UUID getAdminId() {
        return admin.getId();
    }

    public Admin getAdmin() {
        return admin;
    }

    public boolean isSuperAdmin() {
        return admin.isSuperAdmin();
    }

    /** A DJ login: one hall's playlist and nothing else (SecurityConfig keeps it to /api/playlist/**). */
    public boolean isDj() {
        return admin.getRole() == AdminRole.DJ;
    }

    /** A banker login: one hall's bank table and nothing else (SecurityConfig keeps it to /api/bank/**). */
    public boolean isBanker() {
        return admin.getRole() == AdminRole.BANKER;
    }

    /** A DJ or a banker: one job in one hall, no side, no guests of their own. */
    private boolean isHallStaff() {
        return isDj() || isBanker();
    }

    /** Null for super_admin. */
    public AdminSide getSide() {
        return admin.getSide();
    }

    /** The one hall a hall admin (e.g. sam_hall: SAMARKAND), a DJ or a banker is limited to; null for everyone else. */
    public Hall getHall() {
        return admin.getHall();
    }

    /**
     * super_admin reaches every hall; a side admin only the halls open to
     * their side (see {@link Hall#isOpenTo}) — and a hall admin, a DJ or a
     * banker only their own.
     */
    public boolean canAccessHall(Hall hall) {
        if (isSuperAdmin()) {
            return true;
        }
        if (isHallStaff()) {
            return hall == getHall();
        }
        return hall.isOpenTo(getSide()) && (getHall() == null || getHall() == hall);
    }

    /**
     * Whether this admin may view and edit the guest: super_admin any guest,
     * every admin their own, and a hall admin also every guest their side
     * has in their hall (whoever created it). Mirrored by the
     * get_seating_chart_for_admin procedure's is_own_guest.
     */
    public boolean canManageGuest(Guest guest) {
        if (isHallStaff()) {
            return false;
        }
        if (isSuperAdmin() || guest.getAdmin().getId().equals(getAdminId())) {
            return true;
        }
        return getHall() != null && guest.getHall() == getHall() && guest.getAdmin().getSide() == getSide();
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return List.of(new SimpleGrantedAuthority("ROLE_" + admin.getRole().name()));
    }

    @Override
    public String getPassword() {
        return admin.getPasswordHash();
    }

    @Override
    public String getUsername() {
        return admin.getUsername();
    }

    @Override
    public boolean isAccountNonExpired() {
        return true;
    }

    @Override
    public boolean isAccountNonLocked() {
        return admin.isActive();
    }

    @Override
    public boolean isCredentialsNonExpired() {
        return true;
    }

    @Override
    public boolean isEnabled() {
        return admin.isActive();
    }
}
