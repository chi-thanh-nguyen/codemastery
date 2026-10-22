package com.codemastery.modules.auth;

import com.codemastery.modules.auth.domain.model.AccountStatus;
import com.codemastery.modules.auth.domain.model.Role;
import com.codemastery.modules.auth.domain.model.User;
import com.codemastery.modules.auth.infrastructure.persistence.entity.UserEntity;
import com.codemastery.modules.auth.infrastructure.persistence.mapper.UserMapper;
import com.codemastery.modules.auth.infrastructure.persistence.repository.UserJpaRepository;
import com.codemastery.modules.auth.infrastructure.persistence.repository.UserRepositoryImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.dao.DataIntegrityViolationException;

import java.util.Optional;
import java.util.UUID;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

/** AU03 unit/mocked regression coverage; no Spring context or PostgreSQL is exercised. */
class UserPersistenceTest {

    private static final UUID INPUT_ID = UUID.fromString("6cb14938-fc4d-4e06-85a3-9833a5ef09b1");
    private static final UUID STORED_ID = UUID.fromString("70edf148-7593-4c9e-99f9-9c889bceba95");
    // Opaque synthetic markers verify propagation, not BCrypt validity or authentication.
    private static final String INPUT_HASH = "$2b$14$synthetic-au03-input-hash";
    private static final String STORED_HASH = "$2b$14$synthetic-au03-stored-hash";

    static Stream<Arguments> storedRepresentations() {
        // Literal expectations are independent of the enum getCode() implementations.
        return Stream.of(
                Arguments.of(Role.LEARNER, AccountStatus.ACTIVE, "learner", "active"),
                Arguments.of(Role.LEARNER, AccountStatus.LOCKED, "learner", "locked"),
                Arguments.of(Role.INSTRUCTOR, AccountStatus.ACTIVE, "instructor", "active"),
                Arguments.of(Role.INSTRUCTOR, AccountStatus.LOCKED, "instructor", "locked"),
                Arguments.of(Role.ADMIN, AccountStatus.ACTIVE, "admin", "active"),
                Arguments.of(Role.ADMIN, AccountStatus.LOCKED, "admin", "locked"));
    }

    private static User inputUser(Role role, AccountStatus status) {
        return new User(INPUT_ID, "learner@example.test", INPUT_HASH, "Synthetic Learner", role, status);
    }

    private static User storedUser(Role role, AccountStatus status) {
        return new User(STORED_ID, "instructor@example.test", STORED_HASH, "Synthetic Instructor", role, status);
    }

    private static UserEntity storedEntity(String roleCode, String statusCode) {
        return new UserEntity(STORED_ID, "instructor@example.test", STORED_HASH,
                "Synthetic Instructor", roleCode, statusCode);
    }

    private static void assertEntityFields(UserEntity actual, User expected,
                                          String roleCode, String statusCode) {
        assertAll(
                () -> assertEquals(expected.id(), actual.getId()),
                () -> assertEquals(expected.email(), actual.getEmail()),
                () -> assertEquals(expected.passwordHash(), actual.getPasswordHash()),
                () -> assertEquals(expected.displayName(), actual.getDisplayName()),
                () -> assertEquals(roleCode, actual.getRole()),
                () -> assertEquals(statusCode, actual.getAccountStatus()));
    }

    @Nested
    class MapperTests {

        private final UserMapper mapper = new UserMapper();

        @ParameterizedTest(name = "{0}/{1} uses {2}/{3} and preserves both round trips")
        @MethodSource("com.codemastery.modules.auth.UserPersistenceTest#storedRepresentations")
        void preservesAllFieldsAndExplicitCodesInBothDirections(
                Role role, AccountStatus status, String roleCode, String statusCode) {
            User input = inputUser(role, status);
            UserEntity mapped = mapper.toEntity(input);
            assertEntityFields(mapped, input, roleCode, statusCode);
            assertEquals(input, mapper.toDomain(mapped));

            UserEntity stored = storedEntity(roleCode, statusCode);
            User decoded = mapper.toDomain(stored);
            assertEquals(storedUser(role, status), decoded);
            assertEntityFields(mapper.toEntity(decoded), storedUser(role, status), roleCode, statusCode);
        }

        @ParameterizedTest
        @NullSource
        @ValueSource(strings = {"unknown", "administrator", "", "LEARNER", " learner", "learner "})
        void rejectsInvalidOrNullStoredRole(String roleCode) {
            assertThrows(IllegalArgumentException.class,
                    () -> mapper.toDomain(storedEntity(roleCode, "active")));
        }

        @ParameterizedTest
        @NullSource
        @ValueSource(strings = {"unknown", "disabled", "", "ACTIVE", " active", "active "})
        void rejectsInvalidOrNullStoredStatus(String statusCode) {
            assertThrows(IllegalArgumentException.class,
                    () -> mapper.toDomain(storedEntity("learner", statusCode)));
        }

        @Test
        void propagatesScalarValuesWithoutNormalizationOrHashing() {
            // Deliberately noncanonical text is a unit-only probe, not a valid PostgreSQL fixture.
            User input = new User(INPUT_ID, " Mixed.Case@EXAMPLE.TEST ", INPUT_HASH,
                    "  Nguyễn Synthetic Learner  ", Role.ADMIN, AccountStatus.LOCKED);

            UserEntity mapped = mapper.toEntity(input);

            assertEntityFields(mapped, input, "admin", "locked");
            assertEquals(input, mapper.toDomain(mapped));
        }
    }

    @Nested
    class AdapterTests {

        private UserJpaRepository jpa;
        private UserRepositoryImpl repository;

        @BeforeEach
        void setUp() {
            jpa = mock(UserJpaRepository.class);
            repository = new UserRepositoryImpl(jpa, new UserMapper());
        }

        @Test
        void findsAndMapsLockedAccountByIdWithoutAuthenticationPolicy() {
            when(jpa.findById(STORED_ID)).thenReturn(Optional.of(storedEntity("instructor", "locked")));

            assertEquals(Optional.of(storedUser(Role.INSTRUCTOR, AccountStatus.LOCKED)),
                    repository.findById(STORED_ID));
            verify(jpa).findById(STORED_ID);
            verifyNoMoreInteractions(jpa);
        }

        @Test
        void returnsEmptyForMissingId() {
            when(jpa.findById(INPUT_ID)).thenReturn(Optional.empty());

            assertEquals(Optional.empty(), repository.findById(INPUT_ID));
            verify(jpa).findById(INPUT_ID);
            verifyNoMoreInteractions(jpa);
        }

        @Test
        void findsAndMapsPresentAccountByExactEmail() {
            String email = "instructor@example.test";
            when(jpa.findByEmail(email)).thenReturn(Optional.of(storedEntity("admin", "active")));

            assertEquals(Optional.of(storedUser(Role.ADMIN, AccountStatus.ACTIVE)),
                    repository.findByEmail(email));
            verify(jpa).findByEmail(email);
            verifyNoMoreInteractions(jpa);
        }

        @Test
        void forwardsExactEmailAndReturnsEmptyForMissingAccount() {
            // A mock checks delegation only; it makes no claim about real SQL matching.
            String email = " Missing.Account@EXAMPLE.TEST ";
            when(jpa.findByEmail(email)).thenReturn(Optional.empty());

            assertEquals(Optional.empty(), repository.findByEmail(email));
            verify(jpa).findByEmail(email);
            verifyNoMoreInteractions(jpa);
        }

        @Test
        void savesAllFieldsAndMapsTheDistinctEntityReturnedByPersistence() {
            User input = inputUser(Role.LEARNER, AccountStatus.ACTIVE);
            UserEntity persisted = storedEntity("instructor", "locked");
            when(jpa.save(any(UserEntity.class))).thenReturn(persisted);

            User result = repository.save(input);

            ArgumentCaptor<UserEntity> submitted = ArgumentCaptor.forClass(UserEntity.class);
            verify(jpa).save(submitted.capture());
            assertEntityFields(submitted.getValue(), input, "learner", "active");
            assertEquals(storedUser(Role.INSTRUCTOR, AccountStatus.LOCKED), result);
            verifyNoMoreInteractions(jpa);
        }

        @Test
        void propagatesIdLookupPersistenceFailure() {
            var failure = new DataAccessResourceFailureException("Synthetic lookup failure");
            when(jpa.findById(INPUT_ID)).thenThrow(failure);

            assertSame(failure, assertThrows(DataAccessResourceFailureException.class,
                    () -> repository.findById(INPUT_ID)));
            verify(jpa).findById(INPUT_ID);
            verifyNoMoreInteractions(jpa);
        }

        @Test
        void propagatesEmailLookupPersistenceFailure() {
            String email = "learner@example.test";
            var failure = new DataAccessResourceFailureException("Synthetic lookup failure");
            when(jpa.findByEmail(email)).thenThrow(failure);

            assertSame(failure, assertThrows(DataAccessResourceFailureException.class,
                    () -> repository.findByEmail(email)));
            verify(jpa).findByEmail(email);
            verifyNoMoreInteractions(jpa);
        }

        @Test
        void propagatesSaveConstraintFailureWithoutHttpTranslation() {
            var failure = new DataIntegrityViolationException("Synthetic constraint failure");
            when(jpa.save(any(UserEntity.class))).thenThrow(failure);

            assertSame(failure, assertThrows(DataIntegrityViolationException.class,
                    () -> repository.save(inputUser(Role.LEARNER, AccountStatus.ACTIVE))));
            verify(jpa).save(any(UserEntity.class));
            verifyNoMoreInteractions(jpa);
        }
    }
}
