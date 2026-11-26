package org.example.school_wallet.academic;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AppSettingRepository extends JpaRepository<AppSetting, UUID> {

    Optional<AppSetting> findBySettingKey(String settingKey);
}
