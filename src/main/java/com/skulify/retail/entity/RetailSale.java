package com.skulify.retail.entity;

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

@Entity
@Table(name = "retail_sales",
        uniqueConstraints = {
                @UniqueConstraint(columnNames = {"tenant_id", "reference"}),
                @UniqueConstraint(columnNames = {"tenant_id", "idempotency_key"})
        })
@Getter
@Setter
@NoArgsConstructor
public class RetailSale extends TenantOwnedEntity {

    @Column(nullable = false, length = 60)
    private String reference;

    @Column(name = "student_id", nullable = false)
    private UUID studentId;

    @Column(name = "wallet_id", nullable = false)
    private UUID walletId;

    @Column(name = "card_id")
    private UUID cardId;

    @Column(name = "merchant_id", nullable = false)
    private UUID merchantId;

    @Column(name = "terminal_id", nullable = false)
    private UUID terminalId;

    @Column(nullable = false)
    private long subtotal;

    @Column(nullable = false)
    private long discount;

    @Column(nullable = false)
    private long total;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private RetailSaleStatus status = RetailSaleStatus.COMPLETED;

    @Column(name = "payment_method", nullable = false, length = 30)
    private String paymentMethod = "POCKET_MONEY";

    @Column(name = "pocket_purchase_id")
    private UUID pocketPurchaseId;

    @Column(name = "pricing_fingerprint", nullable = false, length = 64)
    private String pricingFingerprint;

    @Column(name = "idempotency_key", nullable = false, length = 120)
    private String idempotencyKey;

    @Column(name = "request_hash", nullable = false, length = 64)
    private String requestHash;

    @Column(name = "receipt_no", nullable = false, length = 60)
    private String receiptNo;

    @Column(length = 500)
    private String notes;
}
