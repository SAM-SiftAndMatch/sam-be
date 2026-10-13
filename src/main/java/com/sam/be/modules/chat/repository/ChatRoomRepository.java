package com.sam.be.modules.chat.repository;

import com.sam.be.modules.chat.entity.ChatRoom;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface ChatRoomRepository extends JpaRepository<ChatRoom, UUID> {
    Optional<ChatRoom> findByJobIdAndFreelancerId(UUID jobId, UUID freelancerId);

    @EntityGraph(attributePaths = {"job", "client", "freelancer"})
    @Query("SELECT r FROM ChatRoom r WHERE r.id = :roomId")
    Optional<ChatRoom> findDetailById(@Param("roomId") UUID roomId);

    @EntityGraph(attributePaths = {"job", "client", "freelancer"})
    @Query(
            "SELECT r FROM ChatRoom r WHERE r.client.id = :userId OR r.freelancer.id = :userId ORDER BY r.updatedAt DESC")
    List<ChatRoom> findAllByMemberId(@Param("userId") UUID userId);

    @EntityGraph(attributePaths = {"job", "client", "freelancer"})
    @Query(
            "SELECT r FROM ChatRoom r WHERE (r.client.id = :userId OR r.freelancer.id = :userId) "
                    + "AND (:search IS NULL OR LOWER(r.job.title) LIKE :search "
                    + "OR LOWER(r.client.fullName) LIKE :search OR LOWER(r.freelancer.fullName) LIKE :search) "
                    + "ORDER BY r.updatedAt DESC, r.id DESC")
    Page<ChatRoom> findPageByMemberId(
            @Param("userId") UUID userId, @Param("search") String search, Pageable pageable);

    @EntityGraph(attributePaths = {"job", "client", "freelancer"})
    @Query(
            "SELECT r FROM ChatRoom r WHERE r.job.id = :jobId AND (r.client.id = :userId OR r.freelancer.id = :userId)")
    Optional<ChatRoom> findByJobIdAndMemberId(
            @Param("jobId") UUID jobId, @Param("userId") UUID userId);
}
