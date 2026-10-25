package com.smartrecruit.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.ForeignKey;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;

/**
 * Compétence d'un candidat. Le nom est unique par profil (pas de doublon "React" / "react").
 */
@Entity
@Table(
        name = "candidate_skills",
        uniqueConstraints = @UniqueConstraint(name = "uk_candidate_skills_profile_name",
                columnNames = {"profile_id", "name"}),
        indexes = @Index(name = "idx_candidate_skills_name", columnList = "name")
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CandidateSkill {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "profile_id", nullable = false,
            foreignKey = @ForeignKey(name = "fk_candidate_skills_profile"))
    @OnDelete(action = OnDeleteAction.CASCADE)
    private CandidateProfile profile;

    @Column(name = "name", nullable = false, length = 80)
    private String name;

    @Builder.Default
    @Enumerated(EnumType.STRING)
    @Column(name = "source", nullable = false, length = 10)
    private SkillSource source = SkillSource.MANUAL;
}
