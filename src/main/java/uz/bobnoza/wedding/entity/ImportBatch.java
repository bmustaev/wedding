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

/** One `.txt` guest-list upload, tracked for review/audit. */
@Entity
@Table(name = "import_batches")
public class ImportBatch {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "admin_id", nullable = false)
    private Admin admin;

    @Column(nullable = false)
    private String filename;

    @Column(nullable = false)
    private ImportStatus status = ImportStatus.PENDING;

    @Column(name = "total_rows", nullable = false)
    private int totalRows = 0;

    @Column(name = "success_rows", nullable = false)
    private int successRows = 0;

    @Column(name = "error_rows", nullable = false)
    private int errorRows = 0;

    @Column(name = "imported_at")
    private Instant importedAt;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected ImportBatch() {
        // required by JPA
    }

    public ImportBatch(UUID id, Admin admin, String filename, ImportStatus status, int totalRows,
                        int successRows, int errorRows, Instant importedAt, Instant createdAt, Instant updatedAt) {
        this.id = id;
        this.admin = admin;
        this.filename = filename;
        this.status = status;
        this.totalRows = totalRows;
        this.successRows = successRows;
        this.errorRows = errorRows;
        this.importedAt = importedAt;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    public static Builder builder() {
        return new Builder();
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

    public String getFilename() {
        return filename;
    }

    public void setFilename(String filename) {
        this.filename = filename;
    }

    public ImportStatus getStatus() {
        return status;
    }

    public void setStatus(ImportStatus status) {
        this.status = status;
    }

    public int getTotalRows() {
        return totalRows;
    }

    public void setTotalRows(int totalRows) {
        this.totalRows = totalRows;
    }

    public int getSuccessRows() {
        return successRows;
    }

    public void setSuccessRows(int successRows) {
        this.successRows = successRows;
    }

    public int getErrorRows() {
        return errorRows;
    }

    public void setErrorRows(int errorRows) {
        this.errorRows = errorRows;
    }

    public Instant getImportedAt() {
        return importedAt;
    }

    public void setImportedAt(Instant importedAt) {
        this.importedAt = importedAt;
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
        if (!(o instanceof ImportBatch other)) return false;
        return id != null && id.equals(other.id);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(id);
    }

    public static final class Builder {
        private UUID id;
        private Admin admin;
        private String filename;
        private ImportStatus status = ImportStatus.PENDING;
        private int totalRows = 0;
        private int successRows = 0;
        private int errorRows = 0;
        private Instant importedAt;
        private Instant createdAt;
        private Instant updatedAt;

        public Builder id(UUID id) { this.id = id; return this; }
        public Builder admin(Admin admin) { this.admin = admin; return this; }
        public Builder filename(String filename) { this.filename = filename; return this; }
        public Builder status(ImportStatus status) { this.status = status; return this; }
        public Builder totalRows(int totalRows) { this.totalRows = totalRows; return this; }
        public Builder successRows(int successRows) { this.successRows = successRows; return this; }
        public Builder errorRows(int errorRows) { this.errorRows = errorRows; return this; }
        public Builder importedAt(Instant importedAt) { this.importedAt = importedAt; return this; }
        public Builder createdAt(Instant createdAt) { this.createdAt = createdAt; return this; }
        public Builder updatedAt(Instant updatedAt) { this.updatedAt = updatedAt; return this; }

        public ImportBatch build() {
            return new ImportBatch(id, admin, filename, status, totalRows, successRows, errorRows,
                    importedAt, createdAt, updatedAt);
        }
    }
}
