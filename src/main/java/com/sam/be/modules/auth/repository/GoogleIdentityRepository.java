package com.sam.be.modules.auth.repository;

import com.sam.be.modules.auth.entity.GoogleIdentity;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface GoogleIdentityRepository extends JpaRepository<GoogleIdentity, UUID> {
    Optional<GoogleIdentity> findByIssuerAndSubject(String issuer, String subject);

    boolean existsByUserId(UUID userId);
}
