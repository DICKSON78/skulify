package org.example.school_wallet.operations;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.example.school_wallet.academic.StudentLinked;
import org.example.school_wallet.common.domain.TenantOwnedEntity;

/** Optional per-category limits set by a parent, e.g. SNACKS or TRANSPORT. */
@Entity
@Table(name = "spending_limits")
@Getter
@Setter
@NoArgsConstructor
public class SpendingLimit extends TenantOwnedEntity implements StudentLinked {

    @Column(name = "card_id", nullable = false)
    private UUID cardId;

    @Column(name = "student_id", nullable = false)
    private UUID studentId;

    @Column(nullable = false, length = 40)
    private String category;

    @Column(name = "daily_limit", precision = 14, scale = 2)
    private BigDecimal dailyLimit;

    @Column(name = "weekly_limit", precision = 14, scale = 2)
    private BigDecimal weeklyLimit;

    @Column(name = "monthly_limit", precision = 14, scale = 2)
    private BigDecimal monthlyLimit;

    @Column(nullable = false)
    private boolean active = true;
}
