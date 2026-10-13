package com.sam.be.modules.workflow.repository;

import com.sam.be.modules.workflow.entity.QaChecklistItem;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface QaChecklistItemRepository extends JpaRepository<QaChecklistItem, UUID> {
    List<QaChecklistItem> findAllByQaPlanIdOrderBySortOrderAsc(UUID qaPlanId);
}
