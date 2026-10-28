package com.sam.be.common.security.config;

import jakarta.validation.constraints.NotEmpty;
import java.util.List;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.validation.annotation.Validated;

@Configuration
@ConfigurationProperties(prefix = "app.cors")
@Validated
@Getter
@Setter
public class CorsProperties {
    @NotEmpty
    private List<String> allowedOrigins;
}
