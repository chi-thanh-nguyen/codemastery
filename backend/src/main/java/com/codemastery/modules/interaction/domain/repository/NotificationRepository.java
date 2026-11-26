package com.codemastery.modules.interaction.domain.repository;

import com.codemastery.modules.interaction.domain.model.Notification;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Persistence port of the in-app notifications (table {@code notifications}, V006).
 * CT11 (ADR012 §4): creation is internal and idempotent; reads and mark-read are owner-scoped.
 */
public interface NotificationRepository {

    /**
     * Inserts the rows in the given order with {@code ON CONFLICT (id) DO NOTHING} and returns
     * the number of rows that were actually new. Requires the caller's read-write transaction.
     * The caller owns de-duplication and the ascending-id order (ADR012 §4.1, r2).
     */
    int insertNew(List<Notification> notifications);

    /**
     * Own notifications, newest first ({@code created_at} desc, then {@code id} desc). The order
     * is fixed by the query; any sort in {@code pageable} is ignored.
     */
    Page<Notification> findOwned(UUID userId, boolean unreadOnly, Pageable pageable);

    long countUnread(UUID userId);

    /**
     * Sets {@code is_read = true} on an own row and returns it. Idempotent. A missing row and
     * another user's row are both {@link Optional#empty()}.
     */
    Optional<Notification> markRead(UUID id, UUID userId);

    /** Marks every own unread row read in one statement; returns the number of rows changed. */
    int markAllRead(UUID userId);
}
