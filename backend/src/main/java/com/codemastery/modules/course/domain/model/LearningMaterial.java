package com.codemastery.modules.course.domain.model;

import java.util.UUID;

public record LearningMaterial(UUID id,
                               UUID lessonId,
                               String title,
                               MaterialType materialType,
                               PublishStatus publicationStatus,
                               String textContent,
                               String objectKey,
                               String originalFileName,
                               String externalVideoUrl
                               ) {
}