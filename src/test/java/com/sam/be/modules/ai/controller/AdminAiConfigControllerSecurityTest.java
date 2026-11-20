package com.sam.be.modules.ai.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sam.be.common.exception.GlobalExceptionHandler;
import com.sam.be.common.security.config.CorsProperties;
import com.sam.be.common.security.config.JwtAuthenticationConverter;
import com.sam.be.common.security.config.SecurityConfig;
import com.sam.be.common.security.jwt.CustomJwtDecoder;
import com.sam.be.modules.ai.dto.AdminAiDtos.*;
import com.sam.be.modules.ai.entity.AiTestStatus;
import com.sam.be.modules.ai.service.AdminAiConfigService;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.*;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockServletContext;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.util.AopTestUtils;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.support.AnnotationConfigWebApplicationContext;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;

class AdminAiConfigControllerSecurityTest {

    @TestConfiguration
    @EnableWebMvc
    @EnableAspectJAutoProxy(proxyTargetClass = true)
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
        AdminAiConfigService service() {
            return mock(AdminAiConfigService.class);
        }

        @Bean
        AdminAiConfigController controller(AdminAiConfigService service) {
            return new AdminAiConfigController(service);
        }

        @Bean
        GlobalExceptionHandler errors() {
            return new GlobalExceptionHandler();
        }
    }

    @Test
    void enforcesAdminRoleAndValidation() throws Exception {
        try (var context = new AnnotationConfigWebApplicationContext()) {
            context.setServletContext(new MockServletContext());
            context.register(Config.class);
            context.refresh();
            var mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
            AdminAiConfigService service =
                    AopTestUtils.getUltimateTargetObject(
                            context.getBean(AdminAiConfigService.class));

            when(service.runtimeStatus())
                    .thenReturn(new RuntimeStatus(true, "AIza••••1234", "gemini-2.5-flash", 1, 1));
            when(service.listKeys())
                    .thenReturn(
                            List.of(
                                    new AiKeyResponse(
                                            1L,
                                            "Primary",
                                            "AIza••••9999",
                                            true,
                                            10,
                                            0,
                                            Instant.now(),
                                            AiTestStatus.OK,
                                            "OK",
                                            Instant.now(),
                                            Instant.now())));

            // Unauthenticated -> 401
            mvc.perform(get("/api/v1/admin/ai/runtime")).andExpect(status().isUnauthorized());

            // Non-admin -> 403
            mvc.perform(
                            get("/api/v1/admin/ai/keys")
                                    .with(
                                            jwt().authorities(
                                                            new SimpleGrantedAuthority(
                                                                    "ROLE_CLIENT"))))
                    .andExpect(status().isForbidden());

            // Admin -> 200 and masked response only
            mvc.perform(
                            get("/api/v1/admin/ai/keys")
                                    .with(
                                            jwt().authorities(
                                                            new SimpleGrantedAuthority(
                                                                    "ROLE_ADMIN"))))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.result[0].keyMasked").value("AIza••••9999"));

            // Invalid model name -> 400 validation error
            mvc.perform(
                            post("/api/v1/admin/ai/models")
                                    .with(
                                            jwt().authorities(
                                                            new SimpleGrantedAuthority(
                                                                    "ROLE_ADMIN")))
                                    .contentType(MediaType.APPLICATION_JSON)
                                    .content("{\"modelName\":\"bad model name!\",\"priority\":10}"))
                    .andExpect(status().isBadRequest());

            verify(service, never()).createModel(any());
        }
    }
}
