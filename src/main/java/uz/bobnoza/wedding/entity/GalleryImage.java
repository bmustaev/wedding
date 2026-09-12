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

/** One photo in the invitation page's "about us" gallery, in caption(s) + display order. */
@Entity
@Table(name = "gallery_images")
public class GalleryImage {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    /** Object storage key/path — file bytes live outside the database. */
    @Column(name = "storage_key", nullable = false)
    private String storageKey;

    @Column(name = "mime_type", nullable = false)
    private String mimeType;

    @Column(name = "caption_ru", nullable = false)
    private String captionRu;

    @Column(name = "caption_uz", nullable = false)
    private String captionUz;

    @Column(name = "caption_en", nullable = false)
    private String captionEn;

    @Column(name = "display_order", nullable = false)
    private int displayOrder;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected GalleryImage() {
        // required by JPA
    }

    public GalleryImage(UUID id, String storageKey, String mimeType, String captionRu, String captionUz,
                         String captionEn, int displayOrder, Instant createdAt, Instant updatedAt) {
        this.id = id;
        this.storageKey = storageKey;
        this.mimeType = mimeType;
        this.captionRu = captionRu;
        this.captionUz = captionUz;
        this.captionEn = captionEn;
        this.displayOrder = displayOrder;
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

    public String getStorageKey() {
        return storageKey;
    }

    public void setStorageKey(String storageKey) {
        this.storageKey = storageKey;
    }

    public String getMimeType() {
        return mimeType;
    }

    public void setMimeType(String mimeType) {
        this.mimeType = mimeType;
    }

    public String getCaptionRu() {
        return captionRu;
    }

    public void setCaptionRu(String captionRu) {
        this.captionRu = captionRu;
    }

    public String getCaptionUz() {
        return captionUz;
    }

    public void setCaptionUz(String captionUz) {
        this.captionUz = captionUz;
    }

    public String getCaptionEn() {
        return captionEn;
    }

    public void setCaptionEn(String captionEn) {
        this.captionEn = captionEn;
    }

    public int getDisplayOrder() {
        return displayOrder;
    }

    public void setDisplayOrder(int displayOrder) {
        this.displayOrder = displayOrder;
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
        if (!(o instanceof GalleryImage other)) return false;
        return id != null && id.equals(other.id);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(id);
    }

    public static final class Builder {
        private UUID id;
        private String storageKey;
        private String mimeType;
        private String captionRu = "";
        private String captionUz = "";
        private String captionEn = "";
        private int displayOrder;
        private Instant createdAt;
        private Instant updatedAt;

        public Builder id(UUID id) { this.id = id; return this; }
        public Builder storageKey(String storageKey) { this.storageKey = storageKey; return this; }
        public Builder mimeType(String mimeType) { this.mimeType = mimeType; return this; }
        public Builder captionRu(String captionRu) { this.captionRu = captionRu; return this; }
        public Builder captionUz(String captionUz) { this.captionUz = captionUz; return this; }
        public Builder captionEn(String captionEn) { this.captionEn = captionEn; return this; }
        public Builder displayOrder(int displayOrder) { this.displayOrder = displayOrder; return this; }
        public Builder createdAt(Instant createdAt) { this.createdAt = createdAt; return this; }
        public Builder updatedAt(Instant updatedAt) { this.updatedAt = updatedAt; return this; }

        public GalleryImage build() {
            return new GalleryImage(id, storageKey, mimeType, captionRu, captionUz, captionEn, displayOrder,
                    createdAt, updatedAt);
        }
    }
}
