package com.codemastery.modules.interaction.infrastructure.persistence.repository;

import com.codemastery.modules.interaction.infrastructure.persistence.entity.NotificationEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

/**
 * Spring Data access to {@code public.notifications}. Writes are native statements that touch
 * only the columns ADR012 §19.2 allows: INSERT, and UPDATE of {@code is_read}. There is no
 * {@code save()}: a merge would issue a full-row UPDATE.
 */
public interface NotificationJpaRepository extends Repository<NotificationEntity, UUID> {

    Page<NotificationEntity> findByUserIdOrderByCreatedAtDescIdDesc(UUID userId, Pageable pageable);

    Page<NotificationEntity> findByUserIdAndReadFalseOrderByCreatedAtDescIdDesc(UUID userId, Pageable pageable);

    long countByUserIdAndReadFalse(UUID userId);

    Optional<NotificationEntity> findByIdAndUserId(UUID id, UUID userId);

    /** Returns 1 for a new row and 0 when the identifier already exists (idempotent creation). */
    @Modifying(flushAutomatically = true)
    @Query(value = "INSERT INTO public.notifications (id, user_id, message, is_read, created_at) "
            + "VALUES (:id, :userId, :message, false, :createdAt) ON CONFLICT (id) DO NOTHING",
            nativeQuery = true)
    int insertIfAbsent(@Param("id") UUID id, @Param("userId") UUID userId,
                       @Param("message") String message, @Param("createdAt") Instant createdAt);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query(value = "UPDATE public.notifications SET is_read = true "
            + "WHERE id = :id AND user_id = :userId AND is_read = false", nativeQuery = true)
    int markRead(@Param("id") UUID id, @Param("userId") UUID userId);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query(value = "UPDATE public.notifications SET is_read = true "
            + "WHERE user_id = :userId AND is_read = false", nativeQuery = true)
    int markAllRead(@Param("userId") UUID userId);
}
