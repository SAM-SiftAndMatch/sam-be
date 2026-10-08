package com.sam.be.modules.chat.repository;

import com.sam.be.modules.chat.entity.ChatRoom;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface ChatRoomRepository extends JpaRepository<ChatRoom, UUID> {
    Optional<ChatRoom> findByJobIdAndFreelancerId(UUID jobId, UUID freelancerId);

    @Query(
            "SELECT r FROM ChatRoom r WHERE r.client.id = :userId OR r.freelancer.id = :userId ORDER BY r.updatedAt DESC")
    List<ChatRoom> findAllByMemberId(@Param("userId") UUID userId);

    @Query(
            "SELECT r FROM ChatRoom r WHERE r.job.id = :jobId AND (r.client.id = :userId OR r.freelancer.id = :userId)")
    Optional<ChatRoom> findByJobIdAndMemberId(
            @Param("jobId") UUID jobId, @Param("userId") UUID userId);
}
