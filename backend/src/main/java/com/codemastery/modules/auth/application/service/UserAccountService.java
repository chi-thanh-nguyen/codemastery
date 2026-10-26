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
}
