package org.example.school_wallet.operations;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.example.school_wallet.common.domain.TenantOwnedEntity;

@Entity
@Table(name = "book_copies", uniqueConstraints = @UniqueConstraint(columnNames = {"tenant_id", "barcode"}))
@Getter
@Setter
@NoArgsConstructor
public class BookCopy extends TenantOwnedEntity {

    @Column(nullable = false, length = 60)
    private String barcode;

    @Column(name = "book_id", nullable = false)
    private UUID bookId;

    @Column(length = 120)
    private String location;

    @Column(length = 20)
    private String status = "AVAILABLE";
}
