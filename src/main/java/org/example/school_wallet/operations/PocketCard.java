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

/** School wallet card. cardUid is the NFC/RFID/QR identifier read by the POS terminal. */
@Entity
@Table(name = "pocket_cards", uniqueConstraints = @UniqueConstraint(columnNames = {"tenant_id", "card_uid"}))
@Getter
@Setter
@NoArgsConstructor
public class PocketCard extends TenantOwnedEntity implements StudentLinked {

    @Column(name = "card_uid", nullable = false, length = 80)
    private String cardUid;

    @Column(name = "student_id", nullable = false)
    private UUID studentId;

    @Column(nullable = false, precision = 14, scale = 2)
    private BigDecimal balance = BigDecimal.ZERO;

    @Column(name = "daily_limit", precision = 14, scale = 2)
    private BigDecimal dailyLimit = BigDecimal.ZERO;

    @Column(name = "weekly_limit", precision = 14, scale = 2)
    private BigDecimal weeklyLimit = BigDecimal.ZERO;

    @Column(name = "monthly_limit", precision = 14, scale = 2)
    private BigDecimal monthlyLimit = BigDecimal.ZERO;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 15)
    private Status status = Status.ACTIVE;

    @Column(name = "issued_at", length = 20)
    private String issuedAt;

    public enum Status { ACTIVE, FROZEN, LOST, CLOSED }
}
