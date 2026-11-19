package com.sam.be.modules.auth.controller;

import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sam.be.common.security.config.CorsProperties;
import com.sam.be.common.security.config.JwtAuthenticationConverter;
import com.sam.be.common.security.config.SecurityConfig;
import com.sam.be.common.security.jwt.CustomJwtDecoder;
import com.sam.be.common.security.jwt.JwtProperties;
import com.sam.be.modules.auth.config.GoogleOAuthProperties;
import com.sam.be.modules.auth.service.AuthCookieService;
import com.sam.be.modules.auth.service.GoogleOAuthService;
import jakarta.servlet.Filter;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.*;
import org.springframework.mock.web.MockServletContext;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.support.AnnotationConfigWebApplicationContext;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;

class PasswordSetupSecurityTest {
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
            var properties = new CorsProperties();
            properties.setAllowedOrigins(List.of("https://www.sam.pro.vn"));
            return properties;
        }

        @Bean
        GoogleOAuthService google() {
            return mock(GoogleOAuthService.class);
        }

        @Bean
        PasswordSetupController passwordSetup(GoogleOAuthService google) {
            return new PasswordSetupController(
                    google,
                    new GoogleOAuthProperties(),
                    new AuthCookieService(new JwtProperties()));
        }
    }

    @Test
    void everyNewEndpointRejectsAnonymousRequestsEvenWithAProofCookie() throws Exception {
        try (var context = new AnnotationConfigWebApplicationContext()) {
            context.setServletContext(new MockServletContext());
            context.register(Config.class);
            context.refresh();
            var mvc =
                    MockMvcBuilders.webAppContextSetup(context)
                            .addFilters(context.getBean("springSecurityFilterChain", Filter.class))
                            .build();
            var proof =
                    new jakarta.servlet.http.Cookie(
                            AuthCookieService.PASSWORD_SETUP_COOKIE, "proof");
            mvc.perform(get("/api/v1/auth/password/status").cookie(proof))
                    .andExpect(status().isUnauthorized());
            mvc.perform(post("/api/v1/auth/password/setup/start").cookie(proof))
                    .andExpect(status().isUnauthorized());
            mvc.perform(
                            post("/api/v1/auth/password/setup")
                                    .cookie(proof)
                                    .contentType("application/json")
                                    .content(
                                            "{\"newPassword\":\"new-password\",\"confirmPassword\":\"new-password\"}"))
                    .andExpect(status().isUnauthorized());
            verifyNoInteractions(context.getBean(GoogleOAuthService.class));
        }
    }
}
