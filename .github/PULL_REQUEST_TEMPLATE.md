<!--
Follow CONTRIBUTING.md.

Keep this pull request focused on one logical change.
Complete applicable free-text fields and use "N/A" where appropriate.
Leave non-applicable checkboxes unchecked.

Do not include secrets, credentials, real user data, local environment files,
build outputs, or other local-only artifacts.
The tracked `.env.example` may be updated when configuration changes.
-->

## Summary

### What changed
<!-- Briefly describe what changed. -->

### Why
<!-- Explain why this change is needed. -->

## Related Issue / Requirement

- Issue: N/A <!-- Example: Closes #12 -->
- Requirement / user story: N/A

## Type of Change

<!-- Select one primary type. -->

- [ ] `feat` – new functionality
- [ ] `fix` – bug fix
- [ ] `docs` – documentation only
- [ ] `refactor` – behavior-preserving restructuring
- [ ] `test` – tests only
- [ ] `chore` – build, CI, dependencies, tooling, or repository maintenance

## Affected Areas

<!-- Select all that apply. -->

- [ ] Backend – `auth`
- [ ] Backend – `course`
- [ ] Backend – `assessment`
- [ ] Backend – `adaptive`
- [ ] Backend – `interaction`
- [ ] Backend – `admin`
- [ ] Backend – `common` / `infrastructure`
- [ ] Frontend
- [ ] Database / Flyway
- [ ] End-to-end tests (`e2e/`)
- [ ] Infrastructure / Docker (`infrastructure/`, Dockerfiles)
- [ ] Sample data (`sample-data/`)
- [ ] Documentation
- [ ] GitHub / CI (`.github/`)

### Cross-module Impact

<!-- See docs/decisions/001-modular-monolith-module-boundaries.md -->

- [ ] No cross-module interaction changed
- [ ] Cross-module interaction changed and follows the approved module-boundary contract

Details: N/A

## Validation

<!-- List the commands and/or scenarios actually verified. -->

```text
# Examples:
# cd backend && ./mvnw verify
# cd frontend && npm run build
# docker compose --env-file .env -f infrastructure/docker-compose.yml config --quiet
```

- [ ] Relevant automated tests added or updated where applicable
- [ ] Relevant tests / validation pass locally
- [ ] Integration behavior verified where applicable
- [ ] No automated test required

Reason if no automated test is required: N/A

## Change Impact

### Database

- [ ] No database changes
- [ ] Schema change uses a new versioned Flyway migration
- [ ] No previously applied Flyway migration was modified
- [ ] `docs/data-model.md` updated where required

Migration: N/A

### API

- [ ] No public REST API changes
- [ ] `docs/api/openapi.yaml` updated for changed endpoints or payloads
- [ ] Request validation / error handling updated where applicable
- [ ] Frontend API contract kept in sync where applicable

### Configuration

- [ ] No configuration changes
- [ ] `.env.example` updated where required
- [ ] Setup documentation updated where required

Environment variables changed: N/A

### Security

- [ ] No security-sensitive changes
- [ ] Authentication / password / token handling changed
- [ ] Authorization / role checks changed
- [ ] File upload / download or object-storage handling changed
- [ ] Secret / credential handling changed

Security notes: N/A

### Adaptive Learning

- [ ] No Adaptive Learning behavior changed
- [ ] Changes remain consistent with `docs/adaptive-learning.md`
- [ ] Relevant deterministic tests / scenarios updated where applicable
- [ ] Adaptive documentation updated where required

Adaptive notes: N/A

### UI

- [ ] No visible UI changes
- [ ] Responsive behavior checked where applicable
- [ ] If UI changed, all user-facing text is in English
- [ ] Screenshots / recording attached where useful

## Review Checklist

- [ ] Branch name follows the repository convention
- [ ] Commits follow Conventional Commits
- [ ] No unresolved conflicts with `main`
- [ ] Documentation is updated where required
- [ ] No secrets, credentials, local environment files, build outputs, or unintended local artifacts are included
- [ ] No unapproved architectural changes were introduced
- [ ] The PR is focused and ready for review

## Notes for Reviewers

<!-- Optional: risks, trade-offs, known limitations, or areas needing attention. -->

N/A