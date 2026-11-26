# Security policy

CodeMastery is a university-developed web application. This policy covers reports
about its code, dependencies and repository tooling.

## Supported versions

The project is under active development and has no maintained stable release
line or long-term support commitment. Reports should identify the affected
commit or preview version and, where practical, whether the issue also affects
current `main`. Fixes are developed on `main`; older previews and draft release
candidates have no guaranteed backport support.

Maintenance is best effort within the student project. There is no guaranteed
response or fix timeline and no bug bounty offered by this repository.

## Report a vulnerability privately

Use [GitHub private vulnerability reporting](https://github.com/chi-thanh-nguyen/codemastery/security/advisories/new)
(sign-in required), or open the repository's **Security → Advisories** tab and
select **Report a vulnerability**. Private reporting is enabled for this repository.

Include what you can:

- Affected commit, version, component and relevant configuration.
- Reproduction steps or a minimal proof of concept using synthetic data.
- Expected and observed behavior, impact and required attacker privileges.
- Redacted logs or source locations that help reproduce the issue.
- Any mitigation you have identified and your preferred attribution for disclosure.

Do not include real passwords, tokens, private keys or personal/academic records.
Report exposed credentials by their location and type without copying the secret.
Do not publish vulnerability details or credentials in Issues, pull requests,
discussions, logs or screenshots.

## Coordinated disclosure

Keep reproduction details and discussion in the private report while maintainers
assess the issue and prepare a fix or mitigation. Coordinate public disclosure
and any reporter credit in that thread so affected users can act on accurate
information. Do not test against a running deployment without its operator's
authorization or access other users' data to demonstrate impact.

## Scope and operational limits

Repository tests, CodeQL, scans and artifact verification provide evidence for
their specific scope and revision. They do not guarantee that a deployed instance
is secure, up to date, available or recoverable. Instance configuration and
credential rotation remain the operator's responsibility.

Vulnerability reporting does not grant release security admission, residual-risk
acceptance or production deployment approval. Those decisions follow the separate
[release and deployment procedures](docs/deployment.md#phase-4-release-pipeline--23-november-2026).
