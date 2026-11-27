package com.skulify.pocketmoney.topup;

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

/** Parent-funded top-up request. Ledger rows remain the source of truth for money. */
@Entity
@Table(name = "pm_topups")
@Check(name = "pm_topups_amount_check", constraints = "gross_amount > 0 AND fee_amount >= 0 AND net_amount > 0")
@Check(name = "pm_topups_status_check", constraints = "status IN ('PENDING', 'SUCCESS', 'FAILED', 'REVERSED')")
@Getter
@Setter
@NoArgsConstructor
public class PmTopUp {

    public enum Status { PENDING, SUCCESS, FAILED, REVERSED }

    @Id
    @Column(name = "id", nullable = false)
    private UUID id;

    @Column(name = "tenant_id", nullable = false, updatable = false)
    private UUID tenantId;

    @Column(name = "parent_user_id", nullable = false, updatable = false)
    private UUID parentUserId;

    @Column(name = "wallet_id", nullable = false, updatable = false)
    private UUID walletId;

    @Column(name = "gross_amount", nullable = false)
    private long grossAmount;

    @Column(name = "fee_amount", nullable = false)
    private long feeAmount;

    @Column(name = "net_amount", nullable = false)
    private long netAmount;

    @Column(name = "currency", nullable = false, length = 3)
    private String currency = "TZS";

    @Column(name = "provider", nullable = false, length = 30)
    private String provider;

    @Column(name = "provider_ref", length = 100)
    private String providerReference;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 15)
    private Status status = Status.PENDING;

    @Column(name = "idempotency_key", nullable = false, updatable = false, length = 120)
    private String idempotencyKey;

    @Column(name = "created_by", updatable = false)
    private UUID createdBy;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
}
