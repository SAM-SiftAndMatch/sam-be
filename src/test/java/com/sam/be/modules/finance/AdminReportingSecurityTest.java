package com.sam.be.modules.finance;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.*;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.core.context.SecurityContextHolder;

class AdminReportingSecurityTest {
    @Configuration
    @EnableMethodSecurity
    static class Config {
        @Bean
        AdminReportingService reporting() {
            return mock(AdminReportingService.class);
        }

        @Bean
        AdminReportingController controller(AdminReportingService reporting) {
            return new AdminReportingController(reporting);
        }
    }

    @Test
    void reportingEndpointsRequireAdminAtBackendEvenWhenCalledOutsideTheDashboard() {
        try (var context = new AnnotationConfigApplicationContext(Config.class)) {
            var controller = context.getBean(AdminReportingController.class);
            var service = context.getBean(AdminReportingService.class);
            assertThrows(AuthenticationCredentialsNotFoundException.class, controller::dashboard);
            for (String role : new String[] {"CLIENT", "FREELANCER"}) {
                SecurityContextHolder.getContext()
                        .setAuthentication(
                                new UsernamePasswordAuthenticationToken(
                                        "user",
                                        "unused",
                                        AuthorityUtils.createAuthorityList("ROLE_" + role)));
                assertThrows(AccessDeniedException.class, controller::dashboard);
                assertThrows(
                        AccessDeniedException.class,
                        () -> controller.revenue(null, null, "", null, "", 0));
                assertThrows(AccessDeniedException.class, () -> controller.users("", "", "", 0));
            }
            verifyNoInteractions(service);
            SecurityContextHolder.getContext()
                    .setAuthentication(
                            new UsernamePasswordAuthenticationToken(
                                    "admin",
                                    "unused",
                                    AuthorityUtils.createAuthorityList("ROLE_ADMIN")));
            assertDoesNotThrow(controller::dashboard);
            assertDoesNotThrow(() -> controller.revenue(null, null, "", null, "", 0));
            assertDoesNotThrow(() -> controller.users("CLIENT", "An", "ACTIVE", 0));
            verify(service).overview();
            verify(service).revenue(null, null, "", null, "", 0);
            verify(service).users("CLIENT", "An", "ACTIVE", 0);
        } finally {
            SecurityContextHolder.clearContext();
        }
    }
}
