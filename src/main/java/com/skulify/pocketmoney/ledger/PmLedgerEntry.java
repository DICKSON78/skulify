package com.skulify.pocketmoney.ledger;

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
import org.hibernate.annotations.Immutable;

@Entity
@Table(name = "pm_ledger_entries")
@Immutable
@Check(name = "pm_ledger_amount_check", constraints = "amount > 0")
@Getter
@Setter
@NoArgsConstructor
public class PmLedgerEntry {

    @Id
    @Column(name = "id", nullable = false)
    private UUID id;

    @Column(name = "tenant_id", nullable = false, updatable = false)
    private UUID tenantId;

    @Column(name = "wallet_id", updatable = false)
    private UUID walletId;

    @Column(name = "reference_id", nullable = false, updatable = false)
    private UUID referenceId;

    @Enumerated(EnumType.STRING)
    @Column(name = "type", nullable = false, length = 30, updatable = false)
    private LedgerEntryType type;

    @Enumerated(EnumType.STRING)
    @Column(name = "direction", nullable = false, length = 6, updatable = false)
    private Direction direction;

    @Column(name = "amount", nullable = false, updatable = false)
    private long amount;

    @Column(name = "currency", nullable = false, length = 3, updatable = false)
    private String currency = "TZS";

    @Column(name = "balance_after", updatable = false)
    private Long balanceAfter;

    @Column(name = "account_code", nullable = false, length = 40, updatable = false)
    private String accountCode;

    @Column(name = "counter_account_code", length = 40, updatable = false)
    private String counterAccountCode;

    @Column(name = "idempotency_key", nullable = false, length = 120, updatable = false)
    private String idempotencyKey;

    @Column(name = "created_by", updatable = false)
    private UUID createdBy;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;
}
