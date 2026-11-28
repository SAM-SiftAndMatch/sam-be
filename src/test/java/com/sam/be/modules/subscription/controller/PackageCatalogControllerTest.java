package com.sam.be.modules.subscription.controller;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sam.be.common.constant.enums.PackageType;
import com.sam.be.common.exception.GlobalExceptionHandler;
import com.sam.be.common.security.config.CorsProperties;
import com.sam.be.common.security.config.JwtAuthenticationConverter;
import com.sam.be.common.security.config.SecurityConfig;
import com.sam.be.common.security.jwt.CustomJwtDecoder;
import com.sam.be.modules.subscription.entity.ServicePackage;
import com.sam.be.modules.subscription.repository.ServicePackageRepository;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.mock.web.MockServletContext;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.support.AnnotationConfigWebApplicationContext;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;

class PackageCatalogControllerTest {

    @TestConfiguration
    @EnableWebMvc
    @Import(SecurityConfig.class)
    static class Config {
        @Bean
        CustomJwtDecoder decoder() {
            return mock(CustomJwtDecoder.class);
        }

        @Bean
        JwtAuthenticationConverter converter() {
            return mock(JwtAuthenticationConverter.class);
        }

        @Bean
        ObjectMapper mapper() {
            return new ObjectMapper().findAndRegisterModules();
        }

        @Bean
        CorsProperties cors() {
            var c = new CorsProperties();
            c.setAllowedOrigins(List.of("http://localhost:5173"));
            return c;
        }

        @Bean
        ServicePackageRepository packages() {
            return mock(ServicePackageRepository.class);
        }

        @Bean
        PackageCatalogController controller(ServicePackageRepository packages) {
            return new PackageCatalogController(packages);
        }

        @Bean
        GlobalExceptionHandler errors() {
            return new GlobalExceptionHandler();
        }
    }

    @Test
    void allowsAnonymousAccessAndFiltersByRole() throws Exception {
        try (var context = new AnnotationConfigWebApplicationContext()) {
            context.setServletContext(new MockServletContext());
            context.register(Config.class);
            context.refresh();
            var mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
            ServicePackageRepository packages = context.getBean(ServicePackageRepository.class);

            var clientPkg =
                    ServicePackage.builder()
                            .id(UUID.fromString("00000000-0000-0000-0000-000000000005"))
                            .name("Gói BUSINESS")
                            .type(PackageType.SUBSCRIPTION)
                            .price(new BigDecimal("199000"))
                            .isDynamicPrice(false)
                            .isActive(true)
                            .description("Client Pro")
                            .build();
            var freelancerPkg =
                    ServicePackage.builder()
                            .id(UUID.fromString("00000000-0000-0000-0000-000000000006"))
                            .name("Gói PRO DEV")
                            .type(PackageType.SUBSCRIPTION)
                            .price(new BigDecimal("149000"))
                            .isDynamicPrice(false)
                            .isActive(true)
                            .description("Freelancer Pro")
                            .build();
            var inactivePkg =
                    ServicePackage.builder()
                            .id(UUID.fromString("10000000-0000-0000-0000-000000000005"))
                            .name("Inactive")
                            .type(PackageType.SUBSCRIPTION)
                            .price(new BigDecimal("99000"))
                            .isDynamicPrice(false)
                            .isActive(false)
                            .build();

            when(packages.findAll()).thenReturn(List.of(clientPkg, freelancerPkg, inactivePkg));

            mvc.perform(get("/api/v1/packages").param("role", "CLIENT"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.result.length()").value(1))
                    .andExpect(jsonPath("$.result[0].name").value("Gói BUSINESS"));

            mvc.perform(get("/api/v1/packages").param("role", "FREELANCER"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.result.length()").value(1))
                    .andExpect(jsonPath("$.result[0].name").value("Gói PRO DEV"));
        }
    }
}
