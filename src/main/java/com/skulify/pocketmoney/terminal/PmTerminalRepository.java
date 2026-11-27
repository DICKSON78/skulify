package com.skulify.pocketmoney.terminal;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PmTerminalRepository extends JpaRepository<PmTerminal, UUID> {

    Optional<PmTerminal> findByTenantIdAndCode(UUID tenantId, String code);

    List<PmTerminal> findByTenantIdOrderByCreatedAtDesc(UUID tenantId);
}
