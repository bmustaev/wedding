package uz.bobnoza.wedding.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Super admins, regular admins, DJs and bankers share this table; {@link #role} distinguishes them.
 * Guests never have a row here — they authenticate purely via {@link Guest#getLandingSlug()}.
 */
@Entity
@Table(name = "admins")
public class Admin {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false, unique = true)
    private String username;

    /** Argon2id or bcrypt hash only — never a plaintext or reversible value. */
    @Column(name = "password_hash", nullable = false)
    private String passwordHash;

    @Column(nullable = false)
    private AdminRole role = AdminRole.ADMIN;

    /** Null for super_admin, DJs and bankers — every regular admin must have one. */
    @Column
    private AdminSide side;

    /**
     * Set only for a hall admin: limited to this one hall, but manages every
     * guest their side has there (see AdminPrincipal#canManageGuest). Null —
     * every hall open to their side, own guests only. Always set for a DJ
     * (the hall whose playlist they work) and a banker (whose bank table).
     */
    @Column
    private Hall hall;

    @Column(name = "is_active", nullable = false)
    private boolean active = true;

    /**
     * The 4-digit PIN this person types on a guest's phone at the bank
     * table, as BankCodes#pinDigest — never the digits. Null: none set.
     */
    @Column(name = "bank_pin")
    private String bankPin;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "created_by")
    private Admin createdBy;

    @Column(name = "last_login_at")
    private Instant lastLoginAt;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected Admin() {
        // required by JPA
    }

    public Admin(UUID id, String username, String passwordHash, AdminRole role, AdminSide side, Hall hall, boolean active,
                 Admin createdBy, Instant lastLoginAt, Instant createdAt, Instant updatedAt) {
        this.id = id;
        this.username = username;
        this.passwordHash = passwordHash;
        this.role = role;
        this.side = side;
        this.hall = hall;
        this.active = active;
        this.createdBy = createdBy;
        this.lastLoginAt = lastLoginAt;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    public static Builder builder() {
        return new Builder();
    }

    public boolean isSuperAdmin() {
        return role == AdminRole.SUPER_ADMIN;
    }

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getPasswordHash() {
        return passwordHash;
    }

    public void setPasswordHash(String passwordHash) {
        this.passwordHash = passwordHash;
    }

    public AdminRole getRole() {
        return role;
    }

    public void setRole(AdminRole role) {
        this.role = role;
    }

    public AdminSide getSide() {
        return side;
    }

    public void setSide(AdminSide side) {
        this.side = side;
    }

    public Hall getHall() {
        return hall;
    }

    public void setHall(Hall hall) {
        this.hall = hall;
    }

    public boolean isActive() {
        return active;
    }

    public void setActive(boolean active) {
        this.active = active;
    }

    public String getBankPin() {
        return bankPin;
    }

    public void setBankPin(String bankPin) {
        this.bankPin = bankPin;
    }

    public Admin getCreatedBy() {
        return createdBy;
    }

    public void setCreatedBy(Admin createdBy) {
        this.createdBy = createdBy;
    }

    public Instant getLastLoginAt() {
        return lastLoginAt;
    }

    public void setLastLoginAt(Instant lastLoginAt) {
        this.lastLoginAt = lastLoginAt;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(Instant updatedAt) {
        this.updatedAt = updatedAt;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Admin other)) return false;
        return id != null && id.equals(other.id);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(id);
    }

    public static final class Builder {
        private UUID id;
        private String username;
        private String passwordHash;
        private AdminRole role = AdminRole.ADMIN;
        private AdminSide side;
        private Hall hall;
        private boolean active = true;
        private Admin createdBy;
        private Instant lastLoginAt;
        private Instant createdAt;
        private Instant updatedAt;

        public Builder id(UUID id) { this.id = id; return this; }
        public Builder username(String username) { this.username = username; return this; }
        public Builder passwordHash(String passwordHash) { this.passwordHash = passwordHash; return this; }
        public Builder role(AdminRole role) { this.role = role; return this; }
        public Builder side(AdminSide side) { this.side = side; return this; }
        public Builder hall(Hall hall) { this.hall = hall; return this; }
        public Builder active(boolean active) { this.active = active; return this; }
        public Builder createdBy(Admin createdBy) { this.createdBy = createdBy; return this; }
        public Builder lastLoginAt(Instant lastLoginAt) { this.lastLoginAt = lastLoginAt; return this; }
        public Builder createdAt(Instant createdAt) { this.createdAt = createdAt; return this; }
        public Builder updatedAt(Instant updatedAt) { this.updatedAt = updatedAt; return this; }

        public Admin build() {
            return new Admin(id, username, passwordHash, role, side, hall, active, createdBy, lastLoginAt, createdAt, updatedAt);
        }
    }
}
