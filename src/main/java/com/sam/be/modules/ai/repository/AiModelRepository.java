package com.sam.be.modules.ai.repository;

import com.sam.be.modules.ai.entity.AiModel;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AiModelRepository extends JpaRepository<AiModel, Long> {
    List<AiModel> findAllByOrderByPriorityAscIdAsc();

    List<AiModel> findByActiveTrueOrderByPriorityAscIdAsc();

    boolean existsByModelNameIgnoreCase(String modelName);
}
