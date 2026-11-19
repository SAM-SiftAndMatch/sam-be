package com.sam.be.modules.workflow.repository;

import com.sam.be.modules.workflow.entity.ProjectTimelineTask;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ProjectTimelineTaskRepository extends JpaRepository<ProjectTimelineTask, UUID> {
    List<ProjectTimelineTask> findAllByJobIdOrderByWeekNumberAscSortOrderAsc(UUID jobId);

    Optional<ProjectTimelineTask> findByIdAndJobId(UUID taskId, UUID jobId);

    @Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT t FROM ProjectTimelineTask t WHERE t.id = :taskId AND t.job.id = :jobId")
    Optional<ProjectTimelineTask> findByIdAndJobIdForUpdate(
            @Param("taskId") UUID taskId, @Param("jobId") UUID jobId);
}
