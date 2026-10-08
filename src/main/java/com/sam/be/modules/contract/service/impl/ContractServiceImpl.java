package com.sam.be.modules.contract.service.impl;

import com.sam.be.common.constant.enums.JobStatus;
import com.sam.be.common.exception.ApiException;
import com.sam.be.common.exception.ErrorCode;
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
import com.sam.be.modules.contract.dto.response.ContractDetailResponse;
import com.sam.be.modules.contract.dto.response.ContractDraftResponse;
import com.sam.be.modules.contract.dto.response.ContractRevisionResponse;
import com.sam.be.modules.contract.entity.Contract;
import com.sam.be.modules.contract.entity.ContractRevision;
import com.sam.be.modules.contract.repository.ContractRepository;
import com.sam.be.modules.contract.repository.ContractRevisionRepository;
import com.sam.be.modules.contract.service.ContractService;
import com.sam.be.modules.job.entity.Job;
import com.sam.be.modules.job.repository.JobRepository;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ContractServiceImpl implements ContractService {

    private final ContractRepository contractRepository;
    private final ContractRevisionRepository contractRevisionRepository;
    private final ChatRoomRepository chatRoomRepository;
    private final JobRepository jobRepository;
    private final AiService aiService;
    private final SimpMessagingTemplate messagingTemplate;
    private final ChatService chatService;

    @Override
    @Transactional
    public ContractDraftResponse createAiDraft(UUID roomId, UUID userId) {
        ChatRoom room =
                chatRoomRepository
                        .findById(roomId)
                        .orElseThrow(() -> new ApiException(ErrorCode.RESOURCE_NOT_FOUND));

        // Chỉ Client mới được khởi tạo hợp đồng, Freelancer chờ Client tạo
        if (!room.getClient().getId().equals(userId)) {
            throw new ApiException(ErrorCode.FORBIDDEN_ACTION);
        }

        Job job = room.getJob();

        if (contractRepository.findByJobId(job.getId()).isPresent()) {
            throw new ApiException(ErrorCode.DUPLICATE_RESOURCE);
        }

        job.setStatus(JobStatus.NEGOTIATING);
        jobRepository.save(job);

        String srsData = job.getTitle() + "\n" + job.getDescription();

        AiContractDraft aiDraft =
                aiService.generateContractDraft(
                        srsData,
                        job.getBudgetMin(),
                        job.getBudgetMax(),
                        room.getClient().getFullName(),
                        room.getFreelancer().getFullName());

        Contract contract =
                Contract.builder()
                        .job(job)
                        .client(room.getClient())
                        .freelancer(room.getFreelancer())
                        .agreedAmount(
                                aiDraft.getSuggestedPrice() != null
                                        ? aiDraft.getSuggestedPrice()
                                        : job.getBudgetMax())
                        .aiSuggestedAmount(aiDraft.getSuggestedPrice())
                        .termsAndConditions(aiDraft.getTermsAndConditions())
                        .build();

        contract = contractRepository.save(contract);
        saveRevision(contract, userId);

        SendMessagePayload chatPayload = new SendMessagePayload();
        chatPayload.setSenderId(userId);
        chatPayload.setContent(
                "Tôi vừa khởi tạo bản nháp Hợp đồng. Chúng ta cùng xem và chốt nhé!");
        ChatMessageDto savedMsg = chatService.saveAndBroadcastMessage(room.getId(), chatPayload);
        messagingTemplate.convertAndSend("/topic/chat/" + room.getId(), savedMsg);

        return ContractDraftResponse.builder()
                .contractId(contract.getId())
                .jobId(job.getId())
                .agreedAmount(contract.getAgreedAmount())
                .termsAndConditions(contract.getTermsAndConditions())
                .build();
    }

    @Override
    @Transactional(readOnly = true)
    public ContractDetailResponse getContractByRoom(UUID roomId, UUID userId) {
        ChatRoom room =
                chatRoomRepository
                        .findById(roomId)
                        .orElseThrow(() -> new ApiException(ErrorCode.RESOURCE_NOT_FOUND));

        if (!room.getClient().getId().equals(userId)
                && !room.getFreelancer().getId().equals(userId)) {
            throw new ApiException(ErrorCode.FORBIDDEN_ACTION);
        }

        Contract contract =
                contractRepository
                        .findByJobId(room.getJob().getId())
                        .orElseThrow(() -> new ApiException(ErrorCode.RESOURCE_NOT_FOUND));

        return toDetail(contract);
    }

    @Override
    @Transactional(readOnly = true)
    public java.util.List<ContractRevisionResponse> getRevisionsByRoom(UUID roomId, UUID userId) {
        ChatRoom room =
                chatRoomRepository
                        .findById(roomId)
                        .orElseThrow(() -> new ApiException(ErrorCode.RESOURCE_NOT_FOUND));

        if (!room.getClient().getId().equals(userId)
                && !room.getFreelancer().getId().equals(userId)) {
            throw new ApiException(ErrorCode.FORBIDDEN_ACTION);
        }

        Contract contract =
                contractRepository
                        .findByJobId(room.getJob().getId())
                        .orElseThrow(() -> new ApiException(ErrorCode.RESOURCE_NOT_FOUND));

        return contractRevisionRepository.findByContractIdOrderByCreatedAtDesc(contract.getId())
                .stream()
                .map(rev -> toRevisionResponse(rev, contract))
                .toList();
    }

    private ContractRevisionResponse toRevisionResponse(ContractRevision rev, Contract contract) {
        boolean isClient = contract.getClient().getId().equals(rev.getEditedBy().getId());
        return ContractRevisionResponse.builder()
                .id(rev.getId())
                .editorId(rev.getEditedBy().getId())
                .editorName(rev.getEditedBy().getFullName())
                .editorSide(isClient ? "CLIENT" : "FREELANCER")
                .agreedAmount(rev.getAgreedAmount())
                .termsAndConditions(rev.getTermsAndConditions())
                .createdAt(rev.getCreatedAt())
                .build();
    }

    // Lưu snapshot sau mỗi lần tạo/sửa. Gộp burst gõ liên tục: cùng người sửa trong
    // 60s và giá không đổi thì cập nhật bản mới nhất thay vì chèn dòng mới.
    private void saveRevision(Contract contract, UUID editorId) {
        var editor =
                contract.getClient().getId().equals(editorId)
                        ? contract.getClient()
                        : contract.getFreelancer();
        var latest =
                contractRevisionRepository.findTopByContractIdOrderByCreatedAtDesc(
                        contract.getId());
        if (latest.isPresent()
                && latest.get().getEditedBy().getId().equals(editorId)
                && latest.get().getCreatedAt() != null
                && latest.get()
                        .getCreatedAt()
                        .isAfter(java.time.LocalDateTime.now().minusSeconds(60))
                && java.util.Objects.equals(
                        latest.get().getAgreedAmount(), contract.getAgreedAmount())) {
            latest.get().setTermsAndConditions(contract.getTermsAndConditions());
            contractRevisionRepository.save(latest.get());
            return;
        }
        contractRevisionRepository.save(
                ContractRevision.builder()
                        .contract(contract)
                        .editedBy(editor)
                        .agreedAmount(contract.getAgreedAmount())
                        .termsAndConditions(contract.getTermsAndConditions())
                        .build());
        // Giữ tối đa 100 bản gần nhất cho mỗi hợp đồng
        var all =
                contractRevisionRepository.findByContractIdOrderByCreatedAtDesc(contract.getId());
        if (all.size() > 100) {
            contractRevisionRepository.deleteAll(all.subList(100, all.size()));
        }
    }

    private ContractDetailResponse toDetail(Contract contract) {
        return ContractDetailResponse.builder()
                .id(contract.getId())
                .jobId(contract.getJob().getId())
                .jobTitle(contract.getJob().getTitle())
                .agreedAmount(contract.getAgreedAmount())
                .termsAndConditions(contract.getTermsAndConditions())
                .status(contract.getStatus())
                .clientAgreed(contract.getClientAgreed())
                .freelancerAgreed(contract.getFreelancerAgreed())
                .reviewStatus(contract.getReviewStatus())
                .reviewNote(contract.getReviewNote())
                .clientKeepConfirmed(contract.getClientKeepConfirmed())
                .freelancerKeepConfirmed(contract.getFreelancerKeepConfirmed())
                .clientId(contract.getClient().getId())
                .clientName(contract.getClient().getFullName())
                .freelancerId(contract.getFreelancer().getId())
                .freelancerName(contract.getFreelancer().getFullName())
                .build();
    }

    @Override
    @Transactional
    public void syncContract(UUID contractId, ContractSyncPayload payload) {
        Contract contract =
                contractRepository
                        .findById(contractId)
                        .orElseThrow(() -> new ApiException(ErrorCode.RESOURCE_NOT_FOUND));

        if (!contract.getClient().getId().equals(payload.getSenderId())
                && !contract.getFreelancer().getId().equals(payload.getSenderId())) {
            throw new ApiException(ErrorCode.FORBIDDEN_ACTION);
        }

        // Chỉ cập nhật field nào FE gửi lên (tránh ghi đè null khi đồng chỉnh từng phần)
        if (payload.getAgreedAmount() != null) {
            if (payload.getAgreedAmount().signum() <= 0) {
                throw new ApiException(ErrorCode.REQUEST_FAILED, "Giá thỏa thuận phải lớn hơn 0");
            }
            contract.setAgreedAmount(payload.getAgreedAmount());
        }
        if (payload.getTermsAndConditions() != null) {
            contract.setTermsAndConditions(payload.getTermsAndConditions());
        }

        contract.setClientAgreed(false);
        contract.setFreelancerAgreed(false);
        // Sửa là bản thẩm định cũ hết hiệu lực
        contract.setReviewStatus(null);
        contract.setReviewExtractedAmount(null);
        contract.setReviewNote(null);
        contract.setClientKeepConfirmed(false);
        contract.setFreelancerKeepConfirmed(false);

        contractRepository.save(contract);
        saveRevision(contract, payload.getSenderId());

        messagingTemplate.convertAndSend(
                "/topic/contracts/" + contractId, broadcastOf(contract, "SYNC"));

        ChatRoom room =
                chatRoomRepository
                        .findByJobIdAndFreelancerId(
                                contract.getJob().getId(), contract.getFreelancer().getId())
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
        Contract contract =
                contractRepository
                        .findById(contractId)
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
        boolean aiAnnounce = false;

        if (Boolean.TRUE.equals(contract.getClientAgreed())
                && Boolean.TRUE.equals(contract.getFreelancerAgreed())) {
            // Ký đôi xong CHƯA chốt vội: gửi AI thẩm định trước (lỗi mạng cũng không được
            // rollback chữ ký — rớt sang luồng xác nhận tay)
            com.sam.be.modules.ai.dto.response.AiContractReview review;
            try {
                review =
                        aiService.reviewContractTerms(
                                contract.getTermsAndConditions(),
                                contract.getAgreedAmount(),
                                contract.getAiSuggestedAmount());
            } catch (Exception e) {
                org.slf4j.LoggerFactory.getLogger(ContractServiceImpl.class)
                        .warn("AI review call failed, fallback to manual confirm", e);
                review = new com.sam.be.modules.ai.dto.response.AiContractReview();
                review.setVerdict("NEEDS_CONFIRM");
                review.setChatMessage(
                        "AI tạm thời không thẩm định được (lỗi kỹ thuật). Hai bên tự kiểm tra kỹ số tiền và điều khoản: nếu giữ nguyên bản này thì bấm \"Giữ nguyên\", còn không thì sửa lại rồi ký lại.");
            }
            contract.setReviewStatus(review.getVerdict());
            contract.setReviewExtractedAmount(review.getExtractedAmount());
            contract.setReviewNote(review.getChatMessage());
            contract.setClientKeepConfirmed(false);
            contract.setFreelancerKeepConfirmed(false);
            aiAnnounce = true;

            if ("OK".equalsIgnoreCase(review.getVerdict())) {
                finalizeContract(contract);
                broadcastType = "COMPLETED";
                chatMsg = review.getChatMessage();
            } else {
                // AI thấy lệch: xóa chữ ký 2 bên, 2 bên hoặc bấm Giữ nguyên hoặc sửa rồi ký lại
                contract.setClientAgreed(false);
                contract.setFreelancerAgreed(false);
                chatMsg = review.getChatMessage();
            }
        }

        contractRepository.save(contract);

        messagingTemplate.convertAndSend(
                "/topic/contracts/" + contractId, broadcastOf(contract, broadcastType));

        ChatRoom room =
                chatRoomRepository
                        .findByJobIdAndFreelancerId(
                                contract.getJob().getId(), contract.getFreelancer().getId())
                        .orElseThrow(() -> new ApiException(ErrorCode.RESOURCE_NOT_FOUND));

        ChatMessageDto savedMsg;
        if (aiAnnounce) {
            // Tin của AI: danh nghĩa hệ thống, không gắn tên người ký cuối
            savedMsg = chatService.saveSystemMessage(room.getId(), "🤖 " + chatMsg);
        } else {
            SendMessagePayload chatPayload = new SendMessagePayload();
            chatPayload.setSenderId(payload.getSenderId());
            chatPayload.setContent(chatMsg);
            savedMsg = chatService.saveAndBroadcastMessage(room.getId(), chatPayload);
        }
        messagingTemplate.convertAndSend("/topic/chat/" + room.getId(), savedMsg);
    }

    @Override
    @Transactional
    public void confirmKeepContract(UUID contractId, ContractSignPayload payload) {
        Contract contract =
                contractRepository
                        .findById(contractId)
                        .orElseThrow(() -> new ApiException(ErrorCode.RESOURCE_NOT_FOUND));

        boolean isClient = contract.getClient().getId().equals(payload.getSenderId());
        boolean isFreelancer = contract.getFreelancer().getId().equals(payload.getSenderId());
        if (!isClient && !isFreelancer) {
            throw new ApiException(ErrorCode.FORBIDDEN_ACTION);
        }
        // Chỉ được "Giữ nguyên" khi AI đang yêu cầu xác nhận
        if (!"NEEDS_CONFIRM".equalsIgnoreCase(contract.getReviewStatus())) {
            throw new ApiException(
                    ErrorCode.REQUEST_FAILED, "Hợp đồng không ở trạng thái chờ xác nhận giữ nguyên");
        }

        if (isClient) {
            contract.setClientKeepConfirmed(payload.getIsAgreed());
        } else {
            contract.setFreelancerKeepConfirmed(payload.getIsAgreed());
        }

        String broadcastType = "CONFIRM_KEEP";
        String chatMsg = "Tôi đã xác nhận giữ nguyên bản hợp đồng hiện tại.";
        boolean aiAnnounce = false;

        if (Boolean.TRUE.equals(contract.getClientKeepConfirmed())
                && Boolean.TRUE.equals(contract.getFreelancerKeepConfirmed())) {
            // Cả 2 đồng ý giữ nguyên: chốt số tiền theo đúng văn bản rồi cho đi tiếp
            if (contract.getReviewExtractedAmount() != null) {
                contract.setAgreedAmount(contract.getReviewExtractedAmount());
            }
            finalizeContract(contract);
            broadcastType = "COMPLETED";
            chatMsg =
                    "Cả 2 bên đã chốt giữ nguyên hợp đồng. Số tiền chốt: "
                            + contract.getAgreedAmount()
                            + ". Nạp tiền để dự án bắt đầu nhé!";
            aiAnnounce = true;
        }

        contractRepository.save(contract);
        messagingTemplate.convertAndSend(
                "/topic/contracts/" + contractId, broadcastOf(contract, broadcastType));

        ChatRoom room =
                chatRoomRepository
                        .findByJobIdAndFreelancerId(
                                contract.getJob().getId(), contract.getFreelancer().getId())
                        .orElseThrow(() -> new ApiException(ErrorCode.RESOURCE_NOT_FOUND));
        ChatMessageDto savedMsg;
        if (aiAnnounce) {
            savedMsg = chatService.saveSystemMessage(room.getId(), "🤖 " + chatMsg);
        } else {
            SendMessagePayload chatPayload = new SendMessagePayload();
            chatPayload.setSenderId(payload.getSenderId());
            chatPayload.setContent(chatMsg);
            savedMsg = chatService.saveAndBroadcastMessage(room.getId(), chatPayload);
        }
        messagingTemplate.convertAndSend("/topic/chat/" + room.getId(), savedMsg);
    }

    // Chốt hợp đồng: ACTIVE + job sang chờ nạp tiền (đủ tiền mới IN_PROGRESS)
    private void finalizeContract(Contract contract) {
        contract.setStatus(com.sam.be.common.constant.enums.ContractStatus.ACTIVE);
        contract.setReviewStatus("OK");
        Job job = contract.getJob();
        job.setStatus(JobStatus.AWAITING_PAYMENT);
        jobRepository.save(job);
    }

    private ContractBroadcastData broadcastOf(Contract contract, String type) {
        return ContractBroadcastData.builder()
                .type(type)
                .agreedAmount(contract.getAgreedAmount())
                .termsAndConditions(contract.getTermsAndConditions())
                .clientAgreed(contract.getClientAgreed())
                .freelancerAgreed(contract.getFreelancerAgreed())
                .contractStatus(contract.getStatus().name())
                .reviewStatus(contract.getReviewStatus())
                .reviewNote(contract.getReviewNote())
                .clientKeepConfirmed(contract.getClientKeepConfirmed())
                .freelancerKeepConfirmed(contract.getFreelancerKeepConfirmed())
                .build();
    }

    @Override
    @Transactional(readOnly = true)
    public Contract getContractById(UUID contractId) {
        return contractRepository
                .findById(contractId)
                .orElseThrow(() -> new ApiException(ErrorCode.RESOURCE_NOT_FOUND));
    }
}
