package com.sam.be.modules.user.repository;

import com.sam.be.modules.user.entity.FreelancerSkill;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface FreelancerSkillRepository extends JpaRepository<FreelancerSkill, FreelancerSkill.FreelancerSkillId> {

    // Hàm mới thêm để phục vụ thuật toán bốc Top 5 Dev của AI Matcher
    List<FreelancerSkill> findByFreelancerIdIn(List<UUID> freelancerIds);

    // Hàm gốc của hệ thống phục vụ việc lấy thông tin Profile (Đã khôi phục)
    List<FreelancerSkill> findAllByFreelancerId(UUID freelancerId);
}