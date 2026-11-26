package org.example.school_wallet.tenant;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface TenantRepository extends JpaRepository<Tenant, UUID> {

    Optional<Tenant> findByCodeIgnoreCase(String code);

    boolean existsByCodeIgnoreCase(String code);

    @Query("""
            select t from Tenant t
            where t.id <> :systemId
              and (:q is null
                   or lower(t.name) like lower(concat('%', :q, '%'))
                   or lower(t.code) like lower(concat('%', :q, '%')))
              and (:status is null or t.status = :status)
            order by t.name
            """)
    Page<Tenant> search(
            @Param("systemId") UUID systemId,
            @Param("q") String q,
            @Param("status") TenantStatus status,
            Pageable pageable);

    List<Tenant> findByIdNot(UUID systemId);
}
