package com.sam.be.modules.user.repository;

import com.sam.be.modules.user.entity.FreelancerProfile;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface FreelancerProfileRepository extends JpaRepository<FreelancerProfile, UUID> {
    Optional<FreelancerProfile> findByUserId(UUID userId);
}
