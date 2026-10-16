package com.sam.be.modules.user.repository;

import com.sam.be.modules.user.entity.FreelancerProfile;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface FreelancerProfileRepository extends JpaRepository<FreelancerProfile, UUID> {
    Optional<FreelancerProfile> findByUserId(UUID userId);

    List<FreelancerProfile> findAllByUserIdIn(List<UUID> userIds);

    @Query(value = "SELECT p FROM FreelancerProfile p JOIN FETCH p.user u "
            + "WHERE u.isActive = true AND u.role = com.sam.be.common.constant.enums.UserRole.FREELANCER "
            + "AND (:search IS NULL OR LOWER(u.fullName) LIKE :search OR LOWER(p.headline) LIKE :search "
            + "OR EXISTS (SELECT fs FROM FreelancerSkill fs WHERE fs.freelancer = u AND LOWER(fs.skill.name) LIKE :search)) "
            + "AND (:skillId IS NULL OR EXISTS (SELECT fs FROM FreelancerSkill fs WHERE fs.freelancer = u AND fs.skill.id = :skillId))",
            countQuery = "SELECT COUNT(p) FROM FreelancerProfile p JOIN p.user u "
            + "WHERE u.isActive = true AND u.role = com.sam.be.common.constant.enums.UserRole.FREELANCER "
            + "AND (:search IS NULL OR LOWER(u.fullName) LIKE :search OR LOWER(p.headline) LIKE :search "
            + "OR EXISTS (SELECT fs FROM FreelancerSkill fs WHERE fs.freelancer = u AND LOWER(fs.skill.name) LIKE :search)) "
            + "AND (:skillId IS NULL OR EXISTS (SELECT fs FROM FreelancerSkill fs WHERE fs.freelancer = u AND fs.skill.id = :skillId))")
    Page<FreelancerProfile> findDirectoryPage(@Param("search") String search,
            @Param("skillId") Integer skillId, Pageable pageable);
}
