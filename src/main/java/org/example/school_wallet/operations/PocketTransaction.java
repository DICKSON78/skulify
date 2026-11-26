package org.example.school_wallet.operations;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.math.BigDecimal;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.example.school_wallet.academic.StudentLinked;
import org.example.school_wallet.common.domain.TenantOwnedEntity;

@Entity
@Table(
        name = "pocket_transactions",
        uniqueConstraints = @UniqueConstraint(columnNames = {"tenant_id", "provider_reference"}))
@Getter
@Setter
@NoArgsConstructor
public class PocketTransaction extends TenantOwnedEntity implements StudentLinked {

    @Column(nullable = false, length = 40)
    private String reference;

    @Column(name = "card_id", nullable = false)
    private UUID cardId;

    @Column(name = "student_id", nullable = false)
    private UUID studentId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 15)
    private Type type;

    @Column(nullable = false, precision = 14, scale = 2)
    private BigDecimal amount;

    @Column(length = 40)
    private String category;

    @Column(name = "balance_after", precision = 14, scale = 2)
    private BigDecimal balanceAfter;

    @Column(name = "terminal_id")
    private UUID terminalId;

    @Column(name = "store_item_id")
    private UUID storeItemId;

    @Column(nullable = false)
    private int quantity = 1;

    @Column(name = "provider_reference", length = 80)
    private String providerReference;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 15)
    private Status status = Status.POSTED;

    @Column(length = 500)
    private String description;

    public enum Type { TOPUP, PURCHASE, REFUND, ADJUSTMENT, CASH_OUT }

    public enum Status { PENDING, POSTED, REVERSED, FAILED }
}
