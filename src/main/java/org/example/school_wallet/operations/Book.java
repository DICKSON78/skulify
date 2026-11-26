package org.example.school_wallet.operations;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.example.school_wallet.common.domain.TenantOwnedEntity;

@Entity
@Table(name = "books", uniqueConstraints = @UniqueConstraint(columnNames = {"tenant_id", "isbn"}))
@Getter
@Setter
@NoArgsConstructor
public class Book extends TenantOwnedEntity {

    @Column(nullable = false, length = 30)
    private String isbn;

    @Column(nullable = false, length = 200)
    private String title;

    @Column(length = 150)
    private String author;

    @Column(length = 100)
    private String subject;

    @Column(length = 100)
    private String department;

    @Column(length = 120)
    private String location;

    @Column(name = "copies_total", nullable = false)
    private int copiesTotal;

    @Column(name = "copies_available", nullable = false)
    private int copiesAvailable;

    @Column(name = "digital_url", length = 500)
    private String digitalUrl;

    @Column(length = 20)
    private String status = "ACTIVE";
}
