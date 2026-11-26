package org.example.school_wallet.operations;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/** Opt-in background automation: set app.automation.enabled=true (AUTOMATION_ENABLED=true). */
@Configuration
@EnableScheduling
@ConditionalOnProperty(prefix = "app.automation", name = "enabled", havingValue = "true")
public class AutomationConfig {
}
