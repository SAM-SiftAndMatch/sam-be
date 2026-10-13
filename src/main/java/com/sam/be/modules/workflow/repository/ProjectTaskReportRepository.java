package com.sam.be.modules.workflow.repository;

import com.sam.be.modules.workflow.entity.ProjectTaskReport;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProjectTaskReportRepository extends JpaRepository<ProjectTaskReport, UUID> {
    List<ProjectTaskReport> findAllByTaskIdInOrderByCreatedAtDesc(Collection<UUID> taskIds);
}
