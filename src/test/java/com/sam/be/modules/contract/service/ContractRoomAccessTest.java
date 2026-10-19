package com.sam.be.modules.contract.service;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.*;

import com.sam.be.common.exception.ApiException;
import com.sam.be.infrastructure.cache.service.RedisCacheService;
import com.sam.be.modules.chat.entity.ChatRoom;
import com.sam.be.modules.chat.repository.ChatRoomRepository;
import com.sam.be.modules.contract.entity.Contract;
import com.sam.be.modules.contract.repository.ContractRepository;
import com.sam.be.modules.contract.service.impl.ContractServiceImpl;
import com.sam.be.modules.job.entity.Job;
import com.sam.be.modules.user.entity.User;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ContractRoomAccessTest {
    @Test
    void candidateInAnotherRoomCannotReadSelectedFreelancersContractOrCache() {
        UUID clientId = UUID.randomUUID();
        UUID otherId = UUID.randomUUID();
        UUID roomId = UUID.randomUUID();
        Job job = Job.builder().id(UUID.randomUUID()).build();
        User client = User.builder().id(clientId).build();
        User other = User.builder().id(otherId).build();
        User selected = User.builder().id(UUID.randomUUID()).build();
        ChatRoom room = ChatRoom.builder().id(roomId).job(job).client(client).freelancer(other).build();
        Contract contract = Contract.builder().job(job).client(client).freelancer(selected).build();
        ChatRoomRepository rooms = mock(ChatRoomRepository.class);
        ContractRepository contracts = mock(ContractRepository.class);
        RedisCacheService cache = mock(RedisCacheService.class);
        when(rooms.findById(roomId)).thenReturn(Optional.of(room));
        when(contracts.findByJobId(job.getId())).thenReturn(Optional.of(contract));
        ContractServiceImpl service = new ContractServiceImpl(contracts, null, rooms, null,
                null, null, null, cache, null, null, null);

        assertThrows(ApiException.class, () -> service.getContractByRoom(roomId, otherId));
        assertThrows(ApiException.class, () -> service.getRevisionsByRoom(roomId, otherId));
        assertThrows(ApiException.class, () -> service.getRevisionsByRoomPage(roomId, otherId, 0, 10));
        verifyNoInteractions(cache);
    }
}
