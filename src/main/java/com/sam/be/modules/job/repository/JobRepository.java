package com.sam.be.modules.job.repository;

import com.sam.be.modules.job.entity.Job;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface JobRepository extends JpaRepository<Job, UUID> {
    List<Job> findAllByClientId(UUID clientId);
}
