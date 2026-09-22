package com.sam.be.modules.user.entity;

import com.sam.be.modules.skill.entity.Skill;
import jakarta.persistence.*;
import java.io.Serializable;
import java.util.UUID;
import lombok.*;

@Entity
@Table(name = "freelancer_skills")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class FreelancerSkill {

    @EmbeddedId private FreelancerSkillId id;

    @ManyToOne(fetch = FetchType.LAZY)
    @MapsId("freelancerId")
    @JoinColumn(name = "freelancer_id")
    private User freelancer;

    @ManyToOne(fetch = FetchType.LAZY)
    @MapsId("skillId")
    @JoinColumn(name = "skill_id")
    private Skill skill;

    @Column(name = "years_of_experience")
    private Integer yearsOfExperience;

    @Embeddable
    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @EqualsAndHashCode
    public static class FreelancerSkillId implements Serializable {
        private UUID freelancerId;
        private Integer skillId;
    }
}
