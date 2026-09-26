package com.sam.be.modules.chat.service.impl;

import com.sam.be.modules.chat.entity.ChatRoom;
import com.sam.be.modules.chat.repository.ChatRoomRepository;
import com.sam.be.modules.chat.service.ChatService;
import com.sam.be.modules.job.entity.Job;
import com.sam.be.modules.user.entity.User;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ChatServiceImpl implements ChatService {

    private final ChatRoomRepository chatRoomRepository;

    @Override
    @Transactional
    public ChatRoom getOrCreateRoom(Job job, User freelancer) {
        // Tìm xem 2 người này đã có phòng chat cho Job này chưa
        return chatRoomRepository.findByJobIdAndFreelancerId(job.getId(), freelancer.getId())
                .orElseGet(() -> {
                    // Nếu chưa có thì tạo mới tinh
                    ChatRoom newRoom = ChatRoom.builder()
                            .job(job)
                            .client(job.getClient())
                            .freelancer(freelancer)
                            .build();
                    return chatRoomRepository.save(newRoom);
                });
    }
}