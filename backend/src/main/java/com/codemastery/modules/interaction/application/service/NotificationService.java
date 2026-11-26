package com.codemastery.modules.interaction.application.service;

import com.codemastery.common.api.PageResponse;
import com.codemastery.common.exception.ResourceNotFoundException;
import com.codemastery.modules.interaction.domain.model.Notification;
import com.codemastery.modules.interaction.domain.repository.NotificationRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * In-app notifications (IN07; CT11, ADR012 §4). Delivery is in-app only.
 *
 * <p>{@link #create} is Interaction-internal: only the Interaction listener, scheduler and
 * discussion service call it, and there is no REST creation. The read operations serve the
 * learner REST endpoints and act only on the caller's own rows.
 */
@Service
public class NotificationService {

    private static final String ID_PREFIX = "codemastery/notification/";

    /**
     * Unsigned, big-endian comparison of the 16 UUID bytes. This is PostgreSQL's {@code uuid}
     * ordering, which {@link UUID#compareTo} is not (it compares signed longs), so concurrent
     * creators lock overlapping keys in the same order (ADR012 §4.1, r2).
     */
    private static final Comparator<UUID> POSTGRES_UUID_ORDER = (a, b) -> {
        int high = Long.compareUnsigned(a.getMostSignificantBits(), b.getMostSignificantBits());
        return high != 0 ? high
                : Long.compareUnsigned(a.getLeastSignificantBits(), b.getLeastSignificantBits());
    };

    private final NotificationRepository notifications;
    private final Clock clock;

    @Autowired
    public NotificationService(NotificationRepository notifications) {
        this(notifications, Clock.systemUTC());
    }

    /** Explicit clock seam for deterministic tests (ADR008 §6 local seam, no shared bean). */
    public NotificationService(NotificationRepository notifications, Clock clock) {
        this.notifications = Objects.requireNonNull(notifications);
        this.clock = Objects.requireNonNull(clock);
    }

    /** A notification to create; the identifier must come from {@link #derivedId}. */
    public record NewNotification(UUID id, UUID recipientId, String message) {
    }

    /**
     * Deterministic identifier: {@code UUID.nameUUIDFromBytes} of the UTF-8 string
     * {@code codemastery/notification/{purpose}/{part1}/{part2}...}. Callers pass instants as
     * {@code Instant.toString()} of the microsecond-truncated value and UUIDs in lowercase.
     */
    public static UUID derivedId(String purpose, String... parts) {
        Objects.requireNonNull(purpose, "purpose");
        Objects.requireNonNull(parts, "parts");
        StringBuilder name = new StringBuilder(ID_PREFIX).append(purpose);
        for (String part : parts) {
            name.append('/').append(Objects.requireNonNull(part, "part"));
        }
        return UUID.nameUUIDFromBytes(name.toString().getBytes(StandardCharsets.UTF_8));
    }

    /**
     * Creates the notifications in the caller's transaction and returns the number of new rows.
     * Duplicate identifiers inside the call are collapsed (first wins), rows are inserted in
     * ascending id order with one microsecond-truncated {@code created_at} for the whole call,
     * and an identifier that already exists creates nothing.
     *
     * @throws IllegalStateException if there is no active read-write transaction
     */
    public int create(Collection<NewNotification> requests) {
        Objects.requireNonNull(requests, "notifications");
        if (!TransactionSynchronizationManager.isActualTransactionActive()
                || TransactionSynchronizationManager.isCurrentTransactionReadOnly()) {
            throw new IllegalStateException("Notification creation requires an active read-write transaction");
        }
        Map<UUID, NewNotification> unique = new LinkedHashMap<>();
        for (NewNotification request : requests) {
            Objects.requireNonNull(request, "notification");
            Objects.requireNonNull(request.id(), "notification id");
            Objects.requireNonNull(request.recipientId(), "recipient id");
            if (request.message() == null || request.message().isBlank()) {
                throw new IllegalArgumentException("A notification message must contain a non-whitespace character");
            }
            unique.putIfAbsent(request.id(), request);
        }
        if (unique.isEmpty()) {
            return 0;
        }
        Instant createdAt = clock.instant().truncatedTo(ChronoUnit.MICROS);
        List<Notification> rows = new ArrayList<>(unique.size());
        unique.values().stream()
                .sorted(Comparator.comparing(NewNotification::id, POSTGRES_UUID_ORDER))
                .forEach(n -> rows.add(new Notification(n.id(), n.recipientId(), n.message(), false, createdAt)));
        return notifications.insertNew(rows);
    }

    /** Own notifications, newest first. Invalid {@code page} or {@code size} is a 400 (CT18). */
    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public Page<Notification> listOwn(UUID userId, boolean unreadOnly, int page, int size) {
        Objects.requireNonNull(userId, "userId");
        PageResponse.validateRequest(page, size);
        return notifications.findOwned(userId, unreadOnly, PageRequest.of(page, size));
    }

    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public long unreadCount(UUID userId) {
        Objects.requireNonNull(userId, "userId");
        return notifications.countUnread(userId);
    }

    /** Idempotent. A missing and a foreign identifier are both a 404. */
    @Transactional
    public Notification markRead(UUID userId, UUID notificationId) {
        Objects.requireNonNull(userId, "userId");
        Objects.requireNonNull(notificationId, "notificationId");
        return notifications.markRead(notificationId, userId)
                .orElseThrow(() -> new ResourceNotFoundException("Notification unavailable"));
    }

    @Transactional
    public int markAllRead(UUID userId) {
        Objects.requireNonNull(userId, "userId");
        return notifications.markAllRead(userId);
    }
}
