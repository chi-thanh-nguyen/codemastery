package com.codemastery.integration;

import com.codemastery.common.api.PageResponse;
import com.codemastery.common.exception.ResourceNotFoundException;
import com.codemastery.modules.admin.application.service.AdminCourseService;
import com.codemastery.modules.admin.application.service.AdminCourseService.AdminCourseItem;
import com.codemastery.modules.admin.application.service.AdminCourseService.AdminCourseOutcome;
import com.codemastery.modules.auth.domain.model.Role;
import com.codemastery.modules.auth.infrastructure.security.AuthenticatedUser;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * IN20 real-PostgreSQL suite for Administrator Course administration (ADR012 sections 7.2, 10.2). The fixtures
 * are committed (the service owns its transaction) and removed after each test.
 */
@DirtiesContext
class AdminCourseIntegrationTest extends AbstractIntegrationTest {

    private static final String JWT_SECRET = ephemeralJwtSecret();

    private static String ephemeralJwtSecret() {
        byte[] bytes = new byte[32];
        new SecureRandom().nextBytes(bytes);
        return HexFormat.of().formatHex(bytes);
    }

    @DynamicPropertySource
    static void provideEphemeralJwtSecret(DynamicPropertyRegistry registry) {
        registry.add("JWT_SECRET", () -> JWT_SECRET);
    }

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private AdminCourseService service;

    private final List<UUID> users = new ArrayList<>();
    private final List<UUID> courses = new ArrayList<>();
    private UUID admin;
    private UUID instructor;
    private String tag;

    @BeforeEach
    void fixtures() {
        tag = "IN20-" + UUID.randomUUID();
        admin = user("admin", "IN20 Admin");
        instructor = user("instructor", "IN20 Instructor");
    }

    @AfterEach
    void removeFixtures() {
        SecurityContextHolder.clearContext();
        for (UUID id : users) {
            jdbc.update("DELETE FROM public.audit_logs WHERE actor_id = ?", id);
        }
        for (UUID id : courses) {
            jdbc.update("DELETE FROM public.courses WHERE id = ?", id);
        }
        for (UUID id : users) {
            jdbc.update("DELETE FROM public.users WHERE id = ?", id);
        }
        courses.clear();
        users.clear();
    }

    @Test
    void hidingChangesTheCourseAndWritesExactlyOneAuditRowOnce() {
        UUID course = course("published", "Hide me");
        signIn(admin, Role.ADMIN);

        AdminCourseOutcome first = service.hideCourse(course);
        AdminCourseOutcome again = service.hideCourse(course);

        assertTrue(first.changed());
        assertEquals("hidden", first.course().publicationStatus());
        assertEquals("IN20 Instructor", first.course().instructorDisplayName());
        assertFalse(again.changed(), "already hidden is a no-op");
        assertEquals("hidden", status(course));
        assertEquals(List.of(admin + "|course_hidden|course|" + course), auditRows());
    }

    @Test
    void nonAdministratorsAreRefusedAndNothingChanges() {
        UUID course = course("published", "Keep me");
        signIn(instructor, Role.INSTRUCTOR);

        assertThrows(AccessDeniedException.class, () -> service.hideCourse(course));
        assertThrows(AccessDeniedException.class, () -> service.list(tag, null, 0, 20));

        assertEquals("published", status(course));
        assertEquals(List.of(), auditRows());
    }

    @Test
    void aMissingCourseIsNotFoundAndNotAudited() {
        signIn(admin, Role.ADMIN);

        assertThrows(ResourceNotFoundException.class, () -> service.hideCourse(UUID.randomUUID()));

        assertEquals(List.of(), auditRows());
    }

    @Test
    void anAuditFailureRollsBackTheHide() {
        // An authenticated Administrator principal without a users row makes the audit INSERT fail its
        // actor foreign key, after Course has already hidden the row inside the same transaction.
        UUID course = course("published", "Rollback");
        UUID ghost = UUID.randomUUID();
        signIn(ghost, Role.ADMIN);

        assertThrows(RuntimeException.class, () -> service.hideCourse(course));

        assertEquals("published", status(course));
        assertEquals(0, jdbc.queryForObject(
                "SELECT count(*) FROM public.audit_logs WHERE actor_id = ?", Integer.class, ghost));
    }

    @Test
    void listIncludesHiddenAndPublishedCoursesFiltersAndNamesTheInstructor() {
        UUID visible = course("published", "Alpha");
        UUID hidden = course("hidden", "Beta");
        signIn(admin, Role.ADMIN);

        PageResponse<AdminCourseItem> all = service.list(tag.toLowerCase(), null, 0, 20);
        PageResponse<AdminCourseItem> onlyHidden = service.list(tag, "hidden", 0, 20);

        assertEquals(List.of(visible, hidden), all.content().stream().map(AdminCourseItem::courseId).toList(),
                "title order, hidden and published together, case-insensitive substring");
        assertTrue(all.content().stream().allMatch(row -> "IN20 Instructor".equals(row.instructorDisplayName())));
        assertEquals(List.of(hidden), onlyHidden.content().stream().map(AdminCourseItem::courseId).toList());
    }

    // ------------------------------------------------------------------------------- helpers

    private UUID user(String role, String displayName) {
        UUID id = UUID.randomUUID();
        users.add(id);
        jdbc.update("INSERT INTO public.users (id, email, password_hash, display_name, role, account_status) "
                + "VALUES (?, ?, 'not-a-login-fixture', ?, ?, 'active')", id, "in20-" + id + "@example.test",
                displayName, role);
        return id;
    }

    /** Titles sort (COLLATE "C") by the unique tag then the suffix, so the order is predictable. */
    private UUID course(String publicationStatus, String suffix) {
        UUID id = UUID.randomUUID();
        courses.add(id);
        jdbc.update("INSERT INTO public.courses (id, instructor_id, title, description, publication_status, "
                + "adaptive_enabled) VALUES (?, ?, ?, 'IN20 fixture', ?, false)",
                id, instructor, tag + " " + suffix, publicationStatus);
        return id;
    }

    private String status(UUID course) {
        return jdbc.queryForObject("SELECT publication_status FROM public.courses WHERE id = ?", String.class, course);
    }

    private List<String> auditRows() {
        return jdbc.queryForList("SELECT actor_id || '|' || action || '|' || target_type || '|' || target_reference "
                + "FROM public.audit_logs WHERE actor_id = ? ORDER BY occurred_at, id", String.class, admin);
    }

    private static void signIn(UUID userId, Role role) {
        AuthenticatedUser principal = new AuthenticatedUser(userId, role);
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities()));
    }
}
