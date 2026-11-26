package org.example.school_wallet.tenant;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TenantModuleRepository extends JpaRepository<TenantModule, UUID> {

    List<TenantModule> findAllByOrderByModuleCodeAsc();

    Optional<TenantModule> findByModuleCode(ModuleCode moduleCode);

    List<TenantModule> findByEnabledTrue();
}
