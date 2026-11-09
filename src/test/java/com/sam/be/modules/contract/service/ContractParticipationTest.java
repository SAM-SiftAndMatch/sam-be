package com.sam.be.modules.contract.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.sam.be.common.constant.enums.JobStatus;
import com.sam.be.common.exception.ApiException;
import com.sam.be.infrastructure.cache.service.RedisCacheService;
import com.sam.be.modules.ai.dto.response.AiContractDraft;
import com.sam.be.modules.ai.service.AiService;
import com.sam.be.modules.chat.dto.ChatMessageDto;
import com.sam.be.modules.chat.entity.ChatRoom;
import com.sam.be.modules.chat.repository.ChatRoomRepository;
import com.sam.be.modules.chat.service.ChatService;
import com.sam.be.modules.contract.entity.Contract;
import com.sam.be.modules.contract.repository.ContractRepository;
import com.sam.be.modules.contract.repository.ContractRevisionRepository;
import com.sam.be.modules.contract.service.impl.ContractServiceImpl;
import com.sam.be.modules.job.entity.Job;
import com.sam.be.modules.job.repository.JobRepository;
import com.sam.be.modules.user.entity.User;
import com.sam.be.modules.user.service.ParticipationPolicy;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.SimpleTransactionStatus;

class ContractParticipationTest {
    private User user(String name, String email, String phone) {
        User user =
                User.builder()
                        .id(UUID.randomUUID())
                        .fullName("Account " + name)
                        .contractName(name)
                        .contactEmail(email)
                        .phoneNumber(phone)
                        .build();
        ParticipationPolicy.accept(user);
        return user;
    }

    @Test
    void draftSnapshotsIdentityDateAndPlatformTermsIndependentlyOfLaterProfileEdits() {
        User client = user("Nguyễn An", "client@example.test", "0901234567");
        User freelancer = user("Trần Bình", "dev@example.test", "0912345678");
        Job job =
                Job.builder()
                        .id(UUID.randomUUID())
                        .title("Website")
                        .description("Project scope")
                        .client(client)
                        .selectedFreelancer(freelancer)
                        .status(JobStatus.NEGOTIATING)
                        .budgetMin(new BigDecimal("3000000"))
                        .budgetMax(new BigDecimal("3000000"))
                        .build();
        ChatRoom room =
                ChatRoom.builder()
                        .id(UUID.randomUUID())
                        .job(job)
                        .client(client)
                        .freelancer(freelancer)
                        .build();
        var rooms = mock(ChatRoomRepository.class);
        var jobs = mock(JobRepository.class);
        var contracts = mock(ContractRepository.class);
        var revisions = mock(ContractRevisionRepository.class);
        var ai = mock(AiService.class);
        var chat = mock(ChatService.class);
        var cache = mock(RedisCacheService.class);
        var tx = mock(PlatformTransactionManager.class);
        when(tx.getTransaction(any())).thenAnswer(i -> new SimpleTransactionStatus());
        when(rooms.findById(room.getId())).thenReturn(Optional.of(room));
        when(jobs.findByIdForWorkflowUpdate(job.getId())).thenReturn(Optional.of(job));
        when(contracts.findByJobId(job.getId())).thenReturn(Optional.empty());
        var aiDraft = new AiContractDraft();
        aiDraft.setSuggestedPrice(new BigDecimal("3000000"));
        aiDraft.setTermsAndConditions("## Phạm vi\nXây dựng website đã thống nhất.");
        when(ai.generateContractDraft(anyString(), any(), any(), eq("Nguyễn An"), eq("Trần Bình")))
                .thenReturn(aiDraft);
        when(chat.saveAndBroadcastMessage(eq(room.getId()), any()))
                .thenReturn(ChatMessageDto.builder().id(UUID.randomUUID()).build());
        AtomicReference<Contract> saved = new AtomicReference<>();
        when(contracts.save(any()))
                .thenAnswer(
                        i -> {
                            Contract contract = i.getArgument(0);
                            contract.setId(UUID.randomUUID());
                            saved.set(contract);
                            return contract;
                        });
        var service =
                new ContractServiceImpl(
                        contracts,
                        revisions,
                        rooms,
                        jobs,
                        ai,
                        mock(SimpMessagingTemplate.class),
                        chat,
                        cache,
                        null,
                        null,
                        tx);
        service.createAiDraft(room.getId(), client.getId());
        Contract contract = saved.get();
        assertEquals("Nguyễn An", contract.getClientName());
        assertEquals("client@example.test", contract.getClientEmail());
        assertEquals("0901234567", contract.getClientPhone());
        assertEquals("Trần Bình", contract.getFreelancerName());
        assertEquals("dev@example.test", contract.getFreelancerEmail());
        assertEquals("0912345678", contract.getFreelancerPhone());
        assertEquals(LocalDate.now(ZoneId.of("Asia/Ho_Chi_Minh")), contract.getContractDate());
        assertEquals(ParticipationPolicy.VERSION, contract.getPolicyVersion());
        assertEquals(ParticipationPolicy.CONTRACT_NOTICE, contract.getPlatformTerms());
        client.setContractName("Updated name");
        client.setContactEmail("updated@example.test");
        freelancer.setPhoneNumber("0999999999");
        when(contracts.findByJobId(job.getId())).thenReturn(Optional.of(contract));
        var detail = service.getContractByRoom(room.getId(), client.getId());
        assertEquals("Nguyễn An", detail.getClientName());
        assertEquals("client@example.test", detail.getClientEmail());
        assertEquals("0912345678", detail.getFreelancerPhone());
        assertEquals(ParticipationPolicy.CONTRACT_NOTICE, detail.getPlatformTerms());
    }

    @Test
    void missingFreelancerContactStopsDraftBeforeCallingAi() {
        User client = user("Client", "client@example.test", "0901234567");
        User freelancer = user("Freelancer", "dev@example.test", "0912345678");
        freelancer.setPhoneNumber(null);
        var rooms = mock(ChatRoomRepository.class);
        var ai = mock(AiService.class);
        var tx = mock(PlatformTransactionManager.class);
        when(tx.getTransaction(any())).thenReturn(new SimpleTransactionStatus());
        UUID roomId = UUID.randomUUID();
        when(rooms.findById(roomId))
                .thenReturn(
                        Optional.of(
                                ChatRoom.builder()
                                        .id(roomId)
                                        .client(client)
                                        .freelancer(freelancer)
                                        .build()));
        var service =
                new ContractServiceImpl(
                        null, null, rooms, null, ai, null, null, null, null, null, tx);
        assertThrows(ApiException.class, () -> service.createAiDraft(roomId, client.getId()));
        verifyNoInteractions(ai);
    }
}
