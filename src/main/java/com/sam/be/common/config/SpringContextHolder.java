package com.sam.be.common.config;

import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

@Component
public class SpringContextHolder implements ApplicationContextAware {

    private static ApplicationContext applicationContext;

    @Override
    public void setApplicationContext(ApplicationContext applicationContext) {
        SpringContextHolder.applicationContext = applicationContext;
    }

    public static String getProperty(String key) {
        if (applicationContext == null) {
            return null;
        }

        Environment environment = applicationContext.getEnvironment();
        return environment.getProperty(key);
    }
}
