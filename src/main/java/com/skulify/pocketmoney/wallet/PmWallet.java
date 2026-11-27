package com.skulify.pocketmoney.wallet;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.Instant;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.Check;

@Entity
@Table(
        name = "pm_wallets",
        uniqueConstraints = @UniqueConstraint(
                name = "pm_wallets_student_key",
                columnNames = "student_id"))
@Check(name = "pm_wallets_balance_check", constraints = "balance >= 0")
@Getter
@Setter
@NoArgsConstructor
public class PmWallet {

    @Id
    @Column(name = "id", nullable = false)
    private UUID id;

    @Column(name = "tenant_id", nullable = false, updatable = false)
    private UUID tenantId;

    @Column(name = "student_id", nullable = false, updatable = false)
    private UUID studentId;

    @Column(name = "account_code", nullable = false, length = 40)
    private String accountCode = "STUDENT_WALLET";

    @Column(name = "currency", nullable = false, length = 3)
    private String currency = "TZS";

    @Column(name = "balance", nullable = false)
    private long balance;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 15)
    private WalletStatus status = WalletStatus.ACTIVE;

    @Column(name = "daily_limit", nullable = false)
    private long dailyLimit;

    @Column(name = "weekly_limit", nullable = false)
    private long weeklyLimit;

    @Column(name = "monthly_limit", nullable = false)
    private long monthlyLimit;

    @Column(name = "per_txn_limit", nullable = false)
    private long perTxnLimit;

    /** Kept for schema compatibility; concurrency is controlled by the pessimistic row lock. */
    @Column(name = "version", nullable = false)
    private long version;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public PmWallet(UUID id, UUID tenantId, UUID studentId, long dailyLimit, long perTxnLimit) {
        this.id = id;
        this.tenantId = tenantId;
        this.studentId = studentId;
        this.dailyLimit = dailyLimit;
        this.perTxnLimit = perTxnLimit;
        Instant now = Instant.now();
        this.createdAt = now;
        this.updatedAt = now;
    }
}
