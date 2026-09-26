package com.sam.be.modules.chat.repository;

import com.sam.be.modules.chat.entity.ChatRoom;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface ChatRoomRepository extends JpaRepository<ChatRoom, UUID> {
    Optional<ChatRoom> findByJobIdAndFreelancerId(UUID jobId, UUID freelancerId);
}