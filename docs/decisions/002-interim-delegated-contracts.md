# ADR002: Bounded Interim Delegated Technical Contracts

## Status and authority

DECISION_AUTHORIZED. The Project Leader explicitly selected CT20.I and activated Level B for the bounded contracts below on2026-10-11 Asia/Ho_Chi_Minh. This ADR is proposed through protected review; it is not evidence that canonical main has merged it, that application features exist, or that implementation has been authorized. Once merged it records the specified approved slices. The isolated execution ledger preserves all58 source dispositions and exact instruction provenance; no entire contract family is approved by its slice label.

Sources: official project-requirements PDF; approved architecture/data model; ADR001; AGENTS.md; completed audit/readiness/governance packages; accepted planning graph33a0cc601d39bd866d02aecde70ef50509ab716c. The155 optimized IDs/212 mappings, Stage5 certified67 Issues and56 child links remain unchanged. The428 expanded graph edges and352 staging relationships are conditional prerequisites, not universal whole-Issue blockers. This ADR introduces no new native Issue dependencies, physical schema, module boundary, roles or production infrastructure.

## Approved slices

### CT20.I — LEADER_AUTHORIZED_BY_EXPLICIT_INSTRUCTION

Core Authentication/Authorization; real browser → backend REST → PostgreSQL Course catalogue/detail; deterministic local mastery calculation/classification with executed and independently verifiable results. Integrated quiz → grading → persisted mastery/progress → recommendation → map is Stretch. Preserve English Interim report (six required sections, maximum10 pages), weekly contributions, evaluation plans/results, lecturer-accessible prototype, external verification and required submission documentation. Deadline2026-11-01 23:59 Asia/Ho_Chi_Minh. No direct conflict found in the official Interim requirements; this does not waive full Final requirements.

Rationale: Select the three expressly authorized components; do not promote every Final acceptance criterion into Interim Minimum.

Alternatives considered: Stretch gives stronger integration evidence but adds event/progress/routing/coverage contracts; selecting every Final feature now increases the Interim critical path. Local mocks alone cannot establish core integration or external access.

Owner: Leader, all seven capability/evidence owners. Consumers: CT20, QA14, DO03. Checkpoints: ['QA14.I-minimum.plan', 'DO03 selected scope'].

Deferred: Unselected Stretch/Final capabilities; actual deployment, report, contribution evidence and academic submission remain separate gates.

### CT18.minimum — DELEGATED_APPROVED

Use Spring-native RFC9457 ProblemDetail and application/problem+json: type=about:blank; title=standard HTTP reason; status=HTTP status; detail=safe English text; instance=request path without query. Validation extension errors=[{field,code,message}] sorted by field then code; no rejected values, secrets, database details or stack traces. Validation/malformed UUID/body/pagination400; missing/invalid credentials401 with generic authentication failure; authenticated forbidden403; unavailable public resource404; duplicate canonical email409; unexpected500 generic. Framework405/415 retain proper HTTP semantics and safe problem representation. PageResponse<T>={content,page,size,totalElements,totalPages}; zero-based page default0, size default20 range1..100, negatives/invalid bounds400; beyond-last-page200 empty content; totalPages=ceil(totalElements/size), empty=0. No undocumented fields/automatic mutation retries.

Rationale: Native framework support avoids a parallel exception library; explicit envelope stabilizes client integration. Bounded paging controls resource usage without selecting Final search/filter.

Alternatives considered: A bespoke error DTO adds another public shape and may require an unapproved file. Returning 200 for failures or exposing exception/SQL/token details harms interoperability/security. Feature-specific errors and page limits are outside this slice.

Owner: Leader with Auth, Course and Frontend reviewers. Consumers: CT18, SH01, AU05, AU06, CO05, FE03, FE07. Checkpoints: ['CT18.errors', 'CT18.page-validation', 'SH01 selected slices', 'AU05/AU06/CO05/FE03/FE07 consumed bindings'].

Deferred: Final search/filter APIs and unrelated contracts; rate-limiting policy and new production dependencies.

### CT01.core — DELEGATED_APPROVED

Registration/login email: trim outer whitespace, ASCII lowercase, require syntactically valid ASCII email with no remaining whitespace, consistent with V001 unique canonical email. Password: do not trim or normalize; minimum15 Unicode code points, maximum72 UTF-8 bytes; no character-class rules; reject overflow. displayName: trim, nonblank, maximum100 Unicode code points, no control characters. Learner self-registration only; no supplied role/status accepted. BCrypt $2b$, local work factor14: five measured verification samples median835.355ms, maximum836.828ms with Spring Security crypto7.1.1 on Java25; remeasure on target host before deployment. JJWT0.13.0 fixed HS256, hex-decoded JWT_SECRET exactly64 hexadecimal characters (32 random bytes supplied at runtime, no secret value here), issuer codemastery, audience codemastery-api, UUID subject, iat and exp required, lifetime PT15M, zero expiry clock skew; reject incorrect algorithm/signature/issuer/audience, future iat, expired token or absent claims. Effective account status and role are checked against Auth-owned data for each protected request; locked accounts cannot authenticate or use a token. POST /api/auth/register201 returns {id,displayName,role}; POST /api/auth/login200 returns {accessToken,tokenType:Bearer,expiresIn:900,user:{id,displayName,role}}. No registration auto-login. Role values Learner/Instructor/Administrator at public DTO boundary, mapped to existing learner/instructor/admin storage. Never log passwords/tokens. Generic401 for missing user, bad password or locked account; use an equally costly dummy verification for missing accounts. AuthService owns application orchestration, UserRepository stays inside Auth; transactional registration relies on DB uniqueness and safely maps races to409.

Rationale: 15-minute lifetime bounds stolen-token exposure while memory-only clients can reauthenticate; fixed HS256 avoids algorithm confusion. Cost14 is the strongest measured candidate under the ~1-second local verification budget. This is evidence about this machine, not a concurrency or production benchmark. Existing three roles/trust boundaries remain.

Alternatives considered: HttpOnly cookie transport adds cookie/CSRF/origin policy and changes the API; localStorage persists theft exposure; refresh/revocation tables create new durable commitments. Stateless logout is simpler but does NOT immediately revoke a stolen token. HS256 suits one trust domain; asymmetric multi-service trust is unnecessary for this monolith. BCrypt remains mandated; no Argon2 substitution.

Owner: Auth @duyduyit; Frontend and Leader review. Consumers: CT01, AU03, AU04, AU05, AU06, FE03, QA01. Checkpoints: ['AU04 password predicate', 'AU05 JWT/security', 'AU06 selected API', 'FE03 Auth client', 'QA01 selected expectations'].

Deferred: Server logout/revocation and explicit acceptance of residual-token validity require Leader security decision; no logout endpoint approved here. Profile/reset/refresh/persistent browser sessions, production abuse controls and external trust/access policy remain unapproved. Rebenchmark cost on deployment host.

### CT16.minimum — DELEGATED_APPROVED

Use native fetch in existing shared/api/httpClient.ts with explicit VITE_API_BASE_URL (absolute origin or same-origin /api path), JSON request/response normalization matching OpenAPI, typed PageResponse and ProblemDetail in shared/types/apiTypes.ts. Bearer token held in Auth-provider memory only; reload requires login. Send Authorization only to the configured API origin; no browser persistent token storage, URL token or default credentials cookie. On401 clear memory and request login; preserve403 separately; show safe English validation/general/network errors. No automatic retries for POST; GET retries, cancellation and router package selection remain deferred. Feature-specific clients use existing authApi.ts and frontend/src/features/catalogue/api/catalogueApi.ts paths; no new Axios/client framework dependency.

Rationale: Native fetch satisfies the bounded client need without dependency duplication. Memory-only token handling reduces durable token theft, with explicit re-login usability cost.

Alternatives considered: Multiple clients or cookie/token hybrids create inconsistent trust behavior. Hand-written routing makes deep links and history harder. Latest router is not automatically compatible with the declared Node range; no runtime upgrade is selected.

Owner: Frontend @heizseung; Auth and Leader review. Consumers: CT16, FE03, FE07, QA07. Checkpoints: ['CT16.http', 'CT16.shell', 'CT16.auth', 'FE03', 'FE07 consumed client'].

Deferred: Server logout semantics; router dependency selection and full Frontend behavior; no existing executable consumer assumed.

**CT16 path correction provenance — 24 October 2026:** Under the Leader's
explicit “CodeMastery — Bounded Technical Approval Under Explicit Leader
Delegation”, Codex technically approves only the mistaken `courseApi.ts`
reference correction above to the canonical existing
`frontend/src/features/catalogue/api/catalogueApi.ts`. The immutable original
repository tree and current approved inventory agree on this location. See
[CT16-2026-10-24-r1](../setup.md#ct16-delegated-technical-approval--24-october-2026)
for authority, evidence and protected-integration gates. This correction is
**APPROVED_PENDING_PROTECTED_INTEGRATION**; no filename, API, unrelated ADR002
clause or historical disposition is changed, and no implementation is authorized.

### CT38.core — DELEGATED_APPROVED

Retain current names DB_HOST/DB_PORT/DB_NAME/DB_USER/DB_PASSWORD, JWT_SECRET/JWT_EXPIRATION, CORS_ALLOWED_ORIGINS, VITE_API_BASE_URL and SCHEDULING_ENABLED; do not claim current placeholders implement validation. New Auth target JWT_EXPIRATION defaults PT15M and must equal PT15M for this Interim policy; audience codemastery-api and issuer codemastery are fixed to this single application. Fail consumed configuration binding on missing/placeholder secret, invalid hex key, invalid duration, malformed/empty API origin or invalid consumed datasource input. No secret defaults. Only public API address crosses into VITE_*; nothing sensitive. Host local ports/bindings remain existing8080/5173 and Compose service overrides; exact existing local CORS origin http://localhost:5173, no wildcard origins. Test secrets are generated ephemerally, not committed; disposable test database and scheduler disabled as documented. Validate optional integrations only when actually consumed; declaring placeholders alone is not proof of startup validation.

Rationale: Typed fail-fast consumed configuration prevents misleading defaults while avoiding mandatory unused integrations for a bounded local calculator. Current one-hour JWT fallback is existing configuration, not approved new policy; implementation changes are a later task.

Alternatives considered: Blanket reuse of local Compose defaults misroutes browsers and weakens deployment security; inventing new env names or silently selecting ports expands the contract. Secret managers/new services are outside Minimum.

Owner: Leader; Auth, Frontend and Infrastructure reviewers. Consumers: CT38, SH01, AU05, AU06, FE03, QA01. Checkpoints: ['CT38.core', 'SH01 config', 'AU05/AU06 runtime', 'QA01 selected config'].

Deferred: Real secrets, production DNS/origins/credentials and runtime config edits; password reset/storage/scheduler business values not newly approved.

### CT03.catalogue — DELEGATED_APPROVED

Anonymous read-only published Course catalogue and detail are allowed. Filter publication in backend repository/application query. Missing and unpublished course detail return the same404. Public syllabus includes published lesson headings only, excluding chapters without published lessons. Expose no unpublished title, lesson content, protected materials, storage URLs, enrolment/progress or adaptive_settings. Instructor/learner modification and learning-content access are outside this read contract.

Rationale: A public discovery path satisfies the selected workflow without inventing an Auth identity dependency; publication filtering protects hidden data.

Alternatives considered: Requiring identity for public catalogue adds a cross-module Auth gate with little benefit to discoverability. Returning 403 for hidden public resources reveals existence. This selection does not authorize hidden-course management or protected learning reads.

Owner: Course @TamiaoNguyen; Auth/Frontend/Leader reviewers. Consumers: CT03, CO03, CO04, CO05, FE07, QA02. Checkpoints: ['CT03.catalogue', 'CO04 catalogue', 'CO05', 'FE07', 'QA02.I-catalogue'].

Deferred: Protected learning content, enrolment/ownership write policies and Final criteria.

### CT21.catalogue — DELEGATED_APPROVED

GET /api/courses?page=0&size=20 returns PageResponse<CourseSummaryResponse>. CourseSummaryResponse={id:UUID,title:string,description:string,instructorId:UUID}. GET /api/courses/{courseId} returns CourseDetailResponse={id,title,description,instructorId,prerequisiteDescription:string|null,syllabus:[{id:UUID,title:string,position:integer,lessons:[{id:UUID,title:string,position:integer}]}]}. List order title ascending with PostgreSQL COLLATE C then UUID ascending tie-break; course order is not learning-path order. Syllabus sorted chapter.position then lesson.position, preserving canonical course sequence. DTO mapping stays in Course; do not join Auth entities/internals. Full human-readable instructor display requires separately approved CT01.identity and is not claimed satisfied by instructorId. Reject unsupported query parameters with400; no selected search/filter/sort parameter in this Interim slice.

Rationale: Small exact DTOs prevent entity leakage. Stable paging/order is interoperable. Keeping instructorId avoids material new identity coupling while preserving an explicit unresolved display criterion.

Alternatives considered: Full search/pagination adds public validation/page defaults and additional tests; a bounded demo-list slice is cheaper but only partial US-02. Returning entities or joining another module internals violates boundaries. Fake instructor names and pretending a UUID is the whole user-facing instructor criterion are unacceptable.

Owner: Course @TamiaoNguyen and Frontend @heizseung; Leader approval. Consumers: CT21, CO03, CO05, FE07, QA02, QA07. Checkpoints: ['CT21.catalogue', 'CO03 catalogue capability', 'CO05', 'FE07', 'QA02/QA07 selected cases'].

Deferred: Instructor displayName lookup and full US-02 acceptance; Final search/filter; physical schema unchanged.

### CT06.prototype — DELEGATED_APPROVED

INTERIM_ONLY_DELEGATED_APPROVAL. Pure local one-course/one-skill snapshot of synthetic, already classified evidence: courseId and skillId UUIDs; correctCount and answeredCount integers0..2147483647 with correctCount<=answeredCount. No grading, aggregation or persistence implied. answeredCount=0 yields Unknown; otherwise exact rational rate correctCount/answeredCount. Configurable explicit rational thresholds weak=1/2 and mastered=3/4 for Interim demonstration; require0<weak<mastered<=1. rate<weak→Weak; weak<=rate<mastered→Learning; rate>=mastered→Mastered. Equality is explicit; compare with long cross products, not floating rounding. Invalid inputs/policy raise IllegalArgumentException and cause no side effect. Evidence absence is different from zero successes. Examples0/0 Unknown,0/1 Weak,1/2 Learning,2/3 Learning,3/4 Mastered,1/1 Mastered. Values are illustrative delegated prototype choices, not empirically validated learning thresholds or the routing evaluation target.

Rationale: An exact count ratio provides transparent independent arithmetic and deterministic boundaries; rational thresholds avoid float ambiguity. Compared with weighted history/decay it introduces fewer unsupported assumptions and no schema/event dependency. It is not a production mastery or quiz-grading algorithm.

Alternatives considered: A fabricated weighted average, latest-attempt rule or threshold would invent educational behavior. Pulling production settings persistence/AD08 into local calculation increases unresolved scope; mocking outputs is not calculation evidence.

Owner: Adaptive @thanhtran0311; Leader and relevant data reviewers. Consumers: CT06, AD09, QA14. Checkpoints: ['CT06.prototype', 'AD09 selected local implementation'].

Deferred: Final mastery policy, settings JSON/persistence, attempt aggregation/weighting/decay, events, lesson progress, routing, map and cross-module effects.

### CT24.calculation — DELEGATED_APPROVED

INTERIM_ONLY_DELEGATED_APPROVAL. Existing adaptive/application/mastery/MasteryCalculator.java: calculate(SkillEvidence)->MasteryEvidenceRate, with immutable nested records SkillEvidence(UUID courseId,UUID skillId,int correctCount,int answeredCount) and MasteryEvidenceRate carrying those validated IDs/counts and isUnknown(). Existing MasteryStateClassifier.java: classify(MasteryCalculator.MasteryEvidenceRate,ClassificationThresholds)->MasteryState; nested immutable RationalThreshold(int numerator,int denominator) and ClassificationThresholds(weak,mastered). Use existing domain mastery domain/model/MasteryState categorical enum. Null/invalid IDs, counts or policies raise IllegalArgumentException. Records validate the same constraints, and classifier compares exact counts/thresholds. Callers pass explicit policy; no hidden clock, repository, settings lookup or AD08 persistence requirement. No new top-level files/interfaces or cross-module calls.

Rationale: Pure functions in approved existing files provide locally testable contracts with transparent state and no new structural boundary. long products of bounded positive int operands cannot overflow signed long.

Alternatives considered: Exposing a premature public cross-module calculator API fixes unapproved behavior; injecting repository/settings services into the classifier couples the local prototype to production. Conforming doubles may support local orchestration but cannot prove actual computation.

Owner: Adaptive @thanhtran0311; Leader and applicable consumers review. Consumers: CT24, AD09, QA14. Checkpoints: ['CT24.calculation', 'AD09 consumed types'].

Deferred: Final policy reuse, AD08 persisted settings types and integrated consumers; dependent AD03 actual enum artifact still needed.

### CT15.I-data — DELEGATED_APPROVED

Use a versioned English synthetic dataset interim-v1. UUIDv5 using standard URL namespace and name https://codemastery.test/interim-v1/<logical-key>. Identities: user/instructor, instructor@example.test, Instructor Demo, instructor/active; user/learner, learner@example.test, Learner Demo, learner/active. No seeded Administrator or real person. Courses course/intro=Introduction to Programming and course/practice=Programming Practice published; course/draft=Instructor Draft unpublished, all owned by synthetic instructor. Set adaptive_enabled=false and adaptive_settings=NULL; draft publication_status=hidden; published lesson publication_status=published. Each published course has fully course-qualified keys <course-key>/chapter/1 Getting Started and <course-key>/chapter/1/lesson/1 Concepts, <course-key>/chapter/1/lesson/2 Practice, positions1/2; course descriptions Demo course for the Interim catalogue, prerequisiteDescription No prerequisites. Other required lifecycle fields/timestamps are supplied explicitly by the versioned seed generator, fixed2026-10-11T00:00:00Z where schema consumes timestamps. Use existing sample-data/seed files, not migrations; generate password hashes locally from operator-supplied synthetic runtime password satisfying CT01. No committed plaintext/hashes/real credentials. Explicit transaction loader skips exact matching rows and stops on conflicting IDs/content; no reset/truncate/overwrite. Pure mastery fixtures are independent in-memory approved counts/policies, not learner DB records.

Rationale: Deterministic IDs and hidden/published contrast enable repeatability and privacy checks; reserved .test names prevent contact with real accounts. Runtime credentials avoid a public fixed demo secret.

Alternatives considered: Large sample packs and file materials introduce licensing/storage scope. Checked-in known passwords or opaque unknown hash generation harms access/security. Auto-loading production seeds from migrations changes schema/data ownership.

Owner: Auth @duyduyit; Course @TamiaoNguyen; Leader coordinates data/QA. Consumers: CT15, SD01, SD02, QA13, QA01, QA02, QA07, QA14. Checkpoints: ['SD01.I-min', 'SD02.I-min', 'QA13.I-data if selected', 'QA01/QA02/QA07 consumed fixtures'].

Deferred: Actual dataset/generator/loader implementation and independent fixture review; protected materials/Final samples and real external accounts.

### CT14.interim — DELEGATED_APPROVED

Retain Boot-managed JUnit Jupiter6/Mockito, Maven Wrapper, existing disposable PostgreSQL test harness, rollback/explicit cleanup for committed HTTP/thread work. Auth unit tests in existing AuthServiceTest.java and JwtTokenProviderTest.java; extracted internal password utility covered through Auth service behavior initially, separate test files require approved path decision. Course service unit tests in backend/src/test/java/com/codemastery/modules/course/CourseServiceTest.java and repository/access/pagination PostgreSQL integration in backend/src/test/java/com/codemastery/integration/CourseCatalogueIntegrationTest.java; these two precise test-file additions within existing approved test subtrees are approved as B5 placement decisions for a later separately authorized implementation task; backend authorization in existing integration/AuthorizationIntegrationTest.java. Pure mastery tests in existing mastery/MasteryCalculatorTest.java and MasteryStateClassifierTest.java, no DB/event/clock. Existing feature unit tests plus approved integration harness support catalogue; do not put unrelated tests into EnrollmentServiceTest. QA07 independent browser protocol proves browser→REST→real PG and hidden course filtering; unit tests alone do not. Cover password15-codepoint/72-byte boundaries, Unicode, canonical-email races, invalid JWT claims/expiry/locked account, no token persistence,401/403, pagination1/100/invalid/end, hidden404, DTO privacy and exact mastery boundaries. No new framework, threshold coverage percentage or invented script.

Rationale: Layered evidence matches the approved test strategy and distinguishes local arithmetic from persistence/browser/deployment behavior. Test scope follows consumed capabilities, not whole-Issue blocking.

Alternatives considered: Mocks do not prove PG constraints; browser screenshots do not prove every API denial. A new frontend runner adds dependency/scope approval; fabricating commands for currently empty E2E files is misleading.

Owner: Leader, feature test owners, Frontend and Adaptive reviewers. Consumers: CT14, SH01, AU03, AU04, CO04, CO05, FE03, FE07, QA01, QA02, QA07, QA14. Checkpoints: ['CT14.interim', 'CT14.coverage', 'selected local-complete', 'QA01/QA02/QA07/QA14 selected plans'].

Deferred: No other new test locations approved; actual test runs/feature implementation and independent review remain gates.

### CT36.I-results — DELEGATED_APPROVED

INTERIM_ONLY_DELEGATED_APPROVAL for prototype oracle. Record explicit inputs/policy/expected state for0/0 Unknown;0/1 Weak;1/2 Learning;49/100 Weak;50/100 Learning;74/100 Learning;75/100 Mastered;100/100 Mastered. Alternate policy weak1/3/mastered2/3:1/3 Learning,2/3 Mastered. Reject negative counts, successes>answers, zero/negative threshold denominator, unordered/equal/out-of-range thresholds, nulls. Counts2147483647/2147483647→Mastered exercise overflow boundary. Expected outcomes are self-authored arithmetic, not independently verified. Before accepted Interim results: reviewer other than implementation/oracle author checks arithmetic and boundary table, actual test execution records command/revision/environment/stdout/result, reviewer signs identity/time and reproduces independently. Preserve adverse cases and failures. No claimed implementation run in this contract task.

Rationale: An explicit oracle enables independent reproduction and catches boundary errors; separating authorship/review avoids circular verification.

Alternatives considered: Golden values copied from the calculator are circular; quiz grades are not mastery oracles. Full integrated adaptive evaluation is stronger but belongs to selected Stretch/Final, not a prerequisite for preparing independent local cases.

Owner: Adaptive @thanhtran0311, Leader and scenario reviewers. Consumers: CT36, AD09, QA14, DO03. Checkpoints: ['CT36.I-results', 'AD09 selected tests', 'QA14.I-minimum', 'DO03 advanced-evidence'].

Deferred: Actual independent reviewer, actual executed implementation results, full Final adaptive metrics/datasets/baselines.

### NC-EVIDENCE-MODE — DELEGATED_APPROVED

Minimum evidence uses backend automated unit/PG tests plus an independently performed manual browser protocol for real Auth and catalogue; record browser/OS/revision/API and redacted PG query evidence with timestamps and outcomes. Local mastery requires independently reviewed expected outputs and actual execution. External access is a separate independently verified gate. Playwright remains approved Final E2E framework; this selection is not a waiver of required testing or Final E2E delivery.

Rationale: Executable targeted evidence avoids pretending an empty E2E skeleton is a complete test suite. Independent manual checks supplement, rather than replace, automated backend tests.

Alternatives considered: Mocked UI, CI green alone or localhost health cannot prove the selected integrated flow. Requiring every Final E2E journey blocks a valid bounded Interim; omitting all browser verification leaves the real integration claim unsupported.

Owner: Frontend @heizseung, QA capability owners and Leader. Consumers: CT20, CT14, QA07, QA14, DO03. Checkpoints: ['QA07.selected.plan', 'QA14.I-minimum', 'DO03 verification evidence'].

Deferred: Actual tester/contributions/results; automated frontend/E2E setup and Final strategy remain.

### CT17.I-scope — DELEGATED_APPROVED

Deployment documentation must separate planned topology, prerequisite contract/artifact checks, non-secret config, local build/Compose validation, deployment/access authorization, health/API/browser checks, evidence and recovery. Retain approved one-VM Docker Compose topology and existing service definitions; do not require provisioned MinIO to be a demonstrated file feature. Only real selected Auth/catalogue functions count externally; local mastery evidence remains separately reproducible. No deployment performed or authorized by this slice.

Rationale: Clear capability/evidence distinction prevents infrastructure-only progress being mistaken for a functional prototype.

Alternatives considered: Local Compose or mocks alone fail external prototype access; forcing full integrated advanced flow is unnecessary for initial-result evidence.

Owner: Infrastructure @tihh647-ui with Leader, Auth/Course/Frontend reviewers. Consumers: CT17, CT20, DP01, DP03, DO03. Checkpoints: ['DP01.I-config/I-provision/I-deploy', 'DP03.I-access/I-features'].

Deferred: Provider/VM/budget/credentials/TLS/public exposure/deployment authorization and operational execution.

### CT17.I-access — DELEGATED_APPROVED

Verification methodology only: independently access actual Leader-approved external HTTPS endpoint from outside developer LAN; check existing /actuator/health without details, real browser Auth flow/catalogue/detail, hidden-course404 and real server/PG evidence, English demo content, role protections and absence of leaked secrets. Record URL, revision/build, UTC+ICT timestamps, tester identity, actual role used, browser and redacted outcomes. Health alone is insufficient. Stop on wrong origin/TLS failure, credential/data exposure, incorrect role access, placeholder app or local-only endpoint. No hostname/access credentials/provider selected here.

Rationale: Independent outside-network feature verification directly checks lecturer accessibility without authorizing public exposure or handling real credentials.

Alternatives considered: Direct public DB/storage or HTTP bearer transport expands exposure; a loopback URL or shared screenshot does not establish lecturer access.

Owner: Infrastructure @tihh647-ui; Auth @duyduyit, Frontend @heizseung, Leader. Consumers: CT17, DP01, DP03, DO03. Checkpoints: ['DP01.I-config/I-deploy', 'DP03.I-access', 'DP03.I-features', 'DO03 deployment-evidence'].

Deferred: Actual endpoint, hosting/access/TLS/public data policy and external execution are Leader-reserved.

## Verification and integration gates

These decisions are contracts, not delivered features. Validate OpenAPI and contract consistency in this documentation change; run the relevant approved feature tests in a separately authorized implementation task. Protect all immutable Flyway/reference artifacts. Auth logout/revocation remains a security decision; the selected core Auth flow cannot be declared wholly accepted while its required logout semantics are unresolved. Human-readable instructor identity requires approved module-boundary design before that criterion can be satisfied. Current catalogue DTOs remain bounded and partial against full US-02. The local mastery policy has no authority over Final settings, grading, persistence or routing.

Independently reviewed arithmetic and actual executable prototype results remain mandatory. Self-authored expected values are not independent evidence. External deployment, real access validation, report/contribution records and submission remain unfulfilled external gates. Existing1-hour JWT configuration has not been changed by these docs; approved15-minute policy is a future implementation target. Required code-owner approval, last-push review, Backend/Frontend checks and protected merge remain in force.

## Full source disposition inventory

| Decision | Disposition | Scope |
|---|---|---|
| CT20.I | LEADER_AUTHORIZED_BY_EXPLICIT_INSTRUCTION | Bounded clauses above |
| CT18.minimum | DELEGATED_APPROVED | Bounded clauses above |
| CT01.core | DELEGATED_APPROVED | Bounded clauses above |
| CT16.minimum | DELEGATED_APPROVED | Bounded clauses above |
| CT38.core | DELEGATED_APPROVED | Bounded clauses above |
| CT03.catalogue | DELEGATED_APPROVED | Bounded clauses above |
| CT21.catalogue | DELEGATED_APPROVED | Bounded clauses above |
| CT06.prototype | DELEGATED_APPROVED | Bounded clauses above |
| CT24.calculation | DELEGATED_APPROVED | Bounded clauses above |
| CT01.identity | PROPOSED_LEADER_RESERVED | Material Auth public display-name lookup for Course would create a new cross-module identity contract. Recommend minimal UUID→public displayName application interface, no email/entities, after Auth/Course/Leader review; not required for instructorId-only bounded DTO. |
| CT15.I-data | DELEGATED_APPROVED | Bounded clauses above |
| CT14.interim | DELEGATED_APPROVED | Bounded clauses above |
| CT36.I-results | DELEGATED_APPROVED | Bounded clauses above |
| NC-EVIDENCE-MODE | DELEGATED_APPROVED | Bounded clauses above |
| CT17.I-scope | DELEGATED_APPROVED | Bounded clauses above |
| CT17.I-runtime | PROPOSED_LEADER_RESERVED | Actual provider, VM identity/size/budget, TLS/DNS, secrets, production origins and public/admin access must be provided/authorized before deployment. |
| CT17.I-access | DELEGATED_APPROVED | Bounded clauses above |
| NC-EXTERNAL-RECORDS | BLOCKED_BY_MISSING_EVIDENCE | Actual submission link, Google Doc/report, weekly contribution records, lecturer access instructions and reviewer/tester identities are external inputs. No academic submission or connected-account search authorized. |
| CT01.remaining | DEFERRED | No new technical values approved. Preserve existing approved clauses and the prior family/slice definition. |
| CT02 | DEFERRED | No new technical values approved. Preserve existing approved clauses and the prior family/slice definition. |
| CT03.remaining | DEFERRED | No new technical values approved. Preserve existing approved clauses and the prior family/slice definition. |
| CT04 | DEFERRED | No new technical values approved. Preserve existing approved clauses and the prior family/slice definition. |
| CT05 | DEFERRED | No new technical values approved. Preserve existing approved clauses and the prior family/slice definition. |
| CT06.remaining | DEFERRED | No new technical values approved. Preserve existing approved clauses and the prior family/slice definition. |
| CT07 | DEFERRED | No new technical values approved. Preserve existing approved clauses and the prior family/slice definition. |
| CT08 | DEFERRED | No new technical values approved. Preserve existing approved clauses and the prior family/slice definition. |
| CT09 | DEFERRED | No new technical values approved. Preserve existing approved clauses and the prior family/slice definition. |
| CT10 | DEFERRED | No new technical values approved. Preserve existing approved clauses and the prior family/slice definition. |
| CT11 | DEFERRED | No new technical values approved. Preserve existing approved clauses and the prior family/slice definition. |
| CT12 | DEFERRED | No new technical values approved. Preserve existing approved clauses and the prior family/slice definition. |
| CT13 | DEFERRED | No new technical values approved. Preserve existing approved clauses and the prior family/slice definition. |
| CT14.remaining | DEFERRED | No new technical values approved. Preserve existing approved clauses and the prior family/slice definition. |
| CT15.remaining | DEFERRED | No new technical values approved. Preserve existing approved clauses and the prior family/slice definition. |
| CT16.remaining | DEFERRED | No new technical values approved. Preserve existing approved clauses and the prior family/slice definition. |
| CT17.remaining | DEFERRED | No new technical values approved. Preserve existing approved clauses and the prior family/slice definition. |
| CT18.remaining | DEFERRED | No new technical values approved. Preserve existing approved clauses and the prior family/slice definition. |
| CT19 | DEFERRED | No new technical values approved. Preserve existing approved clauses and the prior family/slice definition. |
| CT20.remaining | DEFERRED | No new technical values approved. Preserve existing approved clauses and the prior family/slice definition. |
| CT21.remaining | DEFERRED | No new technical values approved. Preserve existing approved clauses and the prior family/slice definition. |
| CT22 | DEFERRED | No new technical values approved. Preserve existing approved clauses and the prior family/slice definition. |
| CT23 | DEFERRED | No new technical values approved. Preserve existing approved clauses and the prior family/slice definition. |
| CT24.remaining | DEFERRED | No new technical values approved. Preserve existing approved clauses and the prior family/slice definition. |
| CT25 | DEFERRED | No new technical values approved. Preserve existing approved clauses and the prior family/slice definition. |
| CT26 | DEFERRED | No new technical values approved. Preserve existing approved clauses and the prior family/slice definition. |
| CT27 | DEFERRED | No new technical values approved. Preserve existing approved clauses and the prior family/slice definition. |
| CT28 | DEFERRED | No new technical values approved. Preserve existing approved clauses and the prior family/slice definition. |
| CT29 | DEFERRED | No new technical values approved. Preserve existing approved clauses and the prior family/slice definition. |
| CT30 | DEFERRED | No new technical values approved. Preserve existing approved clauses and the prior family/slice definition. |
| CT31 | DEFERRED | No new technical values approved. Preserve existing approved clauses and the prior family/slice definition. |
| CT32 | DEFERRED | No new technical values approved. Preserve existing approved clauses and the prior family/slice definition. |
| CT33 | DEFERRED | No new technical values approved. Preserve existing approved clauses and the prior family/slice definition. |
| CT34 | DEFERRED | No new technical values approved. Preserve existing approved clauses and the prior family/slice definition. |
| CT35 | DEFERRED | No new technical values approved. Preserve existing approved clauses and the prior family/slice definition. |
| CT36.remaining | DEFERRED | No new technical values approved. Preserve existing approved clauses and the prior family/slice definition. |
| CT37 | DEFERRED | No new technical values approved. Preserve existing approved clauses and the prior family/slice definition. |
| CT38.remaining | DEFERRED | No new technical values approved. Preserve existing approved clauses and the prior family/slice definition. |
| CT39 | DEFERRED | No new technical values approved. Preserve existing approved clauses and the prior family/slice definition. |
| CT40 | DEFERRED | No new technical values approved. Preserve existing approved clauses and the prior family/slice definition. |
