package com.sam.be.modules.user.repository;

import com.sam.be.modules.user.entity.FreelancerSkill;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface FreelancerSkillRepository
        extends JpaRepository<FreelancerSkill, FreelancerSkill.FreelancerSkillId> {

    // Phục vụ AI Matcher - JOIN FETCH skill để tránh N+1 khi duyệt danh sách skill
    @Query(
            "SELECT fs FROM FreelancerSkill fs JOIN FETCH fs.skill WHERE fs.freelancer.id IN :freelancerIds")
    List<FreelancerSkill> findByFreelancerIdIn(@Param("freelancerIds") List<UUID> freelancerIds);

    // Phục vụ lấy Profile Freelancer - JOIN FETCH skill để gom vào 1 câu SQL duy nhất
    @Query(
            "SELECT fs FROM FreelancerSkill fs JOIN FETCH fs.skill WHERE fs.freelancer.id = :freelancerId")
    List<FreelancerSkill> findAllByFreelancerId(@Param("freelancerId") UUID freelancerId);
}
