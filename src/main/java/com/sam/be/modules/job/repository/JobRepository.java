package com.sam.be.modules.job.repository;

import com.sam.be.common.constant.enums.JobStatus;
import com.sam.be.modules.job.entity.Job;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface JobRepository extends JpaRepository<Job, UUID> {

    Optional<Job> findByClientIdAndClientRequestId(UUID clientId, UUID clientRequestId);

    @Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT j FROM Job j WHERE j.id = :id")
    Optional<Job> findByIdForWorkflowUpdate(@Param("id") UUID id);

    @Query(
            "SELECT DISTINCT j FROM Job j "
                    + "LEFT JOIN FETCH j.client "
                    + "LEFT JOIN FETCH j.jobSkills js "
                    + "LEFT JOIN FETCH js.skill "
                    + "WHERE j.client.id = :clientId "
                    + "ORDER BY j.createdAt DESC")
    List<Job> findAllByClientId(@Param("clientId") UUID clientId);

    @Query(
            "SELECT j FROM Job j "
                    + "LEFT JOIN FETCH j.client "
                    + "LEFT JOIN FETCH j.jobSkills js "
                    + "LEFT JOIN FETCH js.skill "
                    + "WHERE j.id = :id")
    Optional<Job> findByIdWithDetails(@Param("id") UUID id);

    @Query(
            "SELECT j.id FROM Job j WHERE j.status = :status "
                    + "AND (j.deadline IS NULL OR j.deadline >= CURRENT_TIMESTAMP) "
                    + "AND (j.isUrgentHiring = false OR (j.isUrgentHiring = true AND j.createdAt <= :fiveMinsAgo)) "
                    + "ORDER BY j.isFeatured DESC, j.createdAt DESC, j.id DESC")
    List<UUID> findAllOpenJobIdsFiltered(
            @Param("status") JobStatus status,
            @Param("fiveMinsAgo") java.time.LocalDateTime fiveMinsAgo);

    @Query("SELECT j.id FROM Job j WHERE j.status = :status AND j.deadline < :deadline ORDER BY j.id")
    List<UUID> findIdsByStatusAndDeadlineBefore(@Param("status") JobStatus status,
            @Param("deadline") java.time.LocalDateTime deadline);

    @Query(
            "SELECT DISTINCT j FROM Contract c JOIN c.job j "
                    + "LEFT JOIN FETCH j.client "
                    + "LEFT JOIN FETCH j.jobSkills js "
                    + "LEFT JOIN FETCH js.skill "
                    + "WHERE c.freelancer.id = :freelancerId "
                    + "ORDER BY j.createdAt DESC")
    List<Job> findAllProjectsByFreelancerId(@Param("freelancerId") UUID freelancerId);

    @Query(
            "SELECT DISTINCT j FROM Job j LEFT JOIN FETCH j.client "
                    + "LEFT JOIN FETCH j.jobSkills js LEFT JOIN FETCH js.skill WHERE j.id IN :ids")
    List<Job> findPageDetails(@Param("ids") Collection<UUID> ids);

    @Query(
            value =
                    "SELECT j FROM Job j WHERE j.status = :status "
                            + "AND (j.deadline IS NULL OR j.deadline >= CURRENT_TIMESTAMP) "
                            + "AND (j.isUrgentHiring = false OR j.createdAt <= :visibleBefore) "
                            + "AND (:search IS NULL OR LOWER(j.title) LIKE :search OR LOWER(j.description) LIKE :search) "
                            + "AND (:skillId IS NULL OR EXISTS (SELECT js FROM JobSkill js WHERE js.job = j AND js.skill.id = :skillId)) "
                            + "AND (:skillsActive = false OR EXISTS (SELECT js FROM JobSkill js WHERE js.job = j "
                            + "AND (LOWER(js.skill.name) IN :skillNames OR LOWER(js.skill.name) LIKE :customSkill))) "
                            + "AND (:negotiable = false OR (j.budgetMin IS NULL AND j.budgetMax IS NULL)) "
                            + "AND (:minBudget IS NULL OR COALESCE(j.budgetMax,j.budgetMin) >= :minBudget) "
                            + "AND (:maxBudget IS NULL OR COALESCE(j.budgetMax,j.budgetMin) <= :maxBudget) "
                            + "ORDER BY j.isFeatured DESC, "
                            + "CASE WHEN :sort = 'budget_asc' THEN COALESCE(j.budgetMin,j.budgetMax,0) END ASC, "
                            + "CASE WHEN :sort = 'budget_desc' THEN COALESCE(j.budgetMax,j.budgetMin,0) END DESC, "
                            + "j.createdAt DESC, j.id DESC",
            countQuery =
                    "SELECT COUNT(j) FROM Job j WHERE j.status = :status "
                            + "AND (j.deadline IS NULL OR j.deadline >= CURRENT_TIMESTAMP) "
                            + "AND (j.isUrgentHiring = false OR j.createdAt <= :visibleBefore) "
                            + "AND (:search IS NULL OR LOWER(j.title) LIKE :search OR LOWER(j.description) LIKE :search) "
                            + "AND (:skillId IS NULL OR EXISTS (SELECT js FROM JobSkill js WHERE js.job = j AND js.skill.id = :skillId)) "
                            + "AND (:skillsActive = false OR EXISTS (SELECT js FROM JobSkill js WHERE js.job = j "
                            + "AND (LOWER(js.skill.name) IN :skillNames OR LOWER(js.skill.name) LIKE :customSkill))) "
                            + "AND (:negotiable = false OR (j.budgetMin IS NULL AND j.budgetMax IS NULL)) "
                            + "AND (:minBudget IS NULL OR COALESCE(j.budgetMax,j.budgetMin) >= :minBudget) "
                            + "AND (:maxBudget IS NULL OR COALESCE(j.budgetMax,j.budgetMin) <= :maxBudget)")
    Page<Job> findOpenPage(
            @Param("status") JobStatus status,
            @Param("visibleBefore") java.time.LocalDateTime visibleBefore,
            @Param("search") String search,
            @Param("skillId") Integer skillId,
            @Param("skillsActive") boolean skillsActive,
            @Param("skillNames") List<String> skillNames,
            @Param("customSkill") String customSkill,
            @Param("negotiable") boolean negotiable,
            @Param("minBudget") java.math.BigDecimal minBudget,
            @Param("maxBudget") java.math.BigDecimal maxBudget,
            @Param("sort") String sort,
            Pageable pageable);

    @Query(
            value =
                    "SELECT j FROM Job j WHERE j.client.id = :clientId "
                            + "AND (:status IS NULL OR j.status = :status) "
                            + "AND (:search IS NULL OR LOWER(j.title) LIKE :search) "
                            + "ORDER BY CASE WHEN j.status = :cancelled THEN 1 ELSE 0 END, j.createdAt DESC, j.id DESC",
            countQuery =
                    "SELECT COUNT(j) FROM Job j WHERE j.client.id = :clientId "
                            + "AND (:status IS NULL OR j.status = :status) "
                            + "AND (:search IS NULL OR LOWER(j.title) LIKE :search)")
    Page<Job> findClientPage(
            @Param("clientId") UUID clientId,
            @Param("status") JobStatus status,
            @Param("search") String search,
            @Param("cancelled") JobStatus cancelled,
            Pageable pageable);

    @Query(
            value =
                    "SELECT j FROM Job j WHERE EXISTS (SELECT c FROM Contract c "
                            + "WHERE c.job = j AND c.freelancer.id = :freelancerId) "
                            + "AND (:status IS NULL OR j.status = :status) "
                            + "AND (:groupActive = false OR j.status IN :groups) "
                            + "AND (:search IS NULL OR LOWER(j.title) LIKE :search OR LOWER(j.client.fullName) LIKE :search) "
                            + "ORDER BY j.createdAt DESC, j.id DESC",
            countQuery =
                    "SELECT COUNT(j) FROM Job j WHERE EXISTS (SELECT c FROM Contract c "
                            + "WHERE c.job = j AND c.freelancer.id = :freelancerId) "
                            + "AND (:status IS NULL OR j.status = :status) "
                            + "AND (:groupActive = false OR j.status IN :groups) "
                            + "AND (:search IS NULL OR LOWER(j.title) LIKE :search OR LOWER(j.client.fullName) LIKE :search)")
    Page<Job> findFreelancerProjectsPage(
            @Param("freelancerId") UUID freelancerId,
            @Param("status") JobStatus status,
            @Param("groupActive") boolean groupActive,
            @Param("groups") List<JobStatus> groups,
            @Param("search") String search,
            Pageable pageable);
}
