package com.sam.be.modules.feedback;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sam.be.common.exception.GlobalExceptionHandler;
import com.sam.be.common.security.config.CorsProperties;
import com.sam.be.common.security.config.JwtAuthenticationConverter;
import com.sam.be.common.security.config.SecurityConfig;
import com.sam.be.common.security.jwt.CustomJwtDecoder;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.*;
import org.springframework.mock.web.MockServletContext;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.util.AopTestUtils;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.support.AnnotationConfigWebApplicationContext;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;

class FeedbackSecurityTest {
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
            c.setAllowedOrigins(List.of("https://www.sam.pro.vn"));
            return c;
        }

        @Bean
        FeedbackService service() {
            return mock(FeedbackService.class);
        }

        @Bean
        FeedbackController userFeedback(FeedbackService service) {
            return new FeedbackController(service);
        }

        @Bean
        AdminFeedbackController adminFeedback(FeedbackService service) {
            return new AdminFeedbackController(service);
        }

        @Bean
        GlobalExceptionHandler errors() {
            return new GlobalExceptionHandler();
        }
    }

    @Test
    void endpointsEnforceRolesOwnershipAndValidationAtTheHttpBoundary() throws Exception {
        try (var context = new AnnotationConfigWebApplicationContext()) {
            context.setServletContext(new MockServletContext());
            context.register(Config.class);
            context.refresh();
            var mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
            FeedbackService service =
                    AopTestUtils.getUltimateTargetObject(context.getBean(FeedbackService.class));
            UUID userId = UUID.randomUUID(), itemId = UUID.randomUUID();
            String input =
                    "{\"rating\":5,\"comment\":\"Tốt\",\"requestKey\":\""
                            + UUID.randomUUID()
                            + "\"}";
            mvc.perform(get("/api/v1/feedback/mine")).andExpect(status().isUnauthorized());
            mvc.perform(post("/api/v1/feedback").contentType("application/json").content(input))
                    .andExpect(status().isUnauthorized());
            mvc.perform(get("/api/v1/admin/feedback")).andExpect(status().isUnauthorized());
            for (String role : List.of("CLIENT", "FREELANCER")) {
                var auth =
                        jwt().jwt(token -> token.claim("user_id", userId.toString()))
                                .authorities(new SimpleGrantedAuthority("ROLE_" + role));
                mvc.perform(get("/api/v1/admin/feedback").with(auth))
                        .andExpect(status().isForbidden());
                mvc.perform(get("/api/v1/admin/feedback/summary").with(auth))
                        .andExpect(status().isForbidden());
                mvc.perform(get("/api/v1/admin/feedback/" + itemId).with(auth))
                        .andExpect(status().isForbidden());
                mvc.perform(
                                patch("/api/v1/admin/feedback/" + itemId)
                                        .with(auth)
                                        .contentType("application/json")
                                        .content("{\"status\":\"RESOLVED\",\"version\":0}"))
                        .andExpect(status().isForbidden());
            }
            verifyNoInteractions(service);
            var client =
                    jwt().jwt(token -> token.claim("user_id", userId.toString()))
                            .authorities(new SimpleGrantedAuthority("ROLE_CLIENT"));
            var admin =
                    jwt().jwt(token -> token.claim("user_id", userId.toString()))
                            .authorities(new SimpleGrantedAuthority("ROLE_ADMIN"));
            mvc.perform(
                            post("/api/v1/feedback")
                                    .with(admin)
                                    .contentType("application/json")
                                    .content(input))
                    .andExpect(status().isForbidden());
            mvc.perform(
                            post("/api/v1/feedback")
                                    .with(client)
                                    .contentType("application/json")
                                    .content(input.replace("\"rating\":5", "\"rating\":6")))
                    .andExpect(status().isBadRequest());
            mvc.perform(
                            post("/api/v1/feedback")
                                    .with(client)
                                    .contentType("application/json")
                                    .content(input))
                    .andExpect(status().isOk());
            verify(service).create(eq(userId), any());
            mvc.perform(
                            get("/api/v1/feedback/mine")
                                    .param("userId", UUID.randomUUID().toString())
                                    .with(client))
                    .andExpect(status().isOk());
            verify(service).mine(userId, 0, 10);
            mvc.perform(get("/api/v1/admin/feedback").with(admin)).andExpect(status().isOk());
            verify(service).list(null, null, "", 0, 20);
            mvc.perform(
                            patch("/api/v1/admin/feedback/" + itemId)
                                    .with(admin)
                                    .contentType("application/json")
                                    .content("{\"status\":\"RESOLVED\",\"version\":0}"))
                    .andExpect(status().isOk());
            verify(service).update(eq(userId), eq(itemId), any());
        }
    }
}
