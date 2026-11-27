package com.skulify.pocketmoney.purchase;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.Check;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/** Public record of a sensor payment. Money itself lives in the append-only ledger. */
@Entity
@Table(name = "pm_purchases")
@Check(name = "pm_purchases_amount_check", constraints = "amount > 0")
@Getter
@Setter
@NoArgsConstructor
public class PmPurchase {

    @Id
    @Column(name = "id", nullable = false)
    private UUID id;

    @Column(name = "tenant_id", nullable = false, updatable = false)
    private UUID tenantId;

    @Column(name = "wallet_id", nullable = false)
    private UUID walletId;

    @Column(name = "merchant_id", nullable = false)
    private UUID merchantId;

    @Column(name = "terminal_id", nullable = false)
    private UUID terminalId;

    @Column(name = "amount", nullable = false)
    private long amount;

    @Column(name = "currency", nullable = false, length = 3)
    private String currency = "TZS";

    @Column(name = "balance_after")
    private Long balanceAfter;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private PmPurchaseStatus status = PmPurchaseStatus.APPROVED;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "items")
    private String items;

    @Column(name = "offline", nullable = false)
    private boolean offline;

    @Column(name = "nonce", length = 80)
    private String nonce;

    @Column(name = "request_timestamp")
    private Instant requestTimestamp;

    @Column(name = "idempotency_key", nullable = false, updatable = false, length = 120)
    private String idempotencyKey;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;
}
