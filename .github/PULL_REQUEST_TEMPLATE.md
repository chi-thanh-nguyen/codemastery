<!--
Follow CONTRIBUTING.md. Complete every applicable section and write "N/A" for sections that do not apply.
Keep this pull request focused on one logical concern and small enough to review effectively.
Never include secrets, credentials, real user data, or generated local files.
-->

## Summary

### What changed
<!-- Describe WHAT was changed. -->

### Why
<!-- Describe WHY the change is needed. -->

## Related Issue / Requirement

<!-- Reference the issue, task, or requirement so the change stays traceable. -->

- Closes #
- User story / acceptance criterion: <!-- e.g. US-06; write N/A if none -->

## Type of Change

<!-- Keep aligned with the branch prefix and the Conventional Commits type. -->

- [ ] `feat` – new functionality (`feature/…`)
- [ ] `fix` – bug fix (`fix/…`)
- [ ] `docs` – documentation only (`docs/…`)
- [ ] `refactor` – behavior-preserving restructuring (`refactor/…`)
- [ ] `test` – adding or improving automated tests (`test/…`)
- [ ] `chore` – build, CI, dependency, tooling, or repository maintenance (`chore/…`)

## Affected Areas

- [ ] Backend – `auth`
- [ ] Backend – `course`
- [ ] Backend – `assessment`
- [ ] Backend – `adaptive`
- [ ] Backend – `interaction`
- [ ] Backend – `admin`
- [ ] Backend – `common` / `infrastructure`
- [ ] Frontend
- [ ] End-to-end tests (`e2e/`)
- [ ] Infrastructure / deployment (`infrastructure/`, Dockerfiles)
- [ ] Sample data (`sample-data/`)
- [ ] Documentation (`docs/`, `README.md`)
- [ ] GitHub configuration (`.github/`)

Cross-module dependency introduced or changed? <!-- Yes / No. If yes, explain how it follows docs/decisions/001-modular-monolith-module-boundaries.md (public application interface or approved event mechanism). -->

## Testing Performed

<!-- List the commands run and/or the scenarios verified. -->

```text
# commands run, e.g. ./mvnw test (backend/), e2e run, etc.
```

- [ ] Unit / business-logic tests added or updated (JUnit 5 + Mockito)
- [ ] Integration tests added or updated (persistence, transactions, module integration, configuration)
- [ ] End-to-end tests added or updated (Playwright, `e2e/`)
- [ ] Deterministic Adaptive Learning scenarios added or updated
- [ ] No automated test required – reason: <!-- e.g. documentation-only change -->

Relevant tests pass locally: <!-- Yes / No – explain -->

## Configuration Changes

- [ ] No configuration changes
- [ ] Environment variables added / modified / removed: <!-- list them -->
- [ ] `.env.example` updated (placeholders only, no real secrets)
- [ ] `README.md` / `docs/setup.md` updated accordingly

## Database Migration Changes

- [ ] No database changes
- [ ] New versioned Flyway migration added in `backend/src/main/resources/db/migration/`: <!-- file name -->
- [ ] No previously applied migration was modified (corrections use a new migration)
- [ ] JPA entities, repositories, mappers, constraints, and migrations are consistent
- [ ] Persistence behavior and relied-upon constraints are tested
- [ ] Seed/demo data kept separate from schema migrations (`sample-data/seed/`)
- [ ] `docs/data-model.md` updated

## API Changes

- [ ] No public REST API changes
- [ ] `docs/api/openapi.yaml` updated
- [ ] Frontend TypeScript API types kept in sync with the OpenAPI specification
- [ ] Request input validated at the API boundary (Bean Validation); errors go through the centralized exception handling

## Security-Sensitive Changes

- [ ] This PR does not touch authentication, authorization, file handling, credentials, or secrets
- [ ] Authentication / password / token handling changed
- [ ] Authorization or role checks changed (enforced on the backend, not only via frontend route guards)
- [ ] File upload / download or object storage handling changed (authorization and ownership checks before accepting or serving files)
- [ ] Credentials or secrets handling changed (read from environment configuration only)

Details and mitigations: <!-- N/A if none -->

## UI Changes

- [ ] No visible UI changes
- [ ] Screenshots or screen recording attached below
- [ ] Responsive behavior checked (desktop and mobile)
- [ ] All user-facing text is in English

<!-- Attach screenshots / recordings here. -->

## Adaptive Learning Impact

- [ ] This PR does not change Adaptive Learning behavior

If it does, confirm all applicable items:

- [ ] Skill prerequisites remain an acyclic graph within each course; cycle-creating relationships are rejected
- [ ] Exactly four mastery states are used (`Unknown`, `Learning`, `Mastered`, `Weak`) with configured thresholds applied consistently across assessment, mastery evaluation, and adaptive decisions
- [ ] Decisions remain rule-based and limited to `continue`, `skip (Test-out)`, and `remedial` (no ML models, external AI services, or separate Python service)
- [ ] Every recommendation is logged with type, target lesson, reason, and timestamp
- [ ] Canonical course order is not rewritten and no lesson is hard-blocked solely by an unmastered prerequisite (a warning is shown instead)
- [ ] Only quiz results drive real-time mastery updates; file-submission assignments do not
- [ ] Assessment → Adaptive communication uses the approved event mechanism, not direct access to Adaptive internals
- [ ] Mastery update and the related `LessonProgress` update succeed or fail together in a single transaction
- [ ] Notifications remain owned by Interaction & Notification; reporting does not own adaptive decision logic
- [ ] Behavior stays within a single course (no cross-course adaptation)
- [ ] Deterministic tests/scenarios added or updated
- [ ] `docs/adaptive-learning.md` updated

## Documentation Updated

- [ ] No documentation changes needed
- [ ] `README.md` – scope, roles, features, or technology stack
- [ ] `docs/setup.md` / `.env.example` – setup, prerequisites, or environment variables
- [ ] `docs/architecture.md` / `docs/decisions/` – module boundaries or architecture
- [ ] `docs/data-model.md` – entities, tables, or relationships
- [ ] `docs/adaptive-learning.md` – skill graph, mastery rules, thresholds, or decisions
- [ ] `docs/testing.md` – testing tools, strategy, or commands
- [ ] `docs/deployment.md` – deployment, Docker configuration, or VM deployment
- [ ] `docs/api/openapi.yaml` – REST endpoints or payloads

## Before Requesting Review

- [ ] Branch name follows `type/short-description` (lowercase, hyphen-separated)
- [ ] Commits follow Conventional Commits (`type(scope): description`) and use my own Git identity
- [ ] Branch is up to date with `main`
- [ ] Relevant tests pass locally
- [ ] Documentation affected by the change is updated in this PR
- [ ] No secrets, credentials, `.env` files, or generated local files are included

## Definition of Done

- [ ] The implementation is complete and satisfies the associated issue, task, or requirement
- [ ] Relevant automated tests have been added or updated
- [ ] Relevant tests pass locally
- [ ] Required CI checks pass
- [ ] API documentation updated where applicable
- [ ] Data-model documentation updated where applicable
- [ ] Other affected documentation updated
- [ ] No secrets, credentials, or sensitive local files are included
- [ ] The change remains within the approved project scope
- [ ] No unapproved architectural changes have been introduced (no microservices, no ML/external AI services)
- [ ] The pull request is focused and reviewable
- [ ] Required review feedback has been addressed in this pull request
- [ ] The change is ready to be merged into `main`

## Notes for Reviewers

<!-- Optional: areas needing extra attention, trade-offs, follow-up work, known limitations. -->