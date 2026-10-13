package com.sam.be.modules.workflow.repository;

import com.sam.be.modules.workflow.entity.ProjectTimelineTask;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;

public interface ProjectTimelineTaskRepository extends JpaRepository<ProjectTimelineTask, UUID> {
    List<ProjectTimelineTask> findAllByJobIdOrderByWeekNumberAscSortOrderAsc(UUID jobId);

    @Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    Optional<ProjectTimelineTask> findByIdAndJobId(UUID taskId, UUID jobId);
}
