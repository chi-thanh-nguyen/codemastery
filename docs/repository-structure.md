# CodeMastery Repository Structure

**Status:** Approved
**Source:** `docs/references/originals/final-repository-tree.docx`

This document defines the approved repository structure for CodeMastery.
Agents and contributors must preserve this structure unless an explicit
architectural change is approved.

## Bounded Auth test inventory addition

On 2026-10-22 (Asia/Ho_Chi_Minh), Project Leader and repository Owner
@chi-thanh-nguyen explicitly approved exactly one file-inventory exception in the
instruction “CodeMastery — AU03 B2: Approve and Record Bounded Test Placement”:
`backend/src/test/java/com/codemastery/modules/auth/UserPersistenceTest.java`.
Its sole purpose is `UserMapper` unit and `UserRepositoryImpl` mocked-adapter
regression coverage for effective `AU03.mapping` and `AU03.operations` behavior.
[The bounded testing decision](testing.md#approved-au03-persistence-regression-test-placement)
records permitted cases and retained gates. This approved addition is shown in
the Auth test inventory below. The initial placement-only documentation record
did not authorize or deliver implementation; protected integration of this record
is pending. No other file or directory is added to the approved inventory. The immutable
canonical `final-repository-tree.docx` is unchanged; this explicit exception does
not reinterpret its original contents or approve broader structural changes.

The Leader's subsequent instruction “CodeMastery — PR #94: Implement Approved B2
Remediation Directly” on the same date prospectively authorizes implementation of
this file on PR #94 and these two documentation updates. The existing Auth test
CODEOWNERS remain @duyduyit and @chi-thanh-nguyen; no ownership rule changes.
Only mapper unit and mocked-adapter regression coverage is authorized. PostgreSQL
verification, unrelated CT14 proposals and protected review/integration remain
separate gates; original AU03 implementation authorization is not established by
this grant.

## Bounded AU04 test inventory addition

On 2026-10-22 (Asia/Ho_Chi_Minh), Project Leader and repository Owner
@chi-thanh-nguyen explicitly approved one additional file-inventory exception in
“CodeMastery — PR #95: Direct B2 Regression-Test Remediation”:
`backend/src/test/java/com/codemastery/modules/auth/StrongPasswordTest.java`.
The same instruction prospectively authorizes this test and the two documentation
updates on PR #95's existing branch. Its sole responsibility is AU04 password
predicate and Jakarta Bean Validation regression coverage, including Unicode/byte
bounds, whitespace, null/requiredness composition and supported `CharSequence`
inputs. Existing Auth CODEOWNERS remain @duyduyit and @chi-thanh-nguyen.
[The bounded testing decision](testing.md#approved-au04-password-validation-regression-test-placement)
records the exact scope and retained AU06/QA01 consumer/integration gates.

This single approved addition is shown in the Auth test inventory below, alongside
the separate AU03 exception. The immutable canonical `final-repository-tree.docx`
is unchanged; its original contents are not reinterpreted or rewritten. No other
filename, directory, ownership or structural change is approved. Unrelated pending
CT14 proposals retain their dispositions; original AU04 implementation authorization
and protected review/integration are not established by this prospective grant.

## Bounded FE01 inventory additions

The Leader/Owner's explicit D1–D4 delegation in “CodeMastery — Authorized FE01
Remediation and Professional Dashboard UI/UX Implementation”, supplied on
2026-10-23 (Asia/Ho_Chi_Minh), authorized necessary frontend presentation and
verification additions within the existing subtrees. [PR #99](https://github.com/chi-thanh-nguyen/codemastery/pull/99)
merged them as `89998c6e0be1764ed5ec8fcf101124a369a984f2` on 2026-10-24.
The precise additions, now included in the tree below, are:

- `frontend/src/shared/components/PresentationIcon.tsx` — original shared outline SVGs.
- `frontend/src/shared/components/presentation.browser.cjs` — optional existing-Playwright browser verification and evidence capture.
- `frontend/src/shared/components/presentation.test.tsx` — presentation/semantics/callback regressions.
- `frontend/src/shared/hooks/useDebounce.test.tsx` — real React state/timer lifecycle regressions.
- `frontend/src/shared/utils/format.test.ts` — caller-owned formatting and finite-duration regressions.

[The bounded FE01 record](testing.md#bounded-fe01-decisions-and-regression-coverage)
preserves authority, rationale, contracts, verification and exclusions without a
new ADR. The Leader's 2026-10-24 post-merge documentation instruction authorizes
this synchronization-debt correction; independent review/protected integration
remain pending. This does not retroactively satisfy pre-merge documentation
timing, rewrite the immutable canonical tree, change ownership/hierarchy, approve
unrelated CT14/CT37 scope or establish Issue #37 completion.

## Bounded Course unit-test placement correction

On 2026-10-26 (Asia/Ho_Chi_Minh), the Project Leader manually relocated
`CourseServiceTest.java` to
`backend/src/test/java/com/codemastery/modules/course/application/service/CourseServiceTest.java`
and explicitly authorized verification and the corresponding documentation updates.
The [ADR002 CT14.interim placement amendment](decisions/002-interim-delegated-contracts.md#ct14interim--delegated_approved)
records the previous location, this bounded supersession and its rationale.

This approved placement is included in the tree below. The directory matches the
existing `com.codemastery.modules.course.application.service` package and retains
the test's access to package-private Course assertions. The test contents and the
existing Course test CODEOWNERS, @TamiaoNguyen and @chi-thanh-nguyen, are unchanged.
The immutable canonical tree remains unchanged; this is an explicit placement
exception, not a reinterpretation of its original inventory. Other test paths,
contract decisions, test execution and protected review remain outside this
correction.

The Project Leader separately authorized retaining the existing
`EnrollmentServiceTest.java` relocation on 2026-10-29 (Asia/Ho_Chi_Minh) during
PR #114 remediation. [ADR002's Enrollment test placement amendment](decisions/002-interim-delegated-contracts.md#ct14interim--delegated_approved)
records the exact instruction and supersession of its former `modules/course/`
path by `modules/course/application/service/EnrollmentServiceTest.java`.
Existing test responsibilities, package-private access and Course CODEOWNERS
remain unchanged; no production behavior or other inventory change is approved.

```text
codemastery/

├── .github/

    ├── workflows/

    │   └── ci.yml

    ├── ISSUE_TEMPLATE/

    │   ├── feature.md

    │   └── bug-report.md

    ├── CODEOWNERS

    └── PULL_REQUEST_TEMPLATE.md

├── backend/

│   ├── pom.xml

│   ├── mvnw

│   ├── mvnw.cmd

│   ├── .mvn/

│   │   └── wrapper/

│   │       └── maven-wrapper.properties

│   ├── Dockerfile

│   ├── .dockerignore

│   └── src/

│       ├── main/

│       │   ├── java/

│       │   │   └── com/

│       │   │       └── codemastery/

│       │   │           ├── CodeMasteryApplication.java

│       │   │           ├── common/

│       │   │           │   ├── api/

│       │   │           │   │   └── PageResponse.java

│       │   │           │   ├── config/

│       │   │           │   │   └── AppConfig.java

│       │   │           │   ├── exception/

│       │   │           │   │   ├── BusinessRuleException.java

│       │   │           │   │   ├── GlobalExceptionHandler.java

│       │   │           │   │   └── ResourceNotFoundException.java

│       │   │           │   ├── logging/

│       │   │           │   │   └── CorrelationIdFilter.java

│       │   │           │   └── validation/

│       │   │           │       └── StrongPassword.java

│       │   │           ├── infrastructure/

│       │   │           │   └── storage/

│       │   │           │       ├── FileStorage.java

│       │   │           │       └── s3/

│       │   │           │           └── S3FileStorage.java

│       │   │           └── modules/

│       │   │               ├── auth/

│       │   │               │   ├── api/

│       │   │               │   │   ├── controller/

│       │   │               │   │   │   ├── AuthController.java

│       │   │               │   │   │   └── UserProfileController.java

│       │   │               │   │   └── dto/

│       │   │               │   │       ├── AuthRequests.java

│       │   │               │   │       ├── AuthResponse.java

│       │   │               │   │       └── UserResponse.java

│       │   │               │   ├── application/

│       │   │               │   │   └── service/

│       │   │               │   │       ├── AuthService.java

│       │   │               │   │       ├── PasswordResetService.java

│       │   │               │   │       └── UserAccountService.java

│       │   │               │   ├── domain/

│       │   │               │   │   ├── model/

│       │   │               │   │   │   ├── AccountStatus.java

│       │   │               │   │   │   ├── Role.java

│       │   │               │   │   │   └── User.java

│       │   │               │   │   └── repository/

│       │   │               │   │       └── UserRepository.java

│       │   │               │   └── infrastructure/

│       │   │               │       ├── persistence/

│       │   │               │       │   ├── entity/

│       │   │               │       │   │   └── UserEntity.java

│       │   │               │       │   ├── mapper/

│       │   │               │       │   │   └── UserMapper.java

│       │   │               │       │   └── repository/

│       │   │               │       │       ├── UserJpaRepository.java

│       │   │               │       │       └── UserRepositoryImpl.java

│       │   │               │       └── security/

│       │   │               │           ├── AuthenticatedUser.java

│       │   │               │           ├── JwtAuthenticationFilter.java

│       │   │               │           ├── JwtTokenProvider.java

│       │   │               │           ├── RestSecurityErrorHandler.java

│       │   │               │           └── SecurityConfig.java

│       │   │               ├── course/

│       │   │               │   ├── api/

│       │   │               │   │   ├── controller/

│       │   │               │   │   │   ├── CourseCatalogueController.java

│       │   │               │   │   │   ├── CourseManagementController.java

│       │   │               │   │   │   ├── CourseStructureController.java

│       │   │               │   │   │   └── EnrollmentController.java

│       │   │               │   │   └── dto/

│       │   │               │   │       ├── CourseDetailResponse.java

│       │   │               │   │       ├── CourseProgressResponse.java

│       │   │               │   │       ├── CourseRequest.java

│       │   │               │   │       ├── CourseSearchRequest.java

│       │   │               │   │       ├── CourseStructureRequests.java

│       │   │               │   │       ├── CourseStructureResponses.java

│       │   │               │   │       └── CourseSummaryResponse.java

│       │   │               │   ├── application/

│       │   │               │   │   ├── service/

│       │   │               │   │   │   ├── CourseAccessService.java

│       │   │               │   │   │   ├── CourseCatalogueService.java

│       │   │               │   │   │   ├── CourseManagementService.java

│       │   │               │   │   │   ├── CourseStructureService.java

│       │   │               │   │   │   ├── EnrollmentService.java

│       │   │               │   │   │   ├── LearningMaterialService.java

│       │   │               │   │   │   └── LessonProgressService.java

│       │   │               │   │   └── event/

│       │   │               │   │       └── ContentPublishedEvent.java

│       │   │               │   ├── domain/

│       │   │               │   │   ├── model/

│       │   │               │   │   │   ├── Chapter.java

│       │   │               │   │   │   ├── Course.java

│       │   │               │   │   │   ├── Enrollment.java

│       │   │               │   │   │   ├── LearningMaterial.java

│       │   │               │   │   │   ├── Lesson.java

│       │   │               │   │   │   ├── LessonProgress.java

│       │   │               │   │   │   └── PublishStatus.java

│       │   │               │   │   └── repository/

│       │   │               │   │       ├── CourseContentRepository.java

│       │   │               │   │       ├── CourseRepository.java

│       │   │               │   │       └── EnrollmentRepository.java

│       │   │               │   └── infrastructure/

│       │   │               │       └── persistence/

│       │   │               │           ├── entity/

│       │   │               │           │   ├── ChapterEntity.java

│       │   │               │           │   ├── CourseEntity.java

│       │   │               │           │   ├── EnrollmentEntity.java

│       │   │               │           │   ├── LearningMaterialEntity.java

│       │   │               │           │   ├── LessonEntity.java

│       │   │               │           │   └── LessonProgressEntity.java

│       │   │               │           ├── mapper/

│       │   │               │           │   ├── CourseContentMapper.java

│       │   │               │           │   ├── CourseMapper.java

│       │   │               │           │   └── EnrollmentMapper.java

│       │   │               │           └── repository/

│       │   │               │               ├── ChapterJpaRepository.java

│       │   │               │               ├── CourseContentRepositoryImpl.java

│       │   │               │               ├── CourseJpaRepository.java

│       │   │               │               ├── CourseRepositoryImpl.java

│       │   │               │               ├── EnrollmentJpaRepository.java

│       │   │               │               ├── EnrollmentRepositoryImpl.java

│       │   │               │               ├── LearningMaterialJpaRepository.java

│       │   │               │               ├── LessonJpaRepository.java

│       │   │               │               └── LessonProgressJpaRepository.java

│       │   │               ├── assessment/

│       │   │               │   ├── api/

│       │   │               │   │   ├── controller/

│       │   │               │   │   │   ├── AssignmentController.java

│       │   │               │   │   │   ├── QuestionBankController.java

│       │   │               │   │   │   ├── QuizAttemptController.java

│       │   │               │   │   │   └── QuizController.java

│       │   │               │   │   └── dto/

│       │   │               │   │       ├── AssignmentRequests.java

│       │   │               │   │       ├── AssignmentResponses.java

│       │   │               │   │       ├── AttemptResponses.java

│       │   │               │   │       ├── QuestionRequest.java

│       │   │               │   │       ├── QuestionResponse.java

│       │   │               │   │       ├── QuizRequest.java

│       │   │               │   │       ├── QuizResponses.java

│       │   │               │   │       └── SubmitAttemptRequest.java

│       │   │               │   ├── application/

│       │   │               │   │   ├── service/

│       │   │               │   │   │   ├── AssignmentService.java

│       │   │               │   │   │   ├── AttemptService.java

│       │   │               │   │   │   ├── QuestionBankService.java

│       │   │               │   │   │   ├── QuestionSelectionService.java

│       │   │               │   │   │   ├── QuizGradingService.java

│       │   │               │   │   │   ├── QuizService.java

│       │   │               │   │   │   └── SubmissionService.java

│       │   │               │   │   ├── event/

│       │   │               │   │   │   ├── AssignmentGradedEvent.java

│       │   │               │   │   │   └── QuizSubmittedEvent.java

│       │   │               │   │   ├── port/

│       │   │               │   │   │   └── PracticeDifficultyProvider.java

│       │   │               │   │   └── scheduler/

│       │   │               │   │       └── AttemptAutoSubmitScheduler.java

│       │   │               │   ├── domain/

│       │   │               │   │   ├── model/

│       │   │               │   │   │   ├── Assignment.java

│       │   │               │   │   │   ├── Attempt.java

│       │   │               │   │   │   ├── AttemptAnswer.java

│       │   │               │   │   │   ├── DifficultyLevel.java

│       │   │               │   │   │   ├── Question.java

│       │   │               │   │   │   ├── Quiz.java

│       │   │               │   │   │   └── Submission.java

│       │   │               │   │   └── repository/

│       │   │               │   │       ├── AssignmentRepository.java

│       │   │               │   │       ├── AttemptRepository.java

│       │   │               │   │       ├── QuestionRepository.java

│       │   │               │   │       └── QuizRepository.java

│       │   │               │   └── infrastructure/

│       │   │               │       └── persistence/

│       │   │               │           ├── entity/

│       │   │               │           │   ├── AssignmentEntity.java

│       │   │               │           │   ├── AttemptAnswerEntity.java

│       │   │               │           │   ├── AttemptEntity.java

│       │   │               │           │   ├── QuestionEntity.java

│       │   │               │           │   ├── QuizEntity.java

│       │   │               │           │   ├── QuizQuestionEntity.java

│       │   │               │           │   └── SubmissionEntity.java

│       │   │               │           ├── mapper/

│       │   │               │           │   ├── AssignmentMapper.java

│       │   │               │           │   ├── AttemptMapper.java

│       │   │               │           │   └── QuizMapper.java

│       │   │               │           └── repository/

│       │   │               │               ├── AssignmentJpaRepository.java

│       │   │               │               ├── AssignmentRepositoryImpl.java

│       │   │               │               ├── AttemptJpaRepository.java

│       │   │               │               ├── AttemptRepositoryImpl.java

│       │   │               │               ├── QuestionJpaRepository.java

│       │   │               │               ├── QuestionRepositoryImpl.java

│       │   │               │               ├── QuizJpaRepository.java

│       │   │               │               ├── QuizRepositoryImpl.java

│       │   │               │               └── SubmissionJpaRepository.java

│       │   │               ├── adaptive/

│       │   │               │   ├── api/

│       │   │               │   │   ├── controller/

│       │   │               │   │   │   ├── AdaptiveSettingsController.java

│       │   │               │   │   │   ├── LearnerInsightsController.java

│       │   │               │   │   │   ├── MasteryController.java

│       │   │               │   │   │   ├── RecommendationController.java

│       │   │               │   │   │   └── SkillController.java

│       │   │               │   │   └── dto/

│       │   │               │   │       ├── AdaptiveSettingsRequest.java

│       │   │               │   │       ├── AdaptiveSettingsResponse.java

│       │   │               │   │       ├── LearnerInsightResponses.java

│       │   │               │   │       ├── MasteryResponses.java

│       │   │               │   │       ├── RecommendationResponse.java

│       │   │               │   │       ├── SkillGraphResponse.java

│       │   │               │   │       ├── SkillRequests.java

│       │   │               │   │       └── SkillResponse.java

│       │   │               │   ├── application/

│       │   │               │   │   ├── skillgraph/

│       │   │               │   │   │   └── SkillGraphService.java

│       │   │               │   │   ├── mastery/

│       │   │               │   │   │   ├── AdaptiveSettingsService.java

│       │   │               │   │   │   ├── LearnerInsightsService.java 

│       │   │               │   │   │   ├── MasteryCalculator.java

│       │   │               │   │   │   ├── MasteryQueryService.java

│       │   │               │   │   │   ├── MasteryStateClassifier.java

│       │   │               │   │   │   ├── MasteryUpdateService.java

│       │   │               │   │   │   └── PlacementDiagnosticService.java

│       │   │               │   │   ├── decision/

│       │   │               │   │   │   ├── AdaptiveDecisionEngine.java

│       │   │               │   │   │   ├── AdaptiveDecisionService.java

│       │   │               │   │   │   ├── NextLessonResolver.java

│       │   │               │   │   │   └── PracticeDifficultySelector.java

│       │   │               │   │   ├── recommendation/

│       │   │               │   │   │   ├── RecommendationReasonBuilder.java

│       │   │               │   │   │   └── RecommendationService.java

│       │   │               │   │   ├── event/

│       │   │               │   │   │   └── RecommendationCreatedEvent.java

│       │   │               │   │   └── listener/

│       │   │               │   │       └── QuizSubmittedEventListener.java

│       │   │               │   ├── domain/

│       │   │               │   │   ├── model/

│       │   │               │   │   │   ├── AdaptiveDecision.java

│       │   │               │   │   │   ├── LearnerSkillMastery.java

│       │   │               │   │   │   ├── LessonSkill.java

│       │   │               │   │   │   ├── MasteryState.java

│       │   │               │   │   │   ├── MasteryThresholds.java

│       │   │               │   │   │   ├── PathRecommendation.java

│       │   │               │   │   │   ├── RecommendationType.java

│       │   │               │   │   │   ├── Skill.java

│       │   │               │   │   │   ├── SkillGraph.java

│       │   │               │   │   │   └── SkillPrerequisite.java

│       │   │               │   │   └── repository/

│       │   │               │   │       ├── LearnerSkillMasteryRepository.java

│       │   │               │   │       ├── PathRecommendationRepository.java

│       │   │               │   │       └── SkillRepository.java

│       │   │               │   └── infrastructure/

│       │   │               │       └── persistence/

│       │   │               │           ├── entity/

│       │   │               │           │   ├── LearnerSkillMasteryEntity.java

│       │   │               │           │   ├── LessonSkillEntity.java

│       │   │               │           │   ├── PathRecommendationEntity.java

│       │   │               │           │   ├── SkillEntity.java

│       │   │               │           │   └── SkillPrerequisiteEntity.java

│       │   │               │           ├── mapper/

│       │   │               │           │   ├── MasteryMapper.java

│       │   │               │           │   ├── RecommendationMapper.java

│       │   │               │           │   └── SkillMapper.java

│       │   │               │           └── repository/

│       │   │               │               ├── LearnerSkillMasteryJpaRepository.java

│       │   │               │               ├── LearnerSkillMasteryRepositoryImpl.java

│       │   │               │               ├── LessonSkillJpaRepository.java

│       │   │               │               ├── PathRecommendationJpaRepository.java

│       │   │               │               ├── PathRecommendationRepositoryImpl.java

│       │   │               │               ├── SkillJpaRepository.java

│       │   │               │               ├── SkillPrerequisiteJpaRepository.java

│       │   │               │               └── SkillRepositoryImpl.java

│       │   │               ├── interaction/

│       │   │               │   ├── api/

│       │   │               │   │   ├── controller/

│       │   │               │   │   │   ├── ContentReportController.java

│       │   │               │   │   │   ├── DiscussionController.java

│       │   │               │   │   │   └── NotificationController.java

│       │   │               │   │   └── dto/

│       │   │               │   │       ├── InteractionRequests.java

│       │   │               │   │       └── InteractionResponses.java

│       │   │               │   ├── application/

│       │   │               │   │   ├── service/

│       │   │               │   │   │   ├── ContentReportService.java

│       │   │               │   │   │   ├── DiscussionService.java

│       │   │               │   │   │   └── NotificationService.java

│       │   │               │   │   ├── listener/

│       │   │               │   │   │   └── NotificationEventListener.java

│       │   │               │   │   └── scheduler/

│       │   │               │   │       └── DeadlineReminderScheduler.java

│       │   │               │   ├── domain/

│       │   │               │   │   ├── model/

│       │   │               │   │   │   ├── ContentReport.java

│       │   │               │   │   │   ├── DiscussionPost.java

│       │   │               │   │   │   ├── Notification.java

│       │   │               │   │   │   └── NotificationType.java

│       │   │               │   │   └── repository/

│       │   │               │   │       ├── ContentReportRepository.java

│       │   │               │   │       ├── DiscussionPostRepository.java

│       │   │               │   │       └── NotificationRepository.java

│       │   │               │   └── infrastructure/

│       │   │               │       └── persistence/

│       │   │               │           ├── entity/

│       │   │               │           │   ├── ContentReportEntity.java

│       │   │               │           │   ├── DiscussionPostEntity.java

│       │   │               │           │   └── NotificationEntity.java

│       │   │               │           ├── mapper/

│       │   │               │           │   └── InteractionMapper.java

│       │   │               │           └── repository/

│       │   │               │               ├── ContentReportJpaRepository.java

│       │   │               │               ├── ContentReportRepositoryImpl.java

│       │   │               │               ├── DiscussionPostJpaRepository.java

│       │   │               │               ├── DiscussionPostRepositoryImpl.java

│       │   │               │               ├── NotificationJpaRepository.java

│       │   │               │               └── NotificationRepositoryImpl.java

│       │   │               └── admin/

│       │   │                   ├── api/

│       │   │                   │   ├── controller/

│       │   │                   │   │   ├── AdminContentReportController.java

│       │   │                   │   │   ├── AdminCourseController.java

│       │   │                   │   │   ├── AdminReportingController.java

│       │   │                   │   │   └── AdminUserController.java

│       │   │                   │   └── dto/

│       │   │                   │       ├── AdminRequests.java

│       │   │                   │       ├── AdminResponses.java

│       │   │                   │       └── ReportingResponses.java

│       │   │                   ├── application/

│       │   │                   │   ├── service/

│       │   │                   │   │   ├── AdminCourseService.java

│       │   │                   │   │   ├── AuditLogService.java

│       │   │                   │   │   ├── ContentModerationService.java

│       │   │                   │   │   └── UserManagementService.java

│       │   │                   │   └── reporting/

│       │   │                   │       └── PlatformMetricsService.java

│       │   │                   ├── domain/

│       │   │                   │   ├── model/

│       │   │                   │   │   ├── AuditAction.java

│       │   │                   │   │   ├── AuditLog.java

│       │   │                   │   │   ├── CourseMetrics.java

│       │   │                   │   │   └── SkillMasteryDistribution.java

│       │   │                   │   └── repository/

│       │   │                   │       ├── AuditLogRepository.java

│       │   │                   │       └── ReportingQueryRepository.java

│       │   │                   └── infrastructure/

│       │   │                       └── persistence/

│       │   │                           ├── entity/

│       │   │                           │   └── AuditLogEntity.java

│       │   │                           ├── mapper/

│       │   │                           │   └── AuditLogMapper.java

│       │   │                           └── repository/

│       │   │                               ├── AuditLogJpaRepository.java

│       │   │                               ├── AuditLogRepositoryImpl.java

│       │   │                               └── ReportingQueryRepositoryImpl.java

│       │   └── resources/

│       │       ├── application.yml

│       │       └── db/

│       │           └── migration/

│       │               ├── V001__create_auth_schema.sql

│       │               ├── V002__create_course_learning_schema.sql

│       │               ├── V003__create_skill_graph_schema.sql

│       │               ├── V004__create_assessment_schema.sql

│       │               ├── V005__create_mastery_and_recommendation_schema.sql

│       │               └── V006__create_interaction_and_audit_schema.sql

│       └── test/

│           ├── java/

│           │   └── com/

│           │       └── codemastery/

│           │           ├── modules/

│           │           │   ├── auth/

│           │           │   │   ├── AuthServiceTest.java

│           │           │   │   ├── JwtTokenProviderTest.java

│           │           │   │   ├── StrongPasswordTest.java

│           │           │   │   └── UserPersistenceTest.java

│           │           │   ├── course/

│           │           │   │   ├── application/

│           │           │   │   │   └── service/

│           │           │   │   │       ├── CourseServiceTest.java

│           │           │   │   │       └── EnrollmentServiceTest.java

│           │           │   │   └── LessonProgressServiceTest.java

│           │           │   ├── assessment/

│           │           │   │   ├── AttemptServiceTest.java

│           │           │   │   ├── QuizGradingServiceTest.java

│           │           │   │   └── SubmissionServiceTest.java

│           │           │   ├── adaptive/

│           │           │   │   ├── skillgraph/

│           │           │   │   │   └── SkillGraphTest.java

│           │           │   │   ├── mastery/

│           │           │   │   │   ├── AdaptiveSettingsServiceTest.java

│           │           │   │   │   ├── MasteryCalculatorTest.java

│           │           │   │   │   ├── MasteryStateClassifierTest.java

│           │           │   │   │   ├── MasteryUpdateServiceTest.java

│           │           │   │   │   └── PlacementDiagnosticServiceTest.java

│           │           │   │   ├── decision/

│           │           │   │   │   ├── AdaptiveDecisionEngineTest.java

│           │           │   │   │   ├── NextLessonResolverTest.java

│           │           │   │   │   └── PracticeDifficultySelectorTest.java

│           │           │   │   ├── listener/

│           │           │   │   │   └── QuizSubmittedEventListenerTest.java

│           │           │   │   └── evaluation/

│           │           │   │       └── AdaptiveProfileScenarioTest.java

│           │           │   ├── interaction/

│           │           │   │   ├── DeadlineReminderSchedulerTest.java

│           │           │   │   └── NotificationEventListenerTest.java

│           │           │   └── admin/

│           │           │       └── UserManagementServiceTest.java

│           │           ├── integration/

│           │           │   ├── AbstractIntegrationTest.java

│           │           │   ├── AdaptiveLearningFlowIntegrationTest.java

│           │           │   ├── AuthorizationIntegrationTest.java

│           │           │   ├── MasteryProgressTransactionIntegrationTest.java

│           │           │   └── repository/

│           │           │       └── CourseRepositoryIntegrationTest.java

│           │           └── support/

│           │               ├── ScenarioLoader.java

│           │               ├── TestClockConfig.java

│           │               └── fixtures/

│           │                   ├── CourseFixtures.java

│           │                   └── SkillGraphFixtures.java

│           └── resources/

│               ├── application-test.yml

│               └── adaptive-scenarios/

│                   ├── p1-strong-foundation.json

│                   ├── p2-loops-gap.json

│                   └── p3-complete-beginner.json

├── frontend/

│   ├── index.html

│   ├── package.json

│   ├── package-lock.json

│   ├── tsconfig.json

│   ├── vite.config.ts

│   ├── nginx.conf

│   ├── Dockerfile

│   ├── .dockerignore

│   ├── public/

│   │   └── favicon.svg

│   └── src/

│       ├── main.tsx

│       ├── vite-env.d.ts

│       ├── app/

│       │   ├── App.tsx

│       │   ├── providers/

│       │   │   └── AuthProvider.tsx

│       │   └── theme/

│       │       └── theme.ts

│       ├── routes/

│       │   ├── index.tsx

│       │   ├── routePaths.ts

│       │   └── guards/

│       │       └── ProtectedRoute.tsx

│       ├── layouts/

│       │   ├── AuthLayout.tsx

│       │   └── DashboardLayout.tsx

│       ├── features/

│       │   ├── auth/

│       │   │   ├── api/

│       │   │   │   └── authApi.ts

│       │   │   ├── components/

│       │   │   │   └── RegisterForm.tsx

│       │   │   ├── hooks/

│       │   │   │   └── useAuth.ts

│       │   │   ├── pages/

│       │   │   │   ├── ForgotPasswordPage.tsx

│       │   │   │   ├── LoginPage.tsx

│       │   │   │   ├── ProfilePage.tsx

│       │   │   │   ├── RegisterPage.tsx

│       │   │   │   └── ResetPasswordPage.tsx

│       │   │   └── types/

│       │   │       └── authTypes.ts

│       │   ├── catalogue/

│       │   │   ├── api/

│       │   │   │   └── catalogueApi.ts

│       │   │   ├── components/

│       │   │   │   └── CourseCard.tsx

│       │   │   ├── hooks/

│       │   │   │   └── useCourseCatalogue.ts

│       │   │   ├── pages/

│       │   │   │   ├── CourseCataloguePage.tsx

│       │   │   │   └── CourseDetailPage.tsx

│       │   │   └── types/

│       │   │       └── catalogueTypes.ts

│       │   ├── learning/

│       │   │   ├── api/

│       │   │   │   └── learningApi.ts

│       │   │   ├── components/

│       │   │   │   ├── CourseOutline.tsx

│       │   │   │   └── LessonContent.tsx

│       │   │   ├── hooks/

│       │   │   │   └── useCourseLearning.ts

│       │   │   ├── pages/

│       │   │   │   ├── CourseLearningPage.tsx

│       │   │   │   ├── LessonPage.tsx

│       │   │   │   └── MyCoursesPage.tsx

│       │   │   └── types/

│       │   │       └── learningTypes.ts

│       │   ├── assessment/

│       │   │   ├── api/

│       │   │   │   ├── assignmentApi.ts

│       │   │   │   └── quizApi.ts

│       │   │   ├── components/

│       │   │   │   ├── QuestionRenderer.tsx

│       │   │   │   └── QuizTimer.tsx

│       │   │   ├── hooks/

│       │   │   │   ├── useAssignment.ts

│       │   │   │   └── useQuizAttempt.ts

│       │   │   ├── pages/

│       │   │   │   ├── AssignmentDetailPage.tsx

│       │   │   │   ├── AttemptHistoryPage.tsx

│       │   │   │   ├── CourseAssessmentsPage.tsx

│       │   │   │   ├── QuizAttemptPage.tsx

│       │   │   │   └── QuizResultPage.tsx

│       │   │   └── types/

│       │   │       └── assessmentTypes.ts

│       │   ├── mastery/

│       │   │   ├── api/

│       │   │   │   └── masteryApi.ts

│       │   │   ├── components/

│       │   │   │   ├── MasteryGraph.tsx

│       │   │   │   ├── MasteryStateChip.tsx

│       │   │   │   ├── NextRecommendedCard.tsx

│       │   │   │   ├── PlacementPrompt.tsx

│       │   │   │   └── SkillDetailPanel.tsx

│       │   │   ├── hooks/

│       │   │   │   └── useMasteryMap.ts

│       │   │   ├── pages/

│       │   │   │   └── MasteryMapPage.tsx

│       │   │   ├── types/

│       │   │   │   └── masteryTypes.ts

│       │   │   └── utils/

│       │   │       └── masteryGraphLayout.ts

│       │   ├── interaction/

│       │   │   ├── api/

│       │   │   │   ├── discussionApi.ts

│       │   │   │   └── notificationApi.ts

│       │   │   ├── components/

│       │   │   │   ├── DiscussionPanel.tsx

│       │   │   │   ├── NotificationBell.tsx

│       │   │   │   ├── NotificationItem.tsx

│       │   │   │   └── ReportContentDialog.tsx

│       │   │   ├── hooks/

│       │   │   │   ├── useDiscussion.ts

│       │   │   │   └── useNotifications.ts

│       │   │   ├── pages/

│       │   │   │   └── NotificationsPage.tsx

│       │   │   └── types/

│       │   │       └── interactionTypes.ts

│       │   ├── instructor/

│       │   │   ├── course-management/

│       │   │   │   ├── api/

│       │   │   │   │   ├── assessmentAuthoringApi.ts

│       │   │   │   │   └── instructorCourseApi.ts

│       │   │   │   ├── components/

│       │   │   │   │   ├── ChapterList.tsx

│       │   │   │   │   ├── MaterialForm.tsx

│       │   │   │   │   ├── PublishToggle.tsx

│       │   │   │   │   └── QuestionForm.tsx

│       │   │   │   ├── hooks/

│       │   │   │   │   ├── useAssessmentEditor.ts

│       │   │   │   │   ├── useCourseEditor.ts

│       │   │   │   │   └── useQuestionBank.ts

│       │   │   │   ├── pages/

│       │   │   │   │   ├── AssignmentEditorPage.tsx

│       │   │   │   │   ├── CourseEditorPage.tsx

│       │   │   │   │   ├── InstructorCoursesPage.tsx

│       │   │   │   │   ├── LessonEditorPage.tsx

│       │   │   │   │   ├── LessonQaPage.tsx

│       │   │   │   │   ├── QuestionBankPage.tsx

│       │   │   │   │   └── QuizEditorPage.tsx

│       │   │   │   └── types/

│       │   │   │       └── courseManagementTypes.ts

│       │   │   ├── skill-graph-editor/

│       │   │   │   ├── api/

│       │   │   │   │   └── skillGraphApi.ts

│       │   │   │   ├── components/

│       │   │   │   │   ├── AdaptiveSettingsPanel.tsx

│       │   │   │   │   ├── LessonSkillMappingTable.tsx

│       │   │   │   │   ├── SkillForm.tsx

│       │   │   │   │   └── SkillGraphCanvas.tsx

│       │   │   │   ├── hooks/

│       │   │   │   │   └── useSkillGraph.ts

│       │   │   │   ├── pages/

│       │   │   │   │   └── SkillGraphEditorPage.tsx

│       │   │   │   └── types/

│       │   │   │       └── skillGraphTypes.ts

│       │   │   ├── grading/

│       │   │   │   ├── api/

│       │   │   │   │   └── gradingApi.ts

│       │   │   │   ├── components/

│       │   │   │   │   └── SubmissionTable.tsx

│       │   │   │   ├── hooks/

│       │   │   │   │   └── useSubmissions.ts

│       │   │   │   ├── pages/

│       │   │   │   │   ├── GradeSubmissionPage.tsx

│       │   │   │   │   └── SubmissionListPage.tsx

│       │   │   │   └── types/

│       │   │   │       └── gradingTypes.ts

│       │   │   └── learner-insights/

│       │   │       ├── api/

│       │   │       │   └── learnerInsightsApi.ts

│       │   │       ├── components/

│       │   │       │   ├── LearnerTable.tsx

│       │   │       │   └── RecommendationLogTable.tsx

│       │   │       ├── hooks/

│       │   │       │   └── useLearnerInsights.ts

│       │   │       ├── pages/

│       │   │       │   ├── LearnerDetailPage.tsx

│       │   │       │   └── LearnerInsightsPage.tsx

│       │   │       └── types/

│       │   │           └── learnerInsightsTypes.ts

│       │   └── admin/

│       │       ├── user-management/

│       │       │   ├── api/

│       │       │   │   └── adminUserApi.ts

│       │       │   ├── components/

│       │       │   │   └── UserTable.tsx

│       │       │   ├── hooks/

│       │       │   │   └── useUserManagement.ts

│       │       │   ├── pages/

│       │       │   │   └── UserManagementPage.tsx

│       │       │   └── types/

│       │       │       └── userManagementTypes.ts

│       │       ├── content-reports/

│       │       │   ├── api/

│       │       │   │   └── adminReportApi.ts

│       │       │   ├── components/

│       │       │   │   ├── ReportDetailDialog.tsx

│       │       │   │   └── ReportTable.tsx

│       │       │   ├── hooks/

│       │       │   │   └── useReportQueue.ts

│       │       │   ├── pages/

│       │       │   │   └── ContentReportQueuePage.tsx

│       │       │   └── types/

│       │       │       └── contentReportTypes.ts

│       │       ├── course-management/

│       │       │   ├── api/

│       │       │   │   └── adminCourseApi.ts

│       │       │   ├── components/

│       │       │   │   └── AdminCourseTable.tsx

│       │       │   ├── hooks/

│       │       │   │   └── useAdminCourses.ts

│       │       │   ├── pages/

│       │       │   │   └── AdminCoursesPage.tsx

│       │       │   └── types/

│       │       │       └── adminCourseTypes.ts

│       │       └── reporting-dashboard/

│       │           ├── api/

│       │           │   └── reportingApi.ts

│       │           ├── components/

│       │           │   ├── AuditLogTable.tsx

│       │           │   ├── EnrolmentChart.tsx

│       │           │   └── MasteryDistributionChart.tsx

│       │           ├── hooks/

│       │           │   ├── useAuditLog.ts

│       │           │   └── useReportingMetrics.ts

│       │           ├── pages/

│       │           │   ├── AuditLogPage.tsx

│       │           │   └── ReportingDashboardPage.tsx

│       │           └── types/

│       │               └── reportingTypes.ts

│       └── shared/

│           ├── components/

│           │   ├── AppHeader.tsx

│           │   ├── ConfirmDialog.tsx

│           │   ├── FileUploadField.tsx

│           │   ├── MarkdownRenderer.tsx

│           │   ├── PageHeader.tsx

│           │   ├── PresentationIcon.tsx

│           │   ├── SidebarNav.tsx

│           │   ├── StatusViews.tsx

│           │   ├── presentation.browser.cjs

│           │   └── presentation.test.tsx

│           ├── hooks/

│           │   ├── useDebounce.ts

│           │   └── useDebounce.test.tsx

│           ├── api/

│           │   └── httpClient.ts

│           ├── types/

│           │   └── apiTypes.ts

│           └── utils/

│               ├── format.ts

│               └── format.test.ts

├── e2e/

│   ├── package.json

│   ├── package-lock.json

│   ├── playwright.config.ts

│   ├── tests/

│   │   ├── learner-flow.spec.ts

│   │   └── adaptive-flow.spec.ts

│   └── fixtures/

│       ├── auth-fixture.ts

│       └── files/

│           └── sample-submission.txt

├── infrastructure/

│   ├── docker-compose.yml

│   └── vm/

│       └── deploy.sh

├── sample-data/

│   ├── README.md

│   ├── materials/

│   │   └── sample-slides.pdf

│   └── seed/

│       ├── 01-users.sql

│       ├── 02-course-python-fundamentals.sql

│       ├── 03-skill-graph-and-lesson-mapping.sql

│       ├── 04-questions-quizzes-assignments.sql

│       └── 05-learner-profiles.sql

├── docs/

│   ├── requirements.md

│   ├── architecture.md

│   ├── repository-structure.md

│   ├── references/

│       └── originals/

│           ├── project-requirements.pdf

│           ├── approved-architecture.docx

│           └── final-repository-tree.docx

│   ├── setup.md

│   ├── data-model.md

│   ├── adaptive-learning.md

│   ├── testing.md

│   ├── deployment.md

│   ├── api/

│   │   └── openapi.yaml

│   └── decisions/

│       └── 001-modular-monolith-module-boundaries.md

├── .env.example

├── .gitignore

├── AGENTS.md

├── README.md

└── CONTRIBUTING.md
```

## Bounded Interim FE03/FE07 test inventory

**CT14-INTERIM-FE03-FE07-2026-10-24-r1 —
APPROVED_PENDING_PROTECTED_INTEGRATION.** The Leader's explicit
“CodeMastery — Complete Interim Technical Contract Approvals (CT16, CT17, CT14)”,
24 October 2026, delegates technical approval of the smallest FE03/FE07 test
inventory. Codex acts as the delegated approval authority for exactly:

- frontend/src/shared/api/httpClient.test.ts
- frontend/src/app/providers/AuthProvider.test.tsx
- frontend/src/routes/index.test.tsx
- frontend/src/features/catalogue/api/catalogueApi.test.ts
- frontend/src/features/catalogue/pages/CourseCataloguePage.test.tsx
- frontend/src/features/catalogue/pages/CourseDetailPage.test.tsx

These six planned additions use existing owning directories and current
@heizseung/@chi-thanh-nguyen CODEOWNERS; no new helper, hierarchy or ownership rule.
[The exact testing contract](testing.md#approved-interim-fe03fe07-verification-contract--24-october-2026)
records coverage, isolation, CT16 pending-PR dependency and execution limits.
No listed file is created or claimed implemented by this documentation change.
The original canonical tree and existing AU03/AU04/FE01 exceptions are unchanged;
this explicit bounded exception does not approve unrelated test inventory.
Separate implementation authority and compliant protected integration are required.


### FE07 delegated test placement — 27 October 2026

The Leader's explicit full-FE07 task authorizes the necessary test additions below
within existing owning feature/module and integration directories (ADR002
`FE07-2026-10-27-r1`). Existing scaffolds remain the production callable locations;
no module or production directory is added.

- frontend/src/features/learning/api/learningApi.test.ts
- frontend/src/features/learning/pages/MyCoursesPage.test.tsx
- backend/src/test/java/com/codemastery/integration/EnrollmentIntegrationTest.java

Existing CourseCatalogue API/page tests and routes/index.test.tsx cover the other
FE07 changes. e2e/tests/learner-flow.spec.ts is the existing browser-flow location;
the external Playwright installation used for local execution is not a dependency
or E2E pipeline change.


### SD01/SD02 delegated test placement — 30 October 2026

The Leader's explicit SD01/SD02 completion task authorizes the single test addition
below within the existing integration directory (ADR002 `CT15-SD-2026-10-30-r1`,
V1). It verifies the existing `sample-data/seed/` files; no seed, material,
loader-script or helper path is added.

- backend/src/test/java/com/codemastery/integration/SampleDataSeedIntegrationTest.java

The approved `sample-data/` inventory is unchanged: `README.md`,
`materials/sample-slides.pdf` and `seed/01`–`05` keep their existing paths.
