package com.skulify.retail.repository;

import com.skulify.retail.entity.RetailSaleItem;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RetailSaleItemRepository extends JpaRepository<RetailSaleItem, UUID> {
    List<RetailSaleItem> findBySaleIdOrderByCreatedAtAsc(UUID saleId);
}
