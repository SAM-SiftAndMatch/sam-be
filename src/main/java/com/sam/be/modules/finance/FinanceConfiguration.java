package com.sam.be.modules.finance;

import java.time.Clock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class FinanceConfiguration {
    @Bean("financeClock")
    public Clock financeClock() {
        return Clock.systemUTC();
    }

    @Bean("taskScheduler")
    public org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler taskScheduler() {
        var scheduler = new org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler();
        scheduler.setPoolSize(3);
        scheduler.setThreadNamePrefix("sam-maintenance-");
        return scheduler;
    }

    @Bean("recommendationScheduler")
    public org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler
            recommendationScheduler() {
        var scheduler = new org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler();
        scheduler.setPoolSize(1);
        scheduler.setThreadNamePrefix("sam-recommendation-");
        return scheduler;
    }
}
