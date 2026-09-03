package uz.bobnoza.wedding.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * One table in the hall — belongs to {@link #side}: BRIDE, GROOM, or the
 * single fixed HEAD table. tableNumber is null only for the head table;
 * for BRIDE/GROOM it's unique per side (schema.sql: UNIQUE(side, table_number)),
 * not globally, so a "1B" and a "1D" can coexist.
 */
@Entity
@Table(name = "seating_tables")
public class SeatingTable {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false)
    private TableSide side;

    @Column(name = "table_number")
    private Integer tableNumber;

    @Column(nullable = false)
    private Integer capacity = 12;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected SeatingTable() {
        // required by JPA
    }

    public SeatingTable(UUID id, TableSide side, Integer tableNumber, Integer capacity,
                        Instant createdAt, Instant updatedAt) {
        this.id = id;
        this.side = side;
        this.tableNumber = tableNumber;
        this.capacity = capacity;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    public static Builder builder() {
        return new Builder();
    }

    /** Display label: "1B" / "2D" / "Head Table". Matches the SQL CASE in v_table_occupancy. */
    public String getLabel() {
        return switch (side) {
            case HEAD -> "Head Table";
            case BRIDE -> tableNumber + "D";
            case GROOM -> tableNumber + "B";
        };
    }

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public TableSide getSide() {
        return side;
    }

    public void setSide(TableSide side) {
        this.side = side;
    }

    public Integer getTableNumber() {
        return tableNumber;
    }

    public void setTableNumber(Integer tableNumber) {
        this.tableNumber = tableNumber;
    }

    public Integer getCapacity() {
        return capacity;
    }

    public void setCapacity(Integer capacity) {
        this.capacity = capacity;
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
        if (!(o instanceof SeatingTable other)) return false;
        return id != null && id.equals(other.id);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(id);
    }

    public static final class Builder {
        private UUID id;
        private TableSide side;
        private Integer tableNumber;
        private Integer capacity = 12;
        private Instant createdAt;
        private Instant updatedAt;

        public Builder id(UUID id) { this.id = id; return this; }
        public Builder side(TableSide side) { this.side = side; return this; }
        public Builder tableNumber(Integer tableNumber) { this.tableNumber = tableNumber; return this; }
        public Builder capacity(Integer capacity) { this.capacity = capacity; return this; }
        public Builder createdAt(Instant createdAt) { this.createdAt = createdAt; return this; }
        public Builder updatedAt(Instant updatedAt) { this.updatedAt = updatedAt; return this; }

        public SeatingTable build() {
            return new SeatingTable(id, side, tableNumber, capacity, createdAt, updatedAt);
        }
    }
}