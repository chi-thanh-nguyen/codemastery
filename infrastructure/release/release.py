#!/usr/bin/env python3
"""CodeMastery release tooling (ADR015, Phase 4).

Standard-library only; Python 3.9 or later. Every subcommand is a pure function
of its inputs (files and arguments) and fails closed: a malformed, missing,
ambiguous or unexpected input exits non-zero with a short message that never
echoes untrusted archive content or any secret.

Subcommands
  archive-identities  image identities a `docker save --platform` archive yields
  evaluate-checks     latest authenticated evaluation of a source revision's required checks
  findings            normalize Trivy JSON reports into one findings list
  migrations          Flyway migration inventory and baseline compatibility
  candidate-manifest  write release-candidate.json for a candidate package
  admission-request   authenticate the dispatching account and read the Leader's request
  admission-approval  check the protected environment and the authority's approval of the run
  admission-preview   Markdown preview of what an approval of this request admits
  admission-decide    write the admission record of this run from its evidence
  verify-admission    re-derive an admission record from its evidence and the candidate
  check-attestation-run  a verified attestation was signed by exactly the expected run
  check-run-record    online: GitHub still records the admission's dispatch and approval
  check-rescan        fail on any promotion-time finding the admission does not cover
  release-manifest    write release.json for a promoted release package
  verify-package      check a package directory against its manifest and SHA256SUMS
  verify-release-chain  offline check that release, admission and candidate records agree
  expect-images       print --expect-image values for an image store
  summary             Markdown summary for the job summary and draft-release notes
"""

import argparse
import datetime
import hashlib
import json
import os
import re
import sys
import tarfile

SERVICES = ("postgres", "minio", "minio-init", "backend", "frontend")
RELEASE_REFS = {s: "codemastery.invalid/%s:release" % s for s in SERVICES}
PLATFORM = ("linux", "amd64")
CANDIDATE_SCHEMA = "codemastery.release-candidate/v1"
RELEASE_SCHEMA = "codemastery.release/v1"
ADMISSION_SCHEMA = "codemastery.release-admission/v2"
# Trust anchors (ADR015 A1). Changing any of them is a reviewed change of this file.
REPOSITORY = "chi-thanh-nguyen/codemastery"
REPOSITORY_ID = "1391772534"
WORKFLOW_PATH = ".github/workflows/release.yml"
WORKFLOW_REF = REPOSITORY + "/" + WORKFLOW_PATH + "@refs/heads/main"
ADMISSION_ENVIRONMENT = "release-admission"
# The release admission authority: GitHub login -> numeric account ID (the ID survives
# a rename and cannot be taken over by another account that later claims the login).
ADMISSION_AUTHORITY = {"chi-thanh-nguyen": 212181152}
DIGEST = re.compile(r"^sha256:[0-9a-f]{64}$")
HEX64 = re.compile(r"^[0-9a-f]{64}$")
REVISION = re.compile(r"^[0-9a-f]{40}$")
HEX40 = REVISION
VERSION = re.compile(r"^v(0|[1-9][0-9]*)\.(0|[1-9][0-9]*)\.(0|[1-9][0-9]*)(-[0-9A-Za-z]+(\.[0-9A-Za-z]+)*)?$")
RECORD_ID = re.compile(r"^[A-Z0-9][A-Z0-9-]{7,79}$")
CANDIDATE_TAG = re.compile(r"^candidate-[0-9a-f]{12}-[0-9]+-[0-9]+$")
LOGIN = re.compile(r"^[A-Za-z0-9](?:[A-Za-z0-9-]{0,38})$")
ORIGIN = re.compile(r"^https://[a-z0-9]([a-z0-9-]*[a-z0-9])?(\.[a-z0-9]([a-z0-9-]*[a-z0-9])?)+$")
FILE_NAME = re.compile(r"^[A-Za-z0-9][A-Za-z0-9._-]{0,127}$")
MIGRATION = re.compile(r"^V([0-9]+)__[A-Za-z0-9_]+\.sql$")
SEVERITIES = ("CRITICAL", "HIGH", "MEDIUM", "LOW", "UNKNOWN")
INDEX_TYPES = {"application/vnd.oci.image.index.v1+json",
               "application/vnd.docker.distribution.manifest.list.v2+json"}
MANIFEST_TYPES = {"application/vnd.oci.image.manifest.v1+json",
                  "application/vnd.docker.distribution.manifest.v2+json"}
NAME = "io.containerd.image.name"
SUBJECT = "io.containerd.manifest.subject"


class Refused(Exception):
    """A fail-closed refusal; the message is safe to print."""


def refuse(message):
    raise Refused(message)


def sha256_file(path):
    h = hashlib.sha256()
    with open(path, "rb") as f:
        for chunk in iter(lambda: f.read(1 << 20), b""):
            h.update(chunk)
    return h.hexdigest()


def load_json(path, what):
    try:
        with open(path, "rb") as f:
            raw = f.read()
    except OSError:
        refuse("%s is missing or unreadable" % what)
    try:
        return json.loads(raw.decode("utf-8"), object_pairs_hook=_no_duplicate_keys)
    except (UnicodeDecodeError, ValueError):
        refuse("%s is not valid UTF-8 JSON without duplicate keys" % what)


def _no_duplicate_keys(pairs):
    out = {}
    for key, value in pairs:
        if key in out:
            raise ValueError("duplicate key")
        out[key] = value
    return out


def write_json(path, data):
    tmp = path + ".tmp"
    with open(tmp, "w", encoding="utf-8") as f:
        json.dump(data, f, indent=2, sort_keys=True)
        f.write("\n")
    os.replace(tmp, path)


def utc_now():
    return datetime.datetime.now(datetime.timezone.utc).strftime("%Y-%m-%dT%H:%M:%SZ")


# --------------------------------------------------------------------------
# Image references and archive identities (same rules as deploy.sh)

def normalize_reference(name):
    repository, _, digest = name.partition("@")
    tag = ""
    if repository.rfind(":") > repository.rfind("/"):
        repository, tag = repository.rsplit(":", 1)
    parts = repository.split("/")
    if len(parts) == 1 or not ("." in parts[0] or ":" in parts[0] or parts[0] == "localhost"):
        parts.insert(0, "docker.io")
    if parts[0] == "docker.io" and len(parts) == 2:
        parts.insert(1, "library")
    if not tag and not digest:
        tag = "latest"
    return "/".join(parts), tag, digest


def _platform(value):
    value = value or {}
    return value.get("os"), value.get("architecture")


def archive_identities(archive, refs):
    """Return {ref: {containerd, graphdriver, platform}} for every requested ref.

    containerd: the digest of the named top-level index.json entry (what the
    containerd image store reports as the image ID after `docker load`);
    graphdriver: the image config digest. Each blob followed is re-hashed.
    Every image in the archive must be one of the requested refs, and every
    name the archive applies must be one of them.
    """
    if len(set(refs)) != len(refs) or not refs:
        refuse("references must be unique and non-empty")
    wanted = {normalize_reference(r): r for r in refs}
    if len(wanted) != len(refs):
        refuse("references normalize to the same name")
    try:
        t = tarfile.open(archive, "r:*")
    except (OSError, tarfile.TarError):
        refuse("archive is missing or not a tar file")
    with t:
        members = t.getmembers()
        if len({m.name for m in members}) != len(members):
            refuse("archive has duplicate entries")

        def blob(digest, size=None):
            algorithm, _, hexdigest = digest.partition(":")
            if algorithm != "sha256" or not HEX64.match(hexdigest):
                refuse("archive descriptor digest is malformed")
            try:
                data = t.extractfile("blobs/sha256/" + hexdigest).read()
            except (KeyError, AttributeError):
                refuse("archive descriptor points to a missing blob")
            if hashlib.sha256(data).hexdigest() != hexdigest:
                refuse("archive blob does not match its digest")
            if size is not None and size != len(data):
                refuse("archive blob size does not match its descriptor")
            return data

        def read_member(name):
            try:
                return json.loads(t.extractfile(name).read())
            except (KeyError, AttributeError, ValueError):
                refuse("archive lacks a readable %s" % name)

        out = {}
        configs = {}
        for top in read_member("index.json").get("manifests") or []:
            annotations = top.get("annotations") or {}
            if SUBJECT in annotations:
                continue  # attestation referrer; deploy.sh verifies these separately
            name = annotations.get(NAME)
            if not name:
                refuse("archive holds an unnamed runnable image")
            ref = wanted.get(normalize_reference(name))
            if ref is None:
                refuse("archive applies a name that was not requested")
            if ref in out:
                refuse("archive names one reference twice")
            descriptor = top
            if top.get("mediaType") in INDEX_TYPES:
                index = json.loads(blob(top["digest"], top.get("size")))
                runnable = [c for c in index.get("manifests") or []
                            if (c.get("annotations") or {}).get("vnd.docker.reference.type") != "attestation-manifest"
                            and _platform(c.get("platform")) == PLATFORM]
                if len(runnable) != 1 or (runnable[0].get("platform") or {}).get("variant"):
                    refuse("image does not have exactly one linux/amd64 manifest")
                descriptor = runnable[0]
            elif top.get("mediaType") not in MANIFEST_TYPES:
                refuse("archive entry has an unexpected media type")
            manifest = json.loads(blob(descriptor["digest"], descriptor.get("size")))
            config_descriptor = manifest.get("config") or {}
            config = json.loads(blob(config_descriptor.get("digest", ""), config_descriptor.get("size")))
            if _platform(config) != PLATFORM:
                refuse("image config is not linux/amd64")
            out[ref] = {"containerd": top["digest"], "graphdriver": config_descriptor["digest"],
                        "platform": "linux/amd64"}
            configs[config_descriptor["digest"]] = ref
        if set(out) != set(refs):
            refuse("archive does not hold every requested reference")
        # manifest.json (the graph-driver view) must describe the same images.
        seen = set()
        for item in read_member("manifest.json"):
            data = t.extractfile(item["Config"]).read()
            digest = "sha256:" + hashlib.sha256(data).hexdigest()
            ref = configs.get(digest)
            if ref is None:
                refuse("manifest.json holds an image outside index.json")
            for tag in item.get("RepoTags") or []:
                if wanted.get(normalize_reference(tag)) != ref:
                    refuse("manifest.json tag does not match its image")
            seen.add(ref)
        if seen != set(refs):
            refuse("manifest.json does not describe every image")
    return out


# --------------------------------------------------------------------------
# Required status checks of the source revision (ADR015 D2 as amended by A2)

SOURCE_CHECKS_SCHEMA = "codemastery.source-checks/v2"
SOURCE_CHECKS_POLICY = (
    "ADR015 D2 (amendment A2): for every required status check of the live main rules, the latest valid "
    "evaluation of the exact source revision has completed and succeeded. GitHub Actions checks are bound to "
    "GitHub's own job records of a trusted workflow run (push or workflow_dispatch on main, a workflow file of "
    "the revision), and the newest run attempt of that workflow decides; checks of other apps are accepted only "
    "from GitHub-operated apps whose check runs no workflow can create. Superseded results are kept as evidence.")
# Any workflow token with checks: write creates check runs under this app's identity,
# so its check runs count only through GitHub's job record of a trusted run.
ACTIONS_APP_ID = 15368
# GitHub-operated apps whose check runs no workflow token, user or other app can create:
# the app identity authenticates the check run itself. Any other app fails closed.
AUTHENTICATED_APPS = {57789: "GitHub code scanning"}
TRUSTED_EVENTS = ("push", "workflow_dispatch")
WORKFLOW_FILE = re.compile(r"^\.github/workflows/[A-Za-z0-9][A-Za-z0-9._-]*\.ya?ml$")
API_BASE = "https://api.github.com/repos/" + REPOSITORY
WEB_BASE = "https://github.com/" + REPOSITORY
RERUN_ALL = "re-run all jobs of that workflow run (or start a new run of the workflow on main) and dispatch again"


def _is_int(value):
    return isinstance(value, int) and not isinstance(value, bool)


def api_items(pages, key, what):
    """Items of slurped REST pages (`gh api --paginate --slurp`); fails closed on a short or malformed listing."""
    if not isinstance(pages, list) or not pages:
        refuse("%s: no API page was read" % what)
    items, totals = [], set()
    for page in pages:
        if not isinstance(page, dict) or not isinstance(page.get(key), list) or not _is_int(page.get("total_count")):
            refuse("%s: malformed API page" % what)
        items.extend(page[key])
        totals.add(page["total_count"])
    if len(totals) != 1 or totals.pop() != len(items):
        refuse("%s: the pages hold %d item(s), not the total GitHub reports; the listing is incomplete "
               "or changed while it was read" % (what, len(items)))
    ids = [i.get("id") if isinstance(i, dict) else None for i in items]
    if not all(_is_int(i) for i in ids) or len(set(ids)) != len(ids):
        refuse("%s: an item has a missing, malformed or repeated id" % what)
    return items


def merged_pull_request(pulls, revision):
    """The single PR of this repository merged into main whose merge commit is the revision, or None."""
    merged = [p for p in pulls if isinstance(p, dict) and p.get("merged_at") and p.get("merge_commit_sha") == revision
              and (p.get("base") or {}).get("ref") == "main"]
    if len(merged) > 1:
        refuse("more than one merged pull request claims the revision")
    if merged and str(((merged[0].get("base") or {}).get("repo") or {}).get("id")) != REPOSITORY_ID:
        refuse("the merged pull request does not belong to %s" % REPOSITORY)
    return merged[0] if merged else None


def required_contexts(rules):
    """(context, app) pairs of the live main rules; a check this module cannot authenticate fails closed."""
    required = set()
    for rule in rules if isinstance(rules, list) else []:
        if not isinstance(rule, dict) or rule.get("type") != "required_status_checks":
            continue
        for item in (rule.get("parameters") or {}).get("required_status_checks") or []:
            context = item.get("context") if isinstance(item, dict) else None
            app = item.get("integration_id") if isinstance(item, dict) else None
            if not isinstance(context, str) or not context:
                refuse("a required status check of the default branch has no context")
            if not _is_int(app):
                refuse("required check '%s' names no app, so any app could satisfy it; it cannot be authenticated" % context)
            if app != ACTIONS_APP_ID and app not in AUTHENTICATED_APPS:
                refuse("required check '%s' comes from app %d, whose check runs this pipeline cannot authenticate; "
                       "ADR015 D2 must be extended before such a revision can be released" % (context, app))
            required.add((context, app))
    if not required:
        refuse("no required status checks were found for the default branch; refusing to treat the revision as qualified")
    return sorted(required)


def _run_timestamp(value):
    """A real UTC instant in GitHub's canonical, second-resolution representation, or None."""
    if not isinstance(value, str) or not TIMESTAMP.fullmatch(value):
        return None
    try:
        return datetime.datetime.strptime(value, "%Y-%m-%dT%H:%M:%SZ").replace(tzinfo=datetime.timezone.utc)
    except ValueError:
        return None


def _evidence_text(value):
    return (isinstance(value, str) and bool(value) and value == value.strip()
            and not any(ord(c) < 32 or ord(c) == 127 for c in value))


def _run_branch(value):
    """A syntactically valid refs/heads suffix; malformed text cannot prove another ref."""
    return (_evidence_text(value) and not value.endswith(("/", "."))
            and not re.search(r"[ ~^:?*\[\\]", value) and not any(s in value for s in ("..", "@{", "//"))
            and all(part and not part.startswith(".") and not part.endswith(".lock") for part in value.split("/")))


def _repository_id(record):
    """Canonical numeric identity, preserving the existing integer/decimal-string support."""
    value = record.get("id") if isinstance(record, dict) else None
    if _is_int(value) and value > 0:
        return str(value)
    if isinstance(value, str) and re.fullmatch(r"[1-9][0-9]*", value):
        return value
    return None


def _untrusted_run(run, revision, workflow_files):
    """None when the run is a trusted evaluation of the revision; the reason when its record
    shows positively that it is not one. A record that may be a trusted evaluation but lacks,
    or garbles, a field the trust decision or the ordering uses refuses the revision: dropping
    it could let an older success stand in for a newer failure."""
    head_sha, event, branch, path = (run.get(k) for k in ("head_sha", "event", "head_branch", "path"))
    valid = {"head_sha": isinstance(head_sha, str) and bool(REVISION.fullmatch(head_sha)),
             "event": isinstance(event, str) and bool(re.fullmatch(r"[a-z][a-z0-9_]*", event)),
             "head_branch": _run_branch(branch),
             "path": (_evidence_text(path) and not path.startswith("/") and "\\" not in path
                      and all(part not in ("", ".", "..") for part in path.split("/")))}
    if valid["head_sha"] and head_sha != revision:
        return "run of another commit"
    if valid["event"] and event not in TRUSTED_EVENTS:
        return "event %r" % (event,)
    if valid["head_branch"] and branch != "main":
        return "ref %r" % (branch,)
    if valid["path"] and (not WORKFLOW_FILE.fullmatch(path) or path not in workflow_files):
        return "workflow %r is not a workflow file of the revision" % (path,)
    if path == WORKFLOW_PATH:
        return "release stages are not CI evaluations (the candidate run itself is one of them)"
    problems = [k for k, well_formed in valid.items() if not well_formed]
    if _repository_id(run.get("repository")) != REPOSITORY_ID \
            or _repository_id(run.get("head_repository")) != REPOSITORY_ID:
        problems.append("repository")
    if not _is_int(run.get("run_attempt")) or run["run_attempt"] < 1:
        problems.append("run_attempt")
    problems += [k for k in ("check_suite_id", "workflow_id") if not _is_int(run.get(k)) or run[k] < 1]
    if _run_timestamp(run.get("created_at")) is None:
        problems.append("created_at")
    if not _evidence_text(run.get("status")):
        problems.append("status")
    if run.get("conclusion") is not None and not _evidence_text(run.get("conclusion")):
        problems.append("conclusion")
    if problems:
        refuse("workflow run %d may be an evaluation of the revision, but its record is malformed or inconsistent "
               "(%s); it is never ignored, so the revision is refused" % (run["id"], ", ".join(problems)))
    return None


JOB_FIELDS = ("name", "run_id", "run_attempt", "head_sha", "status", "conclusion", "started_at", "completed_at",
              "check_run_url", "html_url")
RUN_FIELDS = ("run_attempt", "status", "conclusion", "created_at", "path", "event", "head_sha", "head_branch",
              "workflow_id", "check_suite_id", "repository", "head_repository")
SNAPSHOT_CUTOFF = ("first reads decide; the revision's workflow runs and the check runs under required names on the "
                   "revision and on the merged pull request's head were read again afterwards and agreed; the "
                   "cutoff is the end of the second read of the workflow runs")


def _check_run_state(listing, names):
    """Decision-relevant fields of the check runs under required names, by ID."""
    state = {}
    for c in listing:
        if c.get("name") not in names:
            continue
        ids = []
        for field in ("app", "check_suite"):
            record = c.get(field)
            ids.append(("dict", _snapshot_value(record.get("id"))) if isinstance(record, dict)
                       else _snapshot_value(record))
        state[c["id"]] = tuple(_snapshot_value(v) for v in
                                (c.get("name"), *ids, c.get("head_sha"), c.get("status"), c.get("conclusion"),
                                 c.get("url"), c.get("html_url")))
    return state


def _snapshot_value(value):
    # Python otherwise equates 1, True and 1.0; malformed types must not hide a change.
    return type(value).__name__, value


def _run_jobs(run, jobs, latest_only):
    """GitHub's job records of one trusted run, each checked to belong to it."""
    if not isinstance(jobs, list):
        refuse("GitHub's job records of workflow run %d were not read; its checks cannot be verified" % run["id"])
    for job in jobs:
        attempt = job.get("run_attempt")
        if job.get("run_id") != run["id"] or job.get("head_sha") != run["head_sha"] or not isinstance(job.get("name"), str) \
                or not _is_int(attempt) or not 1 <= attempt <= run["run_attempt"] \
                or (latest_only and attempt != run["run_attempt"]) \
                or job.get("check_run_url") != "%s/check-runs/%d" % (API_BASE, job["id"]) \
                or job.get("html_url") != "%s/actions/runs/%d/job/%d" % (WEB_BASE, run["id"], job["id"]):
            refuse("a job record of workflow run %d does not belong to that run and attempt (job %s)" % (run["id"], job.get("id")))
    return jobs


def _actions_check(context, revision, check_runs, eligible, attempt_jobs, all_jobs):
    """The latest evaluation of an Actions context, bound to GitHub's job and check-run records."""
    producers = {}
    for rid, run in eligible.items():
        if any(j["name"] == context for j in all_jobs[rid]):
            producers.setdefault(run["path"], []).append(rid)
    if not producers:
        refuse("required check '%s' has no job in a trusted workflow run of the revision (push or workflow_dispatch "
               "on main of a workflow file of the revision)" % context)
    if len(producers) > 1:
        refuse("required check '%s' is a job of more than one workflow (%s); which one is authoritative is ambiguous"
               % (context, ", ".join(sorted(producers))))
    path = next(iter(producers))
    # The newest run of that workflow on the revision, and its newest attempt, decide.
    runs = [r for r in eligible.values() if r["path"] == path]
    run = max(runs, key=lambda r: (_run_timestamp(r["created_at"]), r["id"]))
    rid, attempt = run["id"], run["run_attempt"]
    where = "run %d attempt %d of %s" % (rid, attempt, path)
    if run.get("status") != "completed":
        refuse("required check '%s': its latest evaluation (%s) has not completed (%s); wait for it and dispatch again, "
               "an earlier result never stands in for it" % (context, where, run.get("status")))
    named = [j for j in attempt_jobs[rid] if j["name"] == context]
    if len(named) != 1:
        refuse("required check '%s': GitHub's record of %s holds %d jobs of that name, so the latest evaluation "
               "cannot be identified; %s" % (context, where, len(named), RERUN_ALL))
    job = named[0]
    if job.get("status") != "completed" or job.get("conclusion") != "success":
        refuse("required check '%s': its latest evaluation (%s, job %d) is %s/%s; an earlier success never overrides it"
               % (context, where, job["id"], job.get("status"), job.get("conclusion")))
    check = [c for c in check_runs if c.get("id") == job["id"]]
    c = check[0] if len(check) == 1 else {}
    if c.get("name") != context or (c.get("app") or {}).get("id") != ACTIONS_APP_ID or c.get("head_sha") != revision \
            or c.get("status") != "completed" or c.get("conclusion") != "success" \
            or (c.get("check_suite") or {}).get("id") != run["check_suite_id"] \
            or c.get("url") != job["check_run_url"] or c.get("html_url") != job["html_url"]:
        refuse("required check '%s': the revision's check run %d does not match GitHub's job record of %s"
               % (context, job["id"], where))
    history = []
    for hid in sorted(eligible):
        for j in all_jobs[hid]:
            if j["name"] == context and j["id"] != job["id"]:
                history.append({"run_id": hid, "run_attempt": j["run_attempt"], "job_id": j["id"],
                                "status": j.get("status"), "conclusion": j.get("conclusion"),
                                "workflow_path": eligible[hid]["path"], "url": j["html_url"]})
    history.sort(key=lambda h: (h["run_id"], h["run_attempt"], h["job_id"]))
    reused = bool(isinstance(job.get("started_at"), str) and TIMESTAMP.match(job["started_at"])
                  and isinstance(run.get("run_started_at"), str) and TIMESTAMP.match(run["run_started_at"])
                  and job["started_at"] < run["run_started_at"])
    return {"context": context, "app_id": ACTIONS_APP_ID, "provenance": "github-actions-job", "evidence": "revision",
            "source_sha": revision, "evaluated_sha": revision,
            "workflow": {"path": path, "id": run["workflow_id"], "name": run.get("name"), "event": run["event"],
                         "ref": "refs/heads/main"},
            "run_id": rid, "run_attempt": attempt, "run_url": "%s/actions/runs/%d/attempts/%d" % (WEB_BASE, rid, attempt),
            "job_id": job["id"], "check_run_id": job["id"], "check_suite_id": run["check_suite_id"],
            "status": "completed", "conclusion": "success", "started_at": job.get("started_at"),
            "completed_at": job.get("completed_at"), "result_kept_from_earlier_attempt": reused,
            "url": job["html_url"], "superseded": history}


def _app_check(context, app, sha, pool, evidence):
    """The latest check run of a GitHub-operated app on one commit."""
    for c in pool:
        if c.get("head_sha") != sha or c.get("url") != "%s/check-runs/%d" % (API_BASE, c["id"]):
            refuse("required check '%s': check run %d does not belong to commit %s of %s" % (context, c["id"], sha, REPOSITORY))
    unfinished = [c for c in pool if c.get("status") != "completed"]
    if unfinished:
        refuse("required check '%s' has a run that has not completed on %s (%s); wait for it and dispatch again"
               % (context, evidence, unfinished[0].get("status")))
    newest = max(pool, key=lambda c: c["id"])
    if newest.get("conclusion") != "success":
        refuse("required check '%s': its latest evaluation on %s concluded %s; an earlier success never overrides it"
               % (context, evidence, newest.get("conclusion")))
    history = [{"check_run_id": c["id"], "status": c.get("status"), "conclusion": c.get("conclusion"), "url": c.get("html_url")}
               for c in sorted(pool, key=lambda c: c["id"]) if c is not newest]
    return {"context": context, "app_id": app, "provenance": "github-app-check-run (%s)" % AUTHENTICATED_APPS[app],
            "evaluated_sha": sha, "check_run_id": newest["id"],
            "check_suite_id": (newest.get("check_suite") or {}).get("id"), "status": "completed", "conclusion": "success",
            "started_at": newest.get("started_at"), "completed_at": newest.get("completed_at"),
            "url": newest.get("html_url"), "superseded": history, "evidence": evidence}


def evaluate_checks(revision, rules, check_runs, actions, pr=None, pr_runs=None, check_runs_after=None,
                    pr_runs_after=None):
    """ADR015 D2 (A2): the latest valid evaluation of every required check of the revision passed.

    rules: GET /repos/{o}/{r}/rules/branches/main. check_runs: every check run of the
    revision (filter=all). actions: {"workflow_files": paths under .github/workflows/ in the
    revision, "runs": the revision's workflow runs, "runs_after": the same listing read
    again after everything else, "attempt_jobs": {run id: jobs of its newest attempt},
    "all_jobs": {run id: jobs of every attempt}}.

    A check from GitHub Actions counts only through GitHub's job record: the newest
    attempt of the newest trusted run (push or workflow_dispatch on main, in this
    repository, of a workflow file of the revision) of the single workflow that has a
    job of that name decides, and the revision's check run with the job's ID must match
    it. A check run without such a job (for example one created through the API by a
    workflow with checks: write) is ignored and recorded, never evidence. A check of a
    GitHub-operated app in AUTHENTICATED_APPS counts through its newest check run; when
    that app has no run of the context on the revision, the head of the merged pull
    request whose tree equals the revision's (checked by the caller) may stand in.
    Earlier results of the same evaluation are kept under "superseded".

    check_runs_after and pr_runs_after are the revision's and the merged pull request
    head's check-run listings read again, after the first reads and before the second
    read of the workflow runs: any difference under a required name refuses the
    revision (SNAPSHOT_CUTOFF).
    """
    if not isinstance(revision, str) or not REVISION.match(revision):
        refuse("the revision must be a full commit SHA")
    required = required_contexts(rules)
    for c in check_runs:
        if not isinstance(c.get("name"), str):
            refuse("a check run of the revision has no name")
    runs, after = actions.get("runs") or [], actions.get("runs_after")

    def state(listing):
        return {r.get("id"): tuple(_repository_id(r.get(k)) if k in ("repository", "head_repository")
                                   else _snapshot_value(r.get(k))
                                   for k in RUN_FIELDS) for r in listing}
    if not isinstance(after, list) or state(runs) != state(after):
        refuse("the revision's workflow runs changed while the evidence was read (a run or re-run started or "
               "finished); dispatch again")
    names = {context for context, _ in required}
    if not isinstance(check_runs_after, list) or _check_run_state(check_runs, names) != _check_run_state(check_runs_after, names):
        refuse("the revision's check runs under required names changed while the evidence was read; dispatch again")
    if pr is not None and (not isinstance(pr_runs, list) or not isinstance(pr_runs_after, list)
                           or _check_run_state(pr_runs, names) != _check_run_state(pr_runs_after, names)):
        refuse("the check runs of merged pull request #%s's head changed while the evidence was read (for example a "
               "newer CodeQL analysis started or concluded); dispatch again" % pr.get("number"))
    files = set(actions.get("workflow_files") or [])
    eligible, untrusted = {}, {}
    for run in runs:
        why = _untrusted_run(run, revision, files)
        if why:
            untrusted[run["id"]] = why
        else:
            eligible[run["id"]] = run
    attempt_jobs = {rid: _run_jobs(r, (actions.get("attempt_jobs") or {}).get(rid), True) for rid, r in eligible.items()}
    all_jobs = {rid: _run_jobs(r, (actions.get("all_jobs") or {}).get(rid), False) for rid, r in eligible.items()}
    for rid in eligible:
        def records(jobs):
            return {j["id"]: tuple(_snapshot_value(j.get(k)) for k in JOB_FIELDS) for j in jobs}
        if records(attempt_jobs[rid]) != records(j for j in all_jobs[rid] if j["run_attempt"] == eligible[rid]["run_attempt"]):
            refuse("GitHub's job records of workflow run %d disagree between its attempt and run listings" % rid)
    genuine = {j["id"] for jobs in all_jobs.values() for j in jobs}

    results, used = [], set()
    for context, app in required:
        if app == ACTIONS_APP_ID:
            entry = _actions_check(context, revision, check_runs, eligible, attempt_jobs, all_jobs)
        else:
            on_revision = [c for c in check_runs if c["name"] == context and (c.get("app") or {}).get("id") == app]
            if on_revision:
                entry = _app_check(context, app, revision, on_revision, "the revision")
            elif pr is not None:
                head = (pr.get("head") or {}).get("sha")
                pool = [c for c in pr_runs or [] if c.get("name") == context and (c.get("app") or {}).get("id") == app]
                if not pool:
                    refuse("required check '%s' has no check run on the revision or on its merged pull request's head" % context)
                entry = _app_check(context, app, head, pool, "merged pull request #%s head %s" % (pr.get("number"), head))
                entry["pull_request"] = {"number": pr.get("number"), "head_sha": head, "tree_equals_revision": True}
            else:
                refuse("required check '%s' has no check run on the revision and no merged pull request can stand in" % context)
            entry["source_sha"] = revision
        used.add(context)
        results.append(entry)
    ignored = []
    for c in sorted(check_runs, key=lambda c: c["id"]):
        app = (c.get("app") or {}).get("id")
        if c["name"] not in used:
            continue
        if app == ACTIONS_APP_ID and (c["name"], app) in required:
            if c["id"] in genuine:
                continue        # the decisive job or a superseded one, recorded above
            reason = "no job of a trusted workflow run of the revision has this ID"
        elif (c["name"], app) in required:
            continue            # the authenticated app's own run, recorded above
        else:
            reason = "check run of app %s, which the rule does not name" % (app,)
        ignored.append({"check_run_id": c["id"], "name": c["name"], "app_id": app, "status": c.get("status"),
                        "conclusion": c.get("conclusion"), "reason": reason})
    return {"schema": SOURCE_CHECKS_SCHEMA, "policy": SOURCE_CHECKS_POLICY, "snapshot": SNAPSHOT_CUTOFF,
            "repository": REPOSITORY,
            "repository_id": REPOSITORY_ID, "source_revision": revision, "required_checks": results,
            "ignored_check_runs": ignored,
            "untrusted_workflow_runs": [{"run_id": rid, "reason": why} for rid, why in sorted(untrusted.items())]}


# --------------------------------------------------------------------------
# Trivy findings

def normalize_findings(service, reports):
    """Union of vulnerability rows of every report for one service."""
    rows = {}
    for path in reports:
        report = load_json(path, "scanner report")
        if not isinstance(report, dict) or report.get("SchemaVersion") != 2:
            refuse("scanner report has an unexpected schema")
        for result in report.get("Results") or []:
            for v in result.get("Vulnerabilities") or []:
                vid, pkg = v.get("VulnerabilityID"), v.get("PkgName")
                severity = v.get("Severity") or "UNKNOWN"
                if not vid or not pkg or severity not in SEVERITIES:
                    refuse("scanner report holds a malformed vulnerability row")
                key = (service, vid, pkg, v.get("InstalledVersion") or "")
                prior = rows.get(key)
                if prior is None or SEVERITIES.index(severity) < SEVERITIES.index(prior["severity"]):
                    rows[key] = {"service": service, "id": vid, "package": pkg,
                                 "installed": v.get("InstalledVersion") or "",
                                 "fixed": v.get("FixedVersion") or "", "severity": severity,
                                 "target": result.get("Target") or ""}
    return sorted(rows.values(), key=lambda r: (r["service"], SEVERITIES.index(r["severity"]), r["id"], r["package"]))


def coverage_target(result):
    """A scanned target in a form that depends only on the image's contents.

    Trivy names the operating-system result after the scanned input (the archive
    path, which holds the image name), so that result is identified by its class
    and OS type alone. Package results keep their path inside the image.
    """
    klass, kind = result.get("Class") or "", result.get("Type") or ""
    if klass == "os-pkgs":
        return "os-pkgs/%s" % kind
    return "%s/%s:%s" % (klass, kind, (result.get("Target") or "").lstrip("/"))


def scan_coverage(reports):
    """What the scanner analysed in one service's reports: detected OS and result targets.

    An image whose OS the scanner did not detect or no longer supports yields fewer
    findings without failing the scan: recorded as admission evidence, and the
    promotion rescan must cover at least as much (check_coverage).
    """
    os_names, eosl, targets = set(), False, set()
    for path in reports:
        report = load_json(path, "scanner report")
        detected = (report.get("Metadata") or {}).get("OS") or {}
        if detected.get("Family"):
            os_names.add("%s %s" % (detected.get("Family"), detected.get("Name") or ""))
        eosl = eosl or detected.get("EOSL") is True
        for result in report.get("Results") or []:
            targets.add(coverage_target(result))
    if len(os_names) > 1:
        refuse("scanner reports of one image disagree on its operating system")
    return {"os": os_names.pop().strip() if os_names else None, "os_end_of_support": eosl,
            "targets": sorted(targets)}


def _os_name(value):
    return " ".join(value.split()).lower() if isinstance(value, str) and value.strip() else None


def check_coverage(candidate_coverage, rescan_coverage):
    """Per service, what the promotion rescan analysed less than the candidate scan.

    A rescan that no longer detects the candidate's operating system (or detects
    another), newly reports it as end of support, or lacks any target the
    candidate scan analysed would report fewer findings without failing; each is
    refused. Targets are compared in their image-independent form.
    """
    problems = []
    for service in SERVICES:
        cand = (candidate_coverage if isinstance(candidate_coverage, dict) else {}).get(service)
        rescan = (rescan_coverage if isinstance(rescan_coverage, dict) else {}).get(service)
        if not isinstance(cand, dict) or not isinstance(rescan, dict) \
                or not isinstance(cand.get("targets"), list) or not isinstance(rescan.get("targets"), list):
            problems.append((service, "scan coverage is missing or malformed"))
            continue
        cand_os, rescan_os = _os_name(cand.get("os")), _os_name(rescan.get("os"))
        if cand_os and rescan_os != cand_os:
            problems.append((service, "operating system %s detected by the candidate scan, %s by the rescan"
                             % (cand_os, rescan_os or "none")))
        if rescan.get("os_end_of_support") is True and cand.get("os_end_of_support") is not True:
            problems.append((service, "the rescan reports the operating system as end of support"))
        missing = set(cand["targets"]) - set(rescan["targets"])
        if missing:
            problems.append((service, "%d target(s) of the candidate scan were not scanned: %s"
                             % (len(missing), ", ".join(sorted(missing))[:300])))
    return problems


def count_findings(findings):
    counts = {s: {sev: 0 for sev in SEVERITIES} for s in SERVICES}
    for f in findings:
        counts[f["service"]][f["severity"]] += 1
    return counts


# --------------------------------------------------------------------------
# Flyway migration inventory

def migration_inventory(directory):
    items = []
    try:
        names = sorted(os.listdir(directory))
    except OSError:
        refuse("migration directory is missing")
    versions = set()
    for name in names:
        path = os.path.join(directory, name)
        if not os.path.isfile(path):
            continue
        match = MIGRATION.match(name)
        if not match:
            refuse("unexpected file in the migration directory: only V<n>__<name>.sql is allowed")
        version = int(match.group(1))
        if version in versions:
            refuse("duplicate Flyway version")
        versions.add(version)
        items.append({"version": version, "file": name, "sha256": sha256_file(path)})
    return sorted(items, key=lambda x: x["version"])


def migration_compatibility(current, baseline):
    """A baseline migration must exist unchanged; new ones must be strictly newer."""
    by_file = {m["file"]: m for m in current}
    problems = []
    for old in baseline:
        new = by_file.get(old["file"])
        if new is None:
            problems.append("baseline migration %s is missing" % old["file"])
        elif new["sha256"] != old["sha256"]:
            problems.append("baseline migration %s was modified" % old["file"])
    top = max([m["version"] for m in baseline] or [0])
    old_files = {m["file"] for m in baseline}
    added = [m for m in current if m["file"] not in old_files]
    for m in added:
        if m["version"] <= top:
            problems.append("new migration %s is not newer than the baseline's V%d" % (m["file"], top))
    return {"added": [m["file"] for m in added], "problems": problems}


# --------------------------------------------------------------------------
# Manifests

def file_map(directory, exclude):
    out = {}
    for name in sorted(os.listdir(directory)):
        if name in exclude:
            continue
        path = os.path.join(directory, name)
        if not os.path.isfile(path) or os.path.islink(path) or not FILE_NAME.match(name):
            refuse("package directory holds an unexpected entry")
        out[name] = sha256_file(path)
    return out


def write_sha256sums(directory):
    names = sorted(n for n in os.listdir(directory) if n != "SHA256SUMS")
    lines = []
    for name in names:
        path = os.path.join(directory, name)
        if not os.path.isfile(path) or os.path.islink(path) or not FILE_NAME.match(name):
            refuse("package directory holds an unexpected entry")
        lines.append("%s  %s\n" % (sha256_file(path), name))
    with open(os.path.join(directory, "SHA256SUMS"), "w", encoding="utf-8") as f:
        f.writelines(lines)


def read_sha256sums(path):
    entries = {}
    try:
        with open(path, encoding="utf-8") as f:
            lines = f.read().splitlines()
    except (OSError, UnicodeDecodeError):
        refuse("SHA256SUMS is missing or unreadable")
    for line in lines:
        match = re.match(r"^([0-9a-f]{64})  ([A-Za-z0-9][A-Za-z0-9._-]{0,127})$", line)
        if not match or match.group(2) in entries:
            refuse("SHA256SUMS has a malformed or duplicate line")
        entries[match.group(2)] = match.group(1)
    if not entries:
        refuse("SHA256SUMS is empty")
    return entries


def verify_package(directory, kind, allow_extra=()):
    manifest_name = {"candidate": "release-candidate.json", "release": "release.json"}[kind]
    sums = read_sha256sums(os.path.join(directory, "SHA256SUMS"))
    present = set(os.listdir(directory)) - {"SHA256SUMS"} - set(allow_extra)
    if present != set(sums):
        refuse("package files do not match SHA256SUMS exactly")
    for name, digest in sums.items():
        path = os.path.join(directory, name)
        if os.path.islink(path) or not os.path.isfile(path) or sha256_file(path) != digest:
            refuse("package file %s does not match SHA256SUMS" % name)
    manifest = load_json(os.path.join(directory, manifest_name), manifest_name)
    schema = CANDIDATE_SCHEMA if kind == "candidate" else RELEASE_SCHEMA
    if manifest.get("schema") != schema:
        refuse("%s has an unexpected schema" % manifest_name)
    files = manifest.get("files")
    if not isinstance(files, dict) or set(files) != set(sums) - {manifest_name}:
        refuse("%s does not list exactly the other package files" % manifest_name)
    for name, digest in files.items():
        if sums.get(name) != digest:
            refuse("%s and SHA256SUMS disagree on %s" % (manifest_name, name))
    archive = manifest.get("archive") or {}
    if files.get(archive.get("name")) != archive.get("sha256"):
        refuse("manifest archive entry does not match the package")
    return manifest


# --------------------------------------------------------------------------
# Admission (ADR015 D7 as amended by A1)
#
# A security admission is a Leader decision made inside the promote run itself:
# the pinned admission authority dispatches release.yml on main naming the exact
# candidate manifest digest it reviewed, and approves that run in the protected
# `release-admission` environment. The record is derived from GitHub's own views
# of the run (runner environment, event payload, run record, environment rules and
# approval record), never from a file or a typed login, and it is attested before
# any candidate image runs. A record is valid only for the run that made it.

ADMISSION_KEYS = {"schema", "record_id", "decision", "decided_by", "decided_at", "scope", "statement",
                  "source_revision", "public_origin", "candidate", "images", "accepted_findings",
                  "accepted_counts", "release", "not_granted", "authorization", "rehearsal"}
REQUIRED_NOT_GRANTED = ("deployment", "existing-vm-update", "licensing-determination",
                        "production-secrets", "public-exposure", "public-publication")
REHEARSAL_VERSION = "v0.0.0-rehearsal"
MECHANISM = "workflow_dispatch+environment-approval"
TIMESTAMP = re.compile(r"^[0-9]{4}-[0-9]{2}-[0-9]{2}T[0-9]{2}:[0-9]{2}:[0-9]{2}Z$")
COUNT = re.compile(r"^(0|[1-9][0-9]{0,5})$")
RUN_ID = re.compile(r"^[1-9][0-9]{0,19}$")


def codeowners_for(codeowners_path, pattern):
    owners = []
    try:
        with open(codeowners_path, encoding="utf-8") as f:
            for line in f:
                fields = line.split("#", 1)[0].split()
                if fields and fields[0] == pattern:
                    owners = [o[1:] for o in fields[1:] if o.startswith("@")]
    except OSError:
        refuse("CODEOWNERS is missing")
    return owners


def _text(value, what, limit=1000):
    if value is None:
        return ""
    if not isinstance(value, str) or len(value) > limit or any(ord(c) < 32 or ord(c) == 127 for c in value):
        refuse("%s must be at most %d printable characters on one line" % (what, limit))
    return value.strip()


def _account(value, what, user_only=True):
    """{login, id} of a GitHub account as GitHub reported it; ids are compared, not only logins."""
    if not isinstance(value, dict):
        refuse("%s is missing" % what)
    login, uid = value.get("login"), value.get("id")
    if not isinstance(login, str) or not LOGIN.match(login) or not isinstance(uid, int) or isinstance(uid, bool) or uid <= 0:
        refuse("%s is malformed" % what)
    if user_only and value.get("type") not in (None, "User"):
        refuse("%s is not a user account (%s)" % (what, value.get("type")))
    return {"login": login, "id": uid}


def is_authority(account, owners=None):
    """The pinned admission authority (login and numeric account ID), and, when CODEOWNERS
    is given, also a code owner of /docs/decisions/ in the dispatched revision."""
    ok = isinstance(account, dict) and ADMISSION_AUTHORITY.get(account.get("login")) == account.get("id") \
        and not isinstance(account.get("id"), bool)
    return ok and (owners is None or account["login"] in owners)


def run_url(run_id, attempt):
    return "https://github.com/%s/actions/runs/%s/attempts/%s" % (REPOSITORY, run_id, attempt)


def parse_request(inputs):
    """The Leader's request, exactly as GitHub recorded the dispatch inputs (untrusted text)."""
    if not isinstance(inputs, dict):
        refuse("the dispatch carries no inputs")
    tag, sha, version = inputs.get("candidate"), inputs.get("candidate_manifest_sha256"), inputs.get("version")
    count, flag = inputs.get("accepted_findings"), inputs.get("accept_critical_or_high")
    if not isinstance(tag, str) or not CANDIDATE_TAG.match(tag):
        refuse("candidate must be a candidate draft tag (candidate-<12 hex>-<run>-<attempt>)")
    if not isinstance(sha, str) or not HEX64.match(sha):
        refuse("candidate_manifest_sha256 must be the 64-hex SHA-256 of the reviewed release-candidate.json")
    if not isinstance(version, str) or not VERSION.match(version):
        refuse("version must be a v<major>.<minor>.<patch>[-pre] tag")
    if not isinstance(count, (str, int)) or isinstance(count, bool) or not COUNT.match(str(count)):
        refuse("accepted_findings must be the number of candidate findings being accepted")
    if flag in (True, "true"):
        flag = True
    elif flag in (None, False, "false"):   # an omitted input accepts nothing more
        flag = False
    else:
        refuse("accept_critical_or_high must be true or false")
    return {"candidate": tag, "candidate_manifest_sha256": sha, "version": version,
            "accepted_findings": int(count), "accept_critical_or_high": flag,
            "note": _text(inputs.get("note"), "note")}


def authenticate_dispatch(env, event, run_record):
    """Who started this run, from three GitHub views that must agree.

    The run must be the first attempt of a workflow_dispatch of release.yml on main in
    this repository: a re-run replays the original dispatch event (and runs with the
    original actor's privileges) and can never make a new decision. The account that
    dispatched it (actor, triggering actor, event sender and the run record's actor
    and triggering actor; login and numeric ID) must be the admission authority.
    """
    env = env if isinstance(env, dict) else {}
    checks = (
        ("GITHUB_REPOSITORY", REPOSITORY, "the run is not in %s" % REPOSITORY),
        ("GITHUB_REPOSITORY_ID", REPOSITORY_ID, "the run's repository ID is not %s's" % REPOSITORY),
        ("GITHUB_EVENT_NAME", "workflow_dispatch", "an admission is made only by a workflow_dispatch of release.yml"),
        ("GITHUB_REF", "refs/heads/main", "an admission is made only from main"),
        ("GITHUB_WORKFLOW_REF", WORKFLOW_REF, "the run is not release.yml on refs/heads/main"),
        ("GITHUB_RUN_ATTEMPT", "1", "a re-run replays an earlier dispatch and cannot admit; dispatch the promote stage again"),
    )
    for key, want, message in checks:
        if env.get(key) != want:
            refuse(message)
    sha, rid = env.get("GITHUB_SHA") or "", env.get("GITHUB_RUN_ID") or ""
    if not REVISION.match(sha) or not RUN_ID.match(rid):
        refuse("the run's commit or run ID is malformed")
    if not isinstance(event, dict) or event.get("ref") != "refs/heads/main" \
            or (event.get("repository") or {}).get("full_name") != REPOSITORY \
            or str((event.get("repository") or {}).get("id")) != REPOSITORY_ID:
        refuse("the event payload does not describe a dispatch on main of this repository")
    sender = _account(event.get("sender"), "the event sender")
    if not isinstance(run_record, dict):
        refuse("the run record is missing")
    actor = _account(run_record.get("actor"), "the run record's actor")
    triggering = _account(run_record.get("triggering_actor"), "the run record's triggering actor")
    repo = run_record.get("repository") or {}
    if run_record.get("id") != int(rid) or run_record.get("run_attempt") != 1 \
            or run_record.get("event") != "workflow_dispatch" or run_record.get("head_branch") != "main" \
            or run_record.get("head_sha") != sha or run_record.get("path") != WORKFLOW_PATH \
            or repo.get("full_name") != REPOSITORY or str(repo.get("id")) != REPOSITORY_ID:
        refuse("GitHub's run record does not match this run (id, attempt, event, branch, commit, workflow or repository)")
    logins = {env.get("GITHUB_ACTOR"), env.get("GITHUB_TRIGGERING_ACTOR"), sender["login"], actor["login"], triggering["login"]}
    ids = {str(env.get("GITHUB_ACTOR_ID")), str(sender["id"]), str(actor["id"]), str(triggering["id"])}
    if len(logins) != 1 or len(ids) != 1:
        refuse("the run's actor, triggering actor and event sender are not one account")
    if not is_authority(sender):
        refuse("only the release admission authority may dispatch an admission; %s is not it" % sender["login"])
    request = parse_request(event.get("inputs"))
    if request["version"] == REHEARSAL_VERSION:
        refuse("the rehearsal version cannot be released")
    return {"request": request, "dispatched_by": sender, "run_id": rid, "run_attempt": "1", "tooling_sha": sha,
            "run_url": run_url(rid, 1)}


# GitHub's run approval record (GET /repos/{owner}/{repo}/actions/runs/{run_id}/approvals)
# defines `comment` as a required string: free text the reviewer typed, which may be
# empty, span lines and hold any Unicode. It authorizes nothing (account, review state
# and environment do), so it is kept verbatim, stored only JSON-escaped in
# admission.json (write_json escapes every non-ASCII and control character) and never
# printed to a log, job summary, step output or release note. Null or absent is
# recorded as an empty comment. Any other type, or text longer than this bound (four
# times the 65,536 characters of a GitHub comment body), is malformed API data.
APPROVAL_COMMENT_MAX = 262144


def approval_comment(value):
    if value is None:
        return ""
    if not isinstance(value, str):
        refuse("GitHub's approval comment is not text (malformed API data)")
    if len(value) > APPROVAL_COMMENT_MAX:
        refuse("GitHub's approval comment exceeds %d characters (malformed API data)" % APPROVAL_COMMENT_MAX)
    return value


def authority_approval(approvals):
    """The admission authority's approval of this run in the admission environment."""
    entries = [a for a in (approvals if isinstance(approvals, list) else [])
               if isinstance(a, dict) and any(isinstance(e, dict) and e.get("name") == ADMISSION_ENVIRONMENT
                                              for e in a.get("environments") or [])]
    if any(a.get("state") != "approved" for a in entries):
        refuse("the run has a review for %s that is not an approval" % ADMISSION_ENVIRONMENT)
    approvers = [_account(a.get("user"), "the approving account") for a in entries]
    if not approvers:
        refuse("GitHub records no approval of this run in the %s environment" % ADMISSION_ENVIRONMENT)
    if not all(is_authority(u) for u in approvers):
        refuse("the run was approved by an account that is not the admission authority")
    return approvers[0], approval_comment(entries[0].get("comment"))


def check_environment(environment, branch_policies, approvals):
    """The environment gate is real: protected for the authority, main only, no bypass, approved.

    A job that names an environment that does not exist creates it unprotected, and an
    unprotected environment lets the job run without anyone's approval. So the rules
    are read back and must hold, and an approval record must exist; otherwise the
    admission fails closed.
    """
    if not isinstance(environment, dict) or environment.get("name") != ADMISSION_ENVIRONMENT:
        refuse("the %s environment does not exist" % ADMISSION_ENVIRONMENT)
    reviewer_ids = sorted({(r.get("reviewer") or {}).get("id")
                           for rule in environment.get("protection_rules") or [] if isinstance(rule, dict)
                           and rule.get("type") == "required_reviewers"
                           for r in rule.get("reviewers") or [] if isinstance(r, dict) and r.get("type") == "User"} - {None})
    if not set(reviewer_ids) & set(ADMISSION_AUTHORITY.values()):
        refuse("the %s environment does not require the admission authority's review; an unprotected "
               "environment is not an approval gate" % ADMISSION_ENVIRONMENT)
    if environment.get("can_admins_bypass") is not False:
        refuse("administrators can bypass the %s environment's protection" % ADMISSION_ENVIRONMENT)
    policy = environment.get("deployment_branch_policy")
    names = sorted((p.get("name"), p.get("type", "branch")) for p in (branch_policies or {}).get("branch_policies") or []
                   if isinstance(p, dict))
    if not isinstance(policy, dict) or policy.get("protected_branches") is not False \
            or policy.get("custom_branch_policies") is not True or names != [("main", "branch")]:
        refuse("the %s environment must accept deployments from the main branch only" % ADMISSION_ENVIRONMENT)
    approver, comment = authority_approval(approvals)
    return {"name": ADMISSION_ENVIRONMENT, "id": environment.get("id"), "required_reviewer_ids": reviewer_ids,
            "branch_policy": "main", "admins_can_bypass": False, "approved_by": approver, "approval_comment": comment}


def rehearsal_dispatch(env, candidate, candidate_sha256):
    """A synthetic request for the pull-request rehearsal: it authenticates nobody."""
    run = candidate.get("run") or {}
    findings = (candidate.get("security") or {}).get("findings") or []
    request = {"candidate": "candidate-%s-%s-%s" % (str(candidate.get("source_revision"))[:12], run.get("id") or 0,
                                                    run.get("attempt") or 1),
               "candidate_manifest_sha256": candidate_sha256, "version": REHEARSAL_VERSION,
               "accepted_findings": len(findings),
               "accept_critical_or_high": any(f.get("severity") in ("CRITICAL", "HIGH") for f in findings), "note": ""}
    rid, attempt = env.get("GITHUB_RUN_ID") or "0", env.get("GITHUB_RUN_ATTEMPT") or "1"
    return {"request": request, "dispatched_by": {"login": env.get("GITHUB_ACTOR") or "local", "id": 0},
            "run_id": rid, "run_attempt": attempt, "tooling_sha": env.get("GITHUB_SHA") or "0" * 40,
            "run_url": run_url(rid, attempt)}


def _candidate_rows(candidate):
    rows = []
    for f in (candidate.get("security") or {}).get("findings") or []:
        if not isinstance(f, dict) or f.get("service") not in SERVICES or f.get("severity") not in SEVERITIES \
                or not isinstance(f.get("id"), str) or not isinstance(f.get("package"), str):
            refuse("the candidate manifest holds a malformed finding")
        rows.append({"service": f["service"], "id": f["id"], "package": f["package"],
                     "installed": f.get("installed") or "", "severity": f["severity"]})
    keys = [(r["service"], r["id"], r["package"], r["installed"]) for r in rows]
    if len(set(keys)) != len(keys):
        refuse("the candidate manifest lists a finding twice")
    return sorted(rows, key=lambda r: (r["service"], SEVERITIES.index(r["severity"]), r["id"], r["package"], r["installed"]))


def bind_candidate(candidate, candidate_sha256, request, rehearsal):
    """Check the Leader's request against the attested candidate; return what is admitted."""
    if not isinstance(candidate, dict) or candidate.get("schema") != CANDIDATE_SCHEMA:
        refuse("the candidate manifest has an unexpected schema")
    run = candidate.get("run") or {}
    if rehearsal:
        if run.get("rehearsal") is not True:
            refuse("a rehearsal admission cannot promote a non-rehearsal candidate")
        if request["version"] != REHEARSAL_VERSION:
            refuse("a rehearsal admission promotes only %s" % REHEARSAL_VERSION)
    else:
        if run.get("rehearsal") is not False or candidate.get("repository") != REPOSITORY:
            refuse("only a candidate from a dispatched release run of %s can be promoted" % REPOSITORY)
        if (candidate.get("source_qualification") or {}).get("verified") is not True:
            refuse("the candidate's source revision checks were not verified")
        if request["version"] == REHEARSAL_VERSION:
            refuse("the rehearsal version cannot be released")
    if request["candidate_manifest_sha256"] != candidate_sha256:
        refuse("the candidate manifest is not the one the Leader reviewed (candidate_manifest_sha256 differs)")
    revision = candidate.get("source_revision")
    if not isinstance(revision, str) or not REVISION.match(revision):
        refuse("the candidate's source revision is malformed")
    if request["candidate"] != "candidate-%s-%s-%s" % (revision[:12], run.get("id"), run.get("attempt")):
        refuse("the candidate tag does not name the run and revision recorded in the candidate manifest")
    if not isinstance(candidate.get("public_origin"), str) or not ORIGIN.match(candidate["public_origin"]):
        refuse("the candidate's public origin is malformed")
    archive = (candidate.get("archive") or {}).get("sha256")
    if not isinstance(archive, str) or not HEX64.match(archive):
        refuse("the candidate's archive digest is malformed")
    images = {}
    for s in SERVICES:
        entry = (candidate.get("images") or {}).get(s) or {}
        if not all(isinstance(entry.get(k), str) and DIGEST.match(entry[k]) for k in ("containerd", "graphdriver")):
            refuse("the candidate's %s identities are malformed" % s)
        images[s] = {"containerd": entry["containerd"], "graphdriver": entry["graphdriver"]}
    coverage = (candidate.get("security") or {}).get("coverage")
    if not isinstance(coverage, dict) or set(coverage) != set(SERVICES):
        refuse("the candidate records no scan coverage for every image; promotion could not compare its rescan")
    rows = _candidate_rows(candidate)
    if request["accepted_findings"] != len(rows):
        refuse("the Leader acknowledged %d finding(s); the candidate reports %d" % (request["accepted_findings"], len(rows)))
    severe = [r for r in rows if r["severity"] in ("CRITICAL", "HIGH")]
    if severe and request["accept_critical_or_high"] is not True:
        refuse("the candidate reports %d CRITICAL/HIGH finding(s) and accept_critical_or_high is false" % len(severe))
    return {"revision": revision, "archive": archive, "images": images, "rows": rows}


def decide_admission(candidate, candidate_sha256, dispatch, approval, owners, rehearsal, decided_at):
    """The admission record for this run: the Leader's request bound to the attested candidate."""
    request = dispatch["request"]
    bound = bind_candidate(candidate, candidate_sha256, request, rehearsal)
    if rehearsal:
        decided_by = {"login": "rehearsal-ci", "id": 0}
        authorization = {"mechanism": "rehearsal", "repository": candidate.get("repository"),
                         "run_id": dispatch["run_id"], "run_attempt": dispatch["run_attempt"],
                         "run_url": dispatch["run_url"], "tooling_sha": dispatch["tooling_sha"],
                         "dispatched_by": dispatch["dispatched_by"], "approved_by": decided_by, "request": request}
        record_id = "REHEARSAL-%s-%s" % (dispatch["run_id"], dispatch["run_attempt"])
    else:
        if not is_authority(dispatch["dispatched_by"], owners):
            refuse("the dispatching account is not the admission authority and a code owner of /docs/decisions/")
        decided_by = (approval or {}).get("approved_by")
        if not is_authority(decided_by, owners):
            refuse("the approving account is not the admission authority and a code owner of /docs/decisions/")
        authorization = {"mechanism": MECHANISM, "repository": REPOSITORY, "repository_id": REPOSITORY_ID,
                         "workflow_ref": WORKFLOW_REF, "event": "workflow_dispatch",
                         "run_id": dispatch["run_id"], "run_attempt": dispatch["run_attempt"],
                         "run_url": dispatch["run_url"], "tooling_sha": dispatch["tooling_sha"],
                         "dispatched_by": dispatch["dispatched_by"], "approved_by": decided_by,
                         "environment": {k: approval[k] for k in ("name", "id", "required_reviewer_ids",
                                                                  "branch_policy", "admins_can_bypass")},
                         "approval_comment": approval["approval_comment"], "request": request}
        record_id = "CM-REL-ADMISSION-%s-%s" % (dispatch["run_id"], dispatch["run_attempt"])
    counts = count_findings(bound["rows"])
    record = {
        "schema": ADMISSION_SCHEMA,
        "record_id": record_id,
        "decision": "ADMITTED",
        "decided_by": decided_by,
        "decided_at": decided_at,
        "scope": ("Security admission of exactly the five linux/amd64 image identities of candidate %s (source "
                  "revision %s, candidate manifest SHA-256 %s, archive SHA-256 %s), accepting exactly the %d listed "
                  "findings of that candidate at their installed versions and severities, and its promotion to the "
                  "draft release %s. No other candidate, image identity or finding is admitted."
                  % (request["candidate"], bound["revision"], candidate_sha256, bound["archive"], len(bound["rows"]),
                     request["version"])),
        "statement": request["note"],
        "source_revision": bound["revision"],
        "public_origin": candidate["public_origin"],
        "candidate": {"release_tag": request["candidate"], "manifest_sha256": candidate_sha256,
                      "archive_sha256": bound["archive"],
                      "run": {"id": str((candidate.get("run") or {}).get("id")),
                              "attempt": str((candidate.get("run") or {}).get("attempt"))}},
        "images": bound["images"],
        "accepted_findings": bound["rows"],
        "accepted_counts": counts,
        "release": {"version": request["version"]},
        "not_granted": list(REQUIRED_NOT_GRANTED),
        "authorization": authorization,
    }
    if rehearsal:
        record["rehearsal"] = True
    return record


def verify_admission(admission, candidate, candidate_sha256, owners=None, rehearsal=False, expected_run=None):
    """Re-derive the admission from its own recorded evidence and the candidate; any difference refuses.

    expected_run ({run_id, run_attempt, tooling_sha}) binds the record to the run that
    uses it: promotion accepts only the admission its own run made, and a release
    package only the admission of the run that promoted it, so a record never carries
    over to another run, candidate or version.
    """
    if not isinstance(admission, dict) or set(admission) - ADMISSION_KEYS:
        refuse("admission record has unknown fields")
    missing = (ADMISSION_KEYS - {"rehearsal"}) - set(admission)
    if missing:
        refuse("admission record lacks: %s" % ", ".join(sorted(missing)))
    if admission["schema"] != ADMISSION_SCHEMA:
        refuse("admission record has an unexpected schema")
    if admission["decision"] != "ADMITTED":
        refuse("admission decision is %r; only ADMITTED permits promotion" % (admission["decision"],))
    record_id = admission["record_id"]
    if not isinstance(record_id, str) or not RECORD_ID.match(record_id):
        refuse("admission record_id is malformed")
    if rehearsal:
        if admission.get("rehearsal") is not True or not record_id.startswith("REHEARSAL-"):
            refuse("a rehearsal accepts only a REHEARSAL- record marked rehearsal: true")
    elif "rehearsal" in admission or record_id.startswith("REHEARSAL-"):
        refuse("rehearsal records cannot promote a release")
    if not isinstance(admission["decided_at"], str) or not TIMESTAMP.match(admission["decided_at"]):
        refuse("admission decided_at must be a UTC timestamp")
    auth = admission["authorization"]
    if not isinstance(auth, dict):
        refuse("admission authorization is malformed")
    if not isinstance(admission["not_granted"], list) or not set(REQUIRED_NOT_GRANTED).issubset(admission["not_granted"]):
        refuse("admission not_granted must name %s" % ", ".join(REQUIRED_NOT_GRANTED))
    dispatch = {k: auth.get(k) for k in ("request", "dispatched_by", "run_id", "run_attempt", "tooling_sha", "run_url")}
    if not isinstance(dispatch["request"], dict):
        refuse("admission request is malformed")
    request = parse_request(dispatch["request"])
    if request != dispatch["request"]:
        refuse("admission request is not in its normalized form")
    if not isinstance(dispatch["run_id"], str) or not RUN_ID.match(dispatch["run_id"]) \
            or not isinstance(dispatch["tooling_sha"], str) or not REVISION.match(dispatch["tooling_sha"]) \
            or dispatch["run_url"] != run_url(dispatch["run_id"], dispatch["run_attempt"]):
        refuse("admission run identity is malformed")
    approval = None
    if not rehearsal:
        if auth.get("mechanism") != MECHANISM or auth.get("repository") != REPOSITORY \
                or auth.get("repository_id") != REPOSITORY_ID or auth.get("workflow_ref") != WORKFLOW_REF \
                or auth.get("event") != "workflow_dispatch" or dispatch["run_attempt"] != "1":
            refuse("admission was not made by a first-attempt dispatch of release.yml on main of %s" % REPOSITORY)
        env = auth.get("environment")
        if not isinstance(env, dict) or env.get("name") != ADMISSION_ENVIRONMENT or env.get("branch_policy") != "main" \
                or env.get("admins_can_bypass") is not False \
                or not set(env.get("required_reviewer_ids") or []) & set(ADMISSION_AUTHORITY.values()):
            refuse("admission was not approved through the protected %s environment" % ADMISSION_ENVIRONMENT)
        if not is_authority(auth.get("approved_by")) or admission["decided_by"] != auth.get("approved_by"):
            refuse("admission decided_by is not the admission authority that approved the run")
        if approval_comment(auth.get("approval_comment")) != auth.get("approval_comment"):
            refuse("admission approval_comment is not in its recorded form")
        approval = dict(env, approved_by=auth.get("approved_by"), approval_comment=auth.get("approval_comment"))
    if expected_run is not None:
        for key in ("run_id", "run_attempt", "tooling_sha"):
            if str(expected_run.get(key)) != dispatch[key]:
                refuse("the admission was made by another run (%s differs); an admission is valid only for its own run" % key)
    rebuilt = decide_admission(candidate, candidate_sha256, dispatch, approval, owners, rehearsal, admission["decided_at"])
    for key in sorted(ADMISSION_KEYS):
        if rebuilt.get(key) != admission.get(key):
            refuse("admission %s does not match the candidate and the recorded decision" % key)
    return {"record_id": record_id, "version": request["version"], "decided_by": admission["decided_by"],
            "accepted": len(admission["accepted_findings"])}


def check_attestation_run(results, expected_url, trigger="workflow_dispatch"):
    """At least one verified attestation was signed by exactly the expected run of release.yml on main."""
    for r in results if isinstance(results, list) else []:
        cert = (((r or {}).get("verificationResult") or {}).get("signature") or {}).get("certificate") or {}
        if cert.get("runInvocationURI") == expected_url and cert.get("buildTrigger") == trigger \
                and cert.get("buildSignerURI") == "https://github.com/" + WORKFLOW_REF \
                and cert.get("sourceRepositoryIdentifier") == REPOSITORY_ID \
                and cert.get("runnerEnvironment") == "github-hosted":
            return cert
    refuse("no verified attestation was signed by run %s (%s of release.yml on main)" % (expected_url, trigger))


def check_run_record(admission, run_record, approvals):
    """Online: GitHub still records the dispatch and approval the admission claims."""
    auth = admission.get("authorization") or {}
    if not isinstance(run_record, dict) or str(run_record.get("id")) != auth.get("run_id") \
            or run_record.get("run_attempt") != 1 or run_record.get("event") != "workflow_dispatch" \
            or run_record.get("head_branch") != "main" or run_record.get("head_sha") != auth.get("tooling_sha") \
            or run_record.get("path") != WORKFLOW_PATH \
            or str((run_record.get("repository") or {}).get("id")) != REPOSITORY_ID:
        refuse("GitHub's record of the admission run differs from the admission")
    for key in ("actor", "triggering_actor"):
        if _account(run_record.get(key), "the run's " + key) != auth.get("dispatched_by"):
            refuse("GitHub records another account as the run's %s" % key)
    approver, _ = authority_approval(approvals)
    if approver != admission.get("decided_by"):
        refuse("GitHub records another approver of the admission run")
    return {"run": auth.get("run_url"), "dispatched_by": auth.get("dispatched_by"), "approved_by": approver}


def check_rescan(admitted, findings):
    """Promotion-time findings the admission does not cover, with the reason.

    A rescan row is covered only when the admission accepted the same
    {service, id, package} at the same installed version, and the rescan reports it
    at the same or a lower severity. An admission accepts exactly its candidate's
    findings and has no other way to accept a row, so a new advisory, a newly
    matched installed version or a severity raised since the candidate scan
    (including any reclassification of UNKNOWN) makes this candidate unpromotable:
    a new candidate, then a new admission, is needed.
    """
    rows = {}
    for f in admitted:
        rows[(f["service"], f["id"], f["package"], f.get("installed") or "")] = SEVERITIES.index(f["severity"])
    keys = {k[:3] for k in rows}
    problems = set()
    for f in findings:
        key = (f["service"], f["id"], f["package"])
        row = key + (f.get("installed") or "",)
        if key not in keys:
            problems.add(key + ("not accepted",))
        elif row not in rows:
            problems.add(key + ("installed version %s was not in the admitted candidate scan" % (row[3] or "(none)"),))
        elif SEVERITIES.index(f["severity"]) < rows[row]:
            problems.add(key + ("severity raised to %s since the admitted candidate scan" % f["severity"],))
    return sorted(problems)


# --------------------------------------------------------------------------
# CLI

def cmd_archive_identities(a):
    print(json.dumps(archive_identities(a.archive, a.ref), indent=2, sort_keys=True))


def _workflow_files(path):
    try:
        with open(path, encoding="utf-8") as f:
            names = [line.strip() for line in f if line.strip()]
    except (OSError, UnicodeDecodeError):
        refuse("the revision's workflow file list is missing or unreadable")
    if not names or not all(WORKFLOW_FILE.match(n) for n in names):
        refuse("the revision's workflow file list is empty or malformed")
    return names


def _jobs_file(directory, name, what):
    path = os.path.join(directory, name)
    return api_items(load_json(path, what), "jobs", what) if os.path.isfile(path) else None


def cmd_evaluate_checks(a):
    if not REVISION.match(a.revision):
        refuse("--revision must be a full commit SHA")
    rules = load_json(a.rules, "branch rules")
    pr = merged_pull_request(load_json(a.pulls, "pull requests"), a.revision) if a.pulls else None
    pr_runs = pr_runs_after = None
    if pr is not None:
        if not a.pr_check_runs or (pr.get("head") or {}).get("sha") != a.pr_head or not REVISION.match(a.pr_head or ""):
            refuse("the merged pull request's head check runs are required and must match its head")
        # Evidence from the head counts only for the same content: with squash merges
        # and up-to-date branches the head's tree is the revision's tree.
        if not (a.revision_tree and a.pr_head_tree and HEX40.match(a.revision_tree) and a.pr_head_tree == a.revision_tree):
            refuse("the merged pull request's head tree differs from the revision's tree")
        pr_runs = api_items(load_json(a.pr_check_runs, "pull request check runs"), "check_runs", "pull request check runs")
        if not a.pr_check_runs_after:
            refuse("the merged pull request's head check runs must be read again (--pr-check-runs-after)")
        pr_runs_after = api_items(load_json(a.pr_check_runs_after, "pull request check runs (re-read)"), "check_runs",
                                  "pull request check runs (re-read)")
    runs = api_items(load_json(a.workflow_runs, "workflow runs"), "workflow_runs", "workflow runs")
    after = api_items(load_json(a.workflow_runs_after, "workflow runs (re-read)"), "workflow_runs", "workflow runs (re-read)")
    actions = {"workflow_files": _workflow_files(a.workflow_files), "runs": runs, "runs_after": after,
               "attempt_jobs": {}, "all_jobs": {}}
    for run in runs:
        actions["attempt_jobs"][run["id"]] = _jobs_file(a.jobs_dir, "%d.attempt.json" % run["id"], "jobs of run %d" % run["id"])
        actions["all_jobs"][run["id"]] = _jobs_file(a.jobs_dir, "%d.all.json" % run["id"], "jobs of run %d" % run["id"])
    check_runs = api_items(load_json(a.check_runs, "check runs"), "check_runs", "check runs")
    check_runs_after = api_items(load_json(a.check_runs_after, "check runs (re-read)"), "check_runs", "check runs (re-read)")
    result = evaluate_checks(a.revision, rules, check_runs, actions, pr, pr_runs, check_runs_after, pr_runs_after)
    for entry in result["required_checks"]:
        if "pull_request" in entry:
            entry["pull_request"].update(head_tree=a.pr_head_tree, revision_tree=a.revision_tree)
    print(json.dumps(result, indent=2, sort_keys=True))


def cmd_findings(a):
    findings, by_service = [], {}
    for spec in a.report:
        service, _, path = spec.partition("=")
        if service not in SERVICES or not path:
            refuse("--report must be SERVICE=PATH for a known service")
        findings.extend(normalize_findings(service, [path]))
        by_service.setdefault(service, []).append(path)
    if set(by_service) != set(SERVICES):
        refuse("every service needs scanner reports")
    if a.coverage_output:
        write_json(a.coverage_output, {s: scan_coverage(by_service[s]) for s in SERVICES})
    merged = {}
    for f in findings:
        key = (f["service"], f["id"], f["package"], f["installed"])
        if key not in merged or SEVERITIES.index(f["severity"]) < SEVERITIES.index(merged[key]["severity"]):
            merged[key] = f
    out = sorted(merged.values(), key=lambda r: (r["service"], SEVERITIES.index(r["severity"]), r["id"], r["package"]))
    write_json(a.output, out)
    counts = count_findings(out)
    for s in SERVICES:
        print("%-10s %s" % (s, " ".join("%s=%d" % (sev, counts[s][sev]) for sev in SEVERITIES)))


def cmd_migrations(a):
    current = migration_inventory(a.directory)
    result = {"inventory": current, "baseline": None}
    if a.baseline_directory:
        baseline = migration_inventory(a.baseline_directory)
        compat = migration_compatibility(current, baseline)
        result["baseline"] = {"revision": a.baseline_revision, "added": compat["added"], "problems": compat["problems"]}
        if compat["problems"]:
            write_json(a.output, result)
            refuse("migration baseline incompatibility: " + "; ".join(compat["problems"]))
    write_json(a.output, result)


def cmd_candidate_manifest(a):
    ctx = load_json(a.context, "candidate context")
    ids = load_json(a.identities, "candidate identities")
    findings = load_json(a.findings, "findings")
    migrations = load_json(a.migrations, "migrations")
    qualification = load_json(a.qualification, "qualification results")
    scanner = load_json(a.scanner, "scanner metadata")
    coverage = load_json(a.coverage, "scan coverage")
    checks = load_json(a.source_checks, "source checks") if a.source_checks else None
    if checks is not None and (not isinstance(checks, dict) or checks.get("schema") != SOURCE_CHECKS_SCHEMA
                               or checks.get("source_revision") != ctx["source_revision"]
                               or not isinstance(checks.get("required_checks"), list) or not checks["required_checks"]):
        refuse("the source check evidence is not a %s record of this candidate's source revision" % SOURCE_CHECKS_SCHEMA)
    images = {}
    for s in SERVICES:
        ref = ctx["candidate_refs"][s]
        if ref not in ids:
            refuse("identities lack %s" % s)
        images[s] = dict(ids[ref], candidate_ref=ref, release_ref=RELEASE_REFS[s],
                         sbom="%s.spdx.json" % s,
                         reports=["%s.trivy.json" % s, "%s.trivy-comprehensive.json" % s])
    for q in qualification:
        if q.get("result") != "PASS":
            refuse("qualification %s did not pass" % q.get("id"))
    manifest = {
        "schema": CANDIDATE_SCHEMA,
        "repository": ctx["repository"],
        "source_revision": ctx["source_revision"],
        "source_tree": ctx["source_tree"],
        "public_origin": ctx["public_origin"],
        "tooling": ctx["tooling"],
        "run": ctx["run"],
        "builder": ctx["builder"],
        "created_at": utc_now(),
        "images": images,
        "archive": {"name": a.archive_name, "sha256": sha256_file(os.path.join(a.package, a.archive_name)),
                    "size": os.path.getsize(os.path.join(a.package, a.archive_name))},
        "mc_source": ctx["mc_source"],
        "security": {"scanner": scanner, "findings": findings, "counts": count_findings(findings), "coverage": coverage,
                     "policy": "Findings are recorded, not accepted. Only a Leader admission record may accept them."},
        "qualification": qualification,
        "source_qualification": dict(checks, verified=True) if checks is not None else {"verified": False, "required_checks": []},
        "compatibility": dict(ctx["compatibility"], migrations=migrations),
        "status": {"candidate": "QUALIFIED", "security_admission": "PENDING_LEADER_ADMISSION",
                   "promotion": "LOCKED_UNTIL_ADMITTED", "deployment": "NOT_AUTHORIZED_BY_THIS_PIPELINE"},
    }
    if ctx["run"].get("rehearsal") is not (checks is None):
        refuse("only rehearsals may skip source check verification")
    manifest["files"] = file_map(a.package, {"release-candidate.json", "SHA256SUMS"})
    write_json(os.path.join(a.package, "release-candidate.json"), manifest)
    write_sha256sums(a.package)


RUNNER_ENV = ("GITHUB_REPOSITORY", "GITHUB_REPOSITORY_ID", "GITHUB_EVENT_NAME", "GITHUB_REF", "GITHUB_WORKFLOW_REF",
              "GITHUB_SHA", "GITHUB_RUN_ID", "GITHUB_RUN_ATTEMPT", "GITHUB_ACTOR", "GITHUB_ACTOR_ID",
              "GITHUB_TRIGGERING_ACTOR")


def runner_env():
    """The run's identity as the GitHub runner sets it (one of three views compared)."""
    return {k: os.environ.get(k) for k in RUNNER_ENV}


def cmd_admission_request(a):
    if a.rehearsal:
        if not a.candidate_manifest:
            refuse("a rehearsal request is derived from --candidate-manifest")
        result = rehearsal_dispatch(runner_env(), load_json(a.candidate_manifest, "candidate manifest"),
                                    sha256_file(a.candidate_manifest))
    else:
        if not (a.event and a.run_record):
            refuse("--event and --run-record are required")
        result = authenticate_dispatch(runner_env(), load_json(a.event, "event payload"),
                                       load_json(a.run_record, "run record"))
    write_json(a.output, result)
    r = result["request"]
    print("Admission request by %s: candidate %s, manifest %s, version %s, %d finding(s) acknowledged%s."
          % (result["dispatched_by"]["login"], r["candidate"], r["candidate_manifest_sha256"], r["version"],
             r["accepted_findings"], ", CRITICAL/HIGH accepted" if r["accept_critical_or_high"] else ""))


def cmd_admission_approval(a):
    result = check_environment(load_json(a.environment, "environment"), load_json(a.branch_policies, "branch policies"),
                               load_json(a.approvals, "run approvals"))
    write_json(a.output, result)
    print("Run approved in the protected %s environment by %s (account %d)."
          % (result["name"], result["approved_by"]["login"], result["approved_by"]["id"]))


def cmd_admission_preview(a):
    dispatch = load_json(a.dispatch, "admission request")
    candidate = load_json(a.candidate_manifest, "candidate manifest")
    request = dispatch["request"]
    bound = bind_candidate(candidate, sha256_file(a.candidate_manifest), request, False)
    counts = count_findings(bound["rows"])
    out = ["## Security admission request: %s -> %s" % (request["candidate"], request["version"]), "",
           "Requested by **%s**. Approving the pending `%s` deployment of this run is the security admission of "
           "exactly the following; rejecting it, or letting it expire, admits nothing." % (
               dispatch["dispatched_by"]["login"], ADMISSION_ENVIRONMENT), "",
           "| Field | Value |", "| --- | --- |",
           "| Candidate draft | `%s` |" % request["candidate"],
           "| Candidate manifest SHA-256 | `%s` (verified against the request) |" % request["candidate_manifest_sha256"],
           "| Candidate archive SHA-256 | `%s` |" % bound["archive"],
           "| Source revision | `%s` |" % bound["revision"],
           "| Public origin | `%s` |" % candidate["public_origin"],
           "| Release version | `%s` (not yet used) |" % request["version"],
           "| Findings accepted | %d (CRITICAL/HIGH %s) |" % (len(bound["rows"]),
                                                            "accepted" if request["accept_critical_or_high"] else "none allowed"),
           "| Leader note | %s |" % (request["note"].replace("|", "/") or "(none)"), "",
           "| Service | containerd identity | graph-driver identity |", "| --- | --- | --- |"]
    for s in SERVICES:
        out.append("| %s | `%s` | `%s` |" % (s, bound["images"][s]["containerd"], bound["images"][s]["graphdriver"]))
    out += ["", "| Findings | " + " | ".join(SEVERITIES) + " |", "| --- |" + " --- |" * len(SEVERITIES)]
    for s in SERVICES:
        out.append("| %s | " % s + " | ".join(str(counts[s][sev]) for sev in SEVERITIES) + " |")
    out += ["", "<details><summary>All %d findings to be accepted</summary>" % len(bound["rows"]), "",
            "| Service | Severity | Advisory | Package | Installed |", "| --- | --- | --- | --- | --- |"]
    for r in bound["rows"]:
        out.append("| %s | %s | `%s` | `%s` | `%s` |" % tuple(str(r[k]).replace("|", "/")[:80]
                                                         for k in ("service", "severity", "id", "package", "installed")))
    out += ["", "</details>", "",
            "**Verified by this job:** it downloaded only `release-candidate.json` and its Sigstore bundle, "
            "verified the manifest's attestation (signer `release.yml` on `refs/heads/main` at the tooling commit "
            "the manifest names, GitHub-hosted runner), checked that the manifest's SHA-256 equals "
            "`candidate_manifest_sha256` in your request, that the candidate tag, finding count and CRITICAL/HIGH "
            "choice match the manifest, and that the version is unused.",
            "",
            "**Not re-checked by this job (prior candidate evidence):** the source revision's required checks, the "
            "image qualification, the scans and the identities, findings and coverage above are shown as the "
            "attested candidate manifest records them. The archive, SBOMs, scan reports and other package files "
            "were not downloaded here.",
            "",
            "**Deferred to the protected promotion job, after your approval and before anything is promoted:** it "
            "downloads the whole candidate package, verifies every file against `SHA256SUMS` and the manifest and "
            "the attestations of the archive and `mc-source.tar.gz`, re-derives the admission, checks the image "
            "identities, rescans the images (any new, re-versioned or escalated finding of any severity, or any loss "
            "of scan coverage, stops promotion) and runs `deploy.sh` on the release archive. Not granted: %s."
            % ", ".join(REQUIRED_NOT_GRANTED)]
    print("\n".join(out))


def cmd_admission_decide(a):
    dispatch = load_json(a.dispatch, "admission request")
    candidate = load_json(a.candidate_manifest, "candidate manifest")
    candidate_sha256 = sha256_file(a.candidate_manifest)
    if a.rehearsal:
        approval, owners = None, None
    else:
        if not (a.approval and a.codeowners):
            refuse("--approval and --codeowners are required")
        approval, owners = load_json(a.approval, "approval"), codeowners_for(a.codeowners, "/docs/decisions/")
    record = decide_admission(candidate, candidate_sha256, dispatch, approval, owners, a.rehearsal, utc_now())
    verify_admission(record, candidate, candidate_sha256, owners, a.rehearsal,
                     {k: dispatch[k] for k in ("run_id", "run_attempt", "tooling_sha")})
    write_json(a.output, record)
    print("Admission %s: %s admitted candidate %s for %s, accepting %d finding(s)."
          % (record["record_id"], record["decided_by"]["login"], record["candidate"]["release_tag"],
             record["release"]["version"], len(record["accepted_findings"])))


def cmd_verify_admission(a):
    admission = load_json(a.admission, "admission record")
    candidate = load_json(a.candidate_manifest, "candidate manifest")
    owners = codeowners_for(a.codeowners, "/docs/decisions/") if a.codeowners else None
    run = (a.run_id, a.run_attempt, a.tooling_sha)
    if any(run) and not all(run):
        refuse("--run-id, --run-attempt and --tooling-sha go together")
    expected = dict(zip(("run_id", "run_attempt", "tooling_sha"), run)) if all(run) else None
    if not a.rehearsal and expected is None:
        refuse("a real admission is verified against the run that uses it (--run-id, --run-attempt, --tooling-sha)")
    result = verify_admission(admission, candidate, sha256_file(a.candidate_manifest), owners, a.rehearsal, expected)
    print(json.dumps(result, indent=2, sort_keys=True))


def cmd_check_attestation_run(a):
    cert = check_attestation_run(load_json(a.verification, "attestation verification"), a.run_url, a.trigger)
    print("Signed by %s (%s)." % (cert["runInvocationURI"], cert["buildTrigger"]))


def cmd_check_run_record(a):
    result = check_run_record(load_json(a.admission, "admission record"), load_json(a.run_record, "run record"),
                              load_json(a.approvals, "run approvals"))
    print("GitHub records run %s dispatched by %s and approved by %s."
          % (result["run"], result["dispatched_by"]["login"], result["approved_by"]["login"]))


def cmd_check_rescan(a):
    admission = load_json(a.admission, "admission record")
    candidate = load_json(a.candidate_manifest, "candidate manifest")
    findings = load_json(a.findings, "rescan findings")
    unaccepted = check_rescan(admission["accepted_findings"], findings)
    lost = check_coverage(candidate["security"].get("coverage"), load_json(a.rescan_coverage, "rescan coverage"))
    for service, vid, pkg, reason in unaccepted:
        print("NOT ACCEPTED: %s %s %s (%s)" % (service, vid, pkg, reason), file=sys.stderr)
    for service, reason in lost:
        print("COVERAGE LOST: %s (%s)" % (service, reason), file=sys.stderr)
    if lost:
        refuse("the promotion rescan analysed less than the candidate scan for %d image(s); its findings cannot show "
               "the admission still holds: resolve the scanner change, then build a new candidate and admit it" % len(lost))
    if unaccepted:
        refuse("%d promotion-time finding(s) are not covered by the admission record; this candidate cannot be promoted: "
               "build a new candidate and admit it with a new admission record" % len(unaccepted))
    print("Every promotion-time finding is covered by the admission record; the rescan covered every target the candidate scan did.")


def cmd_release_manifest(a):
    ctx = load_json(a.context, "release context")
    candidate = load_json(os.path.join(a.package, "release-candidate.json"), "candidate manifest")
    admission = load_json(os.path.join(a.package, "admission.json"), "admission record")
    ids = load_json(a.identities, "release identities")
    rescan = load_json(a.findings, "rescan findings")
    scanner = load_json(a.scanner, "scanner metadata")
    images = {}
    for s in SERVICES:
        got = ids.get(RELEASE_REFS[s])
        want = candidate["images"][s]
        if not got or got["containerd"] != want["containerd"] or got["graphdriver"] != want["graphdriver"]:
            refuse("promoted %s identity differs from the admitted candidate" % s)
        images[s] = {"release_ref": RELEASE_REFS[s], "containerd": got["containerd"],
                     "graphdriver": got["graphdriver"], "platform": "linux/amd64",
                     "candidate_ref": want["candidate_ref"], "sbom": "%s.spdx.json" % s,
                     "rescan_reports": ["%s.trivy-rescan.json" % s, "%s.trivy-rescan-comprehensive.json" % s]}
    manifest = {
        "schema": RELEASE_SCHEMA,
        "version": admission["release"]["version"],
        "repository": candidate["repository"],
        "source_revision": candidate["source_revision"],
        "source_tree": candidate["source_tree"],
        "public_origin": candidate["public_origin"],
        "tooling": ctx["tooling"],
        "run": ctx["run"],
        "created_at": utc_now(),
        "candidate": {"release_tag": admission["candidate"]["release_tag"],
                      "manifest_sha256": admission["candidate"]["manifest_sha256"],
                      "archive_sha256": candidate["archive"]["sha256"],
                      "run": candidate["run"]},
        "admission": {"record_id": admission["record_id"], "decided_by": admission["decided_by"],
                      "decided_at": admission["decided_at"], "scope": admission["scope"],
                      "mechanism": admission["authorization"]["mechanism"],
                      "dispatched_by": admission["authorization"]["dispatched_by"],
                      "run_url": admission["authorization"]["run_url"],
                      "file": "admission.json", "sha256": sha256_file(os.path.join(a.package, "admission.json")),
                      "seal": "admission.sigstore.json" if os.path.isfile(os.path.join(a.package, "admission.sigstore.json")) else None},
        "images": images,
        "archive": {"name": a.archive_name, "sha256": sha256_file(os.path.join(a.package, a.archive_name)),
                    "size": os.path.getsize(os.path.join(a.package, a.archive_name))},
        "expected_images": {"containerd": "expected-images.containerd.txt",
                            "graphdriver": "expected-images.graphdriver.txt"},
        "mc_source": candidate["mc_source"],
        "security": {"rescan_scanner": scanner, "rescan_findings": rescan,
                     "rescan_counts": count_findings(rescan), "rescan_coverage": load_json(a.coverage, "scan coverage"),
                     "candidate_scanner": candidate["security"]["scanner"]},
        "verification": ctx["verification"],
        "compatibility": candidate["compatibility"],
        "update_requirements": {
            "deploy_script": "Use infrastructure/vm/deploy.sh and the Compose files from a checkout of source_revision; their SHA-256 values are in compatibility.",
            "existing_state": [
                "--recovery-artifact-verified: an independently checked, restorable pre-update recovery point in approved custody (ADR009 sections 3, 9.5)",
                "--schema-image-compatibility-verified: compatibility.migrations against the target's flyway_schema_history",
                "--persistent-state-preserved: the existing PostgreSQL and MinIO volume bindings",
                "--update-authorization: the operator/update authority reference",
            ],
            "not_verified_by_release": [
                "the target VM's deployed revision, image identities, volumes and Flyway history",
                "the existence or restorability of a recovery point",
                "VM capacity, TLS, packet filtering and operator custody",
            ],
        },
        "status": {"candidate": "QUALIFIED", "security_admission": "ADMITTED (%s by %s)" % (admission["record_id"], admission["decided_by"]["login"]),
                   "promotion": "PROMOTED_TO_DRAFT_RELEASE", "publication": "DRAFT_ONLY",
                   "deployment": "NOT_AUTHORIZED_BY_THIS_PIPELINE"},
    }
    manifest["files"] = file_map(a.package, {"release.json", "SHA256SUMS"})
    write_json(os.path.join(a.package, "release.json"), manifest)
    write_sha256sums(a.package)


def cmd_verify_package(a):
    manifest = verify_package(a.directory, a.kind, tuple(a.allow_extra or ()))
    print("Package verified: %d files match %s and SHA256SUMS; source %s."
          % (len(manifest["files"]) + 1, "release-candidate.json" if a.kind == "candidate" else "release.json",
             manifest["source_revision"]))


def verify_release_chain(directory, repository):
    """Offline consistency of a release package: manifest, admission and candidate agree.

    The admission is re-derived from its own evidence and the bundled candidate
    manifest, must be the decision of the pinned admission authority made through the
    protected environment, and must have been made by the run that promoted the
    package (so it never carries over from another run, candidate or version).
    GitHub's account authentication itself is not checkable offline: the online
    checks are verify-release.sh's run-record and approval checks.
    """
    manifest = verify_package(directory, "release", ("release.sigstore.json",))
    if manifest.get("repository") != repository or repository != REPOSITORY:
        refuse("release.json names a different repository")
    if (manifest.get("run") or {}).get("rehearsal") is not False:
        refuse("release.json is not from a dispatched release run")
    admission = load_json(os.path.join(directory, "admission.json"), "admission.json")
    candidate_path = os.path.join(directory, "release-candidate.json")
    candidate = load_json(candidate_path, "release-candidate.json")
    if manifest["admission"]["sha256"] != sha256_file(os.path.join(directory, "admission.json")):
        refuse("admission.json does not match release.json")
    if manifest["candidate"]["manifest_sha256"] != sha256_file(candidate_path):
        refuse("release-candidate.json is not the admitted candidate manifest")
    verify_admission(admission, candidate, sha256_file(candidate_path), None, False,
                     {"run_id": (manifest.get("run") or {}).get("id"),
                      "run_attempt": (manifest.get("run") or {}).get("attempt"),
                      "tooling_sha": (manifest.get("tooling") or {}).get("sha")})
    if manifest["admission"].get("seal") != "admission.sigstore.json" or "admission.sigstore.json" not in manifest["files"]:
        refuse("the package lacks the admission's own attestation bundle (admission.sigstore.json)")
    for key in ("record_id", "decided_by", "decided_at", "scope"):
        if manifest["admission"].get(key) != admission[key]:
            refuse("release.json records a different admission %s" % key)
    if manifest["admission"].get("run_url") != admission["authorization"]["run_url"] \
            or manifest["admission"].get("dispatched_by") != admission["authorization"]["dispatched_by"]:
        refuse("release.json records a different admission run")
    for key in ("source_revision", "public_origin"):
        if not (manifest[key] == admission[key] == candidate[key]):
            refuse("%s differs between release, admission and candidate" % key)
    if manifest["version"] != admission["release"]["version"]:
        refuse("release version differs from the admission record")
    if manifest["candidate"]["release_tag"] != admission["candidate"]["release_tag"]:
        refuse("release.json names another candidate than the admission")
    for s in SERVICES:
        for store in ("containerd", "graphdriver"):
            if not (manifest["images"][s][store] == admission["images"][s][store] == candidate["images"][s][store]):
                refuse("%s %s identity differs between release, admission and candidate" % (s, store))
    for store in ("containerd", "graphdriver"):
        with open(os.path.join(directory, "expected-images.%s.txt" % store), encoding="utf-8") as f:
            lines = f.read().splitlines()
        want = ["%s=%s" % (RELEASE_REFS[s], manifest["images"][s][store]) for s in SERVICES]
        if lines != want:
            refuse("expected-images.%s.txt does not match release.json" % store)
    if manifest["mc_source"]["sha256"] != manifest["files"].get("mc-source.tar.gz"):
        refuse("mc-source.tar.gz is not the recorded Corresponding Source")
    return manifest


def cmd_verify_release_chain(a):
    m = verify_release_chain(a.directory, a.repository)
    print("Release chain verified: %s from %s, admission %s, archive %s."
          % (m["version"], m["source_revision"], m["admission"]["record_id"], m["archive"]["sha256"]))


def _source_check_rows(sq):
    """Summary rows for the source check evidence; superseded results stay visible to the admission authority."""
    if not isinstance(sq, dict) or not sq.get("required_checks"):
        return []
    if sq.get("schema") != SOURCE_CHECKS_SCHEMA:
        return ["", "Source checks: verified under the earlier D2 rule (legacy record, no job binding)."]
    out = ["", "Source checks (ADR015 D2, amendment A2: latest authenticated evaluation):", "",
           "| Required check | Evidence | Decisive result | Superseded results |", "| --- | --- | --- | --- |"]
    for c in sq["required_checks"]:
        if c.get("provenance") == "github-actions-job":
            decisive = "[job %s](%s), run %s attempt %s of `%s`" % (
                c.get("job_id"), c.get("url"), c.get("run_id"), c.get("run_attempt"), (c.get("workflow") or {}).get("path"))
        else:
            decisive = "[check run %s](%s), app %s on `%s`" % (
                c.get("check_run_id"), c.get("url"), c.get("app_id"), str(c.get("evaluated_sha"))[:12])
        older = [str(h.get("conclusion")) for h in c.get("superseded") or []]
        out.append("| %s | %s | %s | %s |" % (str(c.get("context")).replace("|", "/")[:80], str(c.get("evidence"))[:80],
                                             decisive, ", ".join(older) if older else "none"))
    if sq.get("ignored_check_runs"):
        out += ["", "%d check run(s) under required names were ignored as unauthenticated (listed in the manifest)."
                % len(sq["ignored_check_runs"])]
    return out


def cmd_summary(a):
    """Markdown summary of the packages under DIR (job summary and draft notes). Never fails."""
    banner = {"CANDIDATE": "CANDIDATE - NOT ADMITTED - DO NOT DEPLOY",
              "RELEASE": "PROMOTED DRAFT - NOT PUBLISHED - NOT DEPLOYED",
              "REHEARSAL": "PULL-REQUEST REHEARSAL - NOTHING STORED OR ATTESTED"}.get(a.kind, a.kind)
    out = ["## CodeMastery release pipeline: %s" % banner, ""]
    for sub, name, title in (("candidate", "release-candidate.json", "Candidate"), ("release", "release.json", "Release")):
        path = os.path.join(a.directory, sub, name)
        if not os.path.isfile(path):
            out += ["**%s:** not produced in this run." % title, ""]
            continue
        try:
            m = load_json(path, name)
            out += ["### %s%s" % (title, (" " + m["version"]) if m.get("version") else ""), "",
                    "| Field | Value |", "| --- | --- |",
                    "| Source revision | `%s` |" % m["source_revision"],
                    "| Public origin | `%s` |" % m["public_origin"],
                    "| Archive | `%s` |" % m["archive"]["name"],
                    "| Archive SHA-256 | `%s` |" % m["archive"]["sha256"],
                    "| Manifest SHA-256 | `%s` |" % sha256_file(path)]
            if m.get("admission"):
                adm = m["admission"]
                out.append("| Admission | `%s`: approved by %s at %s in run %s |" % (
                    adm["record_id"], (adm.get("decided_by") or {}).get("login"), adm.get("decided_at"), adm.get("run_url")))
            out += ["", "| Service | containerd identity | graph-driver identity |", "| --- | --- | --- |"]
            for svc in SERVICES:
                i = m["images"][svc]
                out.append("| %s | `%s` | `%s` |" % (svc, i["containerd"], i["graphdriver"]))
            counts = (m.get("security") or {}).get("counts") or (m.get("security") or {}).get("rescan_counts") or {}
            if counts:
                out += ["", "| Findings | " + " | ".join(SEVERITIES) + " |", "| --- |" + " --- |" * len(SEVERITIES)]
                for svc in SERVICES:
                    out.append("| %s | " % svc + " | ".join(str(counts.get(svc, {}).get(sev, 0)) for sev in SEVERITIES) + " |")
            sec = m.get("security") or {}
            coverage = sec.get("coverage") or sec.get("rescan_coverage") or {}
            if coverage:
                out += ["", "| Scan coverage | Detected OS | Scanned targets |", "| --- | --- | --- |"]
                for svc in SERVICES:
                    c = coverage.get(svc) or {}
                    detected = (c.get("os") or "none detected") + (" (end of support)" if c.get("os_end_of_support") else "")
                    out.append("| %s | %s | %d |" % (svc, detected.replace("|", "/")[:60], len(c.get("targets") or [])))
            rows = [f for f in (sec.get("findings") or sec.get("rescan_findings") or [])
                    if f.get("severity") in ("CRITICAL", "HIGH")]
            if rows:
                out += ["", "<details><summary>%d CRITICAL/HIGH findings (public advisories)</summary>" % len(rows), "",
                        "| Service | Severity | Advisory | Package | Installed | Fixed |", "| --- | --- | --- | --- | --- | --- |"]
                for f in rows:
                    out.append("| %s | %s | `%s` | `%s` | `%s` | `%s` |" % tuple(
                        str(f.get(k, "")).replace("|", "/")[:80] for k in ("service", "severity", "id", "package", "installed", "fixed")))
                out += ["", "</details>"]
            out += _source_check_rows(m.get("source_qualification"))
            out += ["", "Status: " + "; ".join("%s=%s" % kv for kv in sorted(m.get("status", {}).items())), ""]
        except (Refused, KeyError, TypeError):
            out += ["**%s:** manifest present but unreadable." % title, ""]
    out.append("Findings are recorded, not accepted. Admission, publication, deployment and any update of an existing VM are separate Leader decisions (ADR015).")
    print("\n".join(out))


def cmd_expect_images(a):
    manifest = load_json(a.manifest, "manifest")
    for s in SERVICES:
        entry = manifest["images"][s]
        ref = entry["release_ref"] if a.release_names else entry["candidate_ref"]
        if not DIGEST.match(entry[a.store]):
            refuse("manifest identity is malformed")
        print("%s=%s" % (ref, entry[a.store]))


def main(argv=None):
    p = argparse.ArgumentParser(prog="release.py", description=__doc__.split("\n")[0])
    sub = p.add_subparsers(dest="command")
    sub.required = True

    s = sub.add_parser("archive-identities")
    s.add_argument("archive")
    s.add_argument("--ref", action="append", required=True)
    s.set_defaults(func=cmd_archive_identities)

    s = sub.add_parser("evaluate-checks")
    s.add_argument("--rules", required=True)
    s.add_argument("--check-runs", required=True, help="slurped pages of GET .../commits/REV/check-runs?filter=all")
    s.add_argument("--revision", required=True)
    s.add_argument("--workflow-files", required=True, help="workflow file paths of the revision, one per line")
    s.add_argument("--workflow-runs", required=True, help="slurped pages of GET .../actions/runs?head_sha=REV")
    s.add_argument("--check-runs-after", required=True, help="the revision's check runs, read again after the first reads")
    s.add_argument("--workflow-runs-after", required=True, help="the workflow runs, read again after everything else")
    s.add_argument("--jobs-dir", required=True,
                   help="RUN.attempt.json (GET .../runs/RUN/attempts/N/jobs, N its run_attempt) and RUN.all.json "
                        "(GET .../runs/RUN/jobs?filter=all), slurped pages, for each run")
    s.add_argument("--pulls", help="GET .../commits/REV/pulls")
    s.add_argument("--pr-head", help="head SHA of the merged pull request")
    s.add_argument("--pr-check-runs", help="slurped check-run pages of that head")
    s.add_argument("--pr-check-runs-after", help="the same listing, read again after the first reads")
    s.add_argument("--revision-tree", help="tree SHA of the revision")
    s.add_argument("--pr-head-tree", help="tree SHA of the merged pull request's head")
    s.set_defaults(func=cmd_evaluate_checks)

    s = sub.add_parser("findings")
    s.add_argument("--report", action="append", required=True, help="SERVICE=PATH; repeatable")
    s.add_argument("--output", required=True)
    s.add_argument("--coverage-output", help="per-service detected OS and scanned targets")
    s.set_defaults(func=cmd_findings)

    s = sub.add_parser("migrations")
    s.add_argument("directory")
    s.add_argument("--baseline-directory")
    s.add_argument("--baseline-revision")
    s.add_argument("--output", required=True)
    s.set_defaults(func=cmd_migrations)

    s = sub.add_parser("candidate-manifest")
    for name in ("package", "archive-name", "context", "identities", "findings", "migrations",
                 "qualification", "scanner", "coverage"):
        s.add_argument("--" + name, required=True)
    s.add_argument("--source-checks")
    s.set_defaults(func=cmd_candidate_manifest)

    s = sub.add_parser("admission-request")
    s.add_argument("--output", required=True)
    s.add_argument("--event", help="GITHUB_EVENT_PATH of the dispatch")
    s.add_argument("--run-record", help="GET .../actions/runs/RUN/attempts/ATTEMPT")
    s.add_argument("--candidate-manifest", help="rehearsal only: derive the synthetic request")
    s.add_argument("--rehearsal", action="store_true")
    s.set_defaults(func=cmd_admission_request)

    s = sub.add_parser("admission-approval")
    s.add_argument("--environment", required=True, help="GET .../environments/release-admission")
    s.add_argument("--branch-policies", required=True, help="GET .../environments/release-admission/deployment-branch-policies")
    s.add_argument("--approvals", required=True, help="GET .../actions/runs/RUN/approvals")
    s.add_argument("--output", required=True)
    s.set_defaults(func=cmd_admission_approval)

    s = sub.add_parser("admission-preview")
    s.add_argument("--dispatch", required=True, help="admission-request output")
    s.add_argument("--candidate-manifest", required=True)
    s.set_defaults(func=cmd_admission_preview)

    s = sub.add_parser("admission-decide")
    s.add_argument("--dispatch", required=True, help="admission-request output")
    s.add_argument("--approval", help="admission-approval output (real admissions)")
    s.add_argument("--candidate-manifest", required=True)
    s.add_argument("--codeowners", help="CODEOWNERS of the dispatched revision (real admissions)")
    s.add_argument("--output", required=True)
    s.add_argument("--rehearsal", action="store_true")
    s.set_defaults(func=cmd_admission_decide)

    s = sub.add_parser("verify-admission")
    s.add_argument("--admission", required=True)
    s.add_argument("--candidate-manifest", required=True)
    s.add_argument("--codeowners")
    s.add_argument("--run-id")
    s.add_argument("--run-attempt")
    s.add_argument("--tooling-sha")
    s.add_argument("--rehearsal", action="store_true")
    s.set_defaults(func=cmd_verify_admission)

    s = sub.add_parser("check-attestation-run")
    s.add_argument("--verification", required=True, help="gh attestation verify --format json output")
    s.add_argument("--run-url", required=True)
    s.add_argument("--trigger", default="workflow_dispatch")
    s.set_defaults(func=cmd_check_attestation_run)

    s = sub.add_parser("check-run-record")
    s.add_argument("--admission", required=True)
    s.add_argument("--run-record", required=True)
    s.add_argument("--approvals", required=True)
    s.set_defaults(func=cmd_check_run_record)

    s = sub.add_parser("check-rescan")
    s.add_argument("--admission", required=True)
    s.add_argument("--candidate-manifest", required=True)
    s.add_argument("--findings", required=True)
    s.add_argument("--rescan-coverage", required=True, help="findings --coverage-output of the rescan")
    s.set_defaults(func=cmd_check_rescan)

    s = sub.add_parser("release-manifest")
    for name in ("package", "archive-name", "context", "identities", "findings", "scanner", "coverage"):
        s.add_argument("--" + name, required=True)
    s.set_defaults(func=cmd_release_manifest)

    s = sub.add_parser("verify-package")
    s.add_argument("directory")
    s.add_argument("--kind", choices=("candidate", "release"), required=True)
    s.add_argument("--allow-extra", action="append", help="file outside SHA256SUMS, e.g. the attestation bundle")
    s.set_defaults(func=cmd_verify_package)

    s = sub.add_parser("verify-release-chain")
    s.add_argument("directory")
    s.add_argument("--repository", required=True)
    s.set_defaults(func=cmd_verify_release_chain)

    s = sub.add_parser("summary")
    s.add_argument("directory")
    s.add_argument("kind", choices=("CANDIDATE", "RELEASE", "REHEARSAL"))
    s.set_defaults(func=cmd_summary)

    s = sub.add_parser("expect-images")
    s.add_argument("manifest")
    s.add_argument("--store", choices=("containerd", "graphdriver"), required=True)
    s.add_argument("--release-names", action="store_true")
    s.set_defaults(func=cmd_expect_images)

    a = p.parse_args(argv)
    try:
        a.func(a)
    except Refused as e:
        print("REFUSED: %s" % e, file=sys.stderr)
        return 3
    except (KeyError, TypeError, ValueError) as e:
        print("REFUSED: malformed input (%s)" % type(e).__name__, file=sys.stderr)
        return 3
    return 0


if __name__ == "__main__":
    sys.exit(main())
