package com.sam.be.modules.user.entity;

import com.sam.be.common.audit.AbstractAuditEntity;
import com.sam.be.common.constant.enums.UserRole;
import jakarta.persistence.*;
import java.util.UUID;
import lombok.*;

@Entity
@Table(name = "users")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class User extends AbstractAuditEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false, unique = true)
    private String email;

    @Column(name = "password_hash")
    private String passwordHash;

    @Column(name = "full_name", nullable = false)
    private String fullName;

    @Column(name = "contract_name")
    private String contractName;

    @Column(name = "contact_email")
    private String contactEmail;

    @Column(name = "phone_number")
    private String phoneNumber;

    @Column(name = "policy_accepted_at")
    private java.time.Instant policyAcceptedAt;

    @Column(name = "policy_version")
    private String policyVersion;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private UserRole role;

    @Builder.Default
    @Column(name = "is_active", nullable = false)
    private Boolean isActive = true;
}
