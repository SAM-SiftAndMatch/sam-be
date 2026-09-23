package com.sam.be.common.security.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sam.be.common.exception.ErrorCode;
import com.sam.be.common.response.ApiResponse;
import com.sam.be.common.security.jwt.CustomJwtDecoder;
import java.time.Instant;
import java.util.List;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity
@RequiredArgsConstructor
@FieldDefaults(makeFinal = true, level = AccessLevel.PRIVATE)
public class SecurityConfig {

    CustomJwtDecoder customJwtDecoder;
    JwtAuthenticationConverter jwtAuthenticationConverter;
    ObjectMapper objectMapper;

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder(10);
    }

    /**
     * Cấu hình CORS tập trung cho toàn bộ ứng dụng. Cho phép các client Web (React/Next.js) gọi API
     * với credentials (cookies/authorization headers).
     */
    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();
        configuration.setAllowedOriginPatterns(List.of("*"));
        configuration.setAllowedMethods(
                List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        configuration.setAllowedHeaders(List.of("*"));
        configuration.setAllowCredentials(true);
        configuration.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }

    /**
     * Public Chain (@Order(0)): Cho phép truy cập không cần xác thực JWT. Chỉ liệt kê chính xác các
     * endpoint public của auth, health check và tài liệu Swagger. Các endpoint auth cần xác thực
     * (như /logout, /me) sẽ tự động rơi vào apiChain.
     */
    @Bean
    @Order(0)
    public SecurityFilterChain publicAuthChain(HttpSecurity http) throws Exception {
        http.securityMatcher(
                        "/api/v1/auth/login",
                        "/api/v1/auth/register",
                        "/api/v1/auth/refresh",
                        "/api/v1/auth/forgot-password/**",
                        "/api/v1/auth/reset-password/**",
                        "/api/v1/internal/healthz",
                        "/actuator/**",
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
                        "/error")
                .cors(Customizer.withDefaults())
                .csrf(AbstractHttpConfigurer::disable)
                .authorizeHttpRequests(auth -> auth.anyRequest().permitAll())
                .oauth2ResourceServer(AbstractHttpConfigurer::disable);

        return http.build();
    }

    /**
     * API Chain (@Order(1)): Yêu cầu xác thực qua Bearer JWT. Bảo vệ toàn bộ /api/**, sử dụng
     * CustomJwtDecoder và JwtAuthenticationConverter.
     */
    @Bean
    @Order(1)
    public SecurityFilterChain apiChain(HttpSecurity http) throws Exception {
        http.securityMatcher("/api/**")
                .cors(Customizer.withDefaults())
                .csrf(AbstractHttpConfigurer::disable)
                .authorizeHttpRequests(
                        auth ->
                                auth.requestMatchers(HttpMethod.OPTIONS, "/**")
                                        .permitAll()
                                        .anyRequest()
                                        .authenticated())
                .exceptionHandling(
                        ex ->
                                ex.authenticationEntryPoint(
                                                (req, res, exAuth) -> {
                                                    res.setStatus(HttpStatus.UNAUTHORIZED.value());
                                                    res.setContentType(
                                                            "application/json;charset=UTF-8");
                                                    objectMapper.writeValue(
                                                            res.getOutputStream(),
                                                            ApiResponse.builder()
                                                                    .code(
                                                                            ErrorCode
                                                                                    .UNAUTHENTICATED
                                                                                    .getCode())
                                                                    .message(
                                                                            ErrorCode
                                                                                    .UNAUTHENTICATED
                                                                                    .getDefaultMessage())
                                                                    .path(req.getRequestURI())
                                                                    .timestamp(Instant.now())
                                                                    .build());
                                                })
                                        .accessDeniedHandler(
                                                (req, res, exDenied) -> {
                                                    res.setStatus(HttpStatus.FORBIDDEN.value());
                                                    res.setContentType(
                                                            "application/json;charset=UTF-8");
                                                    objectMapper.writeValue(
                                                            res.getOutputStream(),
                                                            ApiResponse.builder()
                                                                    .code(
                                                                            ErrorCode
                                                                                    .FORBIDDEN_ACTION
                                                                                    .getCode())
                                                                    .message(
                                                                            ErrorCode
                                                                                    .FORBIDDEN_ACTION
                                                                                    .getDefaultMessage())
                                                                    .path(req.getRequestURI())
                                                                    .timestamp(Instant.now())
                                                                    .build());
                                                }))
                .oauth2ResourceServer(
                        oauth2 ->
                                oauth2.jwt(
                                        jwt ->
                                                jwt.decoder(customJwtDecoder)
                                                        .jwtAuthenticationConverter(
                                                                jwtAuthenticationConverter)));

        return http.build();
    }

    /**
     * Fallback Chain (@Order(2)): Chặn toàn bộ các request không khớp các chain trên (Fail-Closed).
     * Đảm bảo không có endpoint nào vô tình bị mở công khai do thiếu tiền tố /api/ hay routing lỗi.
     */
    @Bean
    @Order(2)
    public SecurityFilterChain fallbackChain(HttpSecurity http) throws Exception {
        http.csrf(AbstractHttpConfigurer::disable)
                .cors(Customizer.withDefaults())
                .authorizeHttpRequests(auth -> auth.anyRequest().denyAll());
        return http.build();
    }
}
