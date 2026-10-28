package com.sam.be.modules.auth.entity;

import com.sam.be.modules.user.entity.User;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.LocalDateTime;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(
        name = "user_google_identities",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_user_google_issuer_subject", columnNames = {"issuer", "subject"}))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class GoogleIdentity {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false, unique = true)
    private User user;

    @Column(nullable = false, length = 100)
    private String issuer;

    @Column(nullable = false, length = 255)
    private String subject;

    @Column(name = "email_at_link", nullable = false)
    private String emailAtLink;

    @Builder.Default
    @Column(name = "linked_at", nullable = false)
    private LocalDateTime linkedAt = LocalDateTime.now();
}
