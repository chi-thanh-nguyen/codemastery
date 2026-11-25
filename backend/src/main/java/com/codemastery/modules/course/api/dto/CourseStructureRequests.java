package com.codemastery.modules.course.api.dto;

import com.codemastery.common.exception.BusinessRuleException;
import com.codemastery.modules.course.domain.model.MaterialType;
import tools.jackson.databind.JsonNode;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/** CT21.authoring Chapter/Lesson bodies (OpenAPI ChapterWrite, LessonWrite, SiblingOrder), parsed like {@link CourseRequest}. */
public final class CourseStructureRequests {
    private CourseStructureRequests() {}

    public record LessonWrite(String title, String contentMarkdown) {}

    /** OpenAPI RelationalMaterialWrite: TextMaterialWrite or VideoMaterialWrite; the field of the other type is null. */
    public record MaterialWrite(String title, MaterialType materialType, String textContent, String externalVideoUrl) {}

    /** CO09: exactly one of the two closed shapes; text rules and the CT37.embed URL check are the service's. */
    public static Optional<MaterialWrite> relationalMaterialWrite(String json) {
        var text = CourseRequest.object(json, Set.of("title", "materialType", "textContent"))
                .filter(input -> input.get("title").isString() && input.get("materialType").isString()
                        && "text".equals(input.get("materialType").asString()) && input.get("textContent").isString())
                .map(input -> new MaterialWrite(input.get("title").asString(), MaterialType.TEXT,
                        input.get("textContent").asString(), null));
        if (text.isPresent()) return text;
        return CourseRequest.object(json, Set.of("title", "materialType", "externalVideoUrl"))
                .filter(input -> input.get("title").isString() && input.get("materialType").isString()
                        && "external_video".equals(input.get("materialType").asString()) && input.get("externalVideoUrl").isString())
                .map(input -> new MaterialWrite(input.get("title").asString(), MaterialType.EXTERNAL_VIDEO,
                        null, input.get("externalVideoUrl").asString()));
    }

    /** B5FileMaterialTitleWrite has the same closed {@code {title}} shape as ChapterWrite. */
    public static Optional<String> fileMaterialTitle(String json) {
        return chapterTitle(json);
    }

    public static Optional<String> chapterTitle(String json) {
        return CourseRequest.object(json, Set.of("title")).map(input -> input.get("title"))
                .filter(JsonNode::isString).map(JsonNode::asString);
    }

    public static Optional<LessonWrite> lessonWrite(String json) {
        return CourseRequest.object(json, Set.of("title", "contentMarkdown"))
                .filter(input -> input.get("title").isString() && CourseRequest.nullableString(input.get("contentMarkdown")))
                .map(input -> new LessonWrite(input.get("title").asString(), CourseRequest.text(input.get("contentMarkdown"))));
    }

    /** The ordered UUIDs as sent; duplicates and permutation checks belong to the service. */
    public static Optional<List<UUID>> siblingOrder(String json) {
        return CourseRequest.object(json, Set.of("orderedIds")).map(input -> input.get("orderedIds"))
                .filter(JsonNode::isArray).flatMap(array -> {
                    List<UUID> ids = new ArrayList<>(array.size());
                    for (int index = 0; index < array.size(); index++) {
                        JsonNode item = array.get(index);
                        if (!item.isString()) return Optional.empty();
                        try {
                            ids.add(CourseSearchRequest.parseCourseId(item.asString()));
                        } catch (BusinessRuleException invalid) {
                            return Optional.empty();
                        }
                    }
                    return Optional.of(List.copyOf(ids));
                });
    }
}
