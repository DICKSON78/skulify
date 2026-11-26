package org.example.school_wallet.config;

import org.example.school_wallet.common.tenant.TenantIdentifierResolver;
import org.hibernate.cfg.MultiTenancySettings;
import org.springframework.boot.hibernate.autoconfigure.HibernatePropertiesCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.domain.AuditorAware;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;

@Configuration
@EnableJpaAuditing(auditorAwareRef = "auditorAware")
public class JpaConfig {

    @Bean
    public HibernatePropertiesCustomizer tenantIdentifierCustomizer(TenantIdentifierResolver resolver) {
        return properties -> properties.put(MultiTenancySettings.MULTI_TENANT_IDENTIFIER_RESOLVER, resolver);
    }

    @Bean
    public AuditorAware<java.util.UUID> auditorAware() {
        return () -> org.example.school_wallet.security.CurrentUser.get()
                .map(org.example.school_wallet.security.AuthenticatedUser::id);
    }
}
