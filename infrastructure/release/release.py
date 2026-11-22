#!/usr/bin/env python3
"""CodeMastery release tooling (ADR015, Phase 4).

Standard-library only; Python 3.9 or later. Every subcommand is a pure function
of its inputs (files and arguments) and fails closed: a malformed, missing,
ambiguous or unexpected input exits non-zero with a short message that never
echoes untrusted archive content or any secret.

Subcommands
  archive-identities  image identities a `docker save --platform` archive yields
  evaluate-checks     required status checks of a source revision
  findings            normalize Trivy JSON reports into one findings list
  migrations          Flyway migration inventory and baseline compatibility
  candidate-manifest  write release-candidate.json for a candidate package
  admission-template  print a PENDING admission record for a candidate
  verify-admission    validate an admission record against a candidate manifest
  check-rescan        fail on any promotion-time finding the admission did not accept
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
import subprocess
import sys
import tarfile

SERVICES = ("postgres", "minio", "minio-init", "backend", "frontend")
RELEASE_REFS = {s: "codemastery.invalid/%s:release" % s for s in SERVICES}
PLATFORM = ("linux", "amd64")
CANDIDATE_SCHEMA = "codemastery.release-candidate/v1"
RELEASE_SCHEMA = "codemastery.release/v1"
ADMISSION_SCHEMA = "codemastery.release-admission/v1"
ADMISSION_DIR = "docs/decisions/release-admissions"
DIGEST = re.compile(r"^sha256:[0-9a-f]{64}$")
HEX64 = re.compile(r"^[0-9a-f]{64}$")
REVISION = re.compile(r"^[0-9a-f]{40}$")
VERSION = re.compile(r"^v(0|[1-9][0-9]*)\.(0|[1-9][0-9]*)\.(0|[1-9][0-9]*)(-[0-9A-Za-z]+(\.[0-9A-Za-z]+)*)?$")
RECORD_ID = re.compile(r"^[A-Z0-9][A-Z0-9-]{7,79}$")
CANDIDATE_TAG = re.compile(r"^candidate-[0-9a-f]{12}-[0-9]+-[0-9]+$")
LOGIN = re.compile(r"^[A-Za-z0-9](?:[A-Za-z0-9-]{0,38})$")
DATE = re.compile(r"^[0-9]{4}-[0-9]{2}-[0-9]{2}$")
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
# Required status checks of the source revision

def evaluate_checks(rules, runs):
    """rules: GET /repos/{o}/{r}/rules/branches/main; runs: list of check runs.

    Every required context must have a completed, successful check run from the
    required app (when the rule names one). The newest run per context decides.
    """
    required = []
    for rule in rules if isinstance(rules, list) else []:
        if rule.get("type") == "required_status_checks":
            for item in (rule.get("parameters") or {}).get("required_status_checks") or []:
                required.append((item.get("context"), item.get("integration_id")))
    if not required:
        refuse("no required status checks were found for the default branch; refusing to treat the revision as qualified")
    results = []
    for context, app in sorted(set(required), key=lambda x: (x[0] or "", x[1] or 0)):
        candidates = [r for r in runs if r.get("name") == context
                      and (app is None or (r.get("app") or {}).get("id") == app)]
        if not candidates:
            refuse("required check '%s' has no check run on the revision" % context)
        newest = max(candidates, key=lambda r: (r.get("started_at") or "", r.get("id") or 0))
        if newest.get("status") != "completed" or newest.get("conclusion") != "success":
            refuse("required check '%s' is %s/%s on the revision"
                   % (context, newest.get("status"), newest.get("conclusion")))
        results.append({"context": context, "app_id": app, "conclusion": "success",
                        "check_run_id": newest.get("id"), "url": newest.get("html_url")})
    return results


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
# Admission

ADMISSION_KEYS = {"schema", "record_id", "decision", "decided_by", "decided_on", "scope",
                  "source_revision", "public_origin", "candidate", "images",
                  "accepted_findings", "release", "not_granted", "notes", "rehearsal"}
REQUIRED_NOT_GRANTED = {"deployment", "public-exposure", "existing-vm-update"}


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


def admission_template(candidate, candidate_tag, version):
    return {
        "schema": ADMISSION_SCHEMA,
        "record_id": "CM-REL-ADMISSION-%s-%s" % (datetime.date.today().isoformat(), candidate["source_revision"][:8].upper()),
        "decision": "PENDING",
        "decided_by": "",
        "decided_on": "",
        "scope": "",
        "source_revision": candidate["source_revision"],
        "public_origin": candidate["public_origin"],
        "candidate": {"release_tag": candidate_tag,
                      "manifest_sha256": "<sha256 of release-candidate.json>",
                      "archive_sha256": candidate["archive"]["sha256"]},
        "images": {s: {"containerd": candidate["images"][s]["containerd"],
                       "graphdriver": candidate["images"][s]["graphdriver"]} for s in SERVICES},
        "accepted_findings": [{"service": f["service"], "id": f["id"], "package": f["package"]}
                              for f in candidate.get("security", {}).get("findings", [])],
        "release": {"version": version},
        "not_granted": sorted(REQUIRED_NOT_GRANTED),
        "notes": "",
    }


def verify_admission(admission, admission_path, candidate, candidate_sha256, codeowners, rehearsal, repo_root):
    if not isinstance(admission, dict) or set(admission) - ADMISSION_KEYS:
        refuse("admission record has unknown fields")
    missing = (ADMISSION_KEYS - {"notes", "rehearsal"}) - set(admission)
    if missing:
        refuse("admission record lacks: %s" % ", ".join(sorted(missing)))
    if admission["schema"] != ADMISSION_SCHEMA:
        refuse("admission record has an unexpected schema")
    record_id = admission["record_id"]
    if not isinstance(record_id, str) or not RECORD_ID.match(record_id):
        refuse("admission record_id is malformed")
    if os.path.basename(admission_path) != record_id + ".json":
        refuse("admission file name must be <record_id>.json")
    is_rehearsal = admission.get("rehearsal") is True or record_id.startswith("REHEARSAL-")
    if rehearsal:
        if not (admission.get("rehearsal") is True and record_id.startswith("REHEARSAL-")):
            refuse("a rehearsal accepts only a REHEARSAL- record marked rehearsal: true")
        if candidate.get("run", {}).get("rehearsal") is not True:
            refuse("a rehearsal admission cannot promote a non-rehearsal candidate")
    else:
        if is_rehearsal or "rehearsal" in admission:
            refuse("rehearsal records cannot promote a release")
        if candidate.get("run", {}).get("rehearsal") is not False:
            refuse("only a candidate from a dispatched release run can be promoted")
        if (candidate.get("source_qualification") or {}).get("verified") is not True:
            refuse("the candidate's source revision checks were not verified")
        _check_tracked(admission_path, repo_root)
    if admission["decision"] != "ADMITTED":
        refuse("admission decision is %r; only ADMITTED permits promotion" % (admission["decision"],))
    login = admission["decided_by"]
    if not isinstance(login, str) or not LOGIN.match(login):
        refuse("admission decided_by must be a GitHub login")
    owners = codeowners_for(codeowners, "/docs/decisions/")
    if not rehearsal and login not in owners:
        refuse("admission decided_by is not a code owner of /docs/decisions/")
    if not isinstance(admission["decided_on"], str) or not DATE.match(admission["decided_on"]):
        refuse("admission decided_on must be YYYY-MM-DD")
    if not isinstance(admission["scope"], str) or not admission["scope"].strip() or len(admission["scope"]) > 2000:
        refuse("admission scope must be a non-empty statement")
    not_granted = admission["not_granted"]
    if not isinstance(not_granted, list) or not REQUIRED_NOT_GRANTED.issubset(set(not_granted)):
        refuse("admission not_granted must name deployment, public-exposure and existing-vm-update")
    if admission["source_revision"] != candidate.get("source_revision") or not REVISION.match(str(admission["source_revision"])):
        refuse("admission source_revision does not match the candidate")
    if admission["public_origin"] != candidate.get("public_origin") or not ORIGIN.match(str(admission["public_origin"])):
        refuse("admission public_origin does not match the candidate")
    c = admission["candidate"]
    if not isinstance(c, dict) or set(c) != {"release_tag", "manifest_sha256", "archive_sha256"}:
        refuse("admission candidate must hold exactly release_tag, manifest_sha256 and archive_sha256")
    if not isinstance(c["release_tag"], str) or not CANDIDATE_TAG.match(c["release_tag"]):
        refuse("admission candidate release_tag is malformed")
    if c["manifest_sha256"] != candidate_sha256:
        refuse("admission names a different candidate manifest")
    if c["archive_sha256"] != (candidate.get("archive") or {}).get("sha256"):
        refuse("admission names a different candidate archive")
    images = admission["images"]
    if not isinstance(images, dict) or set(images) != set(SERVICES):
        refuse("admission must name exactly the five service images")
    for service in SERVICES:
        entry = images[service]
        want = candidate["images"][service]
        if not isinstance(entry, dict) or set(entry) != {"containerd", "graphdriver"}:
            refuse("admission image entry for %s is malformed" % service)
        for store in ("containerd", "graphdriver"):
            if not DIGEST.match(str(entry[store])) or entry[store] != want.get(store):
                refuse("admission %s identity for %s does not match the candidate" % (store, service))
    accepted = admission["accepted_findings"]
    if not isinstance(accepted, list):
        refuse("admission accepted_findings must be a list")
    keys = set()
    for item in accepted:
        if not isinstance(item, dict) or set(item) != {"service", "id", "package"} or item["service"] not in SERVICES:
            refuse("admission accepted_findings entries need exactly service, id and package")
        keys.add((item["service"], item["id"], item["package"]))
    candidate_keys = {(f["service"], f["id"], f["package"]) for f in candidate.get("security", {}).get("findings", [])}
    unaccepted = candidate_keys - keys
    if unaccepted:
        refuse("admission does not accept %d finding(s) reported for the candidate" % len(unaccepted))
    release = admission["release"]
    if not isinstance(release, dict) or set(release) != {"version"} or not VERSION.match(str(release["version"])):
        refuse("admission release.version must be a v<major>.<minor>.<patch>[-pre] tag")
    return {"record_id": record_id, "version": release["version"], "accepted": sorted(keys)}


def _check_tracked(path, repo_root):
    rel = os.path.relpath(os.path.realpath(path), os.path.realpath(repo_root))
    if os.path.dirname(rel) != ADMISSION_DIR:
        refuse("admission records must live in %s/" % ADMISSION_DIR)
    if os.path.islink(path):
        refuse("admission record must be a regular file")
    try:
        subprocess.run(["git", "-C", repo_root, "ls-files", "--error-unmatch", "--", rel],
                       check=True, stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)
        diff = subprocess.run(["git", "-C", repo_root, "status", "--porcelain", "--", rel],
                              check=True, stdout=subprocess.PIPE, stderr=subprocess.DEVNULL).stdout
    except (OSError, subprocess.CalledProcessError):
        refuse("admission record is not committed in the checked-out revision")
    if diff.strip():
        refuse("admission record differs from the committed revision")


def check_rescan(accepted_keys, findings):
    unaccepted = sorted({(f["service"], f["id"], f["package"]) for f in findings} - set(accepted_keys))
    return unaccepted


# --------------------------------------------------------------------------
# CLI

def cmd_archive_identities(a):
    print(json.dumps(archive_identities(a.archive, a.ref), indent=2, sort_keys=True))


def cmd_evaluate_checks(a):
    rules = load_json(a.rules, "branch rules")
    runs = []
    for page in load_json(a.check_runs, "check runs") or []:
        runs.extend(page.get("check_runs") or [] if isinstance(page, dict) else [])
    results = evaluate_checks(rules, runs)
    print(json.dumps(results, indent=2, sort_keys=True))


def cmd_findings(a):
    findings = []
    for spec in a.report:
        service, _, path = spec.partition("=")
        if service not in SERVICES or not path:
            refuse("--report must be SERVICE=PATH for a known service")
        findings.extend(normalize_findings(service, [path]))
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
    checks = load_json(a.source_checks, "source checks") if a.source_checks else None
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
        "security": {"scanner": scanner, "findings": findings, "counts": count_findings(findings),
                     "policy": "Findings are recorded, not accepted. Only a Leader admission record may accept them."},
        "qualification": qualification,
        "source_qualification": {"verified": checks is not None, "required_checks": checks or []},
        "compatibility": dict(ctx["compatibility"], migrations=migrations),
        "status": {"candidate": "QUALIFIED", "security_admission": "PENDING_LEADER_ADMISSION",
                   "promotion": "LOCKED_UNTIL_ADMITTED", "deployment": "NOT_AUTHORIZED_BY_THIS_PIPELINE"},
    }
    if ctx["run"].get("rehearsal") is not (checks is None):
        refuse("only rehearsals may skip source check verification")
    manifest["files"] = file_map(a.package, {"release-candidate.json", "SHA256SUMS"})
    write_json(os.path.join(a.package, "release-candidate.json"), manifest)
    write_sha256sums(a.package)


def cmd_admission_template(a):
    candidate = load_json(a.candidate_manifest, "candidate manifest")
    template = admission_template(candidate, a.release_tag, a.version)
    template["candidate"]["manifest_sha256"] = sha256_file(a.candidate_manifest)
    print(json.dumps(template, indent=2, sort_keys=True))


def cmd_verify_admission(a):
    admission = load_json(a.admission, "admission record")
    candidate = load_json(a.candidate_manifest, "candidate manifest")
    result = verify_admission(admission, a.admission, candidate, sha256_file(a.candidate_manifest),
                              a.codeowners, a.rehearsal, a.repo_root)
    if a.release_tag and admission["candidate"]["release_tag"] != a.release_tag:
        refuse("admission names a different candidate release")
    print(json.dumps(result, indent=2, sort_keys=True))


def cmd_check_rescan(a):
    admission = load_json(a.admission, "admission record")
    findings = load_json(a.findings, "rescan findings")
    accepted = {(i["service"], i["id"], i["package"]) for i in admission.get("accepted_findings") or []}
    unaccepted = check_rescan(accepted, findings)
    if unaccepted:
        for service, vid, pkg in unaccepted:
            print("NOT ACCEPTED: %s %s %s" % (service, vid, pkg), file=sys.stderr)
        refuse("%d promotion-time finding(s) are not accepted by the admission record; a new admission is required" % len(unaccepted))
    print("Every promotion-time finding is accepted by the admission record.")


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
                      "decided_on": admission["decided_on"], "scope": admission["scope"],
                      "file": "admission.json", "sha256": sha256_file(os.path.join(a.package, "admission.json")),
                      "recorded_at": ctx.get("admission_commit")},
        "images": images,
        "archive": {"name": a.archive_name, "sha256": sha256_file(os.path.join(a.package, a.archive_name)),
                    "size": os.path.getsize(os.path.join(a.package, a.archive_name))},
        "expected_images": {"containerd": "expected-images.containerd.txt",
                            "graphdriver": "expected-images.graphdriver.txt"},
        "mc_source": candidate["mc_source"],
        "security": {"rescan_scanner": scanner, "rescan_findings": rescan,
                     "rescan_counts": count_findings(rescan),
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
        "status": {"candidate": "QUALIFIED", "security_admission": "ADMITTED (%s)" % admission["record_id"],
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
    """Offline consistency of a release package: manifest, admission and candidate agree."""
    manifest = verify_package(directory, "release", ("release.sigstore.json",))
    if manifest.get("repository") != repository:
        refuse("release.json names a different repository")
    admission = load_json(os.path.join(directory, "admission.json"), "admission.json")
    candidate_path = os.path.join(directory, "release-candidate.json")
    candidate = load_json(candidate_path, "release-candidate.json")
    if manifest["admission"]["sha256"] != sha256_file(os.path.join(directory, "admission.json")):
        refuse("admission.json does not match release.json")
    if admission.get("decision") != "ADMITTED" or admission.get("rehearsal") is not None:
        refuse("the bundled admission record does not admit a release")
    if manifest["candidate"]["manifest_sha256"] != sha256_file(candidate_path) \
            or admission["candidate"]["manifest_sha256"] != sha256_file(candidate_path):
        refuse("release-candidate.json is not the admitted candidate manifest")
    if candidate.get("schema") != CANDIDATE_SCHEMA or candidate.get("run", {}).get("rehearsal") is not False:
        refuse("the bundled candidate manifest is not a dispatched candidate")
    for key in ("source_revision", "public_origin"):
        if not (manifest[key] == admission[key] == candidate[key]):
            refuse("%s differs between release, admission and candidate" % key)
    if manifest["version"] != admission["release"]["version"]:
        refuse("release version differs from the admission record")
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
                out.append("| Admission | `%s` (%s, %s) |" % (m["admission"]["record_id"], m["admission"]["decided_by"], m["admission"]["decided_on"]))
            out += ["", "| Service | containerd identity | graph-driver identity |", "| --- | --- | --- |"]
            for svc in SERVICES:
                i = m["images"][svc]
                out.append("| %s | `%s` | `%s` |" % (svc, i["containerd"], i["graphdriver"]))
            counts = (m.get("security") or {}).get("counts") or (m.get("security") or {}).get("rescan_counts") or {}
            if counts:
                out += ["", "| Findings | " + " | ".join(SEVERITIES) + " |", "| --- |" + " --- |" * len(SEVERITIES)]
                for svc in SERVICES:
                    out.append("| %s | " % svc + " | ".join(str(counts.get(svc, {}).get(sev, 0)) for sev in SEVERITIES) + " |")
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
    s.add_argument("--check-runs", required=True, help="slurped pages of GET .../commits/REV/check-runs")
    s.set_defaults(func=cmd_evaluate_checks)

    s = sub.add_parser("findings")
    s.add_argument("--report", action="append", required=True, help="SERVICE=PATH; repeatable")
    s.add_argument("--output", required=True)
    s.set_defaults(func=cmd_findings)

    s = sub.add_parser("migrations")
    s.add_argument("directory")
    s.add_argument("--baseline-directory")
    s.add_argument("--baseline-revision")
    s.add_argument("--output", required=True)
    s.set_defaults(func=cmd_migrations)

    s = sub.add_parser("candidate-manifest")
    for name in ("package", "archive-name", "context", "identities", "findings", "migrations",
                 "qualification", "scanner"):
        s.add_argument("--" + name, required=True)
    s.add_argument("--source-checks")
    s.set_defaults(func=cmd_candidate_manifest)

    s = sub.add_parser("admission-template")
    s.add_argument("--candidate-manifest", required=True)
    s.add_argument("--release-tag", required=True)
    s.add_argument("--version", required=True)
    s.set_defaults(func=cmd_admission_template)

    s = sub.add_parser("verify-admission")
    s.add_argument("--admission", required=True)
    s.add_argument("--candidate-manifest", required=True)
    s.add_argument("--codeowners", required=True)
    s.add_argument("--repo-root", required=True)
    s.add_argument("--release-tag")
    s.add_argument("--rehearsal", action="store_true")
    s.set_defaults(func=cmd_verify_admission)

    s = sub.add_parser("check-rescan")
    s.add_argument("--admission", required=True)
    s.add_argument("--findings", required=True)
    s.set_defaults(func=cmd_check_rescan)

    s = sub.add_parser("release-manifest")
    for name in ("package", "archive-name", "context", "identities", "findings", "scanner"):
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
