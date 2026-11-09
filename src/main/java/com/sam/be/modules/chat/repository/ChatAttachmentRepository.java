package com.sam.be.modules.chat.repository;

import com.sam.be.modules.chat.entity.ChatAttachment;
import jakarta.persistence.LockModeType;
import java.util.*;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;

public interface ChatAttachmentRepository extends JpaRepository<ChatAttachment, UUID> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select a from ChatAttachment a where a.id = :id")
    Optional<ChatAttachment> findForUpdate(@Param("id") UUID id);
}
