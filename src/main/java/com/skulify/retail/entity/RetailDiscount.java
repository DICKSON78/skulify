package com.skulify.retail.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.math.BigDecimal;
import java.time.Instant;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.example.school_wallet.common.domain.TenantOwnedEntity;

@Entity
@Table(name = "retail_discounts",
        uniqueConstraints = @UniqueConstraint(columnNames = {"tenant_id", "code"}))
@Getter
@Setter
@NoArgsConstructor
public class RetailDiscount extends TenantOwnedEntity {

    @Column(nullable = false, length = 60)
    private String code;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private RetailDiscountKind kind;

    @Column(name = "discount_value", nullable = false, precision = 14, scale = 2)
    private BigDecimal value;

    @Column(nullable = false, length = 20)
    private String status = "ACTIVE";

    @Column(name = "expires_at")
    private Instant expiresAt;
}
