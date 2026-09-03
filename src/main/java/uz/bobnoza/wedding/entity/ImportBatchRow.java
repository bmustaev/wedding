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

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/** One line of an uploaded `.txt` guest list — success or failure. */
@Entity
@Table(name = "import_batch_rows")
public class ImportBatchRow {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "batch_id", nullable = false)
    private ImportBatch batch;

    @Column(name = "row_number", nullable = false)
    private int rowNumber;

    @Column(name = "raw_line", nullable = false)
    private String rawLine;

    /** Null if the line failed to parse into a guest. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "guest_id")
    private Guest guest;

    @Column(name = "error_message")
    private String errorMessage;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected ImportBatchRow() {
        // required by JPA
    }

    public ImportBatchRow(UUID id, ImportBatch batch, int rowNumber, String rawLine, Guest guest,
                           String errorMessage, Instant createdAt) {
        this.id = id;
        this.batch = batch;
        this.rowNumber = rowNumber;
        this.rawLine = rawLine;
        this.guest = guest;
        this.errorMessage = errorMessage;
        this.createdAt = createdAt;
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

    public ImportBatch getBatch() {
        return batch;
    }

    public void setBatch(ImportBatch batch) {
        this.batch = batch;
    }

    public int getRowNumber() {
        return rowNumber;
    }

    public void setRowNumber(int rowNumber) {
        this.rowNumber = rowNumber;
    }

    public String getRawLine() {
        return rawLine;
    }

    public void setRawLine(String rawLine) {
        this.rawLine = rawLine;
    }

    public Guest getGuest() {
        return guest;
    }

    public void setGuest(Guest guest) {
        this.guest = guest;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    public void setErrorMessage(String errorMessage) {
        this.errorMessage = errorMessage;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof ImportBatchRow other)) return false;
        return id != null && id.equals(other.id);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(id);
    }

    public static final class Builder {
        private UUID id;
        private ImportBatch batch;
        private int rowNumber;
        private String rawLine;
        private Guest guest;
        private String errorMessage;
        private Instant createdAt;

        public Builder id(UUID id) { this.id = id; return this; }
        public Builder batch(ImportBatch batch) { this.batch = batch; return this; }
        public Builder rowNumber(int rowNumber) { this.rowNumber = rowNumber; return this; }
        public Builder rawLine(String rawLine) { this.rawLine = rawLine; return this; }
        public Builder guest(Guest guest) { this.guest = guest; return this; }
        public Builder errorMessage(String errorMessage) { this.errorMessage = errorMessage; return this; }
        public Builder createdAt(Instant createdAt) { this.createdAt = createdAt; return this; }

        public ImportBatchRow build() {
            return new ImportBatchRow(id, batch, rowNumber, rawLine, guest, errorMessage, createdAt);
        }
    }
}
