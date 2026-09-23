package com.sam.be.modules.auth.repository;

import com.sam.be.modules.auth.entity.Session;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface SessionRepository extends JpaRepository<Session, UUID> {

    Optional<Session> findByRefreshTokenAndIsRevokedFalse(String refreshToken);

    Optional<Session> findByRefreshToken(String refreshToken);

    Optional<Session> findByIdAndIsRevokedFalse(UUID id);

    int countByUserIdAndIsRevokedFalse(UUID userId);

    @Modifying
    @Query(
            "UPDATE Session s SET s.isRevoked = true WHERE s.user.id = :userId AND s.isRevoked = false")
    void revokeAllByUserId(@Param("userId") UUID userId);

    @Query(
            "SELECT CASE WHEN COUNT(s) > 0 THEN true ELSE false END "
                    + "FROM Session s WHERE s.id = :sessionId AND s.isRevoked = false AND s.expiresAt > CURRENT_TIMESTAMP")
    boolean isSessionActive(@Param("sessionId") UUID sessionId);
}
