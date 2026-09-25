package com.sam.be.modules.job.entity;

import com.sam.be.modules.skill.entity.Skill;
import jakarta.persistence.*;
import java.io.Serializable;
import java.util.UUID;
import lombok.*;

@Entity
@Table(name = "job_skills")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class JobSkill {

    @EmbeddedId
    private JobSkillId id;

    @ManyToOne(fetch = FetchType.LAZY)
    @MapsId("jobId")
    @JoinColumn(name = "job_id")
    private Job job;

    @ManyToOne(fetch = FetchType.LAZY)
    @MapsId("skillId")
    @JoinColumn(name = "skill_id")
    private Skill skill;

    @Column(name = "required_years_of_experience")
    private Integer requiredYearsOfExperience;

    @Embeddable
    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @EqualsAndHashCode
    public static class JobSkillId implements Serializable {
        private UUID jobId;
        private Integer skillId;
    }
}