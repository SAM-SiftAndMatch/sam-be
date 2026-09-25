package com.sam.be.modules.job.repository;

import com.sam.be.modules.job.entity.JobSkill;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface JobSkillRepository extends JpaRepository<JobSkill, JobSkill.JobSkillId> {
}