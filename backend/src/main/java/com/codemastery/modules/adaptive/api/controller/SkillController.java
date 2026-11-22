package com.codemastery.modules.adaptive.api.controller;

import com.codemastery.common.exception.BusinessRuleException;
import com.codemastery.modules.adaptive.api.dto.SkillGraphResponse;
import com.codemastery.modules.adaptive.api.dto.SkillRequests.SkillWrite;
import com.codemastery.modules.adaptive.api.dto.SkillResponse;
import com.codemastery.modules.adaptive.application.skillgraph.SkillGraphService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.util.MultiValueMap;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;
import java.util.regex.Pattern;

@RestController
@RequestMapping("/api/courses/{courseId}")
public class SkillController {

    private static final Pattern UUID_TEXT = Pattern.compile(
            "[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}");

    private final SkillGraphService graph;

    public SkillController(SkillGraphService graph) {
        this.graph = graph;
    }

    @GetMapping("/skill-graph")
    public SkillGraphResponse readGraph(@PathVariable String courseId,
            @RequestParam MultiValueMap<String, String> params) {
        noQuery(params);
        return SkillGraphResponse.from(graph.readGraph(id(courseId)));
    }

    @PostMapping(path = "/skills", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<SkillResponse> create(@PathVariable String courseId,
            @RequestParam MultiValueMap<String, String> params,
            @Valid @RequestBody SkillWrite body) {
        noQuery(params);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(SkillResponse.from(graph.createSkill(id(courseId), body.name(), body.description())));
    }

    @PutMapping(path = "/skills/{skillId}", consumes = MediaType.APPLICATION_JSON_VALUE)
    public SkillResponse update(@PathVariable String courseId, @PathVariable String skillId,
            @RequestParam MultiValueMap<String, String> params,
            @Valid @RequestBody SkillWrite body) {
        noQuery(params);
        return SkillResponse.from(graph.updateSkill(
                id(courseId), id(skillId), body.name(), body.description()));
    }

    @DeleteMapping("/skills/{skillId}")
    public ResponseEntity<Void> delete(@PathVariable String courseId, @PathVariable String skillId,
            @RequestParam MultiValueMap<String, String> params) {
        noQuery(params);
        graph.deleteSkill(id(courseId), id(skillId));
        return ResponseEntity.noContent().build();
    }

    @PutMapping("/skills/{skillId}/prerequisites/{prerequisiteSkillId}")
    public ResponseEntity<Void> addPrerequisite(@PathVariable String courseId,
            @PathVariable String skillId,
            @PathVariable String prerequisiteSkillId,
            @RequestParam MultiValueMap<String, String> params) {
        noQuery(params);
        graph.addPrerequisite(id(courseId), id(skillId), id(prerequisiteSkillId));
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/skills/{skillId}/prerequisites/{prerequisiteSkillId}")
    public ResponseEntity<Void> removePrerequisite(@PathVariable String courseId,
            @PathVariable String skillId,
            @PathVariable String prerequisiteSkillId,
            @RequestParam MultiValueMap<String, String> params) {
        noQuery(params);
        graph.removePrerequisite(id(courseId), id(skillId), id(prerequisiteSkillId));
        return ResponseEntity.noContent().build();
    }

    @PutMapping("/lessons/{lessonId}/skills/{skillId}")
    public ResponseEntity<Void> mapLesson(@PathVariable String courseId,
            @PathVariable String lessonId,
            @PathVariable String skillId,
            @RequestParam MultiValueMap<String, String> params) {
        noQuery(params);
        graph.mapLesson(id(courseId), id(lessonId), id(skillId));
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/lessons/{lessonId}/skills/{skillId}")
    public ResponseEntity<Void> unmapLesson(@PathVariable String courseId,
            @PathVariable String lessonId,
            @PathVariable String skillId,
            @RequestParam MultiValueMap<String, String> params) {
        noQuery(params);
        graph.unmapLesson(id(courseId), id(lessonId), id(skillId));
        return ResponseEntity.noContent().build();
    }

    private static UUID id(String value) {
        if (value == null || !UUID_TEXT.matcher(value).matches()) {
            throw new BusinessRuleException(HttpStatus.BAD_REQUEST, "Invalid path parameter");
        }
        return UUID.fromString(value);
    }

    private static void noQuery(MultiValueMap<String, String> params) {
        if (!params.isEmpty()) {
            throw new BusinessRuleException(HttpStatus.BAD_REQUEST, "Invalid query");
        }
    }
}