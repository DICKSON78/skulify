package org.example.school_wallet.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * Binds app.* from application.yml / environment variables.
 * No manual bean: hardcoded defaults here used to override production secrets.
 */
@Configuration
@EnableConfigurationProperties(AppProperties.class)
public class AppPropertiesConfig {
}
