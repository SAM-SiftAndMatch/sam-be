package com.sam.be.modules.user.repository;

import com.sam.be.modules.user.entity.ClientProfile;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface ClientProfileRepository extends JpaRepository<ClientProfile, UUID> {
    Optional<ClientProfile> findByUserId(UUID userId);
}
