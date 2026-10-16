package com.sam.be.modules.chat.service;

import com.sam.be.common.exception.ApiException;
import com.sam.be.common.exception.ErrorCode;
import com.sam.be.modules.chat.dto.WorkspaceSummaryDto;
import com.sam.be.modules.chat.repository.ChatRoomRepository;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class WorkspaceSummaryService {

    private final ChatRoomRepository chatRoomRepository;

    @Transactional(readOnly = true)
    public WorkspaceSummaryDto getSummary(UUID roomId, UUID userId) {
        return chatRoomRepository
                .findWorkspaceSummary(roomId, userId)
                .orElseThrow(() -> new ApiException(ErrorCode.RESOURCE_NOT_FOUND));
    }
}
