package com.sam.be.modules.chat.entity;

import com.sam.be.modules.user.entity.User;
import jakarta.persistence.*;
import java.time.LocalDateTime;
import java.util.UUID;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

@Entity
@Table(name = "chat_messages")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ChatMessage {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "room_id", nullable = false)
    private ChatRoom room;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "sender_id")
    private User sender;

    // Tin hệ thống/AI (sender = null): thẩm định hợp đồng, mở dự án...
    @Column(name = "is_system")
    @Builder.Default
    private Boolean isSystem = false;

    @Column(columnDefinition = "TEXT", nullable = false)
    private String content;

    @Column(name = "client_message_id")
    private UUID clientMessageId;

    @OneToMany(mappedBy = "message", fetch = FetchType.LAZY)
    @org.hibernate.annotations.BatchSize(size = 50)
    @Builder.Default
    private java.util.List<ChatAttachment> attachments = new java.util.ArrayList<>();

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;
}
