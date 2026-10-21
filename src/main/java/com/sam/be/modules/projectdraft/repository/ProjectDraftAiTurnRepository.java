package com.sam.be.modules.projectdraft.repository;

import com.sam.be.modules.projectdraft.entity.ProjectDraftAiTurn;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ProjectDraftAiTurnRepository extends JpaRepository<ProjectDraftAiTurn, UUID> {
    @Query("SELECT t FROM ProjectDraftAiTurn t JOIN FETCH t.draft WHERE t.id = :id")
    Optional<ProjectDraftAiTurn> findWithDraftById(@Param("id") UUID id);

    List<ProjectDraftAiTurn> findByDraftIdAndActiveTrueOrderByCreatedAtAscIdAsc(UUID draftId);

    Optional<ProjectDraftAiTurn> findByDraftIdAndClientMessageId(UUID draftId, UUID clientMessageId);

    boolean existsByDraftIdAndStatusIn(UUID draftId, List<String> statuses);

    @Modifying
    @Query("UPDATE ProjectDraftAiTurn t SET t.active = false WHERE t.draft.id = :draftId "
            + "AND t.phase = 'RISK' AND t.active = true")
    int deactivateRiskTurns(@Param("draftId") UUID draftId);

    @Query("SELECT t.id FROM ProjectDraftAiTurn t WHERE t.status = 'PENDING' ORDER BY t.createdAt ASC")
    List<UUID> findPendingIds(Pageable pageable);

    @Modifying
    @Query("UPDATE ProjectDraftAiTurn t SET t.status = 'PROCESSING', t.attempts = t.attempts + 1, "
            + "t.updatedAt = CURRENT_TIMESTAMP WHERE t.id = :id AND t.status = 'PENDING'")
    int claim(@Param("id") UUID id);

    @Modifying
    @Query("UPDATE ProjectDraftAiTurn t SET t.status = 'PENDING', t.updatedAt = CURRENT_TIMESTAMP "
            + "WHERE t.status = 'PROCESSING' AND t.updatedAt < :olderThan AND t.attempts < 3")
    int recoverStale(@Param("olderThan") LocalDateTime olderThan);

    @Modifying
    @Query("UPDATE ProjectDraftAiTurn t SET t.status = 'FAILED', t.updatedAt = CURRENT_TIMESTAMP "
            + "WHERE t.status = 'PROCESSING' AND t.updatedAt < :olderThan AND t.attempts >= 3")
    int failStale(@Param("olderThan") LocalDateTime olderThan);
}
