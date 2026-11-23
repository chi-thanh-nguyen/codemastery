package com.codemastery.modules.course.infrastructure.persistence.mapper;

import com.codemastery.modules.course.domain.model.Course;
import com.codemastery.modules.course.domain.model.PublishStatus;
import com.codemastery.modules.course.infrastructure.persistence.entity.CourseEntity;
import org.springframework.stereotype.Component;
import tools.jackson.core.StreamReadFeature;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.json.JsonMapper;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Map;
import java.util.UUID;


@Component
public class CourseMapper {

    /** Exact jsonb codec for courses.adaptive_settings: decimals stay BigDecimal, duplicates fail. */
    private static final JsonMapper JSON = JsonMapper.builder()
            .enable(DeserializationFeature.USE_BIG_DECIMAL_FOR_FLOATS)
            .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS)
            .enable(StreamReadFeature.STRICT_DUPLICATE_DETECTION)
            .build();
    private static final TypeReference<Map<String, Object>> JSON_OBJECT = new TypeReference<>() {};

    public CourseEntity toEntity(Course domain) {
        if (domain == null) {
            return null;
        }
        return new CourseEntity(
                domain.id(),
                domain.instructorId(),
                domain.title(),
                domain.description(),
                domain.prerequisiteDescription(),
                toCode(domain.publicationStatus()),
                domain.adaptiveEnabled(),
                domain.adaptiveSettings());
    }

    public Course toDomain(CourseEntity entity) {
        if (entity == null) {
            return null;
        }
        return new Course(
                entity.getId(),
                entity.getInstructorId(),
                entity.getTitle(),
                entity.getDescription(),
                entity.getPrerequisiteDescription(),
                toPublishStatus(entity.getPublicationStatus()),
                entity.isAdaptiveEnabled(),
                entity.getAdaptiveSettings());
    }

    static String toCode(PublishStatus status) {
        return status == null ? null : status.getStatus();
    }

    /** Converts string code to PublishStatus, case-insensitively. */
    static PublishStatus toPublishStatus(String code) {
        if (code == null) {
            return null;
        }
        for (PublishStatus status : PublishStatus.values()) {
            if (status.getStatus().equalsIgnoreCase(code) || status.name().equalsIgnoreCase(code)) {
                return status;
            }
        }
        throw new IllegalArgumentException("Unknown publication status code: " + code);
    }

    /**
     * Maps one public.courses row read by private JDBC persistence. The row must carry
     * id, instructor_id, title, description, prerequisite_description, publication_status,
     * adaptive_enabled and adaptive_settings (as jsonb text).
     */
    public Course courseFromRow(ResultSet row) throws SQLException {
        return new Course(
                row.getObject("id", UUID.class),
                row.getObject("instructor_id", UUID.class),
                row.getString("title"),
                row.getString("description"),
                row.getString("prerequisite_description"),
                publicationFromCode(row.getString("publication_status")),
                row.getBoolean("adaptive_enabled"),
                settingsFromJson(row.getString("adaptive_settings")));
    }

    /** Parses stored jsonb text into a deeply immutable object; SQL NULL stays null. */
    public static Map<String, Object> settingsFromJson(String json) {
        if (json == null) {
            return null;
        }
        Map<String, Object> parsed = JSON.readValue(json, JSON_OBJECT);
        if (parsed == null) {
            throw new IllegalStateException("Stored adaptive settings are not a JSON object");
        }
        return Course.immutableJsonObject(parsed);
    }

    /** Serializes a settings object for a jsonb parameter; null stays SQL NULL. */
    public static String settingsToJson(Map<String, Object> settings) {
        return settings == null ? null : JSON.writeValueAsString(Course.immutableJsonObject(settings));
    }

    /** Exact V002 publication code for one row value; any other code is an integrity failure. */
    public static PublishStatus publicationFromCode(String code) {
        for (PublishStatus status : PublishStatus.values()) {
            if (status.getStatus().equals(code)) {
                return status;
            }
        }
        throw new IllegalStateException("Invalid stored publication status");
    }
}
