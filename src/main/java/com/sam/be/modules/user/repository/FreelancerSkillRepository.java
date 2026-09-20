package com.sam.be.modules.user.repository;

import com.sam.be.modules.user.entity.FreelancerSkill;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface FreelancerSkillRepository extends JpaRepository<FreelancerSkill, FreelancerSkill.FreelancerSkillId> {
    List<FreelancerSkill> findAllByFreelancerId(UUID freelancerId);
}