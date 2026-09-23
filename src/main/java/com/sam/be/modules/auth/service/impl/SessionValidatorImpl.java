package com.sam.be.modules.auth.service.impl;

import com.sam.be.common.security.config.SessionValidator;
import com.sam.be.modules.auth.repository.SessionRepository;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@FieldDefaults(makeFinal = true, level = AccessLevel.PRIVATE)
public class SessionValidatorImpl implements SessionValidator {

    SessionRepository sessionRepository;

    @Override
    public boolean isSessionActive(UUID sessionId) {
        if (sessionId == null) {
            return false;
        }
        return sessionRepository.isSessionActive(sessionId);
    }
}
