package com.sam.be.modules.projectdraft.repository;

import com.sam.be.modules.projectdraft.entity.ProjectDraft;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ProjectDraftRepository extends JpaRepository<ProjectDraft, UUID> {
    Optional<ProjectDraft> findByIdAndClientId(UUID id, UUID clientId);

    Page<ProjectDraft> findByClientIdAndStatus(UUID clientId, String status, Pageable pageable);

    @Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT d FROM ProjectDraft d WHERE d.id = :id AND d.client.id = :clientId")
    Optional<ProjectDraft> findOwnedForUpdate(@Param("id") UUID id, @Param("clientId") UUID clientId);

    @Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT d FROM ProjectDraft d WHERE d.id = :id")
    Optional<ProjectDraft> findByIdForUpdate(@Param("id") UUID id);
}
