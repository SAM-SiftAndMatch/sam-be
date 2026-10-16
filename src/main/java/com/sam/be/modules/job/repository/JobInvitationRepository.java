package com.sam.be.modules.job.repository;

import com.sam.be.modules.job.entity.JobInvitation;
import com.sam.be.modules.job.dto.response.ClientInvitationStatusResponse;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface JobInvitationRepository extends JpaRepository<JobInvitation, UUID> {
    Optional<JobInvitation> findByJobIdAndFreelancerId(UUID jobId, UUID freelancerId);

    @Query("SELECT new com.sam.be.modules.job.dto.response.ClientInvitationStatusResponse(i.job.id, i.freelancer.id, i.status) "
            + "FROM JobInvitation i WHERE i.job.client.id = :clientId AND i.job.status = com.sam.be.common.constant.enums.JobStatus.OPEN "
            + "AND i.freelancer.id IN :freelancerIds")
    java.util.List<ClientInvitationStatusResponse> findOpenStatusesForClientAndFreelancers(
            @Param("clientId") UUID clientId, @Param("freelancerIds") java.util.Collection<UUID> freelancerIds);

    @Query("SELECT i.job.id FROM JobInvitation i WHERE i.id = :invitationId")
    Optional<UUID> findJobIdById(@Param("invitationId") UUID invitationId);

    @Query(value = "SELECT i FROM JobInvitation i JOIN FETCH i.job j JOIN FETCH i.freelancer f "
            + "WHERE j.id = :jobId AND (:status IS NULL OR i.status = :status)",
            countQuery = "SELECT COUNT(i) FROM JobInvitation i WHERE i.job.id = :jobId "
                    + "AND (:status IS NULL OR i.status = :status)")
    Page<JobInvitation> findJobPage(@Param("jobId") UUID jobId,
            @Param("status") JobInvitation.Status status, Pageable pageable);

    @Query(value = "SELECT i FROM JobInvitation i JOIN FETCH i.job j JOIN FETCH i.freelancer f "
            + "WHERE f.id = :freelancerId AND (:status IS NULL OR i.status = :status)",
            countQuery = "SELECT COUNT(i) FROM JobInvitation i WHERE i.freelancer.id = :freelancerId "
                    + "AND (:status IS NULL OR i.status = :status)")
    Page<JobInvitation> findFreelancerPage(@Param("freelancerId") UUID freelancerId,
            @Param("status") JobInvitation.Status status, Pageable pageable);

    java.util.List<JobInvitation> findByJobIdAndStatusIn(UUID jobId, java.util.Collection<JobInvitation.Status> statuses);

    @Query("SELECT i.id FROM JobInvitation i WHERE i.status = :status AND i.expiresAt < :before")
    java.util.List<UUID> findIdsByStatusAndExpiresAtBefore(@Param("status") JobInvitation.Status status,
            @Param("before") java.time.LocalDateTime before);
}
