package com.codemastery.modules.course.infrastructure.persistence.repository;

import com.codemastery.modules.course.domain.model.Course;
import com.codemastery.modules.course.domain.model.EnrollmentStatus;
import com.codemastery.modules.course.domain.model.PublishStatus;
import com.codemastery.modules.course.domain.repository.CourseRepository;
import com.codemastery.modules.course.infrastructure.persistence.mapper.CourseMapper;
import com.codemastery.modules.course.infrastructure.persistence.entity.CourseEntity;
import jakarta.persistence.EntityManager;
import org.hibernate.Session;
import org.hibernate.engine.spi.SessionImplementor;
import org.hibernate.persister.entity.EntityPersister;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.SqlParameterValue;
import org.springframework.stereotype.Repository;

import java.sql.Types;
import java.util.Map;
import java.util.Optional;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

@Repository
public class CourseRepositoryImpl implements CourseRepository {

    private static final String STATUS_PUBLISHED = "published";

    /** Every Course column; jsonb is read as text so the mapper owns its exact parsing. */
    private static final String COURSE_COLUMNS = "id, instructor_id, title, description, prerequisite_description, "
            + "publication_status, adaptive_enabled, adaptive_settings::text AS adaptive_settings";

    private final CourseJpaRepository courseJpaRepository;
    private final CourseMapper courseMapper;
    private final JdbcTemplate jdbc;
    private final EntityManager entityManager;

    public CourseRepositoryImpl(CourseJpaRepository courseJpaRepository, CourseMapper courseMapper,
                                JdbcTemplate jdbc, EntityManager entityManager) {
        this.courseJpaRepository = courseJpaRepository;
        this.courseMapper = courseMapper;
        this.jdbc = jdbc;
        this.entityManager = entityManager;
    }

    @Override
    public Optional<Course> findPublishedById(UUID id) {
        if (id == null) {
            return Optional.empty();
        }
        return courseJpaRepository.findByIdAndPublicationStatus(id, STATUS_PUBLISHED)
                .map(courseMapper::toDomain);
    }

    @Override
    public Page<Course> findPublishedCourses(Pageable pageable) {
        // SQL owns the fixed CT21 order. Never let Spring Data append caller sorting.
        Pageable pagination = pageable == null ? PageRequest.of(0, 20)
                : pageable.isPaged() ? PageRequest.of(pageable.getPageNumber(), pageable.getPageSize())
                : Pageable.unpaged();
        if (pagination.isPaged() && pagination.getOffset() > Integer.MAX_VALUE) {
            return oversizedPage(pagination, "", "any", false);
        }
        return courseJpaRepository.findPublishedCatalogue(pagination).map(courseMapper::toDomain);
    }

    @Override
    public Page<Course> searchPublishedCourses(Pageable pageable, String query, String prerequisites) {
        Pageable pagination = PageRequest.of(pageable.getPageNumber(), pageable.getPageSize());
        if (pagination.getOffset() > Integer.MAX_VALUE) return oversizedPage(pagination, query, prerequisites, true);
        return courseJpaRepository.searchPublishedCatalogue(pagination, query, prerequisites)
                .map(courseMapper::toDomain);
    }

    private Page<Course> oversizedPage(Pageable page, String query, String prerequisites, boolean filtered) {
        // The application service retains the read-only repeatable-read snapshot for count/content.
        long total = filtered ? courseJpaRepository.countPublishedSearch(query, prerequisites)
                : courseJpaRepository.countPublishedCatalogue();
        List<CourseEntity> rows = page.getOffset() >= total ? List.of()
                : courseJpaRepository.findPublishedAtOffset(query, prerequisites, page.getPageSize(), page.getOffset());
        return new PageImpl<>(rows, page, total).map(courseMapper::toDomain);
    }

    @Override
    public Optional<AccessSnapshot> findCourseAccessSnapshot(
            UUID actorId, UUID courseId, UUID lessonId, UUID materialId) {
        Objects.requireNonNull(actorId, "actorId");
        Objects.requireNonNull(courseId, "courseId");
        return courseJpaRepository.findCourseAccessSnapshot(actorId, courseId, lessonId, materialId)
                .map(CourseRepositoryImpl::toAccessSnapshot);
    }

    @Override
    public Optional<AccessSnapshot> findLessonAccessSnapshot(UUID actorId, UUID lessonId) {
        Objects.requireNonNull(actorId, "actorId");
        Objects.requireNonNull(lessonId, "lessonId");
        return courseJpaRepository.findLessonAccessSnapshot(actorId, lessonId)
                .map(CourseRepositoryImpl::toAccessSnapshot);
    }

    private static AccessSnapshot toAccessSnapshot(CourseJpaRepository.AccessRow row) {
        if (row.getCourseId() == null || row.getInstructorId() == null || row.getCourseStatus() == null
                || (row.getLessonId() == null) != (row.getLessonStatus() == null)
                || (row.getMaterialId() == null) != (row.getMaterialStatus() == null)
                || row.getMaterialId() != null && row.getLessonId() == null) {
            throw new IllegalStateException("Invalid Course access snapshot");
        }
        return new AccessSnapshot(row.getCourseId(), row.getInstructorId(), publication(row.getCourseStatus()),
                row.getLessonId(), publication(row.getLessonStatus()),
                row.getMaterialId(), publication(row.getMaterialStatus()), enrollment(row.getEnrollmentStatus()));
    }

    private static PublishStatus publication(String code) {
        if (code == null) return null;
        return switch (code) {
            case "published" -> PublishStatus.PUBLISHED;
            case "hidden" -> PublishStatus.HIDDEN;
            default -> throw new IllegalStateException("Invalid publication status in Course access snapshot");
        };
    }

    private static EnrollmentStatus enrollment(String code) {
        if (code == null) return null;
        return switch (code) {
            case "active" -> EnrollmentStatus.ACTIVE;
            case "completed" -> EnrollmentStatus.COMPLETED;
            case "left" -> EnrollmentStatus.LEFT;
            default -> throw new IllegalStateException("Invalid enrollment status in Course access snapshot");
        };
    }

    // CT21.authoring owner rows (CO03.content). Direct statements never return a managed entity,
    // so a read under the Course root lock always observes committed state.

    @Override
    public Optional<Course> findById(UUID courseId) {
        Objects.requireNonNull(courseId, "courseId");
        return jdbc.query("SELECT " + COURSE_COLUMNS + " FROM public.courses WHERE id = ?",
                (row, index) -> courseMapper.courseFromRow(row), courseId).stream().findFirst();
    }

    @Override
    public Page<Course> findByInstructorId(UUID instructorId, Pageable pageable) {
        Objects.requireNonNull(instructorId, "instructorId");
        Objects.requireNonNull(pageable, "pageable");
        Long total = jdbc.queryForObject("SELECT count(*) FROM public.courses WHERE instructor_id = ?",
                Long.class, instructorId);
        String select = "SELECT " + COURSE_COLUMNS + " FROM public.courses WHERE instructor_id = ? "
                + "ORDER BY title COLLATE \"C\" ASC, id ASC";
        List<Course> rows = pageable.isPaged()
                ? jdbc.query(select + " LIMIT ? OFFSET ?", (row, index) -> courseMapper.courseFromRow(row),
                        instructorId, pageable.getPageSize(), pageable.getOffset())
                : jdbc.query(select, (row, index) -> courseMapper.courseFromRow(row), instructorId);
        return new PageImpl<>(rows, pageable.isPaged() ? PageRequest.of(pageable.getPageNumber(), pageable.getPageSize())
                : Pageable.unpaged(), total == null ? 0 : total);
    }

    @Override
    public void insert(Course course) {
        Objects.requireNonNull(course, "course");
        jdbc.update("INSERT INTO public.courses (id, instructor_id, title, description, prerequisite_description, "
                        + "publication_status, adaptive_enabled, adaptive_settings) VALUES (?, ?, ?, ?, ?, ?, ?, CAST(? AS jsonb))",
                course.id(), course.instructorId(), course.title(), course.description(), course.prerequisiteDescription(),
                Objects.requireNonNull(course.publicationStatus(), "publicationStatus").getStatus(), course.adaptiveEnabled(),
                json(CourseMapper.settingsToJson(course.adaptiveSettings())));
    }

    @Override
    public void updateMetadata(UUID courseId, String title, String description, String prerequisiteDescription) {
        Objects.requireNonNull(courseId, "courseId");
        requireOneRow(jdbc.update("UPDATE public.courses SET title = ?, description = ?, prerequisite_description = ? "
                + "WHERE id = ?", title, description, prerequisiteDescription, courseId));
    }

    @Override
    public void updatePublicationStatus(UUID courseId, PublishStatus publicationStatus) {
        Objects.requireNonNull(courseId, "courseId");
        Objects.requireNonNull(publicationStatus, "publicationStatus");
        requireOneRow(jdbc.update("UPDATE public.courses SET publication_status = ? WHERE id = ?",
                publicationStatus.getStatus(), courseId));
    }

    // ADR005 Course-root guard and stored adaptive configuration (CO14).

    @Override
    public boolean lockCourseRoot(UUID courseId) {
        Objects.requireNonNull(courseId, "courseId");
        return !jdbc.queryForList("SELECT id FROM public.courses WHERE id = ? FOR UPDATE", UUID.class, courseId).isEmpty();
    }

    @Override
    public boolean lockCourseRootNoWait(UUID courseId) {
        Objects.requireNonNull(courseId, "courseId");
        return !jdbc.queryForList("SELECT id FROM public.courses WHERE id = ? FOR UPDATE NOWAIT", UUID.class, courseId)
                .isEmpty();
    }

    @Override
    public Optional<StoredAdaptiveConfiguration> findAdaptiveConfiguration(UUID courseId) {
        Objects.requireNonNull(courseId, "courseId");
        return jdbc.query("SELECT adaptive_enabled, adaptive_settings::text AS adaptive_settings "
                        + "FROM public.courses WHERE id = ?",
                (row, index) -> new StoredAdaptiveConfiguration(courseId, row.getBoolean("adaptive_enabled"),
                        CourseMapper.settingsFromJson(row.getString("adaptive_settings"))), courseId)
                .stream().findFirst();
    }

    @Override
    public boolean replaceAdaptiveConfiguration(UUID courseId,
            boolean expectedEnabled, Map<String, Object> expectedSettings,
            boolean adaptiveEnabled, Map<String, Object> adaptiveSettings) {
        Objects.requireNonNull(courseId, "courseId");
        // jsonb equality is structural: key order, whitespace and duplicate formatting are irrelevant.
        int rows = jdbc.update("UPDATE public.courses SET adaptive_enabled = ?, adaptive_settings = CAST(? AS jsonb) "
                        + "WHERE id = ? AND adaptive_enabled = ? "
                        + "AND adaptive_settings IS NOT DISTINCT FROM CAST(? AS jsonb)",
                adaptiveEnabled, json(CourseMapper.settingsToJson(adaptiveSettings)), courseId, expectedEnabled,
                json(CourseMapper.settingsToJson(expectedSettings)));
        if (rows > 1) {
            throw new IllegalStateException("Course configuration write matched more than one row");
        }
        return rows == 1;
    }

    @Override
    public TransactionFacts currentTransactionFacts() {
        return jdbc.queryForObject("SELECT current_setting('transaction_isolation') AS isolation, "
                        + "current_setting('transaction_read_only') = 'on' AS read_only, "
                        + "pg_current_xact_id_if_assigned() IS NOT NULL AS assigned",
                (row, index) -> new TransactionFacts(row.getString("isolation"), row.getBoolean("read_only"),
                        row.getBoolean("assigned")));
    }

    @Override
    public boolean hasPendingPersistenceChanges() {
        return entityManager.unwrap(Session.class).isDirty();
    }

    @Override
    public void discardCleanPersistenceState() {
        Session session = entityManager.unwrap(Session.class);
        if (session.isDirty()) {
            // Never discard another writer's queued changes to make the guard proceed.
            throw new IllegalStateException("Pending persistence changes precede the Course root lock");
        }
        session.clear();
    }

    @Override
    public void detachManagedCourse(UUID courseId) {
        Objects.requireNonNull(courseId, "courseId");
        SessionImplementor session = entityManager.unwrap(SessionImplementor.class);
        EntityPersister persister = session.getFactory().getMappingMetamodel().getEntityDescriptor(CourseEntity.class);
        Object managed = session.getPersistenceContextInternal().getEntity(session.generateEntityKey(courseId, persister));
        if (managed != null) {
            session.detach(managed);
        }
    }

    private static SqlParameterValue json(String json) {
        return new SqlParameterValue(Types.VARCHAR, json);
    }

    private static void requireOneRow(int rows) {
        if (rows != 1) {
            throw new IllegalStateException("Course write lost its row");
        }
    }
}
