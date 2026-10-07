---
name: Feature / Task
about: Propose new functionality, an enhancement, or planned work for CodeMastery
title: "[FEATURE] "
labels: enhancement
assignees: ''
---

<!--
Use this template for new functionality, enhancements, and planned work.
Reference the corresponding user story, acceptance criterion, or requirement when one exists.
Write "N/A" for sections that do not apply. Write in English.

User story reference (from the approved proposal):
- Learner:       US-01 … US-09
- Instructor:    US-10 … US-14
- Administrator: US-15 … US-17
- Adaptive Learning (Direction 3): US-03, US-06, US-07, US-11, US-14
-->

## Summary

<!-- One or two sentences describing the feature or task. -->

## Problem / Motivation

<!-- What need does this address, and for which user? -->

## Related User Story / Requirement

- User story: <!-- e.g. US-06 -->
- Acceptance criterion / requirement: <!-- quote or summarize the criterion this issue implements or modifies; write N/A if none -->

## Affected Role(s)

- [ ] Learner
- [ ] Instructor
- [ ] Administrator

## Affected Area(s)

- [ ] Backend – `auth`
- [ ] Backend – `course`
- [ ] Backend – `assessment`
- [ ] Backend – `adaptive`
- [ ] Backend – `interaction`
- [ ] Backend – `admin`
- [ ] Frontend
- [ ] End-to-end tests (`e2e/`)
- [ ] Infrastructure / CI / deployment
- [ ] Sample data / seed data
- [ ] Documentation

## Proposed Behavior

<!-- Describe what the system should do. Include main flow and relevant edge cases. -->

## Acceptance Criteria

<!-- Verifiable criteria, preferably Given / When / Then. -->

- [ ] Given …, when …, then …
- [ ] Given …, when …, then …

## Out of Scope

<!-- State what this issue does NOT include. -->

Confirm this issue does not require any project out-of-scope item:

- [ ] Not a code-execution sandbox, ML/AI chatbot, certificate, payment, video hosting/transcoding, email/SMS/push, real-time chat, native mobile app, cross-course adaptive behavior, or plagiarism detection

## Technical Considerations

- [ ] Public REST API changes → update `docs/api/openapi.yaml` and keep frontend types in sync
- [ ] Database schema changes → new versioned Flyway migration (never modify an applied one) and update `docs/data-model.md`
- [ ] New or changed environment variables → update `.env.example`, `README.md`, and `docs/setup.md`
- [ ] Cross-module dependency → consult `docs/decisions/001-modular-monolith-module-boundaries.md` (use the public application interface or the approved event mechanism)
- [ ] File upload / download → authorization and ownership checks; access through the storage abstraction
- [ ] Role-protected API or route → backend authorization enforced (frontend route guard is not a security boundary)
- [ ] Architecture change → requires prior team agreement and an update to `docs/architecture.md` / `docs/decisions/`

Notes: <!-- implementation hints, constraints, risks; write N/A if none -->

## Adaptive Learning Impact

- [ ] No impact on Adaptive Learning

If there is an impact, check what applies:

- [ ] Skill graph / prerequisites (must remain acyclic within each course)
- [ ] Mastery states (`Unknown`, `Learning`, `Mastered`, `Weak`) or configured thresholds
- [ ] Adaptive decisions (`continue`, `skip (Test-out)`, `remedial`) – rule-based only
- [ ] Recommendations (must log type, target lesson, reason, timestamp)
- [ ] Placement diagnostic / practice difficulty selection
- [ ] Event flow (`QuizSubmittedEvent`) or transactional consistency with `LessonProgress`
- [ ] Canonical course order and the no-hard-block rule (warning instead of blocking)

## Testing Plan

- [ ] Unit / business-logic tests (JUnit Jupiter 6 + Mockito; JUnit version managed by Spring Boot)
- [ ] Integration tests (persistence, transactions, module integration, configuration)
- [ ] End-to-end test update (Playwright)
- [ ] Deterministic Adaptive Learning scenarios (e.g. cycle rejection, mastery transitions, Test-out, Remedial routing, recommendation reasons, prerequisite warnings, no-hard-block)
- [ ] No automated test required – reason: <!-- e.g. documentation-only -->

## Documentation to Update

- [ ] `README.md`
- [ ] `docs/setup.md`
- [ ] `docs/architecture.md` / `docs/decisions/`
- [ ] `docs/data-model.md`
- [ ] `docs/adaptive-learning.md`
- [ ] `docs/testing.md`
- [ ] `docs/deployment.md`
- [ ] `docs/api/openapi.yaml`
- [ ] None

## Dependencies

<!-- Related or blocking issues / pull requests. Write N/A if none. -->

- Depends on #
- Blocks #

## Suggested Branch

<!-- type/short-description, lowercase and hyphen-separated, e.g. feature/course-enrolment -->

`feature/`

## Definition of Done

- [ ] Implementation satisfies the acceptance criteria above
- [ ] Relevant automated tests added or updated and passing
- [ ] Required CI checks pass
- [ ] API, data-model, and other affected documentation updated
- [ ] No secrets, credentials, or sensitive local files included
- [ ] Change stays within the approved project scope and architecture
- [ ] Pull request is focused, reviewed, and ready to merge into `main`