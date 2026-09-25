package com.sam.be.modules.user.repository;

import com.sam.be.modules.user.entity.FreelancerProfile;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface FreelancerProfileRepository extends JpaRepository<FreelancerProfile, UUID> {
    Optional<FreelancerProfile> findByUserId(UUID userId);
    List<FreelancerProfile> findAllByUserIdIn(List<UUID> userIds);
}