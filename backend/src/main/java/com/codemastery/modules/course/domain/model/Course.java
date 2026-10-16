package com.codemastery.modules.course.domain.model;

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
                     ) {}