package com.sam.be.modules.chat.service;

import com.sam.be.modules.chat.entity.ChatRoom;
import com.sam.be.modules.job.entity.Job;
import com.sam.be.modules.user.entity.User;

public interface ChatService {
    ChatRoom getOrCreateRoom(Job job, User freelancer);
}