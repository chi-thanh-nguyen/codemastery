package com.codemastery.modules.interaction.infrastructure.persistence.repository;

import com.codemastery.modules.interaction.domain.model.Notification;
import com.codemastery.modules.interaction.domain.repository.NotificationRepository;
import com.codemastery.modules.interaction.infrastructure.persistence.entity.NotificationEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * Adapter for {@link NotificationRepository} (IN07; CT11, ADR012 §§4, 19.2).
 *
 * <p>Creation is one native {@code INSERT ... ON CONFLICT (id) DO NOTHING} per row, executed at
 * the call in the caller's order, and joins the caller's transaction ({@link Propagation#MANDATORY}).
 * Mark-read updates only {@code is_read}; no full-row update exists. Every read and write is
 * scoped by {@code user_id}, so a foreign row is indistinguishable from a missing one.
 */
@Repository
public class NotificationRepositoryImpl implements NotificationRepository {

    private final NotificationJpaRepository jpa;

    public NotificationRepositoryImpl(NotificationJpaRepository jpa) {
        this.jpa = Objects.requireNonNull(jpa);
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public int insertNew(List<Notification> notifications) {
        Objects.requireNonNull(notifications, "notifications");
        int created = 0;
        for (Notification notification : notifications) {
            Objects.requireNonNull(notification, "notification");
            created += jpa.insertIfAbsent(notification.getId(), notification.getUserId(),
                    notification.getMessage(), notification.getCreatedAt());
        }
        return created;
    }

    @Override
    public Page<Notification> findOwned(UUID userId, boolean unreadOnly, Pageable pageable) {
        Objects.requireNonNull(userId, "userId");
        Objects.requireNonNull(pageable, "pageable");
        // The order is part of the derived query name; drop any caller sort so it cannot change it.
        Pageable unsorted = PageRequest.of(pageable.getPageNumber(), pageable.getPageSize());
        Page<NotificationEntity> page = unreadOnly
                ? jpa.findByUserIdAndReadFalseOrderByCreatedAtDescIdDesc(userId, unsorted)
                : jpa.findByUserIdOrderByCreatedAtDescIdDesc(userId, unsorted);
        return page.map(NotificationRepositoryImpl::toDomain);
    }

    @Override
    public long countUnread(UUID userId) {
        Objects.requireNonNull(userId, "userId");
        return jpa.countByUserIdAndReadFalse(userId);
    }

    @Override
    public Optional<Notification> markRead(UUID id, UUID userId) {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(userId, "userId");
        jpa.markRead(id, userId);
        // Re-read after the update: the row is returned whether it just changed or was already read.
        return jpa.findByIdAndUserId(id, userId).map(NotificationRepositoryImpl::toDomain);
    }

    @Override
    public int markAllRead(UUID userId) {
        Objects.requireNonNull(userId, "userId");
        return jpa.markAllRead(userId);
    }

    private static Notification toDomain(NotificationEntity entity) {
        return new Notification(entity.getId(), entity.getUserId(), entity.getMessage(),
                entity.isRead(), entity.getCreatedAt());
    }
}
