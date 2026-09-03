package uz.bobnoza.wedding.security;

import uz.bobnoza.wedding.entity.Admin;
import uz.bobnoza.wedding.entity.AdminSide;
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

    /** Null for super_admin. */
    public AdminSide getSide() {
        return admin.getSide();
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        String role = admin.isSuperAdmin() ? "ROLE_SUPER_ADMIN" : "ROLE_ADMIN";
        return List.of(new SimpleGrantedAuthority(role));
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
