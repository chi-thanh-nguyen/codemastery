package com.codemastery.modules.interaction.infrastructure.persistence.mapper;

import com.codemastery.modules.interaction.domain.model.ContentReport;
import com.codemastery.modules.interaction.domain.model.DiscussionPost;
import com.codemastery.modules.interaction.infrastructure.persistence.entity.ContentReportEntity;
import com.codemastery.modules.interaction.infrastructure.persistence.entity.DiscussionPostEntity;
import org.springframework.stereotype.Component;

@Component
public class InteractionMapper {

    public DiscussionPostEntity toEntity(DiscussionPost domain) {
        if (domain == null) {
            return null;
        }
        return new DiscussionPostEntity(
                domain.getId(),
                domain.getLessonId(),
                domain.getAuthorId(),
                domain.getParentPostId(),
                domain.getContent(),
                domain.getCreatedAt(),
                domain.isHidden());
    }

    public DiscussionPost toDomain(DiscussionPostEntity entity) {
        if (entity == null) {
            return null;
        }
        return new DiscussionPost(
                entity.getId(),
                entity.getLessonId(),
                entity.getAuthorId(),
                entity.getParentPostId(),
                entity.getContent(),
                entity.getCreatedAt(),
                entity.isHidden());
    }

    public ContentReportEntity toEntity(ContentReport domain) {
        if (domain == null) {
            return null;
        }
        return new ContentReportEntity(
                domain.getId(),
                domain.getReporterId(),
                domain.getDiscussionPostId(),
                domain.getLessonId(),
                domain.getReason(),
                toCode(domain.getStatus()),
                domain.getCreatedAt(),
                domain.getResolvedAt());
    }

    public ContentReport toDomain(ContentReportEntity entity) {
        if (entity == null) {
            return null;
        }
        return new ContentReport(
                entity.getId(),
                entity.getReporterId(),
                entity.getDiscussionPostId(),
                entity.getLessonId(),
                entity.getReason(),
                toStatus(entity.getStatus()),
                entity.getCreatedAt(),
                entity.getResolvedAt());
    }

    static String toCode(ContentReport.Status status) {
        return status == null ? null : status.getCode();
    }

    /** Exact match on the stored V006 code ({@code open}, {@code resolved}, {@code dismissed}). */
    static ContentReport.Status toStatus(String code) {
        if (code == null) {
            return null;
        }
        for (ContentReport.Status status : ContentReport.Status.values()) {
            if (status.getCode().equals(code)) {
                return status;
            }
        }
        throw new IllegalArgumentException("Unknown content report status code");
    }
}
