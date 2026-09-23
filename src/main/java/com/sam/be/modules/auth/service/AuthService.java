package com.sam.be.modules.auth.service;

import com.sam.be.modules.auth.dto.request.LoginRequest;
import com.sam.be.modules.auth.dto.request.RegisterRequest;
import com.sam.be.modules.auth.dto.response.AuthResponse;
import jakarta.servlet.http.HttpServletRequest;
import java.util.UUID;

public interface AuthService {

    AuthResponse login(LoginRequest request, HttpServletRequest httpRequest);

    AuthResponse register(RegisterRequest request, HttpServletRequest httpRequest);

    AuthResponse refresh(String refreshToken, HttpServletRequest httpRequest);

    void logout(UUID sessionId);
}
