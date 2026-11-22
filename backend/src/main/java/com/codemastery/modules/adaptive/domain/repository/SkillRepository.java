package com.codemastery.modules.adaptive.domain.repository;

import com.codemastery.modules.adaptive.domain.model.LessonSkill;
import com.codemastery.modules.adaptive.domain.model.Skill;
import com.codemastery.modules.adaptive.domain.model.SkillPrerequisite;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface SkillRepository {

    List<Skill> findSkills(UUID courseId);

    Optional<Skill> findSkill(UUID courseId, UUID skillId);

    Skill insertSkill(Skill skill);

    Skill updateSkill(Skill skill);

    void deleteSkill(UUID courseId, UUID skillId);

    List<SkillPrerequisite> findPrerequisites(UUID courseId);

    Optional<SkillPrerequisite> findPrerequisite(
            UUID courseId,
            UUID skillId,
            UUID prerequisiteSkillId);

    SkillPrerequisite insertPrerequisite(SkillPrerequisite prerequisite);

    void deletePrerequisite(
            UUID courseId,
            UUID skillId,
            UUID prerequisiteSkillId);

    List<LessonSkill> findMappingsForSkills(Collection<UUID> skillIds);

    Optional<LessonSkill> findMapping(UUID lessonId, UUID skillId);

    LessonSkill insertMapping(LessonSkill mapping);

    void deleteMapping(UUID lessonId, UUID skillId);

}