---
name: Bug report
about: Report a defect or unexpected behavior in CodeMastery
title: "[BUG] "
labels: bug
assignees: ''
---

<!--
Use this template for defects and unexpected behavior. Write in English.
Do NOT paste secrets, passwords, tokens, API keys, private keys, real user or lecturer credentials,
or real personal/academic records. Redact them from logs and screenshots.
If a secret was exposed, notify the team immediately so that it can be rotated or revoked.
-->

## Summary

<!-- A clear and concise description of the bug. -->

## Affected Area

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
- [ ] Not sure

## Severity

- [ ] **Critical** – data loss, authorization bypass, secret/credential exposure, deployment unreachable, or trial accounts cannot log in
- [ ] **High** – a main flow of the Learner, Instructor, or Administrator is broken with no workaround
- [ ] **Medium** – a feature is impaired but a workaround exists
- [ ] **Low** – cosmetic issue or minor inconvenience

## Role Used

- [ ] Learner
- [ ] Instructor
- [ ] Administrator
- [ ] Not logged in

## Related User Story / Requirement

<!-- e.g. US-05 (timed quiz auto-submit). Write N/A if unknown. -->

## Steps to Reproduce

1.
2.
3.

## Expected Behavior

<!-- What should happen? Reference the acceptance criterion if applicable. -->

## Actual Behavior

<!-- What actually happens? Include the error message or API status code if available. -->

## Evidence

<!-- Screenshots, screen recordings, API request/response (redacted), and relevant logs. -->

- Correlation ID (from the response or backend log, if available):
- Relevant log excerpt:

```text
(paste redacted logs here)
```

## Environment

- Where observed: <!-- local / CI / deployed VM -->
- Branch / commit / tag:
- Browser and version (frontend issues):
- Operating system:
- Java / Node version (if relevant):
- Data used: <!-- seeded sample data (which file/profile) or custom data -->

## Adaptive Learning Details

<!-- Complete only for mastery, recommendation, or adaptive-decision defects. Otherwise write N/A. -->

- Learner profile: <!-- P1 strong foundation / P2 loops gap / P3 complete beginner / custom -->
- Course and skill(s) involved:
- Quiz / attempt that triggered the behavior:
- Mastery state before → after: <!-- Unknown / Learning / Mastered / Weak -->
- Decision observed: <!-- continue / skip (Test-out) / remedial -->
- Decision expected:
- Recommendation reason shown (if any):
- Scenario file or test reproducing it (if any):

## Possible Cause / Suggested Fix (optional)

<!-- Only if you have an idea. -->

## Additional Context

<!-- Related issues, pull requests, or anything else that helps. -->

## Checklist

- [ ] I searched existing issues and this bug has not already been reported
- [ ] I can reproduce the bug on the latest `main` (or stated the branch/commit above)
- [ ] I removed all secrets and sensitive data from the report
- [ ] A regression test will be added with the fix, or the reason is stated in the pull request