package com.sam.be.common.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import io.swagger.v3.oas.models.servers.Server;
import java.util.List;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.ViewControllerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class SwaggerConfig implements WebMvcConfigurer {

    /** Tên scheme dùng cho các endpoint tích hợp đối tác (/api/v1/integration/**). */
    public static final String API_KEY_SCHEME = "apiKeyAuth";

    @Override
    public void addViewControllers(ViewControllerRegistry registry) {
        registry.addRedirectViewController("/swagger", "/swagger-ui/index.html");
        registry.addRedirectViewController("/swagger-ui", "/swagger-ui/index.html");
        registry.addRedirectViewController("/docs", "/swagger-ui/index.html");
    }

    @Bean
    public OpenAPI customOpenAPI() {
        final String securitySchemeName = "bearerAuth";

        return new OpenAPI()
                .info(
                        new Info()
                                .title("SAM API")
                                .description("API documentation for SAM backend")
                                .version("1.0.0"))
                .servers(List.of(new Server().url("http://localhost:8080").description("Local")))
                .addSecurityItem(new SecurityRequirement().addList(securitySchemeName))
                .components(
                        new Components()
                                .addSecuritySchemes(
                                        securitySchemeName,
                                        new SecurityScheme()
                                                .name(securitySchemeName)
                                                .type(SecurityScheme.Type.HTTP)
                                                .scheme("bearer")
                                                .bearerFormat("JWT"))
                                .addSecuritySchemes(
                                        API_KEY_SCHEME,
                                        new SecurityScheme()
                                                .name("X-API-Key")
                                                .type(SecurityScheme.Type.APIKEY)
                                                .in(SecurityScheme.In.HEADER)
                                                .description(
                                                        "Khóa tích hợp cấp cho phần mềm đối tác."
                                                                + " Cấu hình phía server qua env"
                                                                + " INTEGRATION_API_KEY.")));
    }
}
