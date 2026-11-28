package com.skulify.retail.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.example.school_wallet.common.domain.TenantOwnedEntity;

@Entity
@Table(name = "retail_suppliers",
        uniqueConstraints = @UniqueConstraint(columnNames = {"tenant_id", "code"}))
@Getter
@Setter
@NoArgsConstructor
public class RetailSupplier extends TenantOwnedEntity {

    @Column(nullable = false, length = 60)
    private String code;

    @Column(nullable = false, length = 150)
    private String name;

    @Column(length = 40)
    private String phone;

    @Column(length = 150)
    private String email;

    @Column(nullable = false, length = 20)
    private String status = "ACTIVE";
}
