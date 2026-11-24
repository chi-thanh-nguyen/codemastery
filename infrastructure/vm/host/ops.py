#!/usr/bin/env python3
"""CodeMastery VM operations (ADR017): the root-side half of the SSH continuous deployment.

Invoked only through /usr/local/sbin/codemastery-ops (infrastructure/vm/host/codemastery-ops),
which authenticates the calling role (the restricted deployment identity or the operator)
and receives uploads. Every state-changing operation holds the shared exclusive lock
/run/codemastery/operations.lock (ADR009 section 9.4); contention is a visible failure.

The release's own infrastructure/vm/deploy.sh (from the immutable checkout of its source
revision) remains the only code that loads images, provisions roles and starts services.
This module stages and verifies what deploy.sh consumes, evaluates the gates that deploy.sh
can only take as operator attestations (P9 recovery, schema/image compatibility, preserved
state) from actual evidence, runs deploy.sh and verifies the result. It never prints the
env file, a credential or a rendered Compose model.
"""
import argparse
import base64
import datetime
import fcntl
import hashlib
import json
import os
import re
import shutil
import socket
import ssl
import subprocess
import sys
import tempfile
import time
import zlib

# The installed tooling tree is verified by its git tree ID on every install-tooling;
# writing __pycache__ into it as root would make the next install of the same commit fail.
sys.dont_write_bytecode = True
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import recovery  # noqa: E402  (same directory)
from recovery import RecoveryError, refuse, utc_now, iso, dump_json, write_private, load_json  # noqa: E402

CONF = os.environ.get("CODEMASTERY_OPS_CONF", "/etc/codemastery/deploy/ops.conf")
STATE = os.environ.get("CODEMASTERY_STATE_DIR", "/var/lib/codemastery")
RUN_DIR = os.environ.get("CODEMASTERY_RUN_DIR", "/run/codemastery")
SOURCE_ROOT = os.environ.get("CODEMASTERY_SOURCE_ROOT", "/opt/codemastery/releases")
FREEZE = "/etc/codemastery/freeze"
# Test harness overrides only: sudo's env_reset removes them from every real invocation.
PROJECT = os.environ.get("CODEMASTERY_PROJECT", "codemastery")
RECIPIENT_CERT = os.environ.get("CODEMASTERY_RECIPIENT_CERT", recovery.VM_RECIPIENT_CERT)
PRODUCER_KEY = os.environ.get("CODEMASTERY_PRODUCER_KEY", recovery.VM_PRODUCER_KEY)
SERVICES = ("postgres", "minio", "minio-init", "backend", "frontend")
VERSION = re.compile(r"^v(0|[1-9][0-9]*)\.(0|[1-9][0-9]*)\.(0|[1-9][0-9]*)(-[0-9A-Za-z]+(\.[0-9A-Za-z]+)*)?$")
HEX64 = re.compile(r"^[0-9a-f]{64}$")
REVISION = re.compile(r"^[0-9a-f]{40}$")
FILE_NAME = re.compile(r"^[A-Za-z0-9][A-Za-z0-9._-]{0,127}$")
MIGRATION = re.compile(r"^V([0-9]+)__([A-Za-z0-9_]+)\.sql$")
# Every staged file is checked against release.json's file map (whose digest the request
# names). release.py keeps SHA256SUMS out of that map, so it is checked on the workstation
# (deploy-release.sh, verify-release.sh) and never staged here.
REQUIRED_PACKAGE = ("release.json", "expected-images.containerd.txt", "expected-images.graphdriver.txt",
                    "admission.json")
FULL_PACKAGE = REQUIRED_PACKAGE + ("mc-source.tar.gz",)
CONF_KEYS = {"origin", "env_file", "wait_timeout", "tls_dir", "tls_backup_root", "acme_home", "acme_domain",
             "tar_mode", "capacity", "stop_timeout"}
DEFAULT_CAPACITY = {"image_expansion_factor": 3, "margin_bytes": 1 << 30, "min_free_inodes": 100000}
TLS_RENEW_DAYS = 30           # ADR009 section 9.3: attempt renewal at <= 30 days
TLS_WARN_DAYS = (30, 14, 7)   # warnings at 30/14/7 days


def log(message):
    print("[%s] %s" % (time.strftime("%H:%M:%SZ", time.gmtime()), message), file=sys.stderr, flush=True)


def state(*parts):
    return os.path.join(STATE, *parts)


def config():
    cfg = load_json(CONF, "ops configuration")
    missing = CONF_KEYS - set(cfg)
    if missing:
        refuse("ops configuration lacks %s" % ", ".join(sorted(missing)))
    if not isinstance(cfg["wait_timeout"], int) or cfg["wait_timeout"] <= 0:
        refuse("wait_timeout must be a positive integer (seconds); no default is approved")
    if not re.match(r"^https://[a-z0-9.-]+$", cfg["origin"]):
        refuse("origin must be a bare HTTPS origin")
    cap = dict(DEFAULT_CAPACITY)
    cap.update(cfg.get("capacity") or {})
    cfg["capacity"] = cap
    return cfg


class Lock(object):
    """The shared exclusive operation lock (ADR009 section 9.4); never waits."""

    def __init__(self, what):
        self.what = what
        self.fd = None

    def __enter__(self):
        os.makedirs(RUN_DIR, mode=0o700, exist_ok=True)
        self.fd = os.open(os.path.join(RUN_DIR, "operations.lock"), os.O_RDWR | os.O_CREAT, 0o600)
        try:
            fcntl.flock(self.fd, fcntl.LOCK_EX | fcntl.LOCK_NB)
        except OSError:
            os.close(self.fd)
            refuse("another capture, renewal, deployment or prune holds the operations lock; %s skipped" % self.what)
        os.ftruncate(self.fd, 0)
        os.write(self.fd, ("%d %s %s\n" % (os.getpid(), self.what, iso(utc_now()))).encode("ascii"))
        return self

    def __exit__(self, *exc):
        fcntl.flock(self.fd, fcntl.LOCK_UN)
        os.close(self.fd)


def run(argv, check=True, data=None, timeout=None):
    result = subprocess.run(argv, input=data, stdout=subprocess.PIPE, stderr=subprocess.PIPE, timeout=timeout,
                            **({} if data is not None else {"stdin": subprocess.DEVNULL}))
    if check and result.returncode != 0:
        refuse("%s failed (exit %d)" % (os.path.basename(argv[0]), result.returncode))
    return result


def docker_text(*args):
    r = run(["docker"] + list(args), check=False)
    return r.stdout.decode("utf-8", "replace").strip() if r.returncode == 0 else None


# --------------------------------------------------------------------------- Flyway

def flyway_checksum(data):
    """Flyway's CRC32 over the script's lines (BOM removed, line terminators excluded), as a signed int."""
    text = data.decode("utf-8")
    if text.startswith("﻿"):
        text = text[1:]
    lines = re.split(r"\r\n|\r|\n", text)
    if lines and lines[-1] == "":
        lines = lines[:-1]
    crc = 0
    for line in lines:
        crc = zlib.crc32(line.encode("utf-8"), crc)
    return crc - (1 << 32) if crc >= (1 << 31) else crc


def release_migrations(release, source_dir):
    """The release's migration inventory, re-derived from the immutable source checkout."""
    inventory = ((release.get("compatibility") or {}).get("migrations") or {}).get("inventory")
    if not isinstance(inventory, list) or not inventory:
        refuse("release.json lists no migration inventory")
    directory = os.path.join(source_dir, "backend", "src", "main", "resources", "db", "migration")
    files = sorted(f for f in os.listdir(directory) if f.endswith(".sql"))
    out = []
    for n, item in enumerate(sorted(inventory, key=lambda i: i["version"]), 1):
        m = MIGRATION.match(item.get("file", ""))
        if not m or int(m.group(1)) != item["version"] or item["version"] != n:
            refuse("the release migration inventory is not V001..V%03d without gaps" % len(inventory))
        path = os.path.join(directory, item["file"])
        with open(path, "rb") as f:
            data = f.read()
        if hashlib.sha256(data).hexdigest() != item["sha256"]:
            refuse("%s differs from the release's migration inventory" % item["file"])
        out.append({"version": n, "file": item["file"], "description": m.group(2).replace("_", " "),
                    "checksum": flyway_checksum(data)})
    if files != [i["file"] for i in out]:
        refuse("the source checkout holds migrations outside the release inventory")
    return out


def deferral_rule(deploy_sh_text):
    """Which predecessor rule the release's deploy.sh applies to migration-added columns."""
    rows = []
    m = re.search(r"RUNTIME_MIGRATION_ADDED_COLUMNS='\n(.*?)\n'", deploy_sh_text, re.S)
    if m:
        for line in m.group(1).splitlines():
            parts = line.split()
            if parts:
                rows.append({"relation": parts[0], "column": parts[1], "version": int(parts[2])})
    if "count(*) BETWEEN 1 AND ${version} - 1" in deploy_sh_text or "s.n BETWEEN 1 AND ${version} - 1" in deploy_sh_text:
        rule = "prefix"          # CT17-P6 r9: any successful prefix 1..k, k < version
    elif "count(*) = ${version} - 1" in deploy_sh_text:
        rule = "exact"           # CT17-P6 r4: only k = version - 1
    elif not rows:
        rule = "none"
    else:
        rule = "unknown"
    return rule, rows


def compatibility(history, columns_present, migrations, rule, rows, tables_exist):
    """Evaluate whether this release can update the target's schema without Flyway or deploy.sh refusing.

    history: [{"version","description","checksum","success"}] from flyway_schema_history (None when the
    table does not exist); columns_present: {"relation.column": bool}."""
    blocked = []
    if history is None:
        if tables_exist:
            return {"state": "BLOCKED", "applied": None, "pending": [],
                    "blocked": ["application tables exist without Flyway history (unknown schema)"]}
        return {"state": "FRESH", "applied": 0, "pending": [m["version"] for m in migrations], "blocked": []}
    versions = []
    for h in history:
        if h.get("version") in (None, "") or not re.match(r"^[0-9]{1,4}$", str(h["version"])):
            blocked.append("Flyway history holds a non-versioned or malformed entry")
            continue
        if not h.get("success"):
            blocked.append("Flyway history records a failed migration (version %s)" % h["version"])
        versions.append(int(h["version"]))
    if len(set(versions)) != len(versions):
        blocked.append("Flyway history repeats a version")
    k = len(versions)
    if sorted(versions) != list(range(1, k + 1)):
        blocked.append("Flyway history is not an unbroken V001..V%03d prefix" % k)
    if k > len(migrations):
        blocked.append("the target holds migrations this release does not know (V%03d > V%03d): image-only "
                       "rollback across a schema change is refused" % (k, len(migrations)))
    by_version = {m["version"]: m for m in migrations}
    for h in history:
        try:
            v = int(h["version"])
        except (TypeError, ValueError):
            continue
        m = by_version.get(v)
        if m is None:
            continue
        if h.get("checksum") != m["checksum"]:
            blocked.append("V%03d checksum on the target differs from the release (Flyway validation would stop)" % v)
        if h.get("description") is not None and h["description"] != m["description"]:
            blocked.append("V%03d description on the target differs from the release" % v)
    pending = [m["version"] for m in migrations if m["version"] > k]
    for row in rows:
        key = "%s.%s" % (row["relation"], row["column"])
        present = columns_present.get(key)
        if row["version"] in pending and present:
            blocked.append("%s already exists although V%03d is pending (schema drift)" % (key, row["version"]))
        if row["version"] <= k and present is False:
            blocked.append("%s is missing although V%03d is recorded (schema drift)" % (key, row["version"]))
        if row["version"] in pending and present is False:
            if rule == "exact" and k != row["version"] - 1:
                blocked.append("this release's deploy.sh defers %s only on the exact V%03d predecessor (CT17-P6 r4); "
                               "the target holds V%03d, so provisioning would refuse it: deploy a release built from "
                               "a revision with CT17-P6 r9 (ADR017)" % (key, row["version"] - 1, k))
            elif rule not in ("exact", "prefix"):
                blocked.append("this release's deploy.sh deferral rule is not recognized; refusing (fail closed)")
            elif k < 1:
                blocked.append("an empty Flyway history cannot defer %s" % key)
    return {"state": "BLOCKED" if blocked else ("CURRENT" if not pending else "UPGRADE"), "applied": k,
            "pending": pending, "blocked": blocked}


def pg_container():
    found = recovery.Docker().project_containers(PROJECT)
    return found.get("postgres"), found


def target_schema(rows):
    cid, found = pg_container()
    if not cid:
        return None, {}, False, found
    if not recovery.Docker().state(cid).get("Running"):
        refuse("the PostgreSQL container exists but is not running; schema state cannot be read")
    d = recovery.Docker()
    exists = recovery.pg_query(d, cid, "SELECT to_regclass('public.flyway_schema_history') IS NOT NULL;") == "t"
    tables = int(recovery.pg_query(d, cid, "SELECT count(*) FROM pg_tables WHERE schemaname='public';"))
    history = None
    if exists:
        out = recovery.pg_query(d, cid, "SELECT coalesce(version,''), description, coalesce(checksum::text,''), success "
                                        "FROM public.flyway_schema_history ORDER BY installed_rank;")
        history = []
        for line in (out or "").splitlines():
            v, desc, checksum, ok = line.split("|")
            history.append({"version": v or None, "description": desc,
                            "checksum": int(checksum) if checksum else None, "success": ok == "t"})
    present = {}
    for row in rows:
        q = ("SELECT EXISTS (SELECT 1 FROM pg_attribute WHERE attrelid = to_regclass('public.%s') AND attname = '%s' "
             "AND attnum > 0 AND NOT attisdropped);" % (row["relation"], row["column"]))
        if not re.match(r"^[a-z_][a-z0-9_]*$", row["relation"]) or not re.match(r"^[a-z_][a-z0-9_]*$", row["column"]):
            refuse("malformed migration-added column row in deploy.sh")
        present["%s.%s" % (row["relation"], row["column"])] = recovery.pg_query(d, cid, q) == "t"
    return history, present, tables > (1 if exists else 0), found


# --------------------------------------------------------------------------- staging

def incoming(name):
    if not FILE_NAME.match(name):
        refuse("unsafe incoming file name")
    path = state("incoming", name)
    if os.path.islink(path) or not os.path.isfile(path):
        refuse("%s was not received" % name)
    return path


def sha256(path):
    return recovery.sha256_file(path)


def read_release(path, expected_sha):
    if sha256(path) != expected_sha:
        refuse("release.json is not the verified manifest the request named (SHA-256 differs)")
    release = load_json(path, "release.json")
    if release.get("schema") != "codemastery.release/v1" or not VERSION.match(str(release.get("version"))):
        refuse("release.json is not a release manifest")
    if not REVISION.match(str(release.get("source_revision"))) or not REVISION.match(str(release.get("source_tree"))):
        refuse("release.json names no valid source revision and tree")
    return release


def git_tree(directory):
    """The git tree object ID of a directory, computed offline (no network, no repository)."""
    with tempfile.TemporaryDirectory(prefix="cm-tree-") as gitdir:
        clean = {k: v for k, v in os.environ.items() if not k.startswith("GIT_")}
        clean.update(GIT_CONFIG_NOSYSTEM="1", HOME=gitdir)
        r = subprocess.run(["git", "init", "-q", "--bare", gitdir], env=clean, stdout=subprocess.PIPE,
                           stderr=subprocess.PIPE)
        if r.returncode != 0:
            refuse("computing the source tree hash failed")
        env = dict(clean, GIT_DIR=gitdir, GIT_WORK_TREE=directory, GIT_INDEX_FILE=os.path.join(gitdir, "index"))
        cfg = ["-c", "core.bare=false", "-c", "core.autocrlf=false", "-c", "core.filemode=true",
               "-c", "core.symlinks=true"]
        for argv in (["git"] + cfg + ["add", "-A", "-f", "."], ["git"] + cfg + ["write-tree"]):
            r = subprocess.run(argv, cwd=directory, env=env, stdout=subprocess.PIPE, stderr=subprocess.PIPE)
            if r.returncode != 0:
                refuse("computing the source tree hash failed")
        return r.stdout.decode("ascii").strip()


def safe_extract(tar_path, dest):
    """Extract a git archive of regular files/directories; reject links before any write."""
    import tarfile
    with tarfile.open(tar_path, "r:") as tf:
        members = []
        for m in tf.getmembers():
            name = os.path.normpath(m.name)
            if m.name.startswith("/") or name == ".." or name.startswith("../") or "\x00" in m.name \
                    or ".git" in m.name.split("/"):
                refuse("the source archive contains an unsafe path")
            if not (m.isfile() or m.isdir()):
                if m.type in (b"g", b"x"):     # pax headers (git archive comment)
                    continue
                refuse("the source archive contains a link or special file")
            m.uid = m.gid = 0
            m.uname = m.gname = "root"
            m.mode = (m.mode & 0o111 and 0o755) or (0o755 if m.isdir() else 0o644)
            members.append(m)
        tf.extractall(dest, members=members)


def install_source(release):
    """Immutable root-owned checkout /opt/codemastery/releases/<full-source-sha> (ADR009 section 9.3)."""
    rev, tree = release["source_revision"], release["source_tree"]
    target = os.path.join(SOURCE_ROOT, rev)
    if os.path.lexists(target):
        if os.path.islink(target) or not os.path.isdir(target):
            refuse("%s exists and is not a directory" % target)
        if git_tree(target) != tree:
            refuse("the existing checkout %s no longer matches its source tree (modified)" % target)
        return target, "EXISTING_VERIFIED"
    os.makedirs(SOURCE_ROOT, mode=0o755, exist_ok=True)
    tmp = tempfile.mkdtemp(prefix=".%s." % rev[:12], dir=SOURCE_ROOT)
    try:
        os.chmod(tmp, 0o755)
        safe_extract(incoming("source-%s.tar" % rev), tmp)
        if git_tree(tmp) != tree:
            refuse("the transferred source does not have the release's source tree %s" % tree)
        os.rename(tmp, target)
    except BaseException:
        shutil.rmtree(tmp, ignore_errors=True)
        raise
    return target, "INSTALLED_VERIFIED"


def package_files(release, full):
    names = list(FULL_PACKAGE if full else REQUIRED_PACKAGE)
    if full:
        names.append(release["archive"]["name"])
    return names


def stage_release(version, release_sha, full):
    """Copy the received package into /var/lib/codemastery/releases/<version> (root 0700, files 0400).

    Every file must match release.json's file map; an existing staged release is never replaced
    by different bytes."""
    release_path = incoming("release.json")
    release = read_release(release_path, release_sha)
    if release["version"] != version:
        refuse("the package is release %s, not %s" % (release["version"], version))
    files = release.get("files") or {}
    names = package_files(release, full)
    for name in names:
        if name == "release.json":
            continue
        path = incoming(name)
        if files.get(name) != sha256(path):
            refuse("%s does not match release.json (corrupt, substituted or interrupted transfer)" % name)
    if full and sha256(incoming(release["archive"]["name"])) != release["archive"]["sha256"]:
        refuse("the archive does not match release.json")
    target = state("releases", version)
    os.makedirs(state("releases"), mode=0o700, exist_ok=True)
    if os.path.lexists(target):
        if os.path.islink(target):
            refuse("staged release path is a symlink")
        if sha256(os.path.join(target, "release.json")) != release_sha:
            refuse("a different release %s is already staged; staged releases are never replaced" % version)
        for name in names:
            p = os.path.join(target, name)
            if not os.path.exists(p):
                shutil.copyfile(incoming(name), p)
                os.chmod(p, 0o400)
            elif sha256(p) != (release_sha if name == "release.json" else files.get(name)):
                refuse("staged %s was modified" % name)
        return release, target
    tmp = tempfile.mkdtemp(prefix=".%s." % version, dir=state("releases"))
    try:
        for name in names:
            shutil.copyfile(incoming(name), os.path.join(tmp, name))
            os.chmod(os.path.join(tmp, name), 0o400)
        os.rename(tmp, target)
    except BaseException:
        shutil.rmtree(tmp, ignore_errors=True)
        raise
    return release, target


def clear_incoming():
    shutil.rmtree(state("incoming"), ignore_errors=True)
    os.makedirs(state("incoming"), mode=0o700, exist_ok=True)


# --------------------------------------------------------------------------- inventory and gates

def image_store():
    status = docker_text("info", "--format", "{{json .DriverStatus}}")
    if status is None:
        refuse("Docker image-store discovery failed")
    kinds = [v for k, v in json.loads(status) if k == "driver-type"]
    if kinds == ["io.containerd.snapshotter.v1"]:
        return "containerd"
    if not kinds:
        return "graphdriver"
    refuse("unrecognized Docker image store")


def expected_images(release_dir, store):
    out = {}
    with open(os.path.join(release_dir, "expected-images.%s.txt" % store)) as f:
        for line in f:
            line = line.strip()
            if line:
                ref, _, ident = line.partition("=")
                if not re.match(r"^codemastery\.invalid/[a-z-]+:release$", ref) or not re.match(r"^sha256:[0-9a-f]{64}$",
                                                                                               ident):
                    refuse("malformed expected image line")
                out[ref] = ident
    if sorted(out) != sorted("codemastery.invalid/%s:release" % s for s in SERVICES):
        refuse("expected images do not cover exactly the five services")
    return out


def volumes_report(found):
    d = recovery.Docker()
    labelled = (docker_text("volume", "ls", "--filter", "label=com.docker.compose.project=%s" % PROJECT,
                            "--format", "{{.Name}}") or "").split()
    expected = ["%s_postgres-data" % PROJECT, "%s_minio-data" % PROJECT]
    unknown = sorted(set(labelled) - set(expected))
    present = {}
    for name in expected:
        if name in labelled or docker_text("volume", "inspect", "--format", "{{.Name}}", name):
            present[name] = recovery.volume_inventory(d, name)["created"]
    return {"expected": expected, "present": present, "unknown_project_volumes": unknown}


def disk_report(paths=("/", "/var/lib/docker")):
    out = {}
    for p in paths:
        if os.path.exists(p):
            st = os.statvfs(p)
            out[p] = {"free_bytes": st.f_bavail * st.f_frsize, "free_inodes": st.f_favail}
    return out


def capacity_gate(cfg, archive_size, data_bytes):
    cap = cfg["capacity"]
    need = archive_size * (1 + cap["image_expansion_factor"]) + 2 * data_bytes + cap["margin_bytes"]
    disk = disk_report()
    root = disk.get("/var/lib/docker") or disk["/"]
    problems = []
    if root["free_bytes"] < need:
        problems.append("free disk %d bytes < required %d (archive x%d, two recovery sets, margin)"
                        % (root["free_bytes"], need, 1 + cap["image_expansion_factor"]))
    if root["free_inodes"] < cap["min_free_inodes"]:
        problems.append("free inodes %d < %d" % (root["free_inodes"], cap["min_free_inodes"]))
    return {"required_bytes": need, "disk": disk, "problems": problems}


def data_bytes(found):
    total = 0
    for name in ("%s_postgres-data" % PROJECT, "%s_minio-data" % PROJECT):
        mp = docker_text("volume", "inspect", "--format", "{{.Mountpoint}}", name)
        if mp and os.path.isdir(mp):
            r = run(["du", "-sb", mp], check=False)
            if r.returncode == 0:
                total += int(r.stdout.split()[0])
    return total


def deploy_sh(source_dir):
    return os.path.join(source_dir, "infrastructure", "vm", "deploy.sh")


def preflight_args(cfg, source_dir):
    return ["bash", deploy_sh(source_dir), "preflight", "--env-file", cfg["env_file"], "--origin", cfg["origin"],
            "--compose-override", os.path.join(source_dir, "infrastructure", "vm", "docker-compose.vm.yml")]


def run_logged(argv, log_path, timeout=None):
    with open(log_path, "ab") as f:
        os.fchmod(f.fileno(), 0o600)
        f.write(("\n$ %s\n" % " ".join(a if not a.startswith("--") else a for a in argv[:3])).encode("utf-8"))
        r = subprocess.run(argv, stdout=f, stderr=subprocess.STDOUT, timeout=timeout)
    return r.returncode


def tail(path, n=40):
    try:
        with open(path, "rb") as f:
            return f.read().decode("utf-8", "replace").splitlines()[-n:]
    except OSError:
        return []


def evaluate(cfg, release, release_dir, source_dir, log_path):
    """All read-only gates: deploy.sh preflight, image store, compatibility, volumes, capacity."""
    report = {"version": release["version"], "source_revision": release["source_revision"]}
    rc = run_logged(preflight_args(cfg, source_dir), log_path, timeout=600)
    report["preflight"] = "PASS" if rc == 0 else "FAIL"
    report["image_store"] = image_store()
    with open(deploy_sh(source_dir)) as f:
        rule, rows = deferral_rule(f.read())
    migrations = release_migrations(release, source_dir)
    history, present, tables, found = target_schema(rows)
    report["compatibility"] = compatibility(history, present, migrations, rule, rows, tables)
    report["compatibility"]["deferral_rule"] = rule
    report["volumes"] = volumes_report(found)
    report["existing_state"] = bool(found) or bool(report["volumes"]["present"])
    report["containers"] = {s: {"id": cid[:12], "image": docker_text("inspect", "--format", "{{.Image}}", cid),
                                "running": recovery.Docker().state(cid).get("Running")}
                            for s, cid in sorted(found.items())}
    report["capacity"] = capacity_gate(cfg, release["archive"]["size"], data_bytes(found))
    blockers = []
    if report["preflight"] != "PASS":
        blockers.append("deploy.sh preflight failed (see the VM log)")
    blockers += report["compatibility"]["blocked"]
    if report["volumes"]["unknown_project_volumes"]:
        blockers.append("unknown codemastery volumes: %s" % ", ".join(report["volumes"]["unknown_project_volumes"]))
    blockers += report["capacity"]["problems"]
    report["blockers"] = blockers
    return report


# --------------------------------------------------------------------------- hold / recovery gate

def hold_path():
    return state("backup", "hold.json")


def recovery_gate(set_id, manifest_sha, found, release_report):
    """Evidence for deploy.sh's four P9 attestations; each flag is passed only if its evidence holds."""
    problems = []
    if os.path.exists(state("backup", "capture-intent.json")):
        problems.append("an incomplete capture intent requires operator inspection")
    if not os.path.isfile(hold_path()):
        return {"problems": ["no application hold: run the prepare operation (pre-update capture) first"]}
    hold = load_json(hold_path(), "hold")
    if hold.get("deployment_attempt"):
        problems.append("a deployment was already attempted after this cut; a Leader recovery decision is required")
    if not hold.get("postgres_fenced"):
        problems.append("the held point has no enforced PostgreSQL connection fence")
    elif recovery.pg_other_clients(recovery.Docker(), hold["postgres"]):
        problems.append("a PostgreSQL client is active during the hold")
    if hold["set_id"] != set_id or hold["manifest_sha256"] != manifest_sha:
        problems.append("the requested recovery point is not the held pre-update point %s" % hold["set_id"])
    set_dir = state("backup", "sets", set_id)
    if not os.path.isdir(set_dir) or recovery.set_manifest_sha256(set_dir) != manifest_sha:
        problems.append("the recovery point is not staged on the VM with that manifest")
        return {"problems": problems}
    producer = PRODUCER_KEY
    pub = os.path.join(tempfile.mkdtemp(prefix="cm-pub-"), "producer.pub.pem")
    run([recovery.openssl_bin()[0], "pkey", "-in", producer, "-pubout", "-out", pub])
    try:
        manifest = recovery.verify_set(set_dir, pub, recovery.public_key_fingerprint(pub))
    finally:
        shutil.rmtree(os.path.dirname(pub), ignore_errors=True)
    if manifest["kind"] != "pre-update":
        problems.append("the recovery point is not a pre-update point")
    ack_path = state("backup", "acks", set_id + ".json")
    ack = load_json(ack_path, "acknowledgement") if os.path.isfile(ack_path) else None
    if not ack or ack.get("manifest_sha256") != manifest_sha:
        problems.append("the publisher has not acknowledged independent off-VM custody of this point")
    elif not ack["level"].endswith("+RESTORE_TESTED") or not recovery.restore_qualified(ack.get("restore_test")):
        problems.append("the point has no passing isolated restore test (acknowledged level %s)" % ack["level"])
    cut = recovery.parse_iso(manifest["t_cut"], "t_cut")
    if cut > utc_now():
        problems.append("the recovery cut is in the future (clock disagreement)")
    for service, cid in (hold.get("held") or {}).items():
        st = recovery.Docker().state(cid) if docker_text("inspect", "--format", "{{.Id}}", cid) else None
        if st is None:
            problems.append("the held %s container no longer exists" % service)
            continue
        if st.get("Running") or st.get("StartedAt") != hold["started_at"].get(service):
            problems.append("%s ran after the recovery cut; writes may be missing from the point" % service)
        if docker_text("inspect", "--format", "{{.HostConfig.RestartPolicy.Name}}", cid) != "no":
            problems.append("%s can automatically restart during the hold" % service)
    for s in ("backend", "frontend"):
        if s in found and recovery.Docker().state(found[s]).get("Running"):
            problems.append("%s is running; the application must stay held from the cut to the update" % s)
    vols = release_report["volumes"]["present"]
    for v in manifest["volumes"]:
        if vols.get(v["name"]) != v["created"]:
            problems.append("volume %s is not the one captured (persistent state not preserved)" % v["name"])
    history, _, _, _ = target_schema([])
    if [(h["version"], h["checksum"], h["success"]) for h in history or []] != \
            [(h["version"] or None, h["checksum"], h["success"]) for h in manifest["postgres"]["flyway_history"]]:
        problems.append("the schema changed after the recovery cut")
    return {"problems": problems, "set_id": set_id, "t_cut": manifest["t_cut"], "ack": ack, "manifest": manifest_sha}


# --------------------------------------------------------------------------- operations

def emit(result, code=0):
    print(json.dumps(result, indent=2, sort_keys=True))
    return code


def record(kind, result):
    os.makedirs(state("deployments"), mode=0o700, exist_ok=True)
    name = "%s-%s.json" % (utc_now().strftime("%Y%m%dT%H%M%SZ"), kind)
    write_private(state("deployments", name), dump_json(result))
    return name


def new_log(kind):
    os.makedirs(state("deployments"), mode=0o700, exist_ok=True)
    return state("deployments", "%s-%s.log" % (utc_now().strftime("%Y%m%dT%H%M%SZ"), kind))


def op_plan(args):
    cfg = config()
    log_path = new_log("plan")
    release, release_dir = stage_release(args.version, args.release_json_sha256, full=False)
    source_dir, source_state = install_source(release)
    report = evaluate(cfg, release, release_dir, source_dir, log_path)
    report.update({"operation": "plan", "source_checkout": source_dir, "source_checkout_state": source_state,
                   "hold": load_json(hold_path(), "hold") if os.path.isfile(hold_path()) else None,
                   "result": "READY" if not report["blockers"] else "BLOCKED", "log": os.path.basename(log_path)})
    if report["result"] == "BLOCKED":
        report["log_tail"] = tail(log_path) if report["preflight"] != "PASS" else []
    record("plan", report)
    return emit(report, 0 if report["result"] == "READY" else 3)


def op_prepare(args):
    cfg = config()
    log_path = new_log("prepare")
    with Lock("prepare"):
        release, release_dir = stage_release(args.version, args.release_json_sha256, full=True)
        source_dir, source_state = install_source(release)
        report = evaluate(cfg, release, release_dir, source_dir, log_path)
        report.update({"operation": "prepare", "source_checkout": source_dir, "log": os.path.basename(log_path)})
        if report["blockers"]:
            report["result"] = "BLOCKED"
            report["log_tail"] = tail(log_path) if report["preflight"] != "PASS" else []
            record("prepare", report)
            return emit(report, 3)
        if not report["existing_state"]:
            report["result"] = "READY_FRESH"
            report["capture"] = "not needed: no existing containers or volumes"
            record("prepare", report)
            return emit(report)
        if os.path.isfile(hold_path()):
            report["result"] = "HOLD_EXISTS"
            report["hold"] = load_json(hold_path(), "hold")
            return emit(report, 3)
        report["capture"] = recovery.capture("pre-update", True, backup_root=state("backup"),
                                             recipient_cert=RECIPIENT_CERT, producer_key=PRODUCER_KEY,
                                             project=PROJECT, tar_mode=cfg["tar_mode"],
                                             stop_timeout=cfg["stop_timeout"],
                                             deployment=last_deployment(), log=log)
        report["result"] = "HELD_FOR_PUBLICATION"
        report["next"] = ("Run the recovery publisher on the operator workstation, then dispatch the deploy operation "
                          "with recovery_set=%s and recovery_manifest_sha256=%s, or the resume operation to cancel."
                          % (report["capture"]["set_id"], report["capture"]["manifest_sha256"]))
        record("prepare", report)
        return emit(report)


def last_deployment():
    path = state("deployments", "current.json")
    if os.path.isfile(path):
        cur = load_json(path, "current deployment")
        return {k: cur.get(k) for k in ("version", "source_revision", "archive_sha256", "deployed_at")}
    return {"version": "unknown (not deployed by ADR017 tooling)"}


def decode_context(blob):
    try:
        ctx = json.loads(base64.urlsafe_b64decode(blob.encode("ascii") + b"==").decode("utf-8"))
    except (ValueError, UnicodeDecodeError):
        refuse("malformed deployment context")
    for key in ("authorization", "public_exposure_authorization", "packet_filter_verification"):
        value = ctx.get(key)
        if not isinstance(value, str) or not value.strip() or "\n" in value or "\r" in value or len(value) > 500:
            refuse("deployment context %s must be a single-line non-empty reference" % key)
    return ctx


def op_deploy(args):
    cfg = config()
    ctx = decode_context(args.context)
    log_path = new_log("deploy")
    with Lock("deploy"):
        release_dir = state("releases", args.version)
        release = read_release(os.path.join(release_dir, "release.json"), args.release_json_sha256)
        archive = os.path.join(release_dir, release["archive"]["name"])
        if not os.path.isfile(archive) or sha256(archive) != release["archive"]["sha256"]:
            refuse("the staged archive is missing or modified; run prepare again")
        source_dir, _ = install_source(release)
        report = evaluate(cfg, release, release_dir, source_dir, log_path)
        report["operation"] = "deploy"
        report["log"] = os.path.basename(log_path)
        _, found = pg_container()
        gate = None
        if report["existing_state"]:
            gate = recovery_gate(args.recovery_set, args.recovery_manifest_sha256, found, report)
            report["recovery_gate"] = gate
            report["blockers"] += gate["problems"]
        if report["blockers"]:
            report["result"] = "BLOCKED"
            report["log_tail"] = tail(log_path) if report["preflight"] != "PASS" else []
            record("deploy", report)
            return emit(report, 3)
        store = report["image_store"]
        images = expected_images(release_dir, store)
        q01 = "%s (release.json qualification Q01: exact initializer suite)" % release["candidate"]["run"]["url"]
        argv = ["bash", deploy_sh(source_dir), "all", "--env-file", cfg["env_file"], "--origin", cfg["origin"],
                "--compose-override", os.path.join(source_dir, "infrastructure", "vm", "docker-compose.vm.yml"),
                "--archive", archive, "--archive-sha256", release["archive"]["sha256"],
                "--wait-timeout", str(cfg["wait_timeout"]), "--source-revision", release["source_revision"],
                "--initializer-qualification", q01, "--provision-authorization", ctx["authorization"],
                "--deployment-authorization", ctx["authorization"],
                "--public-exposure-authorization", ctx["public_exposure_authorization"],
                "--packet-filter-verification", ctx["packet_filter_verification"]]
        for ref, ident in sorted(images.items()):
            argv += ["--expect-image", "%s=%s" % (ref, ident)]
        if report["existing_state"]:
            # Each attestation is supported by the evidence evaluated above, never asserted blindly.
            argv += ["--recovery-artifact-verified", "--schema-image-compatibility-verified",
                     "--persistent-state-preserved", "--update-authorization",
                     "%s; recovery point %s (cut %s)" % (ctx["authorization"], gate["set_id"], gate["t_cut"])]
        log("running deploy.sh all from %s (output in %s)" % (source_dir, log_path))
        if gate:
            hold = load_json(hold_path(), "hold")
            # Persist BEFORE anything can migrate/provision. Old containers can
            # survive a failed migration, so their IDs alone never authorize resume.
            hold["deployment_attempt"] = iso(utc_now())
            write_private(hold_path(), dump_json(hold))
            recovery.pg_fence(recovery.Docker(), hold["postgres"], restore=True)
            recovery.restore_policies(recovery.Docker(), hold["restart_policies"])
        rc = run_logged(argv, log_path)
        report["deploy_sh_exit"] = rc
        if rc != 0:
            stopped = []
            for service in ("frontend", "backend"):
                cid = recovery.Docker().project_containers(PROJECT).get(service)
                if cid:
                    run(["docker", "stop", "--time", str(cfg["stop_timeout"]), cid], check=False)
                    stopped.append(not recovery.Docker().state(cid).get("Running"))
            report["result"] = "FAILED_CONTAINED" if all(stopped) else "FAILED_CONTAINMENT_INCOMPLETE"
            report["log_tail"] = tail(log_path)
            report["next"] = ("The hold and recovery point stay; inspect containment and the log. "
                              "Persistent state may have changed. A Leader recovery decision is required; "
                              "resume and automatic retry are refused after a deployment attempt.")
            record("deploy", report)
            return emit(report, 4)
        report["verification"] = verify_state(cfg, release, release_dir, store)
        current = {"version": release["version"], "source_revision": release["source_revision"],
                   "archive_sha256": release["archive"]["sha256"], "deployed_at": iso(utc_now()),
                   "images": images, "recovery_point": gate and gate["set_id"], "authorization": ctx["authorization"]}
        write_private(state("deployments", "current.json"), dump_json(current))
        if os.path.isfile(hold_path()) and not report["verification"]["problems"]:
            os.makedirs(state("backup", "holds"), mode=0o700, exist_ok=True)
            os.rename(hold_path(), state("backup", "holds", "%s.deployed.json" % gate["set_id"]))
        report["result"] = "DEPLOYED" if not report["verification"]["problems"] else "DEPLOYED_WITH_FINDINGS"
        record("deploy", report)
        return emit(report, 0 if report["result"] == "DEPLOYED" else 5)


def frontend_exec(found, *cmd):
    cid = found.get("frontend")
    r = run(["docker", "exec", cid] + list(cmd), check=False, timeout=30)
    return r.returncode, r.stdout.decode("utf-8", "replace")


def served_certificate(host, server_name, port=443, timeout=15):
    """The served leaf, verified against the host trust store with hostname checking."""
    context = ssl.create_default_context()
    context.minimum_version = ssl.TLSVersion.TLSv1_2
    with socket.create_connection((host, port), timeout=timeout) as raw:
        with context.wrap_socket(raw, server_hostname=server_name) as conn:
            der = conn.getpeercert(binary_form=True)
            info = conn.getpeercert()
    not_after = datetime.datetime.strptime(info["notAfter"], "%b %d %H:%M:%S %Y %Z").replace(
        tzinfo=datetime.timezone.utc)
    return {"sha256": hashlib.sha256(der).hexdigest(), "not_after": iso(not_after),
            "days_left": (not_after - utc_now()).days, "san": [v for k, v in info.get("subjectAltName", ()) if k == "DNS"]}


def file_leaf(path):
    binary = recovery.openssl_bin()[0]
    der = run([binary, "x509", "-in", path, "-outform", "DER"]).stdout
    end = run([binary, "x509", "-in", path, "-noout", "-enddate"]).stdout.decode().strip().split("=", 1)[1]
    not_after = datetime.datetime.strptime(end, "%b %d %H:%M:%S %Y %Z").replace(tzinfo=datetime.timezone.utc)
    return {"sha256": hashlib.sha256(der).hexdigest(), "not_after": iso(not_after),
            "days_left": (not_after - utc_now()).days}


def verify_state(cfg, release, release_dir, store):
    """Post-deployment verification, kept in separate classes (infrastructure, readiness, TLS)."""
    images = expected_images(release_dir, store)
    found = recovery.Docker().project_containers(PROJECT)
    problems, checks = [], {}
    for service in SERVICES:
        cid = found.get(service)
        if not cid:
            problems.append("no %s container" % service)
            continue
        st = recovery.Docker().state(cid)
        image = docker_text("inspect", "--format", "{{.Image}}", cid)
        want = images["codemastery.invalid/%s:release" % service]
        restart = docker_text("inspect", "--format", "{{.HostConfig.RestartPolicy.Name}}", cid)
        health = (st.get("Health") or {}).get("Status")
        ok_state = (st.get("Status") == "exited" and st.get("ExitCode") == 0) if service == "minio-init" \
            else st.get("Running") is True
        checks[service] = {"image": image, "expected": want, "running": st.get("Running"), "health": health,
                           "restart": restart}
        if restart != ("no" if service == "minio-init" else "unless-stopped"):
            problems.append("%s has an unexpected restart policy" % service)
        if image != want:
            problems.append("%s runs %s, not the released identity" % (service, image))
        if not ok_state:
            problems.append("%s is not in its expected state" % service)
        if service in ("postgres", "frontend") and health != "healthy":
            problems.append("%s is not healthy" % service)
        ports = json.loads(docker_text("inspect", "--format", "{{json .HostConfig.PortBindings}}", cid) or "{}") or {}
        bound = {k: v for k, v in ports.items() if v}
        if service != "frontend" and bound:
            problems.append("%s publishes host ports" % service)
        if service == "frontend" and sorted(bound) != ["8080/tcp"]:
            problems.append("frontend publishes something other than 8080/tcp")
        if service == "frontend" and any(p.get("HostPort") != "443" for bindings in bound.values() for p in bindings):
            problems.append("frontend ingress is not bound exclusively to host port 443")
    infra = {"containers": checks}
    with open(deploy_sh(os.path.join(SOURCE_ROOT, release["source_revision"]))) as f:
        rule, rows = deferral_rule(f.read())
    migrations = release_migrations(release, os.path.join(SOURCE_ROOT, release["source_revision"]))
    history, present, tables, _ = target_schema(rows)
    compat = compatibility(history, present, migrations, rule, rows, tables)
    infra["flyway"] = {"state": compat["state"], "applied": compat["applied"]}
    if compat["state"] != "CURRENT":
        problems.append("Flyway history does not equal the release's migrations (%s)" % compat["state"])
    readiness = "NOT_CHECKED"
    if found.get("frontend"):
        rc, out = frontend_exec(found, "wget", "-q", "-O", "-", "http://backend:8080/actuator/health")
        try:
            health_body = json.loads(out) if rc == 0 else None
        except ValueError:
            health_body = None
        readiness = "UP" if isinstance(health_body, dict) and health_body.get("status") == "UP" else "NOT_UP"
        if readiness != "UP":
            problems.append("backend readiness is %s" % readiness)
    tls = {}
    try:
        host = cfg["origin"][len("https://"):]
        served = served_certificate("127.0.0.1", host)
        leaf = file_leaf(os.path.join(cfg["tls_dir"], "fullchain.pem"))
        tls = {"served": served, "installed": leaf, "match": served["sha256"] == leaf["sha256"]}
        if not tls["match"]:
            problems.append("the served certificate is not the installed one")
        if host not in served["san"]:
            problems.append("the served certificate does not name %s" % host)
    except (OSError, ssl.SSLError, RecoveryError, ValueError) as exc:
        problems.append("local TLS verification failed (%s)" % exc.__class__.__name__)
    return {"infrastructure": infra, "readiness": readiness, "tls": tls, "problems": problems,
            "feature_behavior": "NOT_VERIFIED_HERE (QA01/QA07/DP03 evidence is separate)"}


def op_verify(args):
    cfg = config()
    cur = load_json(state("deployments", "current.json"), "current deployment")
    version = args.version or cur["version"]
    release_dir = state("releases", version)
    release = load_json(os.path.join(release_dir, "release.json"), "release.json")
    result = verify_state(cfg, release, release_dir, image_store())
    result.update({"operation": "verify", "version": version,
                   "result": "VERIFIED" if not result["problems"] else "FINDINGS"})
    record("verify", result)
    return emit(result, 0 if not result["problems"] else 5)


def op_resume(args):
    """Cancel a held update: restart the exact held containers if nothing replaced them."""
    with Lock("resume"):
        if not os.path.isfile(hold_path()):
            return emit({"operation": "resume", "result": "NO_HOLD"})
        hold = load_json(hold_path(), "hold")
        if hold.get("deployment_attempt"):
            refuse("a deployment attempt may have modified persistent state; a Leader recovery decision is required")
        started = []
        for service in ("backend", "frontend"):
            cid = (hold.get("held") or {}).get(service)
            if not cid:
                continue
            if not docker_text("inspect", "--format", "{{.Id}}", cid):
                refuse("the held %s container was replaced (a deployment attempt ran); a Leader recovery decision "
                       "is required, not a resume" % service)
        if hold.get("postgres_fenced"):
            recovery.pg_fence(recovery.Docker(), hold["postgres"], restore=True)
            hold["postgres_fenced"] = False
            write_private(hold_path(), dump_json(hold))
        recovery.restore_policies(recovery.Docker(), hold.get("restart_policies", {}))
        for service in ("backend", "frontend"):
            cid = (hold.get("held") or {}).get(service)
            if cid:
                run(["docker", "start", cid])
                recovery._wait(lambda: recovery.Docker().state(cid).get("Running"), config()["wait_timeout"],
                               "%s resume" % service)
                started.append(service)
        os.makedirs(state("backup", "holds"), mode=0o700, exist_ok=True)
        os.rename(hold_path(), state("backup", "holds", "%s.resumed.json" % hold["set_id"]))
        return emit({"operation": "resume", "result": "RESUMED", "started": started, "set_id": hold["set_id"]})


def op_capture(args):
    cfg = config()
    with Lock("capture"):
        result = recovery.capture(args.kind, False, backup_root=state("backup"), recipient_cert=RECIPIENT_CERT,
                                  producer_key=PRODUCER_KEY, project=PROJECT, tar_mode=cfg["tar_mode"],
                                  stop_timeout=cfg["stop_timeout"], deployment=last_deployment(), log=log)
    return emit(dict(result, operation="capture", result="CAPTURED"))


def op_status(args):
    now = utc_now()
    staged = recovery.list_staged(state("backup"))
    acked = [s for s in staged if s["acknowledged"] and recovery.restore_qualified(s["acknowledged"].get("restore_test"))]
    newest = None
    for s in acked:
        manifest = load_json(state("backup", "sets", s["set_id"], "manifest.json"), "recovery manifest")
        t = recovery.parse_iso(manifest["t_cut"], "t_cut")
        newest = t if newest is None or t > newest else newest
    age_state, hours = recovery.point_age_status(newest, now)
    out = {"operation": "status", "now": iso(now),
           "hold": load_json(hold_path(), "hold") if os.path.isfile(hold_path()) else None,
           "current_deployment": last_deployment(),
           "staged_sets": [{"set_id": s["set_id"], "complete": s["complete"],
                            "acknowledged": (s["acknowledged"] or {}).get("level")} for s in staged],
           "newest_acknowledged_point": {"state": age_state, "age_hours": hours and round(hours, 2)},
           "last_capture": (load_json(state("backup", "status.json"), "capture status")
                            if os.path.isfile(state("backup", "status.json")) else None),
           "tls": load_json(state("tls", "status.json"), "tls status") if os.path.isfile(state("tls", "status.json"))
           else None}
    return emit(out)


# --------------------------------------------------------------------------- TLS

def tls_status(cfg):
    host = cfg["origin"][len("https://"):]
    leaf = file_leaf(os.path.join(cfg["tls_dir"], "fullchain.pem"))
    out = {"domain": host, "installed": leaf, "checked_at": iso(utc_now())}
    try:
        out["served"] = served_certificate("127.0.0.1", host)
        out["served_matches_installed"] = out["served"]["sha256"] == leaf["sha256"]
    except (OSError, ssl.SSLError, ValueError) as exc:
        out["served"] = {"error": exc.__class__.__name__}
        out["served_matches_installed"] = False
    days = leaf["days_left"]
    out["state"] = "EXPIRED" if days < 0 else next(("WARN_%d" % w for w in reversed(TLS_WARN_DAYS) if days <= w), "OK")
    out["renewal_due"] = days <= TLS_RENEW_DAYS
    findings = []
    home = cfg["acme_home"]
    if os.path.isdir(home):
        for entry in sorted(os.listdir(home)):
            conf = os.path.join(home, entry, "%s.conf" % entry.rsplit("_", 1)[0])
            if entry.endswith("_ecc") and os.path.isfile(conf):
                with open(conf) as f:
                    text = f.read()
                domain = re.search(r"^Le_Domain='([^']*)'", text, re.M)
                key_path = re.search(r"^Le_RealKeyPath='([^']*)'", text, re.M)
                if domain and key_path and domain.group(1) != host and \
                        key_path.group(1).startswith(cfg["tls_dir"].rstrip("/") + "/"):
                    findings.append("acme.sh holds a %s configuration that installs into the live TLS directory; "
                                    "never run acme.sh --cron (only this tool's --renew -d %s)" % (domain.group(1), host))
    out["findings"] = findings
    os.makedirs(state("tls"), mode=0o700, exist_ok=True)
    write_private(state("tls", "status.json"), dump_json(out))
    return out


def op_tls_check(args):
    out = tls_status(config())
    out["operation"] = "tls-check"
    return emit(out, 0 if out["state"] == "OK" and out["served_matches_installed"] else 6)


def validate_pair(cfg, fullchain, key, staging=False):
    binary = recovery.openssl_bin()[0]
    host = cfg["origin"][len("https://"):]
    cert_pub = run([binary, "x509", "-in", fullchain, "-noout", "-pubkey"]).stdout
    a = hashlib.sha256(run([binary, "pkey", "-pubin", "-outform", "DER"], data=cert_pub).stdout).hexdigest()
    b = hashlib.sha256(run([binary, "pkey", "-in", key, "-pubout", "-outform", "DER"]).stdout).hexdigest()
    if a != b:
        refuse("the renewed private key does not match the renewed certificate")
    text = run([binary, "x509", "-in", fullchain, "-noout", "-ext", "subjectAltName"]).stdout.decode()
    names = re.findall(r"DNS:([^,\s]+)", text)
    if host not in names:
        refuse("the renewed certificate does not name %s" % host)
    if not staging:
        r = run([binary, "verify", "-purpose", "sslserver", "-verify_hostname", host, "-CAfile",
                 "/etc/ssl/certs/ca-certificates.crt", "-untrusted", fullchain, fullchain], check=False)
        if r.returncode != 0:
            refuse("the renewed chain does not verify against the host trust store for %s" % host)
    if run([binary, "x509", "-in", fullchain, "-noout", "-checkend", str(TLS_RENEW_DAYS * 86400)],
           check=False).returncode != 0:
        refuse("the renewed certificate is not valid for more than %d days" % TLS_RENEW_DAYS)


def op_tls_renew(args):
    """ADR009 section 9.3: renew only when due, stopping only the frontend, atomic replacement, readback."""
    cfg = config()
    status = tls_status(cfg)
    if not status["renewal_due"] and not args.force and not args.staging:
        return emit({"operation": "tls-renew", "result": "NOT_DUE", "days_left": status["installed"]["days_left"]})
    if os.path.exists(FREEZE):
        refuse("a demo/change freeze is active (%s); no challenge outage is allowed" % FREEZE)
    host = cfg["origin"][len("https://"):]
    acme = os.path.join(cfg["acme_home"], "acme.sh")
    if not os.path.isfile(acme):
        refuse("acme.sh is not installed at %s" % acme)
    with Lock("tls-renew"):
        found = recovery.Docker().project_containers(PROJECT)
        fe = found.get("frontend")
        if not fe or not recovery.Docker().state(fe).get("Running"):
            refuse("the frontend is not running; renewal never starts an absent ingress")
        work_home = None
        argv = [acme, "--home", cfg["acme_home"], "--renew", "-d", host, "--ecc", "--server", "letsencrypt"]
        if args.staging:
            work_home = tempfile.mkdtemp(prefix="cm-acme-staging-")
            argv = [acme, "--home", work_home, "--config-home", work_home, "--issue", "--alpn", "-d", host,
                    "--keylength", "ec-256", "--server", "letsencrypt_test", "--force"]
        elif args.force:
            argv.append("--force")
        staged = (os.path.join(work_home, host + "_ecc", "fullchain.cer"), os.path.join(work_home, host + "_ecc",
                                                                                         host + ".key")) \
            if work_home else ("/etc/codemastery/renewal-staging/fullchain.pem",
                               "/etc/codemastery/renewal-staging/privkey.pem")
        backup = os.path.join(cfg["tls_backup_root"], "tls-%s" % utc_now().strftime("%Y%m%dT%H%M%SZ"))
        live_chain, live_key = os.path.join(cfg["tls_dir"], "fullchain.pem"), os.path.join(cfg["tls_dir"], "privkey.pem")
        result = {"operation": "tls-renew", "staging": bool(args.staging)}
        log("stopping only the frontend to free TCP 443 for the TLS-ALPN-01 challenge")
        replaced = False
        failure = None

        def restore_previous():
            for name in ("fullchain.pem", "privkey.pem"):
                shutil.copy2(os.path.join(backup, name), os.path.join(cfg["tls_dir"], name + ".restore"))
                os.rename(os.path.join(cfg["tls_dir"], name + ".restore"), os.path.join(cfg["tls_dir"], name))
            os.chown(live_key, 101, 0)
            os.chmod(live_key, 0o400)

        def start_and_verify():
            run(["docker", "start", fe])
            recovery._wait(lambda: (recovery.Docker().state(fe).get("Health") or {}).get("Status") == "healthy",
                           cfg["wait_timeout"], "frontend health after renewal")
            rc, _ = frontend_exec(found, "nginx", "-t")
            if rc != 0:
                refuse("frontend nginx configuration check failed")
            served = served_certificate("127.0.0.1", host)
            if served["sha256"] != file_leaf(live_chain)["sha256"]:
                refuse("the frontend does not serve the installed certificate")
            return served

        try:
            run(["docker", "stop", "--time", str(cfg["stop_timeout"]), fe])
            r = subprocess.run(argv, stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL, timeout=600)
            if r.returncode != 0:
                refuse("acme.sh renewal failed (exit %d)" % r.returncode)
            validate_pair(cfg, staged[0], staged[1], staging=bool(args.staging))
            if not args.staging:
                os.makedirs(backup, mode=0o700)
                for src in (live_chain, live_key):
                    shutil.copy2(src, os.path.join(backup, os.path.basename(src)))
                tmp_chain, tmp_key = live_chain + ".new", live_key + ".new"
                shutil.copyfile(staged[0], tmp_chain)
                os.chown(tmp_chain, 0, 0)
                os.chmod(tmp_chain, 0o644)
                shutil.copyfile(staged[1], tmp_key)
                os.chown(tmp_key, 101, 0)
                os.chmod(tmp_key, 0o400)
                # Both old files are backed up. Mark before the first rename so a
                # failure between the two replacements also rolls the pair back.
                replaced = True
                os.rename(tmp_key, live_key)
                os.rename(tmp_chain, live_chain)
                result["previous_pair"] = backup
        except (RecoveryError, OSError, subprocess.TimeoutExpired) as exc:
            failure = exc
        finally:
            if work_home:
                shutil.rmtree(work_home, ignore_errors=True)
        if failure is not None and replaced:
            restore_previous()
            replaced = False
        try:
            served = start_and_verify()
        except (RecoveryError, OSError, ssl.SSLError, ValueError) as exc:
            if replaced:
                log("renewed certificate failed readback; restoring the previous pair")
                run(["docker", "stop", "--time", str(cfg["stop_timeout"]), fe], check=False)
                restore_previous()
                try:
                    start_and_verify()
                except (RecoveryError, OSError, ssl.SSLError, ValueError):
                    refuse("renewal and previous frontend recovery both failed; operator intervention required")
            refuse("frontend recovery/readback failed (%s); renewal is not qualified" % type(exc).__name__)
        if failure is not None:
            refuse("renewal failed (%s); previous frontend health and TLS verified" % type(failure).__name__)
        result.update({"result": "STAGING_CHALLENGE_PASS" if args.staging else "RENEWED", "served": served})
        tls_status(cfg)
        return emit(result)


def op_timers(args):
    units = ("codemastery-capture.timer", "codemastery-tls-renew.timer")
    if args.action == "status":
        r = run(["systemctl", "list-timers", "--all", "--no-pager"] + list(units), check=False)
        return emit({"operation": "timers", "status": r.stdout.decode("utf-8", "replace").splitlines()})
    verb = ["enable", "--now"] if args.action == "enable" else ["disable", "--now"]
    for unit in (args.unit,) if args.unit else units:
        if unit not in units:
            refuse("unknown timer")
        run(["systemctl"] + verb + [unit])
    return emit({"operation": "timers", "result": args.action})


# --------------------------------------------------------------------------- recovery passthrough (operator)

def op_recovery_list(args):
    return emit(recovery.list_staged(state("backup")))


def op_recovery_send(args):
    path = recovery.staged_file(args.set_id, args.file, state("backup"))
    with open(path, "rb") as f:
        shutil.copyfileobj(f, sys.stdout.buffer, recovery.CHUNK)
    return 0


def op_recovery_ack(args):
    by = os.environ.get("SUDO_USER") or "root"
    evidence = recovery.load_json_bytes(base64.urlsafe_b64decode(args.evidence + "=="), "restore evidence") \
        if args.evidence else None
    with Lock("recovery-ack"):
        rec = recovery.acknowledge(args.set_id, args.manifest_sha256, args.level, by, state("backup"), evidence)
        removed = recovery.prune_staged(state("backup"), log=log)
    return emit({"operation": "recovery-ack", "ack": rec, "pruned_staged": removed})


def op_inventory(args):
    """Read-only, secret-free host evidence (the Phase 5 audit, repeatable)."""
    found = recovery.Docker().project_containers(PROJECT)
    out = {"operation": "inventory", "at": iso(utc_now()), "host": os.uname()[1],
           "kernel": os.uname()[2], "image_store": image_store(),
           "docker": docker_text("version", "--format", "{{.Server.Version}}"),
           "compose": docker_text("compose", "version", "--short"), "disk": disk_report(),
           "containers": {s: {"id": cid[:12], "image": docker_text("inspect", "--format", "{{.Image}}", cid),
                              "state": recovery.Docker().state(cid).get("Status")} for s, cid in sorted(found.items())},
           "volumes": volumes_report(found), "current_deployment": last_deployment()}
    history, _, _, _ = target_schema([]) if found.get("postgres") else (None, {}, False, found)
    out["flyway_history"] = history
    return emit(out)


def _clearing(op):
    """Uploads are transient: each run re-sends what it needs, so incoming/ is emptied after use."""
    def run_op(args):
        try:
            return op(args)
        finally:
            clear_incoming()
    return run_op


OPS = {"plan": _clearing(op_plan), "prepare": _clearing(op_prepare), "deploy": _clearing(op_deploy), "verify": op_verify, "resume": op_resume,
       "status": op_status, "inventory": op_inventory, "capture": op_capture, "tls-check": op_tls_check,
       "tls-renew": op_tls_renew, "timers": op_timers, "recovery-list": op_recovery_list,
       "recovery-send": op_recovery_send, "recovery-ack": op_recovery_ack}


def parser():
    p = argparse.ArgumentParser(prog="codemastery-ops")
    sub = p.add_subparsers(dest="op", required=True)
    for name in ("plan", "prepare"):
        s = sub.add_parser(name)
        s.add_argument("--version", required=True)
        s.add_argument("--release-json-sha256", required=True)
    s = sub.add_parser("deploy")
    s.add_argument("--version", required=True)
    s.add_argument("--release-json-sha256", required=True)
    s.add_argument("--recovery-set", default="")
    s.add_argument("--recovery-manifest-sha256", default="")
    s.add_argument("--context", required=True)
    s = sub.add_parser("verify")
    s.add_argument("--version")
    for name in ("resume", "status", "inventory", "tls-check", "recovery-list"):
        sub.add_parser(name)
    s = sub.add_parser("capture")
    s.add_argument("--kind", choices=("scheduled", "manual"), required=True)
    s = sub.add_parser("tls-renew")
    s.add_argument("--staging", action="store_true")
    s.add_argument("--force", action="store_true")
    s = sub.add_parser("timers")
    s.add_argument("action", choices=("enable", "disable", "status"))
    s.add_argument("--unit")
    s = sub.add_parser("recovery-send")
    s.add_argument("set_id")
    s.add_argument("file")
    s = sub.add_parser("recovery-ack")
    s.add_argument("set_id")
    s.add_argument("manifest_sha256")
    s.add_argument("level")
    s.add_argument("evidence", nargs="?", default="")
    return p


def main(argv=None):
    args = parser().parse_args(argv)
    for key in ("version",):
        value = getattr(args, key, None)
        if value and not VERSION.match(value):
            print("REFUSED: malformed version", file=sys.stderr)
            return 2
    for key in ("release_json_sha256", "recovery_manifest_sha256", "manifest_sha256"):
        value = getattr(args, key, None)
        if value and not HEX64.match(value):
            print("REFUSED: %s must be 64 lowercase hex" % key, file=sys.stderr)
            return 2
    try:
        return OPS[args.op](args)
    except RecoveryError as exc:
        print("REFUSED: %s" % exc, file=sys.stderr)
        return emit({"operation": args.op, "result": "REFUSED", "reason": str(exc)}, 2)
    except subprocess.TimeoutExpired:
        print("REFUSED: a step exceeded its deadline", file=sys.stderr)
        return 2


if __name__ == "__main__":
    os.umask(0o077)
    sys.exit(main())
