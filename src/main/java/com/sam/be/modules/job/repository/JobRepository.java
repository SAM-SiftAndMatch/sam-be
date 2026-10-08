package com.sam.be.modules.job.repository;

import com.sam.be.modules.job.entity.Job;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface JobRepository extends JpaRepository<Job, UUID> {

    @Query(
            "SELECT DISTINCT j FROM Job j "
                    + "LEFT JOIN FETCH j.client "
                    + "LEFT JOIN FETCH j.jobSkills js "
                    + "LEFT JOIN FETCH js.skill "
                    + "WHERE j.client.id = :clientId "
                    + "ORDER BY j.createdAt DESC")
    List<Job> findAllByClientId(@Param("clientId") UUID clientId);

    @Query(
            "SELECT j FROM Job j "
                    + "LEFT JOIN FETCH j.client "
                    + "LEFT JOIN FETCH j.jobSkills js "
                    + "LEFT JOIN FETCH js.skill "
                    + "WHERE j.id = :id")
    Optional<Job> findByIdWithDetails(@Param("id") UUID id);
}
