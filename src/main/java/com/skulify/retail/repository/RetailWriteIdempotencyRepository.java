package com.skulify.retail.repository;

import com.skulify.retail.entity.RetailWriteIdempotency;
import com.skulify.retail.entity.RetailWriteIdempotencyId;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface RetailWriteIdempotencyRepository
        extends JpaRepository<RetailWriteIdempotency, RetailWriteIdempotencyId> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select w from RetailWriteIdempotency w
            where w.id.tenantId = :tenantId
              and w.id.scope = :scope
              and w.id.idempotencyKey = :idempotencyKey
            """)
    Optional<RetailWriteIdempotency> findForWrite(
            @Param("tenantId") UUID tenantId,
            @Param("scope") String scope,
            @Param("idempotencyKey") String idempotencyKey);
}
