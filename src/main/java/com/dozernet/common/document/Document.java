package com.dozernet.common.document;

import com.dozernet.common.model.BaseEntity;
import com.dozernet.common.user.User;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

/**
 * A supporting document (NIC copy, driving licence, ownership proof, inspection
 * photo) uploaded by a user. The file itself is written to the upload directory
 * on disk; this row holds the metadata and the link back to its owner.
 */
@Entity
@Table(name = "documents")
public class Document extends BaseEntity {

    @ManyToOne(fetch = FetchType.EAGER, optional = false)
    private User owner;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private DocumentType type;

    /** Name as the user uploaded it, shown in the UI. */
    @Column(nullable = false, length = 255)
    private String originalFilename;

    /** Randomised name on disk, so uploads can never overwrite each other. */
    @Column(nullable = false, length = 120)
    private String storedFilename;

    @Column(nullable = false, length = 100)
    private String contentType;

    @Column(nullable = false)
    private long sizeBytes;

    public Document() {
    }

    public Document(User owner, DocumentType type, String originalFilename,
                    String storedFilename, String contentType, long sizeBytes) {
        this.owner = owner;
        this.type = type;
        this.originalFilename = originalFilename;
        this.storedFilename = storedFilename;
        this.contentType = contentType;
        this.sizeBytes = sizeBytes;
    }

    public User getOwner() {
        return owner;
    }

    public void setOwner(User owner) {
        this.owner = owner;
    }

    public DocumentType getType() {
        return type;
    }

    public void setType(DocumentType type) {
        this.type = type;
    }

    public String getOriginalFilename() {
        return originalFilename;
    }

    public void setOriginalFilename(String originalFilename) {
        this.originalFilename = originalFilename;
    }

    public String getStoredFilename() {
        return storedFilename;
    }

    public void setStoredFilename(String storedFilename) {
        this.storedFilename = storedFilename;
    }

    public String getContentType() {
        return contentType;
    }

    public void setContentType(String contentType) {
        this.contentType = contentType;
    }

    public long getSizeBytes() {
        return sizeBytes;
    }

    public void setSizeBytes(long sizeBytes) {
        this.sizeBytes = sizeBytes;
    }

    /** Rounded size for display, e.g. "248 KB". */
    public String getSizeLabel() {
        if (sizeBytes < 1024) {
            return sizeBytes + " B";
        }
        if (sizeBytes < 1024 * 1024) {
            return (sizeBytes / 1024) + " KB";
        }
        return String.format("%.1f MB", sizeBytes / (1024.0 * 1024.0));
    }
}
