package com.sam.be.modules.contract.service.impl;

import com.sam.be.common.exception.ApiException;
import com.sam.be.common.exception.ErrorCode;
import com.sam.be.common.constant.enums.JobStatus;
import com.sam.be.modules.ai.dto.response.AiContractDraft;
import com.sam.be.modules.ai.service.AiService;
import com.sam.be.modules.chat.entity.ChatRoom;
import com.sam.be.modules.chat.repository.ChatRoomRepository;
import com.sam.be.modules.contract.dto.response.ContractDraftResponse;
import com.sam.be.modules.contract.entity.Contract;
import com.sam.be.modules.contract.repository.ContractRepository;
import com.sam.be.modules.job.entity.Job;
import com.sam.be.modules.job.repository.JobRepository;
import com.sam.be.modules.contract.service.ContractService;
import lombok.RequiredArgsConstructor;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ContractServiceImpl implements ContractService {

    private final ContractRepository contractRepository;
    private final ChatRoomRepository chatRoomRepository;
    private final JobRepository jobRepository;
    private final AiService aiService;
    private final SimpMessagingTemplate messagingTemplate;

    @Override
    @Transactional
    public ContractDraftResponse createAiDraft(UUID roomId, UUID userId) {
        ChatRoom room = chatRoomRepository.findById(roomId)
                .orElseThrow(() -> new ApiException(ErrorCode.RESOURCE_NOT_FOUND));

        if (!room.getClient().getId().equals(userId) && !room.getFreelancer().getId().equals(userId)) {
            throw new ApiException(ErrorCode.FORBIDDEN_ACTION);
        }

        Job job = room.getJob();

        if (contractRepository.findByJobId(job.getId()).isPresent()) {
            throw new ApiException(ErrorCode.DUPLICATE_RESOURCE);
        }

        job.setStatus(JobStatus.NEGOTIATING);
        jobRepository.save(job);

        String srsData = job.getTitle() + "\n" + job.getDescription();

        AiContractDraft aiDraft = aiService.generateContractDraft(srsData, job.getBudgetMin(), job.getBudgetMax());

        Contract contract = Contract.builder()
                .job(job)
                .client(room.getClient())
                .freelancer(room.getFreelancer())
                .agreedAmount(aiDraft.getSuggestedPrice() != null ? aiDraft.getSuggestedPrice() : job.getBudgetMax())
                .revisionLimit(aiDraft.getRevisionLimit() != null ? aiDraft.getRevisionLimit() : 2)
                .termsAndConditions(aiDraft.getTermsAndConditions())
                .build();

        contract = contractRepository.save(contract);

        messagingTemplate.convertAndSend("/topic/chat/" + room.getId(),
                "System: Hợp đồng nháp đã được AI tạo thành công. ID Hợp đồng: " + contract.getId());

        return ContractDraftResponse.builder()
                .contractId(contract.getId())
                .jobId(job.getId())
                .agreedAmount(contract.getAgreedAmount())
                .revisionLimit(contract.getRevisionLimit())
                .termsAndConditions(contract.getTermsAndConditions())
                .build();
    }
}