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
}
