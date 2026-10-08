package com.sam.be.modules.job.repository;

import com.sam.be.common.constant.enums.JobStatus;
import com.sam.be.modules.job.entity.Job;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface JobRepository extends JpaRepository<Job, UUID> {
    List<Job> findAllByClientId(UUID clientId);

    @org.springframework.data.jpa.repository.Query("SELECT j FROM Job j WHERE j.status = :status AND (j.isUrgentHiring = false OR (j.isUrgentHiring = true AND j.createdAt <= :fiveMinsAgo)) ORDER BY j.createdAt DESC")
    List<Job> findAllOpenJobsFiltered(@org.springframework.data.repository.query.Param("status") JobStatus status, @org.springframework.data.repository.query.Param("fiveMinsAgo") java.time.LocalDateTime fiveMinsAgo);

    List<Job> findByStatusAndDeadlineBefore(JobStatus status, java.time.LocalDateTime deadline);
}
