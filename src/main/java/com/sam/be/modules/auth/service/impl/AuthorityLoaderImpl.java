package com.sam.be.modules.auth.service.impl;

import com.sam.be.common.exception.ApiException;
import com.sam.be.common.exception.ErrorCode;
import com.sam.be.common.security.config.AuthorityLoader;
import com.sam.be.infrastructure.cache.model.SessionAuthzCache;
import com.sam.be.modules.user.entity.User;
import com.sam.be.modules.user.repository.UserRepository;
import java.util.List;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@FieldDefaults(makeFinal = true, level = AccessLevel.PRIVATE)
public class AuthorityLoaderImpl implements AuthorityLoader {

    UserRepository userRepository;

    @Override
    public SessionAuthzCache load(UUID userId) {
        User user =
                userRepository
                        .findById(userId)
                        .orElseThrow(
                                () ->
                                        new ApiException(
                                                ErrorCode.RESOURCE_NOT_FOUND, "User not found"));

        if (!Boolean.TRUE.equals(user.getIsActive())) {
            throw new ApiException(ErrorCode.ACCOUNT_DISABLED);
        }

        String role = user.getRole().name();
        List<String> roles = List.of(role);

        List<String> permissions =
                switch (user.getRole()) {
                    case ADMIN ->
                            List.of(
                                    "USER_MANAGE",
                                    "SYSTEM_CONFIG",
                                    "JOB_CREATE",
                                    "JOB_UPDATE",
                                    "JOB_DELETE",
                                    "JOB_VIEW",
                                    "PROPOSAL_VIEW",
                                    "CONTRACT_VIEW",
                                    "REVIEW_VIEW");
                    case CLIENT ->
                            List.of(
                                    "JOB_CREATE",
                                    "JOB_UPDATE",
                                    "JOB_DELETE",
                                    "JOB_VIEW",
                                    "PROPOSAL_VIEW",
                                    "PROPOSAL_ACCEPT",
                                    "PROPOSAL_REJECT",
                                    "CONTRACT_VIEW",
                                    "REVIEW_CREATE");
                    case FREELANCER ->
                            List.of(
                                    "JOB_VIEW",
                                    "PROPOSAL_SUBMIT",
                                    "PROPOSAL_VIEW",
                                    "CONTRACT_VIEW",
                                    "REVIEW_CREATE");
                };

        return new SessionAuthzCache(userId, role, roles, permissions);
    }
}
