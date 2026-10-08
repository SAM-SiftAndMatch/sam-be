package com.sam.be.common.security.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;
import org.springframework.security.web.SecurityFilterChain;

/**
 * Bảo vệ Swagger UI bằng Form Login Authentication. Chỉ được kích hoạt khi biến môi trường
 * SWAGGER_AUTH_ENABLED=true (thông qua property swagger.auth-enabled). Khi không set hoặc =false
 * (ví dụ local dev), bean này không tồn tại và publicAuthChain trong SecurityConfig sẽ xử lý
 * Swagger URLs với permitAll như bình thường.
 */
@Configuration
@ConditionalOnProperty(name = "swagger.auth-enabled", havingValue = "true")
public class SwaggerSecurityConfig {

    @Value("${swagger.username:admin}")
    private String swaggerUsername;

    @Value("${swagger.password}")
    private String swaggerPassword;

    /**
     * InMemoryUserDetailsManager riêng cho Swagger auth, tách biệt khỏi hệ thống JWT chính. Dùng
     * qualifier để Spring không conflict với các UserDetailsService khác (nếu có).
     */
    @Bean("swaggerUserDetailsManager")
    public InMemoryUserDetailsManager swaggerUserDetailsManager(PasswordEncoder passwordEncoder) {
        UserDetails swaggerUser =
                User.builder()
                        .username(swaggerUsername)
                        .password(passwordEncoder.encode(swaggerPassword))
                        .roles("SWAGGER")
                        .build();
        return new InMemoryUserDetailsManager(swaggerUser);
    }

    /**
     * SecurityFilterChain ưu tiên cao nhất (@Order(-1)), chỉ match các URL Swagger/OpenAPI và
     * login/logout. Sử dụng giao diện Form Login chuẩn của Spring Security thay vì popup HTTP Basic
     * Auth.
     */
    @Bean
    @Order(-1)
    public SecurityFilterChain swaggerSecurityChain(
            HttpSecurity http, InMemoryUserDetailsManager swaggerUserDetailsManager)
            throws Exception {
        http.securityMatcher(
                        "/v3/api-docs",
                        "/v3/api-docs/**",
                        "/swagger-ui",
                        "/swagger-ui/",
                        "/swagger-ui/**",
                        "/swagger-ui.html",
                        "/swagger",
                        "/swagger/**",
                        "/docs",
                        "/docs/**",
                        "/swagger-resources",
                        "/swagger-resources/**",
                        "/login",
                        "/logout")
                .csrf(AbstractHttpConfigurer::disable)
                .authorizeHttpRequests(
                        auth ->
                                auth.requestMatchers("/login", "/logout")
                                        .permitAll()
                                        .anyRequest()
                                        .authenticated())
                .userDetailsService(swaggerUserDetailsManager)
                .formLogin(
                        form -> form.defaultSuccessUrl("/swagger-ui/index.html", false).permitAll())
                .logout(logout -> logout.logoutSuccessUrl("/login?logout").permitAll());

        return http.build();
    }
}
