package org.example.school_wallet.operations;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.example.school_wallet.common.domain.TenantOwnedEntity;

/** Uploaded school document with searchable metadata and controlled access. */
@Entity
@Table(
        name = "school_documents",
        uniqueConstraints = @UniqueConstraint(columnNames = {"tenant_id", "reference"}))
@Getter
@Setter
@NoArgsConstructor
public class SchoolDocument extends TenantOwnedEntity {

    @Column(nullable = false, length = 40)
    private String reference;

    @Column(nullable = false, length = 180)
    private String title;

    @Column(length = 40)
    private String category;

    @Column(length = 1000)
    private String description;

    @Column(name = "file_name", length = 180)
    private String fileName;

    @Column(name = "file_path", length = 500)
    private String filePath;

    @Column(name = "mime_type", length = 120)
    private String mimeType;

    @Column(name = "file_size")
    private Long fileSize;

    @Column(name = "document_date", length = 20)
    private String documentDate;

    @Column(name = "uploaded_by_id")
    private UUID uploadedById;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Status status = Status.ACTIVE;

    public enum Status { ACTIVE, ARCHIVED }
}
