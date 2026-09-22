package com.sam.be.modules.auth.service.impl;

import com.sam.be.common.constant.enums.UserRole;
import com.sam.be.common.exception.ApiException;
import com.sam.be.common.exception.ErrorCode;
import com.sam.be.common.security.jwt.JwtProperties;
import com.sam.be.common.security.jwt.JwtTokenProvider;
import com.sam.be.infrastructure.cache.service.SessionCacheService;
import com.sam.be.modules.auth.dto.request.LoginRequest;
import com.sam.be.modules.auth.dto.request.RegisterRequest;
import com.sam.be.modules.auth.dto.response.AuthResponse;
import com.sam.be.modules.auth.entity.Session;
import com.sam.be.modules.auth.repository.SessionRepository;
import com.sam.be.modules.auth.service.AuthService;
import com.sam.be.modules.user.entity.ClientProfile;
import com.sam.be.modules.user.entity.FreelancerProfile;
import com.sam.be.modules.user.entity.User;
import com.sam.be.modules.user.repository.ClientProfileRepository;
import com.sam.be.modules.user.repository.FreelancerProfileRepository;
import com.sam.be.modules.user.repository.UserRepository;
import jakarta.servlet.http.HttpServletRequest;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
@FieldDefaults(makeFinal = true, level = AccessLevel.PRIVATE)
public class AuthServiceImpl implements AuthService {

    UserRepository userRepository;
    SessionRepository sessionRepository;
    FreelancerProfileRepository freelancerProfileRepository;
    ClientProfileRepository clientProfileRepository;
    PasswordEncoder passwordEncoder;
    JwtTokenProvider jwtTokenProvider;
    JwtProperties jwtProperties;
    SessionCacheService sessionCacheService;

    @Override
    @Transactional
    public AuthResponse login(LoginRequest request, HttpServletRequest httpRequest) {
        String email = request.email().toLowerCase().trim();
        User user =
                userRepository
                        .findByEmail(email)
                        .orElseThrow(() -> new ApiException(ErrorCode.INVALID_CREDENTIALS));

        if (!passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            throw new ApiException(ErrorCode.INVALID_CREDENTIALS);
        }

        if (!Boolean.TRUE.equals(user.getIsActive())) {
            throw new ApiException(ErrorCode.ACCOUNT_DISABLED);
        }

        return createSessionAndGenerateTokens(user, httpRequest);
    }

    @Override
    @Transactional
    public AuthResponse register(RegisterRequest request, HttpServletRequest httpRequest) {
        if (request.accountType() == UserRole.ADMIN) {
            throw new ApiException(ErrorCode.FORBIDDEN_ACTION, "Admin registration is not allowed");
        }

        String email = request.email().toLowerCase().trim();
        if (userRepository.existsByEmail(email)) {
            throw new ApiException(ErrorCode.EMAIL_ALREADY_EXISTS);
        }

        User user =
                User.builder()
                        .email(email)
                        .passwordHash(passwordEncoder.encode(request.password()))
                        .fullName(request.fullName().trim())
                        .role(request.accountType())
                        .isActive(true)
                        .build();
        user = userRepository.save(user);

        if (request.accountType() == UserRole.FREELANCER) {
            FreelancerProfile profile = FreelancerProfile.builder().user(user).build();
            freelancerProfileRepository.save(profile);
        } else if (request.accountType() == UserRole.CLIENT) {
            ClientProfile profile = ClientProfile.builder().user(user).build();
            clientProfileRepository.save(profile);
        }

        return createSessionAndGenerateTokens(user, httpRequest);
    }

    @Override
    @Transactional
    public AuthResponse refresh(String refreshToken, HttpServletRequest httpRequest) {
        if (refreshToken == null || refreshToken.isBlank()) {
            throw new ApiException(ErrorCode.INVALID_REFRESH_TOKEN);
        }

        Session oldSession =
                sessionRepository
                        .findByRefreshTokenAndIsRevokedFalse(refreshToken)
                        .orElseThrow(() -> new ApiException(ErrorCode.INVALID_REFRESH_TOKEN));

        if (oldSession.getExpiresAt().isBefore(LocalDateTime.now())) {
            oldSession.setIsRevoked(true);
            sessionRepository.save(oldSession);
            throw new ApiException(ErrorCode.SESSION_EXPIRED);
        }

        User user = oldSession.getUser();
        if (!Boolean.TRUE.equals(user.getIsActive())) {
            throw new ApiException(ErrorCode.ACCOUNT_DISABLED);
        }

        // 1. Thu hồi session cũ (Refresh Token Rotation)
        oldSession.setIsRevoked(true);
        sessionRepository.save(oldSession);

        sessionCacheService.markRevoked(oldSession.getId(), Duration.ofDays(7));
        sessionCacheService.clearActive(oldSession.getId());
        sessionCacheService.clearAuthz(oldSession.getId());

        // 2. Tạo session mới
        return createSessionAndGenerateTokens(user, httpRequest);
    }

    @Override
    @Transactional
    public void logout(UUID sessionId) {
        if (sessionId == null) {
            return;
        }

        sessionRepository
                .findById(sessionId)
                .ifPresent(
                        session -> {
                            session.setIsRevoked(true);
                            sessionRepository.save(session);
                        });

        Duration maxTtl = Duration.ofSeconds(jwtProperties.getAccessExpiration());
        sessionCacheService.markRevoked(sessionId, maxTtl);
        sessionCacheService.clearActive(sessionId);
        sessionCacheService.clearAuthz(sessionId);
    }

    private AuthResponse createSessionAndGenerateTokens(User user, HttpServletRequest httpRequest) {
        String clientIp = extractClientIp(httpRequest);
        String userAgent = httpRequest != null ? httpRequest.getHeader("User-Agent") : null;
        LocalDateTime exp = LocalDateTime.now().plusSeconds(jwtProperties.getRefreshExpiration());
        String refreshToken = jwtTokenProvider.generateRefreshToken();

        Session session =
                Session.builder()
                        .user(user)
                        .refreshToken(refreshToken)
                        .userAgent(userAgent)
                        .ipAddress(clientIp)
                        .isRevoked(false)
                        .expiresAt(exp)
                        .build();
        session = sessionRepository.save(session);

        String accessToken =
                jwtTokenProvider.generateAccessToken(
                        user.getId(), user.getEmail(), session.getId(), user.getRole().name());

        sessionCacheService.markActive(session.getId(), Duration.ofSeconds(60));

        return AuthResponse.builder()
                .accessToken(accessToken)
                .refreshToken(refreshToken)
                .tokenType("Bearer")
                .expiresIn(jwtProperties.getAccessExpiration())
                .userId(user.getId())
                .email(user.getEmail())
                .fullName(user.getFullName())
                .role(user.getRole().name())
                .build();
    }

    private String extractClientIp(HttpServletRequest request) {
        if (request == null) {
            return null;
        }
        String xfHeader = request.getHeader("X-Forwarded-For");
        if (xfHeader != null && !xfHeader.isBlank()) {
            return xfHeader.split(",")[0].trim();
        }
        String xRealIp = request.getHeader("X-Real-IP");
        if (xRealIp != null && !xRealIp.isBlank()) {
            return xRealIp.trim();
        }
        return request.getRemoteAddr();
    }
}
