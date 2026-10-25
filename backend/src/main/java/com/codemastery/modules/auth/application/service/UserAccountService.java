package com.codemastery.modules.auth.application.service;

import com.codemastery.modules.auth.domain.model.Role;
import com.codemastery.modules.auth.domain.repository.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** CT01.identity public-name read only; current-identity adaptation awaits AU05. */
@Service
public class UserAccountService {

    private final UserRepository users;

    public UserAccountService(UserRepository users) {
        this.users = users;
    }

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
