package com.codemastery.modules.auth;

import com.codemastery.common.config.AppConfig;
import com.codemastery.common.exception.BusinessRuleException;
import com.codemastery.common.exception.GlobalExceptionHandler;
import com.codemastery.modules.auth.api.controller.AuthController;
import com.codemastery.modules.auth.api.dto.AuthRequests;
import com.codemastery.modules.auth.api.dto.UserResponse;
import com.codemastery.modules.auth.application.service.AuthService;
import com.codemastery.modules.auth.application.service.UserAccountService;
import com.codemastery.modules.auth.application.service.UserAccountService.CurrentIdentity;
import com.codemastery.modules.auth.application.service.UserAccountService.IdentityRole;
import com.codemastery.modules.auth.infrastructure.security.AuthenticatedUser;
import com.codemastery.modules.auth.infrastructure.security.SecurityConfig;
import com.codemastery.modules.auth.infrastructure.security.JwtTokenProvider;
import com.codemastery.modules.auth.infrastructure.security.RestSecurityErrorHandler;
import com.codemastery.modules.course.application.service.CourseAccessService;
import com.codemastery.modules.course.domain.repository.CourseRepository;
import com.codemastery.modules.course.domain.model.PublishStatus;
import com.codemastery.modules.course.domain.model.EnrollmentStatus;
import com.codemastery.modules.auth.domain.model.AccountStatus;
import com.codemastery.modules.auth.domain.model.Role;
import com.codemastery.modules.auth.domain.model.User;
import com.codemastery.modules.auth.domain.repository.UserRepository;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.hibernate.exception.ConstraintViolationException;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.context.annotation.Bean;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Import;
import org.springframework.boot.jackson.autoconfigure.JacksonAutoConfiguration;
import org.springframework.mock.web.MockServletContext;
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.context.support.AnnotationConfigWebApplicationContext;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.SimpleTransactionStatus;
import org.springframework.validation.beanvalidation.SpringValidatorAdapter;
import tools.jackson.databind.json.JsonMapper;

import java.sql.SQLException;
import java.security.SecureRandom;
import java.time.Duration;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Stream;

import static org.hamcrest.Matchers.aMapWithSize;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.containsString;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    private static final UUID USER_ID = UUID.fromString("00000000-0000-4000-8000-000000000001");
    private static final String PASSWORD = " unit-only-passphrase ";
    private static final BCryptPasswordEncoder BCRYPT =
            new BCryptPasswordEncoder(BCryptPasswordEncoder.BCryptVersion.$2B, 14);
    private static final JsonMapper JSON = JsonMapper.builder().build();
    private static ValidatorFactory factory;
    private static Validator validator;
    private static String passwordHash;

    @Mock
    private UserRepository users;
    @Mock
    private PlatformTransactionManager transactionManager;
    private AuthService service;

    @BeforeAll
    static void setUpValidationAndSyntheticHash() {
        factory = Validation.buildDefaultValidatorFactory();
        validator = factory.getValidator();
        passwordHash = BCRYPT.encode(PASSWORD);
    }

    @AfterAll
    static void closeValidation() {
        if (factory != null) {
            factory.close();
        }
    }

    @BeforeEach
    void setUpService() {
        service = new AuthService(users, validator, transactionManager);
    }

    private void allowTransaction() {
        when(transactionManager.getTransaction(any())).thenReturn(new SimpleTransactionStatus());
    }

    private void saveSubmittedAccount() {
        allowTransaction();
        when(users.save(any())).thenAnswer(call -> call.getArgument(0));
    }

    private static User account(Role role, AccountStatus status) {
        return new User(USER_ID, "learner@example.test", passwordHash, "Unit Learner", role, status);
    }

    private static DataIntegrityViolationException constraintFailure(String name, String state) {
        return new DataIntegrityViolationException("Synthetic persistence failure",
                new ConstraintViolationException("Synthetic constraint failure",
                        new SQLException("Synthetic SQL error", state), "synthetic SQL", name));
    }

    @Nested
    class Registration {

        @Test
        void canonicalizesIdentityCreatesOnlyActiveLearnerAndUsesExactBcryptPolicy() {
            saveSubmittedAccount();
            User result = service.register("  LEARNER@EXAMPLE.TEST\t", PASSWORD, "  Unit Learner  ");
            assertNotNull(result.id());
            assertEquals("learner@example.test", result.email());
            assertEquals("Unit Learner", result.displayName());
            assertEquals(Role.LEARNER, result.role());
            assertEquals(AccountStatus.ACTIVE, result.accountStatus());
            assertTrue(result.passwordHash().startsWith("$2b$14$"));
            assertNotEquals(PASSWORD, result.passwordHash());
            assertTrue(BCRYPT.matches(PASSWORD, result.passwordHash()));
            assertFalse(BCRYPT.matches(PASSWORD.strip(), result.passwordHash()));
            verify(users).findByEmail("learner@example.test");
            verify(users).save(result);
            verify(transactionManager).commit(any());
        }

        @Test
        void acceptsOneHundredSupplementaryDisplayNameCodePoints() {
            saveSubmittedAccount();
            String name = "\uD83D\uDE00".repeat(100);
            assertEquals(name, service.register("learner@example.test", PASSWORD, name).displayName());
        }

        @Test
        void preservesAValidSpaceOnlyPassword() {
            saveSubmittedAccount();
            User result = service.register("learner@example.test", " ".repeat(15), "Unit Learner");
            assertTrue(BCRYPT.matches(" ".repeat(15), result.passwordHash()));
        }

        @Test
        void returnsTheActualSavedRepresentation() {
            allowTransaction();
            User stored = account(Role.LEARNER, AccountStatus.ACTIVE);
            when(users.save(any())).thenReturn(stored);
            assertSame(stored, service.register("learner@example.test", PASSWORD, "Unit Learner"));
        }

        @ParameterizedTest
        @NullSource
        @ValueSource(strings = {"", "learner", "name @example.test", "é@example.test", "K@example.test"})
        void rejectsInvalidOrNonAsciiEmailBeforePersistence(String email) {
            BusinessRuleException error = assertThrows(BusinessRuleException.class,
                    () -> service.register(email, PASSWORD, "Unit Learner"));
            assertEquals(HttpStatus.BAD_REQUEST, error.getStatus());
            verifyNoInteractions(users, transactionManager);
        }

        @ParameterizedTest
        @NullSource
        @ValueSource(strings = {"", "   ", "Unit\tLearner", "Unit\u0000Learner"})
        void rejectsMissingBlankOrControlCharacterDisplayNames(String name) {
            assertEquals(HttpStatus.BAD_REQUEST, assertThrows(BusinessRuleException.class,
                    () -> service.register("learner@example.test", PASSWORD, name)).getStatus());
            verifyNoInteractions(users, transactionManager);
        }

        @Test
        void rejectsOneHundredAndOneDisplayNameCodePoints() {
            assertThrows(BusinessRuleException.class,
                    () -> service.register("learner@example.test", PASSWORD, "\uD83D\uDE00".repeat(101)));
            verifyNoInteractions(users, transactionManager);
        }

        static Stream<Arguments> invalidPasswords() {
            return Stream.of(Arguments.of((Object) null), Arguments.of("a".repeat(14)),
                    Arguments.of("a".repeat(73)), Arguments.of("\uD83D\uDE00".repeat(19)));
        }

        @ParameterizedTest
        @MethodSource("invalidPasswords")
        void rejectsInvalidPasswordsBeforePersistence(String password) {
            assertEquals(HttpStatus.BAD_REQUEST, assertThrows(BusinessRuleException.class,
                    () -> service.register("learner@example.test", password, "Unit Learner")).getStatus());
            verifyNoInteractions(users, transactionManager);
        }

        @Test
        void rejectsAnAlreadyRegisteredCanonicalEmail() {
            when(users.findByEmail("learner@example.test")).thenReturn(Optional.of(account(Role.LEARNER, AccountStatus.ACTIVE)));
            assertEquals(HttpStatus.CONFLICT, assertThrows(BusinessRuleException.class,
                    () -> service.register("LEARNER@EXAMPLE.TEST", PASSWORD, "Unit Learner")).getStatus());
            verify(users, never()).save(any());
            verifyNoInteractions(transactionManager);
        }

        @Test
        void translatesTheExactUniqueEmailConstraintAtSave() {
            allowTransaction();
            when(users.save(any())).thenThrow(constraintFailure("uq_users_email", "23505"));
            assertEquals(HttpStatus.CONFLICT, assertThrows(BusinessRuleException.class,
                    () -> service.register("learner@example.test", PASSWORD, "Unit Learner")).getStatus());
            verify(transactionManager).rollback(any());
        }

        @Test
        void translatesAUniqueEmailRaceThatAppearsOnlyAtCommit() {
            saveSubmittedAccount();
            doThrow(constraintFailure("uq_users_email", "23505")).when(transactionManager).commit(any());
            assertEquals(HttpStatus.CONFLICT, assertThrows(BusinessRuleException.class,
                    () -> service.register("learner@example.test", PASSWORD, "Unit Learner")).getStatus());
        }

        static Stream<Arguments> unrelatedConstraints() {
            return Stream.of(Arguments.of("pk_users", "23505"),
                    Arguments.of("ck_users_text", "23514"), Arguments.of("uq_users_email", "23514"));
        }

        @ParameterizedTest
        @MethodSource("unrelatedConstraints")
        void doesNotMisclassifyOtherDatabaseFailuresAsDuplicateEmail(String name, String state) {
            allowTransaction();
            RuntimeException failure = constraintFailure(name, state);
            when(users.save(any())).thenThrow(failure);
            assertSame(failure, assertThrows(DataIntegrityViolationException.class,
                    () -> service.register("learner@example.test", PASSWORD, "Unit Learner")));
        }
    }

    @Nested
    class CredentialVerification {

        @ParameterizedTest
        @EnumSource(Role.class)
        void checksTheExactPasswordAndPreservesTheCurrentAccountRole(Role role) {
            User current = account(role, AccountStatus.ACTIVE);
            when(users.findByEmail("learner@example.test")).thenReturn(Optional.of(current));
            assertSame(current, service.authenticate(" LEARNER@EXAMPLE.TEST ", PASSWORD));
            verifyNoInteractions(transactionManager);
        }

        @ParameterizedTest
        @ValueSource(strings = {"missing", "wrong", "locked"})
        void missingIncorrectAndLockedCredentialsHaveTheSameGenericFailure(String scenario) {
            when(users.findByEmail("learner@example.test")).thenReturn("missing".equals(scenario)
                    ? Optional.empty() : Optional.of(account(Role.LEARNER,
                    "locked".equals(scenario) ? AccountStatus.LOCKED : AccountStatus.ACTIVE)));
            String password = "wrong".equals(scenario) ? PASSWORD.strip() : PASSWORD;
            BadCredentialsException error = assertThrows(BadCredentialsException.class,
                    () -> service.authenticate("learner@example.test", password));
            assertEquals("Authentication failed.", error.getMessage());
            verify(users, never()).save(any());
        }

        @ParameterizedTest
        @ValueSource(strings = {"ascii", "supplementary"})
        void overlongCredentialsUseGenericRejectionWithoutTruncation(String type) {
            String password = "ascii".equals(type) ? "a".repeat(73) : "\uD83D\uDE00".repeat(19);
            assertEquals("Authentication failed.", assertThrows(BadCredentialsException.class,
                    () -> service.authenticate("learner@example.test", password)).getMessage());
            verifyNoInteractions(users, transactionManager);
        }

        @Test
        void registrationMinimumLengthIsNotAnAdditionalLoginValidationError() {
            assertThrows(BadCredentialsException.class,
                    () -> service.authenticate("learner@example.test", "short"));
        }

        @Test
        void absentPasswordIsAnInvalidRequest() {
            assertEquals(HttpStatus.BAD_REQUEST, assertThrows(BusinessRuleException.class,
                    () -> service.authenticate("learner@example.test", null)).getStatus());
            verifyNoInteractions(users);
        }

        @Test
        void missingAccountDataDueToStorageFailureDoesNotBecomeInvalidCredentials() {
            var failure = new DataAccessResourceFailureException("Synthetic unavailable storage");
            when(users.findByEmail("learner@example.test")).thenThrow(failure);
            assertSame(failure, assertThrows(DataAccessResourceFailureException.class,
                    () -> service.authenticate("learner@example.test", PASSWORD)));
        }
    }

    @Nested
    class PublicInstructorNames {

        @Test
        void emptyInputDoesNotQueryStorage() {
            assertEquals(Map.of(), new UserAccountService(users, () -> { throw new AssertionError("Public names must not read identity"); }).instructorDisplayNames(Set.of()));
            verifyNoInteractions(users);
        }

        @Test
        void returnsOnlyRequestedInstructorNamesIncludingLockedAccountsInAnImmutableMap() {
            UUID locked = UUID.fromString("00000000-0000-4000-8000-000000000002");
            UUID learner = UUID.fromString("00000000-0000-4000-8000-000000000003");
            UUID missing = UUID.fromString("00000000-0000-4000-8000-000000000004");
            when(users.findById(USER_ID)).thenReturn(Optional.of(account(Role.INSTRUCTOR, AccountStatus.ACTIVE)));
            when(users.findById(locked)).thenReturn(Optional.of(new User(locked, "instructor@example.test",
                    passwordHash, "Locked Instructor", Role.INSTRUCTOR, AccountStatus.LOCKED)));
            when(users.findById(learner)).thenReturn(Optional.of(account(Role.LEARNER, AccountStatus.ACTIVE)));
            when(users.findById(missing)).thenReturn(Optional.empty());
            Map<UUID, String> result = new UserAccountService(users, () -> { throw new AssertionError("Public names must not read identity"); })
                    .instructorDisplayNames(Set.of(USER_ID, locked, learner, missing));
            assertEquals(Map.of(USER_ID, "Unit Learner", locked, "Locked Instructor"), result);
            assertThrows(UnsupportedOperationException.class, () -> result.put(learner, "Forbidden addition"));
        }

        @Test
        void propagatesStorageFailuresWithoutInventingNames() {
            var failure = new DataAccessResourceFailureException("Synthetic unavailable storage");
            when(users.findById(USER_ID)).thenThrow(failure);
            assertSame(failure, assertThrows(DataAccessResourceFailureException.class,
                    () -> new UserAccountService(users, () -> { throw new AssertionError("Public names must not read identity"); }).instructorDisplayNames(Set.of(USER_ID))));
        }
    }

    @Nested
    class CurrentIdentityBinding {
        private AnnotationConfigWebApplicationContext context;
        private UserAccountService identities;
        private CourseRepository courses;

        @BeforeEach void startProductionBeans() {
            SecurityContextHolder.clearContext();
            context = new AnnotationConfigWebApplicationContext();
            context.setServletContext(new MockServletContext());
            context.register(IdentityFixture.class);
            context.refresh();
            identities = context.getBean(UserAccountService.class);
            courses = context.getBean(CourseRepository.class);
        }

        @AfterEach void closeProductionBeans() {
            SecurityContextHolder.clearContext();
            if (context != null) context.close();
        }

        @ParameterizedTest @EnumSource(Role.class)
        void springWiresRequestLocalIdentityAndCourseToTheActualAuthAdapter(Role role) {
            var principal = new AuthenticatedUser(USER_ID, role);
            SecurityContextHolder.getContext().setAuthentication(
                    new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities()));
            IdentityRole expected = switch (role) {
                case LEARNER -> IdentityRole.LEARNER;
                case INSTRUCTOR -> IdentityRole.INSTRUCTOR;
                case ADMIN -> IdentityRole.ADMINISTRATOR;
            };
            assertEquals(new CurrentIdentity(USER_ID, expected), identities.requireCurrentIdentity());
            UUID course = UUID.randomUUID();
            if (role == Role.ADMIN) {
                assertThrows(org.springframework.security.access.AccessDeniedException.class,
                        () -> context.getBean(CourseAccessService.class).requireLearnerCourseAccess(course));
                verifyNoInteractions(courses);
            } else {
                when(courses.findCourseAccessSnapshot(USER_ID, course, null, null)).thenReturn(Optional.of(
                        new CourseRepository.AccessSnapshot(course, USER_ID, PublishStatus.PUBLISHED,
                                null, null, null, null, EnrollmentStatus.ACTIVE)));
                var access = context.getBean(CourseAccessService.class);
                if (role == Role.LEARNER) access.requireLearnerCourseAccess(course);
                else access.requireCourseOwnership(course);
                verify(courses).findCourseAccessSnapshot(USER_ID, course, null, null);
            }
            verifyNoInteractions(context.getBean(UserRepository.class)); // No redundant account read.
            SecurityContextHolder.clearContext();
            assertThrows(AuthenticationCredentialsNotFoundException.class, identities::requireCurrentIdentity);
        }

        @Test void rejectsAbsentUnauthenticatedAndForeignPrincipalsWithoutCourseReads() {
            assertThrows(AuthenticationCredentialsNotFoundException.class, identities::requireCurrentIdentity);
            for (var authentication : List.of(
                    new UsernamePasswordAuthenticationToken(new AuthenticatedUser(USER_ID, Role.LEARNER), null),
                    new UsernamePasswordAuthenticationToken("caller-supplied-id", null, List.of()),
                    new UsernamePasswordAuthenticationToken(new AuthenticatedUser(null, Role.LEARNER), null, List.of()),
                    new UsernamePasswordAuthenticationToken(new AuthenticatedUser(USER_ID, null), null, List.of()))) {
                SecurityContextHolder.getContext().setAuthentication(authentication);
                assertThrows(AuthenticationCredentialsNotFoundException.class,
                        () -> context.getBean(CourseAccessService.class).requireLearnerCourseAccess(UUID.randomUUID()));
            }
            verifyNoInteractions(courses, context.getBean(UserRepository.class));
        }

        @Test void producerFailureAndInvalidResultsAreServerFailuresRatherThanAuthenticationDenials() {
            var failure = new DataAccessResourceFailureException("Synthetic provider failure");
            var facade = new UserAccountService(users, () -> { throw failure; });
            assertSame(failure, assertThrows(DataAccessResourceFailureException.class, facade::requireCurrentIdentity));
            for (var invalid : java.util.Arrays.asList(null,
                    new CurrentIdentity(null, IdentityRole.LEARNER), new CurrentIdentity(USER_ID, null))) {
                assertThrows(IllegalStateException.class,
                        () -> new UserAccountService(users, () -> invalid).requireCurrentIdentity());
            }
            verifyNoInteractions(users);
        }
    }

    @TestConfiguration(proxyBeanMethods = false)
    @EnableWebMvc
    @Import({SecurityConfig.class, JwtTokenProvider.class, RestSecurityErrorHandler.class,
            JacksonAutoConfiguration.class, UserAccountService.class, CourseAccessService.class})
    static class IdentityFixture {
        @Bean UserRepository users() { return mock(UserRepository.class); }
        @Bean CourseRepository courses() { return mock(CourseRepository.class); }
        @Bean AppConfig.JwtSettings jwtSettings() {
            byte[] key = new byte[32];
            new SecureRandom().nextBytes(key);
            return new AppConfig.JwtSettings(HexFormat.of().formatHex(key), Duration.ofMinutes(15), "codemastery");
        }
        @Bean AppConfig.CorsSettings corsSettings() {
            return new AppConfig.CorsSettings(List.of("http://localhost:5173"));
        }
    }

    @Nested
    class DtoAndMvcBinding {

        private AuthService application;
        private MockMvc mvc;

        @BeforeEach
        void setUpMvc() {
            application = mock(AuthService.class);
            mvc = MockMvcBuilders.standaloneSetup(new AuthController(application, mock(JwtTokenProvider.class)))
                    .setControllerAdvice(new GlobalExceptionHandler(), new AuthController.RegistrationCacheAdvice())
                    .setValidator(new SpringValidatorAdapter(validator)).build();
        }

        private String registrationJson(String email, String password, String name) {
            return JSON.writeValueAsString(Map.of("email", email, "password", password, "displayName", name));
        }

        @Test
        void registrationBindingReturnsOnlyTheApprovedPublicShapeAndDoesNotIssueAToken() throws Exception {
            when(application.register("learner@example.test", PASSWORD, "Unit Learner"))
                    .thenReturn(account(Role.LEARNER, AccountStatus.ACTIVE));
            mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
                            .content(registrationJson(" LEARNER@EXAMPLE.TEST ", PASSWORD, " Unit Learner ")))
                    .andExpect(status().isCreated()).andExpect(header().string("Cache-Control", "no-store"))
                    .andExpect(jsonPath("$", aMapWithSize(3)))
                    .andExpect(jsonPath("$.id").value(USER_ID.toString()))
                    .andExpect(jsonPath("$.displayName").value("Unit Learner"))
                    .andExpect(jsonPath("$.role").value("Learner"))
                    .andExpect(jsonPath("$.accessToken").doesNotExist())
                    .andExpect(jsonPath("$.passwordHash").doesNotExist());
        }

        static Stream<String> invalidRequests() {
            return Stream.of("null", "[]", "{}",
                    JSON.writeValueAsString(Map.of("email", 7, "password", PASSWORD, "displayName", "Unit Learner")),
                    JSON.writeValueAsString(Map.of("email", "learner@example.test", "password", 123456789012345L,
                            "displayName", "Unit Learner")),
                    JSON.writeValueAsString(Map.of("email", "learner@example.test", "password", PASSWORD,
                            "displayName", 12345)),
                    JSON.writeValueAsString(Map.of("email", "learner@example.test", "password", PASSWORD,
                            "displayName", "Unit Learner", "role", "Instructor")),
                    JSON.writeValueAsString(Map.of("email", "learner@example.test", "password", PASSWORD,
                            "displayName", "Unit Learner", "accountStatus", "active")),
                    JSON.writeValueAsString(Map.of("email", "learner@example.test", "password", PASSWORD,
                            "displayName", "Unit Learner", "extra", "value")));
        }

        @ParameterizedTest
        @MethodSource("invalidRequests")
        void malformedMissingTypedInvalidOrUnknownPropertiesAreSafe400s(String json) throws Exception {
            mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON).content(json))
                    .andExpect(status().isBadRequest()).andExpect(header().string("Cache-Control", "no-store"))
                    .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                    .andExpect(jsonPath("$.detail").value("The request is invalid."));
            verifyNoInteractions(application);
        }

        static Stream<Arguments> invalidRegistrationFields() {
            return Stream.of(Arguments.of("not-an-email", PASSWORD, "Unit Learner"),
                    Arguments.of("é@example.test", PASSWORD, "Unit Learner"),
                    Arguments.of("learner@example.test", "a".repeat(14), "Unit Learner"),
                    Arguments.of("learner@example.test", "a".repeat(73), "Unit Learner"),
                    Arguments.of("learner@example.test", PASSWORD, "\uD83D\uDE00".repeat(101)),
                    Arguments.of("learner@example.test", PASSWORD, "Unit\tLearner"));
        }

        @ParameterizedTest
        @MethodSource("invalidRegistrationFields")
        void approvedFieldPredicatesAreActuallyBoundBeforeTheService(
                String email, String password, String name) throws Exception {
            mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
                            .content(registrationJson(email, password, name)))
                    .andExpect(status().isBadRequest()).andExpect(header().string("Cache-Control", "no-store"))
                    .andExpect(jsonPath("$.errors").isArray())
                    .andExpect(content().string(not(containsString(password))));
            verifyNoInteractions(application);
        }

        @Test
        void conflictUsesSharedSafe409AndNoStore() throws Exception {
            when(application.register("learner@example.test", PASSWORD, "Unit Learner"))
                    .thenThrow(new BusinessRuleException(HttpStatus.CONFLICT, "Synthetic confidential SQL details"));
            mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
                            .content(registrationJson("learner@example.test", PASSWORD, "Unit Learner")))
                    .andExpect(status().isConflict()).andExpect(header().string("Cache-Control", "no-store"))
                    .andExpect(jsonPath("$.detail").value("The request conflicts with an existing resource."))
                    .andExpect(content().string(not(containsString("confidential"))));
        }

        @Test
        void unexpectedFailureDoesNotDiscloseThePasswordOrPersistenceDetails() throws Exception {
            when(application.register("learner@example.test", PASSWORD, "Unit Learner"))
                    .thenThrow(new DataAccessResourceFailureException("Synthetic confidential storage error"));
            mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
                            .content(registrationJson("learner@example.test", PASSWORD, "Unit Learner")))
                    .andExpect(status().isInternalServerError()).andExpect(header().string("Cache-Control", "no-store"))
                    .andExpect(content().string(not(containsString("confidential"))));
        }

        @Test
        void loginDtoDoesNotApplyRegistrationComplexityOrTransformThePassword() {
            var input = JSON.readValue("{\"email\":\" LEARNER@EXAMPLE.TEST \",\"password\":\"short\"}",
                    AuthRequests.LoginRequest.class);
            assertEquals("learner@example.test", input.email());
            assertEquals("short", input.password());
            assertTrue(validator.validate(input).isEmpty());
            assertFalse(input.toString().contains("short"));
            assertFalse(new AuthRequests.RegisterRequest("learner@example.test", PASSWORD, "Unit Learner")
                    .toString().contains(PASSWORD));
        }

        static Stream<Arguments> publicRoles() {
            return Stream.of(Arguments.of(Role.LEARNER, "Learner"),
                    Arguments.of(Role.INSTRUCTOR, "Instructor"), Arguments.of(Role.ADMIN, "Administrator"));
        }

        @ParameterizedTest
        @MethodSource("publicRoles")
        void publicRoleNamesMatchTheApprovedContract(Role role, String expected) {
            assertEquals(expected, UserResponse.from(account(role, AccountStatus.ACTIVE)).role());
        }
    }
}
