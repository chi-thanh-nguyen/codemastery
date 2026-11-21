package com.codemastery.modules.course.domain.model;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public record Course(UUID id,
                     UUID instructorId,
                     String title,
                     String description,
                     String prerequisiteDescription,
                     PublishStatus publicationStatus,
                     boolean adaptiveEnabled,
                     Map<String, Object> adaptiveSettings
                     ) {

    /**
     * The stored settings object is copied, so a domain value never aliases mutable
     * persistence state that a later flush could write back. No key, value or default
     * is interpreted here; the closed policy schema belongs to Adaptive.
     */
    public Course {
        adaptiveSettings = adaptiveSettings == null ? null : immutableJsonObject(adaptiveSettings);
    }

    /**
     * Deeply immutable copy of a JSON object: nested objects and arrays become
     * unmodifiable, and only JSON-representable values (string, boolean, finite
     * number, null) are accepted. Key order is preserved; null values are kept.
     */
    public static Map<String, Object> immutableJsonObject(Map<?, ?> object) {
        if (object == null) {
            throw new IllegalArgumentException("A JSON object must not be null");
        }
        Map<String, Object> copy = new LinkedHashMap<>();
        for (Map.Entry<?, ?> entry : object.entrySet()) {
            if (!(entry.getKey() instanceof String key)) {
                throw new IllegalArgumentException("JSON object keys must be strings");
            }
            copy.put(key, immutableJsonValue(entry.getValue()));
        }
        return Collections.unmodifiableMap(copy);
    }

    private static Object immutableJsonValue(Object value) {
        if (value == null || value instanceof String || value instanceof Boolean
                || value instanceof Integer || value instanceof Long || value instanceof Short
                || value instanceof Byte || value instanceof BigInteger || value instanceof BigDecimal) {
            return value;
        }
        if (value instanceof Double number && Double.isFinite(number)
                || value instanceof Float decimal && Float.isFinite(decimal)) {
            return value;
        }
        if (value instanceof Map<?, ?> object) {
            return immutableJsonObject(object);
        }
        if (value instanceof List<?> array) {
            List<Object> copy = new ArrayList<>(array.size());
            for (Object element : array) {
                copy.add(immutableJsonValue(element));
            }
            return Collections.unmodifiableList(copy);
        }
        throw new IllegalArgumentException("Unsupported JSON value");
    }
}
