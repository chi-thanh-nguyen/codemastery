package com.codemastery.modules.adaptive.infrastructure.persistence.repository;

import com.codemastery.common.exception.BusinessRuleException;
import com.codemastery.modules.adaptive.domain.model.LessonSkill;
import com.codemastery.modules.adaptive.domain.model.Skill;
import com.codemastery.modules.adaptive.domain.model.SkillPrerequisite;
import com.codemastery.modules.adaptive.domain.repository.SkillRepository;
import com.codemastery.modules.adaptive.infrastructure.persistence.entity.LessonSkillEntity;
import com.codemastery.modules.adaptive.infrastructure.persistence.entity.SkillEntity;
import com.codemastery.modules.adaptive.infrastructure.persistence.entity.SkillPrerequisiteEntity;
import jakarta.persistence.PersistenceException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Repository;

import java.sql.SQLException;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

@Repository
public class SkillRepositoryImpl implements SkillRepository {

    private static final String FOREIGN_KEY_VIOLATION = "23503";

    private final SkillJpaRepository skills;
    private final SkillPrerequisiteJpaRepository prerequisites;
    private final LessonSkillJpaRepository mappings;

    public SkillRepositoryImpl(SkillJpaRepository skills,
            SkillPrerequisiteJpaRepository prerequisites,
            LessonSkillJpaRepository mappings) {
        this.skills = Objects.requireNonNull(skills);
        this.prerequisites = Objects.requireNonNull(prerequisites);
        this.mappings = Objects.requireNonNull(mappings);
    }

    @Override
    public List<Skill> findSkills(UUID courseId) {
        if (courseId == null) {
            return List.of();
        }
        return skills.findByCourseId(courseId).stream().map(SkillRepositoryImpl::toDomain).toList();
    }

    @Override
    public Optional<Skill> findSkill(UUID courseId, UUID skillId) {
        if (courseId == null || skillId == null) {
            return Optional.empty();
        }
        return skills.findByCourseIdAndId(courseId, skillId).map(SkillRepositoryImpl::toDomain);
    }

    @Override
    public Skill insertSkill(Skill skill) {
        Objects.requireNonNull(skill, "skill");
        return toDomain(skills.save(new SkillEntity(skill.id(), skill.courseId(), skill.name(), skill.description())));
    }

    @Override
    public Skill updateSkill(Skill skill) {
        Objects.requireNonNull(skill, "skill");
        if (skills.updateText(skill.courseId(), skill.id(), skill.name(), skill.description()) != 1) {
            throw new IllegalStateException("Skill update lost its row");
        }
        return skill;
    }

    @Override
    public void deleteSkill(UUID courseId, UUID skillId) {
        try {
            if (skills.deleteScoped(courseId, skillId) != 1) {
                throw new IllegalStateException("Skill removal lost its row");
            }
        } catch (DataIntegrityViolationException | PersistenceException conflict) {

            if (isForeignKeyViolation(conflict)) {
                throw new BusinessRuleException(HttpStatus.CONFLICT, "A referenced skill cannot be deleted.");
            }
            throw conflict;
        }
    }

    @Override
    public List<SkillPrerequisite> findPrerequisites(UUID courseId) {
        if (courseId == null) {
            return List.of();
        }
        return prerequisites.findByCourseId(courseId).stream()
                .map(SkillRepositoryImpl::toDomain).toList();
    }

    @Override
    public Optional<SkillPrerequisite> findPrerequisite(UUID courseId, UUID skillId, UUID prerequisiteSkillId) {
        if (courseId == null || skillId == null || prerequisiteSkillId == null) {
            return Optional.empty();
        }
        return prerequisites.findByCourseIdAndSkillIdAndPrerequisiteSkillId(courseId, skillId, prerequisiteSkillId)
                .map(SkillRepositoryImpl::toDomain);
    }

    @Override
    public SkillPrerequisite insertPrerequisite(SkillPrerequisite prerequisite) {
        Objects.requireNonNull(prerequisite, "prerequisite");
        return toDomain(prerequisites.save(new SkillPrerequisiteEntity(prerequisite.id(),
                prerequisite.courseId(), prerequisite.skillId(), prerequisite.prerequisiteSkillId())));
    }

    @Override
    public void deletePrerequisite(UUID courseId, UUID skillId, UUID prerequisiteSkillId) {
        if (prerequisites.deleteEdge(courseId, skillId, prerequisiteSkillId) != 1) {
            throw new IllegalStateException("Prerequisite removal lost its row");
        }
    }

    @Override
    public List<LessonSkill> findMappingsForSkills(Collection<UUID> skillIds) {
        if (skillIds == null || skillIds.isEmpty()) {
            return List.of();
        }
        return mappings.findBySkillIdIn(skillIds).stream().map(SkillRepositoryImpl::toDomain).toList();
    }

    @Override
    public Optional<LessonSkill> findMapping(UUID lessonId, UUID skillId) {
        if (lessonId == null || skillId == null) {
            return Optional.empty();
        }
        return mappings.findByLessonIdAndSkillId(lessonId, skillId).map(SkillRepositoryImpl::toDomain);
    }

    @Override
    public LessonSkill insertMapping(LessonSkill mapping) {
        Objects.requireNonNull(mapping, "mapping");
        return toDomain(mappings.save(new LessonSkillEntity(mapping.id(), mapping.lessonId(), mapping.skillId())));
    }

    @Override
    public void deleteMapping(UUID lessonId, UUID skillId) {
        if (mappings.deletePair(lessonId, skillId) != 1) {
            throw new IllegalStateException("Lesson mapping removal lost its row");
        }
    }

    private static boolean isForeignKeyViolation(Throwable failure) {
        for (Throwable current = failure; current != null; current = current.getCause()) {
            if (current instanceof SQLException sql && FOREIGN_KEY_VIOLATION.equals(sql.getSQLState())) {
                return true;
            }
        }
        return false;
    }

    private static Skill toDomain(SkillEntity entity) {
        return new Skill(entity.getId(), entity.getCourseId(), entity.getName(), entity.getDescription());
    }

    private static SkillPrerequisite toDomain(SkillPrerequisiteEntity entity) {
        return new SkillPrerequisite(entity.getId(), entity.getCourseId(),
                entity.getSkillId(), entity.getPrerequisiteSkillId());
    }

    private static LessonSkill toDomain(LessonSkillEntity entity) {
        return new LessonSkill(entity.getId(), entity.getLessonId(), entity.getSkillId());
    }
}