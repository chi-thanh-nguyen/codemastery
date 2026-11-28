package com.codemastery.modules.auth.application.service;

import com.codemastery.modules.auth.domain.model.Role;
import com.codemastery.modules.auth.domain.repository.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.function.Supplier;

/** Approved CT01.identity reads; security principal adaptation stays in Auth infrastructure. */
@Service
public class UserAccountService {

    /** CT01.user-summaries: callers split larger sets into chunks of at most this size. */
    private static final int MAX_USER_SUMMARY_IDS = 100;

    private final UserRepository users;
    private final Supplier<CurrentIdentity> currentIdentity;

    public UserAccountService(UserRepository users, Supplier<CurrentIdentity> currentIdentity) {
        this.users = Objects.requireNonNull(users);
        this.currentIdentity = Objects.requireNonNull(currentIdentity);
    }

    /** Reads the identity already validated by Auth for this request; never caches or revalidates it. */
    public CurrentIdentity requireCurrentIdentity() {
        CurrentIdentity identity = currentIdentity.get();
        if (identity == null || identity.userId() == null || identity.role() == null) {
            throw new IllegalStateException("Invalid current identity provider result");
        }
        return identity;
    }

    public record CurrentIdentity(UUID userId, IdentityRole role) {}
    public enum IdentityRole { LEARNER, INSTRUCTOR, ADMINISTRATOR }

    /** Callers supply IDs from their approved published-course page/detail scope. */
    @Transactional(readOnly = true)
    public Map<UUID, String> instructorDisplayNames(Set<UUID> instructorIds) {
        Map<UUID, String> names = new HashMap<>();
        for (UUID id : instructorIds) {
            users.findById(id).filter(user -> user.role() == Role.INSTRUCTOR)
                    .ifPresent(user -> names.put(id, user.displayName()));
        }
        return Map.copyOf(names);
    }

    /**
     * CT01-LEARNER-NAMES-2026-11-12-r1: Course-only stored-name projection for an
     * owner-admitted page. Historical memberships and later role/status changes retain names.
     */
    @Transactional(readOnly = true)
    public Map<UUID, String> learnerDisplayNames(Set<UUID> learnerIds) {
        Set<UUID> ids = Set.copyOf(learnerIds);
        if (ids.size() > 100) {
            throw new IllegalArgumentException("At most 100 learner IDs per call");
        }
        Map<UUID, String> names = new HashMap<>();
        for (UUID id : ids) {
            users.findById(id).ifPresent(user -> names.put(id, user.displayName()));
        }
        return Map.copyOf(names);
    }

    /**
     * CT01.user-summaries (ADR012 section 3.4): the stored display name and current role literal
     * ({@code learner}, {@code instructor} or {@code admin}) of existing accounts. Unknown IDs are
     * omitted. Callers pass only IDs taken from rows they are returning, at most 100 per call; this
     * is not a user directory. No email, account status or credential data is returned.
     */
    @Transactional(readOnly = true)
    public Map<UUID, UserSummary> userSummaries(Set<UUID> userIds) {
        Objects.requireNonNull(userIds, "userIds");
        if (userIds.size() > MAX_USER_SUMMARY_IDS) {
            throw new IllegalArgumentException("At most " + MAX_USER_SUMMARY_IDS + " user IDs per call");
        }
        Map<UUID, UserSummary> summaries = new HashMap<>();
        for (UUID id : userIds) {
            Objects.requireNonNull(id, "userId");
            users.findById(id).ifPresent(user -> summaries.put(id,
                    new UserSummary(id, user.displayName(), user.role().getCode())));
        }
        return Map.copyOf(summaries);
    }

    public record UserSummary(UUID userId, String displayName, String role) {}
}
