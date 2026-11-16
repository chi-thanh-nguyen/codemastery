# ADR014: Hybrid Authentication — Memory-Only Access JWT with Rotating Refresh Sessions

## 1. Authority, provenance and scope

**DELEGATED_TECHNICAL_APPROVAL; DELEGATED_APPROVED**, 16 November 2026
(Asia/Ho_Chi_Minh). On that date the Project Leader and repository Owner
@chi-thanh-nguyen gave the explicit instruction **“CodeMastery — End-to-End Hybrid
Authentication Upgrade with Delegated Technical Approval”**. It delegates to Claude
Code (Anthropic) the authority to select and technically approve the design of a
memory-only access JWT combined with a rotating opaque refresh token in a Secure,
HttpOnly cookie and PostgreSQL-backed refresh sessions; to amend or supersede the
existing technical contracts that conflict with it; to record those decisions in the
same implementation PR; and to synchronize the affected Issues and Project fields.

Claude Code made every selection below **independently under that delegation**. The
Leader did not review or approve any individual selection personally, and no
residual risk in §12 is accepted by the Leader. The delegation excludes, and this
record does not claim: CODEOWNER or other independent review, a GitHub approving
review, CI or acceptance evidence beyond what §9 records as executed, merge or
integration into `main`, deployment, execution of migrations or grants on any shared
or production database, spending, credential or secret changes, and Leader
acceptance of residual risk.

The revisions below are approved and acted upon **in the implementation branch**
`feature/auth-hybrid-refresh-sessions`, [PR #246](https://github.com/chi-thanh-nguyen/codemastery/pull/246). They become effective on protected `main`
only through compliant integration of that PR; until then the earlier contracts
remain the effective `main` contracts. Technical approval is not implementation
completion, independent review, integration verification or deployment readiness;
§13 keeps those gates separate.

Known AI use: Claude Code (Anthropic) analysed the repository, selected the design,
wrote the implementation, tests and this record. No AI service is added to the
application.

## 2. Baseline and affected contracts

Starting `main`: `7aafe96621755fa5d6aa5a9f7debfc0a4e60143e` (verified 16 November
2026; working tree clean; open PRs #240/#241 change the E2E lifecycle and CI only and
are not modified here).

Implementation verified before the change: HS256 access JWT with a fixed 900-second
lifetime held only in React memory; `Authorization: Bearer` on protected APIs; current
account status and role read on every protected request; no refresh token,
refresh endpoint, logout endpoint or server session; `httpClient.ts` used
`credentials: 'omit'`; Spring Security ignored CSRF for `/api/**`; the production
ingress used `proxy_pass_request_headers off` with an allowlist excluding `Cookie`;
E2E asserted no cookies and loss of the session on reload; V007 stored password-reset
capabilities only; production is single-origin HTTPS while the E2E stack used two
loopback ports.

| Contract (revision, authority) | Effect of this record |
|---|---|
| CT01.core (ADR002, 11 Oct 2026, DELEGATED_APPROVED; reaffirmed in CT01-2026-10-21-r1) | Bounded amendment: one additional required claim `sid` (§3). Algorithm, key, issuer, audience, subject, `iat`/`exp`, PT15M, zero skew, DTOs, BCrypt, generic failures and current-account checks unchanged. |
| CT01-2026-10-21-r1 (ADR001 §§11.2–11.7, LEADER_APPROVED) | Logout/session, client-coordination, “no refresh/no cookie/no logout endpoint” and the related §11.5/§11.6 clauses superseded as listed in §11. Identity facade (§11.4), authorization order and error contract unchanged. |
| CT16.minimum (ADR002) and CT16-2026-10-24-r1 / CT16-FINAL r1 (setup) | The access token stays memory-only. “Reload requires login”, “no default credentials cookie” and “no silent renewal” superseded by CT16-SESSION (§7). |
| CT38.core (ADR002) | No new variable. `CORS_ALLOWED_ORIGINS` additionally lists the trusted origins of the cookie operations and selects the cookie mode (§5). |
| CT18.minimum / ADR001 §11.5 CT18 paragraph | Representation unchanged. 403 `Access is denied.` added for untrusted cookie-operation origins; 204 for logout. |
| CT14 (testing record) | Session expectations replaced by CT14-SESSION (§9). |
| CT17 P6 r1 + r2 (deployment record) | Extended by CT17-P6-PRIVILEGES-2026-11-16-r3 (two new relations). |
| CT17 P7 ingress allowlist (deployment record) | Bounded amendment CT17-INGRESS-COOKIE-2026-11-16-r1 (three exact locations). |
| AU07 / ADR006 §9.4 residual (earlier bearer valid after reset) | Superseded: reset revokes all refresh sessions and their access JWTs (§6). |
| CT12.user (ADR012 §10.1, “Effects: no cascade”) | Binding addition for the future implementation: locking an account revokes its refresh sessions in the same transaction (§6). Not implemented here; CT12.user is unimplemented. |
| Approved architecture, repository structure | Unchanged architecture (Auth module of the modular monolith). Three file additions recorded as STRUCT-AUTH-SESSION-2026-11-16-r1 (§10). |

## 3. `AUTH-SESSION-2026-11-16-r1` — access JWT

| Decision | Selection and rationale | Alternatives rejected |
|---|---|---|
| Transport and storage | Unchanged: memory-only in AuthProvider, `Authorization: Bearer`, never in cookies, storage, URLs, history or logs. Protected business endpoints never accept cookie authentication. | Access JWT in a cookie (ambient credential on every endpoint, CSRF surface); storage (persistent XSS exposure). |
| Profile | Unchanged HS256/key/`iss`/`aud`/UUID `sub`/`iat`/`exp`/PT15M/zero skew/`typ: JWT`, plus a **required `sid` claim**: the canonical lower-case UUID string of the refresh session that issued the token. Missing, mistyped or non-canonical `sid` is the generic 401. | A token-version column on `users` (revokes all devices together, needs a `users` UPDATE grant); a denylist (state that can be lost or must be replicated). |
| Per-request check | The filter now loads the account through one join: the user must exist and be active **and** the bound session must be unrevoked and unexpired. A failed read is the existing safe 500. | Keep stateless access tokens (logout would leave a copied token valid up to 15 minutes). |
| Consequence | Logout, replay detection, password reset and (future) account lock end the session's access JWTs **immediately**, not after up to 15 minutes. This is a stronger revocation mechanism, explicitly approved here; it costs one indexed join per protected request that already performed a primary-key read. | — |

## 4. Refresh tokens, sessions and schema (V008)

**Token.** 32 bytes from `SecureRandom` (256 bits), carried only in the cookie as
43-character unpadded base64url; only the canonical form is accepted. PostgreSQL stores
the SHA-256 digest (`bytea`, exactly 32 bytes). A digest of a 256-bit random value
needs no salt or slow hash; lookups are exact and indexed. The plaintext never
reaches logs, exceptions, metrics or response bodies; value types redact `toString`.

**Lifetimes (contract constants, not configuration).** Absolute session lifetime
**7 days** from sign-in; each refresh token is valid for **24 hours** of idleness,
capped by the session end; every rotation restarts the idle window. A device that is
not used for 24 hours, or any session older than 7 days, must sign in again.
Rationale: a persistent sign-in for a course platform used on personal and shared
lab computers; the absolute cap bounds a stolen cookie, and the idle cap bounds an
abandoned shared computer. Longer windows were rejected for shared-device exposure;
shorter idle windows for repeated sign-ins between study days.

**Schema `V008__create_refresh_sessions.sql`** (new migration after the verified
V001–V007 inventory; no earlier migration changed; no grants in migrations):

| Table | Columns and constraints |
|---|---|
| `refresh_sessions` | `id` uuid PK; `user_id` uuid NOT NULL FK `users` (NO ACTION, not deferrable); `created_at`, `expires_at` timestamptz NOT NULL with `expires_at > created_at`; `revoked_at` timestamptz NULL with `revoked_at >= created_at`; indexes on `user_id` and `expires_at`. |
| `refresh_tokens` | `id` uuid PK; `session_id` uuid NOT NULL FK `refresh_sessions` (NO ACTION, not deferrable); `token_digest` bytea NOT NULL UNIQUE, exactly 32 bytes; `issued_at`, `expires_at` timestamptz NOT NULL with `expires_at > issued_at`; `consumed_at` NULL with `consumed_at >= issued_at`; index on `session_id`; **partial unique index on `session_id` WHERE `consumed_at IS NULL`** (at most one current token per session, so a family can never fork). |

The application supplies all UUIDs and timestamps (microsecond precision). Rotated
tokens keep their rows for replay detection until their session is purged after
expiry.

**Rotation (atomic, READ COMMITTED).** Resolve the digest without a lock (an unknown
digest costs one indexed read), then lock the session row `FOR UPDATE`, re-read the
token under that lock (every token write of a session holds it) and decide:

1. revoked or expired session, expired token, token issued in the future, or digest
   mismatch → 401;
2. token already consumed **less than 30 seconds ago** → 401 **without** revocation:
   a concurrent refresh by the same browser already holds the successor;
3. token consumed 30 seconds ago or earlier → **replay**: revoke the whole session
   (and with it every access JWT bound to it), commit, then 401;
4. missing or locked account → revoke the session, 401;
5. otherwise mark the token consumed, insert the successor with
   `min(now + 24 h, session end)`, issue a new access JWT with the same `sid`.

Revocations commit before the failure is reported. Concurrent presentations of one
token therefore produce exactly one rotation (verified with six parallel requests
against PostgreSQL). Rejected alternatives: returning the successor to a racing
request (requires storing recoverable token material); issuing sibling tokens inside
the grace window (forks the family and weakens replay detection); no grace window
(legitimate cross-tab races would log users out and look like theft).

**Sign-in.** One transaction inserts the session and its first token (the foreign-key
check takes the User key-share lock first, the same User-before-session order as
password reset and CT12), revokes the session of a refresh cookie the browser still
presented (login replacement), then keeps at most **10 active sessions per account**,
revoking the oldest. Afterwards, best effort and never failing the sign-in, up to 100
globally expired sessions are purged with their tokens. A scheduled purge is not
introduced (scheduling stays disabled by default); expired rows of accounts that never
sign in again remain until another sign-in purges them (§12).

## 5. Cookie transport, CSRF, Origin and CORS

| Attribute | Selection |
|---|---|
| Name | `__Host-cm_refresh` whenever any configured origin is HTTPS (production). The `__Host-` prefix forces Secure, `Path=/` and no `Domain`, so no sibling host can set, toss or read it. |
| Attributes | `Secure; HttpOnly; SameSite=Strict; Path=/`; no `Domain`; `Max-Age` = the token's remaining idle lifetime. Deletion: same name/path/attributes with `Max-Age=0`. |
| Loopback development mode | Only when **every** `CORS_ALLOWED_ORIGINS` entry is `http://localhost`, `http://127.0.0.1` or `http://[::1]` (any port) is the cookie `cm_refresh` without `Secure`, because WebKit drops Secure cookies on plain-HTTP loopback (observed on 16 November 2026 with the pinned Playwright WebKit; Chromium kept them). This is CT01 §11.5's existing loopback exception; HTTPS deployments cannot select it, and `deploy.sh` already requires `CORS_ALLOWED_ORIGINS` to equal the HTTPS origin. |
| Scope | Read only by `POST /api/auth/login`, `/refresh` and `/logout`. The frontend sends credentials only for these three; the production ingress forwards `Cookie` only to these three exact paths (CT17-INGRESS-COOKIE). Ambiguous duplicate cookies fail closed. |
| Path choice | `Path=/` is required by `__Host-`. Narrower `Path=/api/auth` with `__Secure-` was rejected: it permits cookie tossing from sibling subdomains (session fixation), while exposure to other paths is already removed by credential-omitting fetches and the ingress allowlist. |

**CSRF protection (all three cookie operations).** A dedicated
`RefreshCookieSecurity.TrustedOriginFilter` in the Spring Security chain rejects with
the CT18 403 representation when: `Sec-Fetch-Site` is present and is not
`same-origin`/`same-site`; `Sec-Fetch-Mode` is present and is not `cors`/`same-origin`
(blocks form posts and navigations); more than one `Origin` header is sent; or the
`Origin` is not exactly one of `CORS_ALLOWED_ORIGINS`. Refresh and logout **require** a
trusted Origin (they authenticate with the ambient cookie). Login requires a trusted
Origin **when one is sent**: it authenticates with body credentials, browsers always
send Origin on cross-origin requests, and absent Origin identifies non-browser clients
— this blocks login CSRF without breaking API clients. Spring's synchronizer-token
CSRF stays unused: bearer endpoints carry no ambient credential, and a token would
require a readable cookie or an extra round trip without adding protection beyond
exact Origin checks. SameSite=Strict and CORS are additional layers, never the
decision.

**CORS.** The three cookie paths have their own configuration (registered first):
the same explicit origins, `POST`/`OPTIONS`, request header `Content-Type` only, and
`Access-Control-Allow-Credentials: true`. Every other path keeps the earlier
configuration without credentials. Production is same-origin; credentials matter only
for the same-site loopback development frontend on another port.

**REST contracts** (documented in `docs/api/openapi.yaml`):

- `POST /api/auth/login` — unchanged JSON request/response; adds `Set-Cookie`.
- `POST /api/auth/refresh` — no body; cookie only; 200 with the login representation
  and a rotated `Set-Cookie`; 401 (generic, `WWW-Authenticate: Bearer`, no
  `Set-Cookie`) for every refused refresh; 403 for an untrusted origin.
- `POST /api/auth/logout` — no body; revokes the presented session when known; always
  204 with the deleting `Set-Cookie` (idempotent, also without a cookie); 403 for an
  untrusted origin.

All responses use `Cache-Control: no-store`. A refused refresh never sets or deletes
the cookie: a late failure must not erase a newer cookie written by a concurrent
success or sign-in.

## 6. Lifecycle interactions, errors and abuse controls

| Event | Behavior |
|---|---|
| Logout | Revokes the presented session; its access JWTs fail at once; the cookie is deleted. Other sessions (devices) stay valid; there is no “sign out everywhere” endpoint. |
| Password reset (AU07) | The confirmation transaction, after updating the hash and consuming the capability, revokes every session of the account. Supersedes ADR006 §9.4's residual: a bearer issued before the reset is now rejected. |
| Account lock (CT12.user, future) | Refresh and every protected request already fail for a locked account, and a refresh attempt revokes that session. **Binding on the CT12.user implementation:** a change to `locked` must also call `UserRepository.revokeRefreshSessionsOfUser` in the same transaction, so unlocking cannot revive older sessions. Role changes need no revocation (the role is read per request and per refresh). |
| Unlock | With the binding above, an unlocked user signs in again. Until CT12.user is implemented, no runtime path locks accounts. |
| Errors | Refused refresh: generic 401. Untrusted origin: 403 `Access is denied.`. Storage failure: safe 500 (fail closed); no failure path issues tokens or cookies. |
| Abuse controls | Unknown tokens cost one indexed read and take no lock; sign-in remains bounded by BCrypt cost 14; at most 10 active sessions per account; no request rate limiter is introduced (CT01 §11.5's production abuse-control gate remains open, §12). Counters: `auth.refresh.replay_detected`, `auth.refresh.rejected`, `auth.refresh.maintenance.failures` (no identifiers). |

## 7. `CT16-SESSION-2026-11-16-r1` — frontend session coordination

`AuthProvider` (existing file) gains the `refreshSessions` lifecycle, enabled by `App`:

- **Bootstrap.** On load it restores from the cookie (`POST /api/auth/refresh` with
  credentials). While restoring, `restoring` is true: protected routes show “Restoring
  your session…” instead of redirecting, the landing page waits, and header sign-in
  actions are hidden. A document opened at `/preview` does not restore, keeping the
  design preview network-free (CT14).
- **Renewal.** 60 seconds before access-token expiry (never sooner than 30 seconds
  after the previous schedule, so a fast browser clock cannot loop). Same user and same
  `sid` (decoded only for this comparison) is an **in-place** rotation: generation,
  mounted pages and their requests stay current. A different user or session is a
  replacement with a new generation. A refusal (401/403/unusable response) ends the
  session with the existing session-ended notice; network or 5xx failures retry every
  10 seconds until expiry. Waking after expiry clears and restores once, rate-limited.
- **Single flight and cross-tab coordination.** One refresh per document at a time
  (also under React StrictMode). Sign-in, refresh and sign-out of all tabs run under
  one Web Lock (`codemastery-auth-session`), so each tab sends the newest rotated
  cookie and a late response cannot overwrite a newer cookie; without Web Locks only
  the document is serialized and the server's 30-second grace absorbs races.
  A `BroadcastChannel` (`codemastery-auth`) tells other tabs `signed-out` (clear
  without a request) and `signed-in` (adopt the cookie session).
- **Logout.** Clears the browser state immediately (also offline), broadcasts, then
  calls logout with `keepalive`. Until the server confirms, a non-secret marker
  (`codemastery.auth.pendingSignOut`) makes the next load retry logout instead of
  restoring; a successful sign-in removes it. No token is ever stored.
- **Unchanged.** Generation/attempt discipline, stale-response suppression, 401 on the
  current session clears it, 403 preserves it, network/5xx are not authentication
  failures, **no automatic replay of any mutation** (no idempotency contract exists),
  routes, English copy and accessibility.
- **Transport.** `httpClient.ts` sends `credentials: 'include'` only for public `POST`
  to `/auth/login`, `/auth/refresh` and `/auth/logout` (logout also `keepalive`);
  everything else keeps `credentials: 'omit'`, and callers still cannot set credential
  headers. A 204 is accepted only where expected and its body is never read.

## 8. Ingress and database privileges

**`CT17-INGRESS-COOKIE-2026-11-16-r1`.** The release image's HTTPS ingress keeps
`proxy_pass_request_headers off` and its allowlist. Three exact locations,
`= /api/auth/login`, `= /api/auth/refresh` and `= /api/auth/logout`, repeat the full
allowlist (a location with its own `proxy_set_header` inherits none) and add `Cookie`,
`Sec-Fetch-Site` and `Sec-Fetch-Mode`. They keep the `;`/`%3B` path-parameter
rejection. Every other `/api` path, including trailing-slash and prefix variants,
still drops these headers. `Set-Cookie` passes through. Single-origin HTTPS, Host/SNI
handling and the forwarding-header policy are unchanged. Details:
[deployment record](../deployment.md#ct17-ingress-cookie-2026-11-16-r1--refresh-cookie-transport-delegated_approved-16-november-2026).

**`CT17-P6-PRIVILEGES-2026-11-16-r3`.** Adds `refresh_sessions` (SELECT, INSERT,
DELETE; UPDATE (revoked_at)) and `refresh_tokens` (SELECT, INSERT, DELETE; UPDATE
(consumed_at)) to the r1+r2 matrix. The column UPDATE also supplies the row-lock
privilege. r1 and r2 rows are unchanged. Details and synchronized tests:
[deployment record](../deployment.md#ct17-p6-privileges-2026-11-16-r3--adr014-refresh-sessions-delegated_approved-16-november-2026).

## 9. `CT14-SESSION-2026-11-16-r1` — verification obligations

Required automated evidence (all at existing or §10-approved locations):

- **Backend (JUnit/Mockito, PostgreSQL via Testcontainers):** cookie issuance and
  attributes, digest-only storage, session-bound access JWT; rotation; grace-window
  reuse without revocation; replay revoking the family and its access JWTs; six
  concurrent refreshes producing exactly one rotation; logout; malformed, unknown,
  duplicate, expired-token and expired-session cookies; locked account; role change;
  login replacement and the 10-session cap; untrusted Origin, `null` Origin,
  cross-site Fetch Metadata and navigation mode; credentialed CORS only on cookie
  paths; password reset revoking sessions; `sid` claim validation; cookie-mode
  selection; existing JWT and authorization regressions.
- **Frontend (Vitest):** restore (also StrictMode, single request), refused restore,
  preview without restore, in-place renewal versus replacement, refused and transient
  renewal, late renewal after logout, logout with pending-sign-out retry, sign-in
  during restore, cross-tab messages, Web Lock usage, wake-up restore, no mutation
  replay, credential scoping and 204 handling, protected-route waiting.
- **Ingress:** stub-backend scoping of Cookie/Fetch Metadata to the three paths and
  `Set-Cookie` pass-through; real backend through HTTPS ingress (issue, rotate, CSRF
  rejections, logout revocation).
- **Database privileges:** containment probes for the r3 grants and denials; the real
  backend's sign-in, rotation, logout and reset revocation under the runtime role.
- **E2E (Playwright, real browser → API → PostgreSQL):** HttpOnly/SameSite cookie, no
  token in storage, reload restoration, second-tab adoption and cross-tab sign-out,
  renewal before expiry, cross-site refresh/logout refused, session-ended notice on a
  refused refresh, sign-out deleting the cookie.

The executed commands and results are recorded in
[testing.md](../testing.md#adr014-hybrid-authentication-verification--16-november-2026).
Self-authored tests are implementation evidence, not QA01/QA07 independent
verification.

## 10. `STRUCT-AUTH-SESSION-2026-11-16-r1` — file additions

Within existing Auth directories and the migration directory, covered by existing
CODEOWNERS rules:

- `backend/src/main/java/com/codemastery/modules/auth/application/service/RefreshSessionService.java`
- `backend/src/main/java/com/codemastery/modules/auth/infrastructure/security/RefreshCookieSecurity.java`
- `backend/src/main/resources/db/migration/V008__create_refresh_sessions.sql`

Persistence methods were added to the existing `UserRepository`/`UserRepositoryImpl`
(the AU07 precedent); no new module, layer, directory, dependency, service or
environment variable. Tests were added to existing test files only.

## 11. Supersession register

Each entry supersedes only the quoted scope; the historical text stays in place.

| Superseded clause | Replacement |
|---|---|
| ADR002 CT01.core: claims list without `sid`; “Deferred: Server logout/revocation … no logout endpoint … refresh/persistent browser sessions … remain unapproved” | §3 (`sid` added), §§4–6 (logout endpoint, refresh, persistent sessions approved). Rest of CT01.core unchanged. |
| ADR002 CT16.minimum: “reload requires login”, “no … default credentials cookie” | §7: reload restores; credentials only for the three cookie operations. |
| ADR001 §11.2 rows “Logout/revocation” and “Client coordination” | §§3–7. |
| ADR001 §11.3 paragraph “There is no refresh token, sliding renewal, persistent login, idle-timeout claim, server session or logout endpoint …” through “… no cross-tab/global logout guarantee is made.” | §§4–7. “Already-admitted server requests can finish after logout” remains true. |
| ADR001 §11.3 sentence “a later login does not revoke earlier credentials” | A sign-in revokes the session of a refresh cookie the browser presented; other sessions remain independent (max 10). |
| ADR001 §11.3 residual-risk paragraph (copied JWT valid after client logout) | Historical Leader acceptance preserved as history. For session-bound tokens the risk is removed by §3; no new acceptance is claimed. |
| ADR001 §11.5 CT16 handoff “No retry of mutations or silent renewal” | Silent renewal approved (§7); no mutation retry retained. |
| ADR001 §11.5 CT18 paragraph “Cookie/session authentication is not selected. CORS must permit only … Authorization/Content-Type headers …” and Security limits “No … cookie policy is selected here” | §5: cookie authentication only for the three Auth session operations with the Origin guard and a separate credentialed CORS configuration. |
| ADR001 §11.6 example “Logout / copied token …” | §9 obligations. |
| ADR006 §9.4 / AU07 residual: earlier bearer valid after reset | §6. |
| setup.md CT16 records (r1, FINAL, PROFILE, CT16.auth) clauses stating memory-only reload loss, no cookie/refresh flow, `credentials: 'omit'` on all fetches, offline-only logout | §7 (pointers added at those sections). |
| deployment.md CT17 records: “no … cookie-based session inference” (CORS row), “No cookie/refresh …”, P7 allowlist without Cookie | §§5, 8. |
| testing.md expectations: memory-only storage, no cookies, reload loses the session | §9. |

## 12. Residual risks, rollback and remaining gates

Residual risks (**not accepted by the Leader**; recorded for review):

1. **Active XSS** can still use the in-memory token and call refresh while the page
   is open (HttpOnly prevents reading the refresh token, not using it). Safe rendering
   (CT37) and HTTPS remain the mitigations.
2. **Persistent sign-in.** A stolen refresh cookie (malware, unlocked device) is usable
   for up to 24 idle hours / 7 days unless the user signs out, resets the password or
   rotation exposes it as a replay. No “sign out everywhere” endpoint exists.
3. **Grace window.** A thief who presents a stolen, already-rotated token within 30
   seconds is refused without triggering family revocation (it gains nothing, but the
   detection signal is lost).
4. **Browsers without Web Locks** rely on the server grace window for cross-tab races;
   a response lost after the server committed a rotation signs that browser out.
5. **No request rate limiter**; abuse controls before external exposure remain the
   open CT01 §11.5 gate.
6. **Purge** runs only after sign-ins; expired rows of inactive accounts persist.
7. **CT12.user lock revocation** is a binding on a future implementation, not yet code.

**Rollback.** Revert the PR before integration. After integration, a code rollback to
the previous release is compatible with an applied V008: the old code ignores the two
new tables and the cookie, and old access tokens without `sid` are rejected only by
the new code. V008 must not be edited; a later forward migration would drop the tables
if the design were withdrawn. An emergency kill switch for all sessions is rotating
`JWT_SECRET` (access tokens) plus revoking all rows (`UPDATE refresh_sessions SET
revoked_at = now()`), an operational action requiring Leader authorization.

**Remaining gates.** Independent Auth (@duyduyit), Frontend (@heizseung),
Infrastructure (@tihh647-ui) and Leader CODEOWNER review; protected integration with
required checks; QA01/QA07 independent verification; V008 execution and r3 grants on
any shared or production database (Leader-reserved; Flyway applies V008 when any build
containing it starts); deployment authorization; Leader decision on the residual
risks above.

## 13. Approval disposition

| Revision | Scope | Disposition |
|---|---|---|
| `AUTH-SESSION-2026-11-16-r1` | §§3–6: session-bound access JWT, refresh tokens, V008 schema, rotation/replay, cookie, CSRF/Origin/CORS, REST contracts, lifecycle interactions, CT12.user binding | DELEGATED_TECHNICAL_APPROVAL; DELEGATED_APPROVED; implemented in the PR branch; not yet effective on `main` |
| `CT16-SESSION-2026-11-16-r1` | §7 frontend coordination | DELEGATED_APPROVED; implemented in the PR branch |
| `CT17-INGRESS-COOKIE-2026-11-16-r1` | §8 ingress | DELEGATED_APPROVED; implemented in the PR branch |
| `CT17-P6-PRIVILEGES-2026-11-16-r3` | §8 privileges | DELEGATED_APPROVED; matrix, script and tests synchronized; no real database provisioned |
| `CT14-SESSION-2026-11-16-r1` | §9 verification obligations | DELEGATED_APPROVED; executed evidence in testing.md; independent QA pending |
| `STRUCT-AUTH-SESSION-2026-11-16-r1` | §10 three file additions | DELEGATED_APPROVED |

Technical references (inform the analysis; approval comes from the authority in §1):
[RFC 9700 OAuth 2.0 Security BCP §4.14 (refresh token rotation)](https://www.rfc-editor.org/rfc/rfc9700.html),
[RFC 6265bis cookie prefixes](https://datatracker.ietf.org/doc/draft-ietf-httpbis-rfc6265bis/),
[OWASP CSRF Prevention Cheat Sheet (Fetch Metadata, Origin verification)](https://cheatsheetseries.owasp.org/cheatsheets/Cross-Site_Request_Forgery_Prevention_Cheat_Sheet.html),
[OWASP Session Management Cheat Sheet](https://cheatsheetseries.owasp.org/cheatsheets/Session_Management_Cheat_Sheet.html).
