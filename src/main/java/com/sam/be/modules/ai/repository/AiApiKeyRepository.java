package com.sam.be.modules.ai.repository;

import com.sam.be.modules.ai.entity.AiApiKey;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AiApiKeyRepository extends JpaRepository<AiApiKey, Long> {
    List<AiApiKey> findAllByOrderByPriorityAscIdAsc();

    List<AiApiKey> findByActiveTrueOrderByPriorityAscIdAsc();

    boolean existsByKeyFingerprint(String keyFingerprint);
}
