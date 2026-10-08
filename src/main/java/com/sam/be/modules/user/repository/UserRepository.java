package com.sam.be.modules.user.repository;

import com.sam.be.modules.user.entity.User;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface UserRepository extends JpaRepository<User, UUID> {
    Optional<User> findByEmail(String email);

    boolean existsByEmail(String email);

    @Query("SELECT u FROM User u LEFT JOIN FETCH u.freelancerProfile WHERE u.id = :userId")
    Optional<User> findByIdWithFreelancerProfile(@Param("userId") UUID userId);

    @Query("SELECT u FROM User u LEFT JOIN FETCH u.clientProfile WHERE u.id = :userId")
    Optional<User> findByIdWithClientProfile(@Param("userId") UUID userId);
}
