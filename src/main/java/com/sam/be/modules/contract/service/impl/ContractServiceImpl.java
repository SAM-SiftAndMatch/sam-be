package com.sam.be.modules.contract.service.impl;

import com.sam.be.common.exception.ApiException;
import com.sam.be.common.exception.ErrorCode;
import com.sam.be.common.constant.enums.JobStatus;
import com.sam.be.modules.ai.dto.response.AiContractDraft;
import com.sam.be.modules.ai.service.AiService;
import com.sam.be.modules.chat.dto.ChatMessageDto;
import com.sam.be.modules.chat.dto.SendMessagePayload;
import com.sam.be.modules.chat.entity.ChatRoom;
import com.sam.be.modules.chat.repository.ChatRoomRepository;
import com.sam.be.modules.chat.service.ChatService;
import com.sam.be.modules.contract.dto.request.ContractSignPayload;
import com.sam.be.modules.contract.dto.request.ContractSyncPayload;
import com.sam.be.modules.contract.dto.response.ContractBroadcastData;
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
    private final ChatService chatService;

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

        SendMessagePayload chatPayload = new SendMessagePayload();
        chatPayload.setSenderId(userId);
        chatPayload.setContent("Tôi vừa khởi tạo bản nháp Hợp đồng. Chúng ta cùng xem và chốt nhé!");
        ChatMessageDto savedMsg = chatService.saveAndBroadcastMessage(room.getId(), chatPayload);
        messagingTemplate.convertAndSend("/topic/chat/" + room.getId(), savedMsg);

        return ContractDraftResponse.builder()
                .contractId(contract.getId())
                .jobId(job.getId())
                .agreedAmount(contract.getAgreedAmount())
                .revisionLimit(contract.getRevisionLimit())
                .termsAndConditions(contract.getTermsAndConditions())
                .build();
    }

    @Override
    @Transactional
    public void syncContract(UUID contractId, ContractSyncPayload payload) {
        Contract contract = contractRepository.findById(contractId)
                .orElseThrow(() -> new ApiException(ErrorCode.RESOURCE_NOT_FOUND));

        if (!contract.getClient().getId().equals(payload.getSenderId()) &&
                !contract.getFreelancer().getId().equals(payload.getSenderId())) {
            throw new ApiException(ErrorCode.FORBIDDEN_ACTION);
        }

        contract.setAgreedAmount(payload.getAgreedAmount());
        contract.setRevisionLimit(payload.getRevisionLimit());
        contract.setTermsAndConditions(payload.getTermsAndConditions());

        contract.setClientAgreed(false);
        contract.setFreelancerAgreed(false);

        contractRepository.save(contract);

        ContractBroadcastData broadcastData = ContractBroadcastData.builder()
                .type("SYNC")
                .agreedAmount(contract.getAgreedAmount())
                .revisionLimit(contract.getRevisionLimit())
                .termsAndConditions(contract.getTermsAndConditions())
                .clientAgreed(contract.getClientAgreed())
                .freelancerAgreed(contract.getFreelancerAgreed())
                .contractStatus(contract.getStatus().name())
                .build();

        messagingTemplate.convertAndSend("/topic/contracts/" + contractId, broadcastData);

        ChatRoom room = chatRoomRepository.findByJobIdAndFreelancerId(contract.getJob().getId(), contract.getFreelancer().getId())
                .orElseThrow(() -> new ApiException(ErrorCode.RESOURCE_NOT_FOUND));

        SendMessagePayload chatPayload = new SendMessagePayload();
        chatPayload.setSenderId(payload.getSenderId());
        chatPayload.setContent("Tôi vừa cập nhật lại các thông số trong bản nháp Hợp đồng.");
        ChatMessageDto savedMsg = chatService.saveAndBroadcastMessage(room.getId(), chatPayload);
        messagingTemplate.convertAndSend("/topic/chat/" + room.getId(), savedMsg);
    }

    @Override
    @Transactional
    public void signContract(UUID contractId, ContractSignPayload payload) {
        Contract contract = contractRepository.findById(contractId)
                .orElseThrow(() -> new ApiException(ErrorCode.RESOURCE_NOT_FOUND));

        boolean isClient = contract.getClient().getId().equals(payload.getSenderId());
        boolean isFreelancer = contract.getFreelancer().getId().equals(payload.getSenderId());

        if (!isClient && !isFreelancer) {
            throw new ApiException(ErrorCode.FORBIDDEN_ACTION);
        }

        if (isClient) {
            contract.setClientAgreed(payload.getIsAgreed());
        } else {
            contract.setFreelancerAgreed(payload.getIsAgreed());
        }

        String broadcastType = "SIGN";
        String chatMsg = "Tôi đã thay đổi trạng thái chữ ký xác nhận Hợp đồng.";

        if (Boolean.TRUE.equals(contract.getClientAgreed()) && Boolean.TRUE.equals(contract.getFreelancerAgreed())) {
            contract.setStatus(com.sam.be.common.constant.enums.ContractStatus.ACTIVE);

            Job job = contract.getJob();
            job.setStatus(JobStatus.IN_PROGRESS);
            jobRepository.save(job);

            broadcastType = "COMPLETED";
            chatMsg = "Tôi đã đồng ý xác nhận. Hợp đồng chính thức được ký kết thành công!";
        }

        contractRepository.save(contract);

        ContractBroadcastData broadcastData = ContractBroadcastData.builder()
                .type(broadcastType)
                .agreedAmount(contract.getAgreedAmount())
                .revisionLimit(contract.getRevisionLimit())
                .termsAndConditions(contract.getTermsAndConditions())
                .clientAgreed(contract.getClientAgreed())
                .freelancerAgreed(contract.getFreelancerAgreed())
                .contractStatus(contract.getStatus().name())
                .build();

        messagingTemplate.convertAndSend("/topic/contracts/" + contractId, broadcastData);

        ChatRoom room = chatRoomRepository.findByJobIdAndFreelancerId(contract.getJob().getId(), contract.getFreelancer().getId())
                .orElseThrow(() -> new ApiException(ErrorCode.RESOURCE_NOT_FOUND));

        SendMessagePayload chatPayload = new SendMessagePayload();
        chatPayload.setSenderId(payload.getSenderId());
        chatPayload.setContent(chatMsg);
        ChatMessageDto savedMsg = chatService.saveAndBroadcastMessage(room.getId(), chatPayload);
        messagingTemplate.convertAndSend("/topic/chat/" + room.getId(), savedMsg);
    }
}