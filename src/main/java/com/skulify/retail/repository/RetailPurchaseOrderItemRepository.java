package com.skulify.retail.repository;

import com.skulify.retail.entity.RetailPurchaseOrderItem;
import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface RetailPurchaseOrderItemRepository extends JpaRepository<RetailPurchaseOrderItem, UUID> {
    List<RetailPurchaseOrderItem> findByOrderIdOrderByCreatedAtAsc(UUID orderId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select i from RetailPurchaseOrderItem i where i.orderId = :orderId order by i.createdAt asc, i.id asc")
    List<RetailPurchaseOrderItem> lockByOrderId(@Param("orderId") UUID orderId);
}
