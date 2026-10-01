package dev.ix1ax.main.model;

import jakarta.persistence.*;

/** Cleanup metadata only: never contains JPEG bytes or a user's schedule. */
@Entity
@Table(name = "shared_image_cleanup", indexes = @Index(name = "idx_share_expiry", columnList = "expires_at"))
public class SharedImage {
    @Id private String fileId;
    @Column(name = "expires_at", nullable = false) private long expiresAt;
    protected SharedImage() {}
    public SharedImage(String fileId, long expiresAt) { this.fileId = fileId; this.expiresAt = expiresAt; }
    public String getFileId() { return fileId; }
    public long getExpiresAt() { return expiresAt; }
}
