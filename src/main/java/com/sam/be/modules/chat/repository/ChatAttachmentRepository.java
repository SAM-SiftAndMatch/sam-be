package com.sam.be.modules.chat.repository;

import com.sam.be.modules.chat.entity.ChatAttachment;
import jakarta.persistence.LockModeType;
import java.util.*;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;

public interface ChatAttachmentRepository extends JpaRepository<ChatAttachment, UUID> {
    @EntityGraph(attributePaths = {"message", "message.sender"})
    @Query(
            "select a from ChatAttachment a where a.room.id = :roomId and a.message is not null "
                    + "and ((:images = true and a.contentType like 'image/%') "
                    + "or (:images = false and a.contentType not like 'image/%'))")
    org.springframework.data.domain.Page<ChatAttachment> findShared(
            @Param("roomId") UUID roomId,
            @Param("images") boolean images,
            org.springframework.data.domain.Pageable pageable);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select a from ChatAttachment a where a.id = :id")
    Optional<ChatAttachment> findForUpdate(@Param("id") UUID id);
}
