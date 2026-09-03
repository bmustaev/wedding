package uz.bobnoza.wedding.entity;

import uz.bobnoza.wedding.entity.converter.StringListCsvConverter;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.security.SecureRandom;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * One invitation entry. May represent a single person or a group/family —
 * tracked as a single row either way, distinguished by {@link #isGroup()} and
 * {@link #getPartySize()}. Owned by exactly one {@link Admin} via {@link #getAdmin()};
 * every guest-list query in the app must filter on that ownership.
 */
@Entity
@Table(name = "guests")
public class Guest {

    private static final SecureRandom RANDOM = new SecureRandom();

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    /** Owning admin. Never expose or filter guest queries without this. */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "admin_id", nullable = false)
    private Admin admin;

    @Column(name = "display_name", nullable = false)
    private String displayName;

    @Column(name = "is_group", nullable = false)
    private boolean group = false;

    @Column(name = "party_size", nullable = false)
    private int partySize = 1;

    /** Optional individual names within a group, for display only. Stored as CSV — see StringListCsvConverter. */
    @Convert(converter = StringListCsvConverter.class)
    @Column(name = "group_members")
    private List<String> groupMembers;

    @Column(name = "greeting_message")
    private String greetingMessage;

    @Column(nullable = false)
    private String language = "en";

    /** Unguessable public token used in the invitation URL. Never expose {@link #id} instead. */
    @Column(name = "landing_slug", nullable = false, unique = true, updatable = false)
    private String landingSlug;

    @Column(name = "page_generated_at")
    private Instant pageGeneratedAt;

    @Column(name = "first_viewed_at")
    private Instant firstViewedAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "table_id")
    private SeatingTable table;

    @Column(name = "is_deleted", nullable = false)
    private boolean deleted = false;

    @Column(name = "deleted_at")
    private Instant deletedAt;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected Guest() {
        // required by JPA
    }

    public Guest(UUID id, Admin admin, String displayName, boolean group, int partySize,
                 List<String> groupMembers, String greetingMessage, String language, String landingSlug,
                 Instant pageGeneratedAt, Instant firstViewedAt, SeatingTable table, boolean deleted,
                 Instant deletedAt, Instant createdAt, Instant updatedAt) {
        this.id = id;
        this.admin = admin;
        this.displayName = displayName;
        this.group = group;
        this.partySize = partySize;
        this.groupMembers = groupMembers;
        this.greetingMessage = greetingMessage;
        this.language = language;
        this.landingSlug = landingSlug;
        this.pageGeneratedAt = pageGeneratedAt;
        this.firstViewedAt = firstViewedAt;
        this.table = table;
        this.deleted = deleted;
        this.deletedAt = deletedAt;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    public static Builder builder() {
        return new Builder();
    }

    @PrePersist
    private void beforeInsert() {
        if (landingSlug == null) {
            landingSlug = generateSlug();
        }
        validatePartySize();
    }

    @PreUpdate
    private void beforeUpdate() {
        validatePartySize();
    }

    private void validatePartySize() {
        // Defense-in-depth: the same rule is a CHECK constraint in the database
        // (ck_guests_group_size in schema.sql).
        if (!group && partySize != 1) {
            throw new IllegalStateException("A non-group guest must have party_size = 1");
        }
        if (partySize < 1) {
            throw new IllegalStateException("party_size must be at least 1");
        }
    }

    private static String generateSlug() {
        byte[] bytes = new byte[16];
        RANDOM.nextBytes(bytes);
        StringBuilder sb = new StringBuilder(bytes.length * 2);
        for (byte b : bytes) {
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
    }

    public void softDelete() {
        this.deleted = true;
        this.deletedAt = Instant.now();
        this.table = null;
    }

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public Admin getAdmin() {
        return admin;
    }

    public void setAdmin(Admin admin) {
        this.admin = admin;
    }

    public String getDisplayName() {
        return displayName;
    }

    public void setDisplayName(String displayName) {
        this.displayName = displayName;
    }

    public boolean isGroup() {
        return group;
    }

    public void setGroup(boolean group) {
        this.group = group;
    }

    public int getPartySize() {
        return partySize;
    }

    public void setPartySize(int partySize) {
        this.partySize = partySize;
    }

    public List<String> getGroupMembers() {
        return groupMembers;
    }

    public void setGroupMembers(List<String> groupMembers) {
        this.groupMembers = groupMembers;
    }

    public String getGreetingMessage() {
        return greetingMessage;
    }

    public void setGreetingMessage(String greetingMessage) {
        this.greetingMessage = greetingMessage;
    }

    public String getLanguage() {
        return language;
    }

    public void setLanguage(String language) {
        this.language = language;
    }

    public String getLandingSlug() {
        return landingSlug;
    }

    public void setLandingSlug(String landingSlug) {
        this.landingSlug = landingSlug;
    }

    public Instant getPageGeneratedAt() {
        return pageGeneratedAt;
    }

    public void setPageGeneratedAt(Instant pageGeneratedAt) {
        this.pageGeneratedAt = pageGeneratedAt;
    }

    public Instant getFirstViewedAt() {
        return firstViewedAt;
    }

    public void setFirstViewedAt(Instant firstViewedAt) {
        this.firstViewedAt = firstViewedAt;
    }

    public SeatingTable getTable() {
        return table;
    }

    public void setTable(SeatingTable table) {
        this.table = table;
    }

    public boolean isDeleted() {
        return deleted;
    }

    public void setDeleted(boolean deleted) {
        this.deleted = deleted;
    }

    public Instant getDeletedAt() {
        return deletedAt;
    }

    public void setDeletedAt(Instant deletedAt) {
        this.deletedAt = deletedAt;
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
        if (!(o instanceof Guest other)) return false;
        return id != null && id.equals(other.id);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(id);
    }

    public static final class Builder {
        private UUID id;
        private Admin admin;
        private String displayName;
        private boolean group = false;
        private int partySize = 1;
        private List<String> groupMembers;
        private String greetingMessage;
        private String language = "en";
        private String landingSlug;
        private Instant pageGeneratedAt;
        private Instant firstViewedAt;
        private SeatingTable table;
        private boolean deleted = false;
        private Instant deletedAt;
        private Instant createdAt;
        private Instant updatedAt;

        public Builder id(UUID id) { this.id = id; return this; }
        public Builder admin(Admin admin) { this.admin = admin; return this; }
        public Builder displayName(String displayName) { this.displayName = displayName; return this; }
        public Builder group(boolean group) { this.group = group; return this; }
        public Builder partySize(int partySize) { this.partySize = partySize; return this; }
        public Builder groupMembers(List<String> groupMembers) { this.groupMembers = groupMembers; return this; }
        public Builder greetingMessage(String greetingMessage) { this.greetingMessage = greetingMessage; return this; }
        public Builder language(String language) { this.language = language; return this; }
        public Builder landingSlug(String landingSlug) { this.landingSlug = landingSlug; return this; }
        public Builder pageGeneratedAt(Instant pageGeneratedAt) { this.pageGeneratedAt = pageGeneratedAt; return this; }
        public Builder firstViewedAt(Instant firstViewedAt) { this.firstViewedAt = firstViewedAt; return this; }
        public Builder table(SeatingTable table) { this.table = table; return this; }
        public Builder deleted(boolean deleted) { this.deleted = deleted; return this; }
        public Builder deletedAt(Instant deletedAt) { this.deletedAt = deletedAt; return this; }
        public Builder createdAt(Instant createdAt) { this.createdAt = createdAt; return this; }
        public Builder updatedAt(Instant updatedAt) { this.updatedAt = updatedAt; return this; }

        public Guest build() {
            return new Guest(id, admin, displayName, group, partySize, groupMembers, greetingMessage,
                    language, landingSlug, pageGeneratedAt, firstViewedAt, table, deleted, deletedAt,
                    createdAt, updatedAt);
        }
    }
}
