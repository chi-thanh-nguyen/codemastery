package com.codemastery.modules.auth.infrastructure.persistence.mapper;

import com.codemastery.modules.auth.domain.model.AccountStatus;
import com.codemastery.modules.auth.domain.model.Role;
import com.codemastery.modules.auth.domain.model.User;
import com.codemastery.modules.auth.infrastructure.persistence.entity.UserEntity;
import org.springframework.stereotype.Component;

@Component
public class UserMapper {

    public UserEntity toEntity(User user) {
        return new UserEntity(
                user.id(),
                user.email(),
                user.passwordHash(),
                user.displayName(),
                user.role().getCode(),
                user.accountStatus().getCode());
    }

    public User toDomain(UserEntity entity) {
        return new User(
                entity.getId(),
                entity.getEmail(),
                entity.getPasswordHash(),
                entity.getDisplayName(),
                roleFromStoredCode(entity.getRole()),
                accountStatusFromStoredCode(entity.getAccountStatus()));
    }

    private static Role roleFromStoredCode(String storedCode) {
        for (Role role : Role.values()) {
            if (role.getCode().equals(storedCode)) {
                return role;
            }
        }
        throw new IllegalArgumentException("Unknown stored role code");
    }

    private static AccountStatus accountStatusFromStoredCode(String storedCode) {
        for (AccountStatus status : AccountStatus.values()) {
            if (status.getCode().equals(storedCode)) {
                return status;
            }
        }
        throw new IllegalArgumentException("Unknown stored account status code");
    }
}
