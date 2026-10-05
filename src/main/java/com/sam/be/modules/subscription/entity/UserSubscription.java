package com.sam.be.modules.subscription.entity;

import com.sam.be.common.audit.AbstractAuditEntity;
import com.sam.be.common.constant.enums.SubscriptionStatus;
import com.sam.be.modules.job.entity.Job;
import com.sam.be.modules.user.entity.User;
import jakarta.persistence.*;
import java.time.LocalDateTime;
import java.util.UUID;
import lombok.*;

@Entity
@Table(name = "user_subscriptions")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UserSubscription extends AbstractAuditEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    // Trỏ về bảng User
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    // Trỏ về bảng Gói dịch vụ
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "package_id", nullable = false)
    private ServicePackage servicePackage;

    @Column(name = "start_date", nullable = false)
    private LocalDateTime startDate;

    // NULL với gói lẻ PAY_PER_USE (sống theo vòng đời project, không hạn ngày).
    @Column(name = "end_date")
    private LocalDateTime endDate;

    // Project mà gói lẻ áp dụng (NULL với gói tháng SUBSCRIPTION).
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "target_project_id")
    private Job targetProject;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private SubscriptionStatus status;
}
