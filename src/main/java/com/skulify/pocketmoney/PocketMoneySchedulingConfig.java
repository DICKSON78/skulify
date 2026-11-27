package com.skulify.pocketmoney;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/** Reconciliation must run independently of the optional school automation module. */
@Configuration
@EnableScheduling
public class PocketMoneySchedulingConfig {
}
