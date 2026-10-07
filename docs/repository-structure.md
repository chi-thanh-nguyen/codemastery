# CodeMastery Repository Structure

**Status:** Approved
**Source:** `docs/references/originals/final-repository-tree.docx`

This document defines the approved repository structure for CodeMastery.
Agents and contributors must preserve this structure unless an explicit
architectural change is approved.

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

│           │           │   │   └── JwtTokenProviderTest.java

│           │           │   ├── course/

│           │           │   │   ├── EnrollmentServiceTest.java

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

│           │   ├── SidebarNav.tsx

│           │   └── StatusViews.tsx

│           ├── hooks/

│           │   └── useDebounce.ts

│           ├── api/

│           │   └── httpClient.ts

│           ├── types/

│           │   └── apiTypes.ts

│           └── utils/

│               └── format.ts

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
