#!/usr/bin/env python3
"""CodeMastery coordinated recovery sets (ADR009 sections 5, 9.4 and 9.5; ADR017).

One module, two places:

* On the VM (as root, called by ops.py with the operations lock held): `capture`
  quiesces every application writer, takes a PostgreSQL 17 custom-format dump and an
  opaque cold copy of the whole MinIO data volume, encrypts each stream before it
  touches the disk (OpenSSL 3 CMS AuthEnvelopedData, AES-256-GCM, RSA-OAEP/SHA-256
  recipient), writes a manifest signed with the producer key (RSA-PSS/SHA-256) and
  resumes or holds the application. The VM holds only the recipient *certificate*:
  it can create recovery sets but never read them.
* On the Leader's workstation: `publish` pulls staged sets over the pinned SSH
  channel, verifies the signature against the independently pinned producer key,
  checks every length and hash, authenticates and decrypts every component without
  writing plaintext, keeps the independent copy, optionally publishes to Azure Blob
  (conditional create, completion marker last, full readback), appends the
  off-VM ledger and acknowledges the set on the VM. `restore-test`, `status` and
  `prune` complete the protocol.

Standard library only; Python 3.9 compatible (the macOS system interpreter). Nothing
here prints a secret, a passphrase, a token or decrypted content.
"""
import argparse
import base64
import datetime
import hashlib
import json
import os
import re
import secrets
import shlex
import shutil
import ssl
import stat
import subprocess
import sys
import tarfile
import tempfile
import time
import urllib.error
import urllib.parse
import urllib.request

SCHEMA = "codemastery.recovery-set/v1"
LEDGER_SCHEMA = "codemastery.recovery-ledger/v1"
ACK_SCHEMA = "codemastery.recovery-ack/v1"
FRAME = b"CMREC1\n"
KINDS = ("scheduled", "pre-update", "manual")
SET_ID = re.compile(r"^CMRS-[0-9]{8}T[0-9]{6}Z-(scheduled|pre-update|manual)-[0-9a-f]{8}$")
HEX64 = re.compile(r"^[0-9a-f]{64}$")
DIGEST = re.compile(r"^sha256:[0-9a-f]{64}$")
TIMESTAMP = re.compile(r"^[0-9]{4}-[0-9]{2}-[0-9]{2}T[0-9]{2}:[0-9]{2}:[0-9]{2}Z$")
COMPONENTS = ("postgres.dump.cms", "minio-data.tar.cms")
MANIFEST = "manifest.json"
SIGNATURE = "manifest.sig"
SET_FILES = (MANIFEST, SIGNATURE) + COMPONENTS
COMPLETE_MARKER = "COMPLETE.json"
ENCRYPTION = "cms-authenveloped-aes-256-gcm/rsa-oaep-sha256-mgf1-sha256"
SIGNING = "rsa-pss-sha256-saltlen-digest"
LEVELS = ("VERIFIED_INDEPENDENT", "VERIFIED_INDEPENDENT_AZURE")
CHUNK = 1 << 20
WARN_AGE_HOURS = 16       # ADR009 section 4.1: warning at 16 h
BREACH_AGE_HOURS = 24     # objective breach above 24 h
PROJECT = "codemastery"

# VM locations (ADR009 section 9.4/9.5).
VM_BACKUP_ROOT = "/var/lib/codemastery/backup"
VM_RECIPIENT_CERT = "/etc/codemastery/backup/recipient.crt"
VM_PRODUCER_KEY = "/etc/codemastery/backup/producer-signing.key"


class RecoveryError(Exception):
    """A refusal: the message is safe to print (no secrets, no content)."""


def refuse(message):
    raise RecoveryError(message)


def utc_now():
    return datetime.datetime.now(datetime.timezone.utc).replace(microsecond=0)


def iso(ts):
    return ts.astimezone(datetime.timezone.utc).strftime("%Y-%m-%dT%H:%M:%SZ")


def parse_iso(value, what):
    if not isinstance(value, str) or not TIMESTAMP.match(value):
        refuse("%s is not a UTC timestamp" % what)
    return datetime.datetime.strptime(value, "%Y-%m-%dT%H:%M:%SZ").replace(tzinfo=datetime.timezone.utc)


def sha256_file(path):
    h = hashlib.sha256()
    with open(path, "rb") as f:
        for block in iter(lambda: f.read(CHUNK), b""):
            h.update(block)
    return h.hexdigest()


def _no_duplicates(pairs):
    seen = {}
    for key, value in pairs:
        if key in seen:
            raise ValueError("duplicate key %r" % key)
        seen[key] = value
    return seen


def load_json_bytes(data, what):
    try:
        return json.loads(data.decode("utf-8"), object_pairs_hook=_no_duplicates)
    except (ValueError, UnicodeDecodeError) as exc:
        refuse("%s is not valid JSON (%s)" % (what, exc.__class__.__name__))


def load_json(path, what):
    try:
        with open(path, "rb") as f:
            return load_json_bytes(f.read(), what)
    except OSError:
        refuse("%s cannot be read" % what)


def dump_json(data):
    return (json.dumps(data, indent=2, sort_keys=True, ensure_ascii=True) + "\n").encode("ascii")


def write_private(path, data, mode=0o600):
    """Write a new file atomically, owner-only, never following a link."""
    directory = os.path.dirname(path) or "."
    fd, tmp = tempfile.mkstemp(prefix=".tmp-", dir=directory)
    try:
        os.fchmod(fd, mode)
        with os.fdopen(fd, "wb") as f:
            f.write(data)
            f.flush()
            os.fsync(f.fileno())
        os.replace(tmp, path)
    except BaseException:
        try:
            os.unlink(tmp)
        except OSError:
            pass
        raise


def private_dir(path, mode=0o700):
    os.makedirs(path, mode=mode, exist_ok=True)
    st = os.lstat(path)
    if not stat.S_ISDIR(st.st_mode) or stat.S_ISLNK(st.st_mode):
        refuse("%s is not a directory" % path)
    if st.st_uid != os.geteuid():
        refuse("%s is not owned by the current user" % path)
    if stat.S_IMODE(st.st_mode) & 0o077:
        os.chmod(path, mode)
    return path


# --------------------------------------------------------------------------- OpenSSL

def openssl_bin():
    """OpenSSL 3 only: LibreSSL (macOS /usr/bin/openssl) has no CMS AuthEnvelopedData/GCM."""
    binary = os.environ.get("CODEMASTERY_OPENSSL") or "openssl"
    try:
        version = subprocess.run([binary, "version"], stdin=subprocess.DEVNULL, stdout=subprocess.PIPE,
                                 stderr=subprocess.DEVNULL,
                                 check=True).stdout.decode("ascii", "replace").strip()
    except (OSError, subprocess.CalledProcessError):
        refuse("OpenSSL 3 is required (set CODEMASTERY_OPENSSL to an OpenSSL 3 binary)")
    if not version.startswith("OpenSSL 3."):
        refuse("OpenSSL 3 is required; %s is %s (set CODEMASTERY_OPENSSL)" % (binary, version.split(" ")[0]))
    return binary, version


def _run(argv, data=None, what="command", pass_fds=()):
    # OpenSSL children never inherit stdin: with an inherited non-terminal stdin, OpenSSL 3.6
    # ignored -passin/-passout sources and fell back to its UI (reproduced on macOS).
    result = subprocess.run(argv, input=data, stdout=subprocess.PIPE, stderr=subprocess.PIPE, pass_fds=pass_fds,
                            **({} if data is not None else {"stdin": subprocess.DEVNULL}))
    if result.returncode != 0:
        refuse("%s failed (exit %d: %s)" % (what, result.returncode, _diagnostic(result.stderr)))
    return result.stdout


def _diagnostic(stderr):
    """The last OpenSSL/tool error line: library error codes only, never content or secrets."""
    lines = [l for l in (stderr or b"").decode("utf-8", "replace").splitlines() if l.strip()]
    return re.sub(r"[^\x20-\x7e]", "?", lines[-1])[-200:] if lines else "no diagnostic"


def public_key_fingerprint(path, private=False):
    """SHA-256 of the DER SubjectPublicKeyInfo: the identity pinned in trust records."""
    binary, _ = openssl_bin()
    argv = [binary, "pkey", "-in", path, "-pubout", "-outform", "DER"]
    if not private:
        argv[2:2] = ["-pubin"]
    return hashlib.sha256(_run(argv, what="public key fingerprint")).hexdigest()


def certificate_key_fingerprint(path):
    binary, _ = openssl_bin()
    pem = _run([binary, "x509", "-in", path, "-noout", "-pubkey"], what="recipient certificate")
    return hashlib.sha256(_run([binary, "pkey", "-pubin", "-outform", "DER"], data=pem,
                               what="recipient certificate key")).hexdigest()


def sign_file(path, key, signature):
    binary, _ = openssl_bin()
    _run([binary, "dgst", "-sha256", "-sign", key, "-sigopt", "rsa_padding_mode:pss",
          "-sigopt", "rsa_pss_saltlen:digest", "-out", signature, path], what="manifest signing")


def verify_signature(path, signature, public_key):
    binary, _ = openssl_bin()
    result = subprocess.run([binary, "dgst", "-sha256", "-verify", public_key, "-sigopt", "rsa_padding_mode:pss",
                             "-sigopt", "rsa_pss_saltlen:digest", "-signature", signature, path],
                            stdin=subprocess.DEVNULL, stdout=subprocess.PIPE, stderr=subprocess.PIPE)
    if result.returncode != 0:
        refuse("the manifest signature does not verify against the pinned producer key")


def encrypt_stream(producer, recipient_cert, out_path, what):
    """Frame (CMREC1) and encrypt a producer's stdout into out_path; nothing plaintext touches disk.

    Every producer and encryptor exit status is checked (pipefail semantics); a failed
    or truncated stream never yields a component."""
    binary, _ = openssl_bin()
    with open(out_path, "xb") as sink:
        os.fchmod(sink.fileno(), 0o600)
        enc = subprocess.Popen([binary, "cms", "-encrypt", "-binary", "-stream", "-outform", "DER", "-aes-256-gcm",
                                "-recip", recipient_cert, "-keyopt", "rsa_padding_mode:oaep",
                                "-keyopt", "rsa_oaep_md:sha256", "-keyopt", "rsa_mgf1_md:sha256"],
                               stdin=subprocess.PIPE, stdout=sink, stderr=subprocess.DEVNULL)
        prod_log, enc_log = tempfile.TemporaryFile(), None
        prod = subprocess.Popen(producer, stdin=subprocess.DEVNULL, stdout=subprocess.PIPE, stderr=prod_log)
        digest, length = hashlib.sha256(), 0
        broken = False
        try:
            enc.stdin.write(FRAME)
            for block in iter(lambda: prod.stdout.read(CHUNK), b""):
                digest.update(block)
                length += len(block)
                enc.stdin.write(block)
        except BrokenPipeError:
            broken = True
        finally:
            try:
                enc.stdin.close()
            except BrokenPipeError:
                broken = True
        prod.stdout.close()
        prod_rc = prod.wait()
        enc_rc = enc.wait()
        prod_log.close()      # never echoed: producer diagnostics can name objects
    del enc_log
    if prod_rc != 0 or enc_rc != 0 or broken:
        try:
            os.unlink(out_path)
        except OSError:
            pass
        refuse("%s failed (producer exit %d, encryptor exit %d)" % (what, prod_rc, enc_rc))
    return {"payload_length": length, "payload_sha256": digest.hexdigest(),
            "ciphertext_length": os.path.getsize(out_path), "ciphertext_sha256": sha256_file(out_path)}


def decrypt_stream(path, key, passphrase, recipient_cert, consume=None):
    """Authenticate and decrypt one component. Returns (length, sha256) of the payload.

    OpenSSL may emit unauthenticated plaintext before it reports a GCM failure, so the
    caller's consumer must have no side effect until this returns: it receives chunks
    only for hashing/inspection or for a private staging sink."""
    binary, _ = openssl_bin()
    read_fd, write_fd = os.pipe()
    try:
        os.write(write_fd, passphrase + b"\n")
    finally:
        os.close(write_fd)
    err = tempfile.TemporaryFile()
    try:
        proc = subprocess.Popen([binary, "cms", "-decrypt", "-binary", "-inform", "DER", "-in", path, "-inkey", key,
                                 "-passin", "fd:%d" % read_fd, "-recip", recipient_cert],
                                stdin=subprocess.DEVNULL, stdout=subprocess.PIPE, stderr=err, pass_fds=(read_fd,))
    finally:
        os.close(read_fd)
    digest, length, head = hashlib.sha256(), 0, b""
    for block in iter(lambda: proc.stdout.read(CHUNK), b""):
        if len(head) < len(FRAME):
            need = len(FRAME) - len(head)
            head += block[:need]
            block = block[need:]
        if block:
            digest.update(block)
            length += len(block)
            if consume is not None:
                consume(block)
    proc.stdout.close()
    rc = proc.wait()
    err.seek(0)
    diagnostic = _diagnostic(err.read())
    err.close()
    if rc != 0:
        refuse("component %s failed authenticated decryption (wrong key, corruption or truncation; %s)"
               % (os.path.basename(path), diagnostic))
    if head != FRAME:
        refuse("component %s does not carry the CMREC1 frame" % os.path.basename(path))
    return length, digest.hexdigest()


# --------------------------------------------------------------------------- manifest

MANIFEST_KEYS = {"schema", "set_id", "kind", "t_cut", "times", "host", "deployment", "tools", "volumes",
                 "postgres", "minio", "components", "custody", "writer_exclusion", "qualification",
                 "external_dependencies"}
COMPONENT_KEYS = {"name", "format", "encryption", "payload_length", "payload_sha256", "ciphertext_length",
                  "ciphertext_sha256"}


def _require(cond, message):
    if not cond:
        refuse(message)


def validate_manifest(manifest):
    """Strict structural validation; the signature covers the exact bytes separately."""
    _require(isinstance(manifest, dict) and set(manifest) == MANIFEST_KEYS,
             "the manifest does not have exactly the recovery-set fields")
    _require(manifest["schema"] == SCHEMA, "unknown manifest schema")
    _require(isinstance(manifest["set_id"], str) and SET_ID.match(manifest["set_id"]), "malformed set ID")
    _require(manifest["kind"] in KINDS, "unknown set kind")
    _require("-%s-" % manifest["kind"] in manifest["set_id"], "the set ID does not name its kind")
    cut = parse_iso(manifest["t_cut"], "t_cut")
    times = manifest["times"]
    _require(isinstance(times, dict), "times must be an object")
    stopped = parse_iso(times.get("writers_stopped_at"), "writers_stopped_at")
    finished = parse_iso(times.get("capture_finished_at"), "capture_finished_at")
    _require(stopped <= cut <= finished, "the cut is not between writer exclusion and capture end")
    comps = manifest["components"]
    _require(isinstance(comps, list) and [c.get("name") for c in comps if isinstance(c, dict)] == list(COMPONENTS),
             "the manifest must list exactly the PostgreSQL and MinIO components, in order")
    for c in comps:
        _require(set(c) == COMPONENT_KEYS, "component %s has unexpected fields" % c.get("name"))
        _require(c["format"] == "CMREC1" and c["encryption"] == ENCRYPTION, "component %s format" % c["name"])
        for key in ("payload_length", "ciphertext_length"):
            _require(isinstance(c[key], int) and not isinstance(c[key], bool) and c[key] >= 0,
                     "component %s %s" % (c["name"], key))
        for key in ("payload_sha256", "ciphertext_sha256"):
            _require(isinstance(c[key], str) and HEX64.match(c[key]), "component %s %s" % (c["name"], key))
    custody = manifest["custody"]
    _require(isinstance(custody, dict) and HEX64.match(str(custody.get("recipient_key_sha256")))
             and HEX64.match(str(custody.get("producer_key_sha256"))) and custody.get("signing") == SIGNING,
             "custody references are malformed")
    _require(manifest["qualification"] == "CAPTURED_UNPUBLISHED", "unexpected qualification status")
    vols = manifest["volumes"]
    _require(isinstance(vols, list) and len(vols) == 2, "volumes inventory is incomplete")
    pg = manifest["postgres"]
    _require(isinstance(pg, dict) and isinstance(pg.get("flyway_history"), list), "postgres inventory missing")
    return manifest


def set_manifest_sha256(directory):
    return sha256_file(os.path.join(directory, MANIFEST))


def _check_set_listing(directory, allow_marker=False):
    names = sorted(os.listdir(directory))
    expected = sorted(SET_FILES + ((COMPLETE_MARKER,) if allow_marker and COMPLETE_MARKER in names else ()))
    _require(names == expected, "the set directory must hold exactly %s" % ", ".join(SET_FILES))
    for name in names:
        st = os.lstat(os.path.join(directory, name))
        _require(stat.S_ISREG(st.st_mode) and st.st_nlink == 1, "%s is not a single regular file" % name)


def verify_set(directory, producer_public_key, expected_producer_sha256, decrypt=None, expected_manifest_sha256=None):
    """Verify a set before trusting any of its names: listing, signature, manifest, hashes.

    decrypt=(key, passphrase, recipient_cert) additionally authenticates and decrypts every
    component in memory (no plaintext is written) and compares payload length and hash."""
    _check_set_listing(directory, allow_marker=True)
    if expected_manifest_sha256 is not None and set_manifest_sha256(directory) != expected_manifest_sha256:
        refuse("the manifest is not the one named (SHA-256 differs)")
    _require(public_key_fingerprint(producer_public_key) == expected_producer_sha256,
             "the trusted producer key does not have the pinned fingerprint")
    verify_signature(os.path.join(directory, MANIFEST), os.path.join(directory, SIGNATURE), producer_public_key)
    manifest = validate_manifest(load_json(os.path.join(directory, MANIFEST), "manifest"))
    _require(manifest["custody"]["producer_key_sha256"] == expected_producer_sha256,
             "the manifest names another producer key")
    for c in manifest["components"]:
        path = os.path.join(directory, c["name"])
        _require(os.path.getsize(path) == c["ciphertext_length"], "%s has the wrong length (truncated?)" % c["name"])
        _require(sha256_file(path) == c["ciphertext_sha256"], "%s does not match its manifest hash" % c["name"])
    if decrypt is not None:
        key, passphrase, cert = decrypt
        _require(certificate_key_fingerprint(cert) == manifest["custody"]["recipient_key_sha256"],
                 "the set was encrypted to another recipient key")
        for c in manifest["components"]:
            length, digest = decrypt_stream(os.path.join(directory, c["name"]), key, passphrase, cert)
            _require(length == c["payload_length"] and digest == c["payload_sha256"],
                     "%s decrypted to unexpected content" % c["name"])
    return manifest


# --------------------------------------------------------------------------- VM capture

class Docker(object):
    """Thin docker CLI wrapper; output parsing only, never prints container environments."""

    def __init__(self, binary="docker"):
        self.binary = binary

    def run(self, *args, **kw):
        data = kw.get("data")
        result = subprocess.run([self.binary] + list(args), input=data, stdout=subprocess.PIPE,
                                stderr=subprocess.PIPE, **({} if data is not None else {"stdin": subprocess.DEVNULL}))
        if result.returncode != 0 and not kw.get("ok_fail"):
            refuse("docker %s failed (exit %d)" % (args[0], result.returncode))
        return result.stdout.decode("utf-8", "replace").strip() if result.returncode == 0 else None

    def inspect(self, ref, template):
        return self.run("inspect", "--format", template, ref)

    def project_containers(self, project):
        out = self.run("ps", "-a", "--no-trunc", "--filter", "label=com.docker.compose.project=%s" % project,
                       "--format", '{{.ID}} {{.Label "com.docker.compose.service"}}')
        found = {}
        for line in (out or "").splitlines():
            cid, _, service = line.partition(" ")
            if service in found:
                refuse("more than one %s container exists in project %s" % (service, project))
            found[service] = cid
        return found

    def state(self, cid):
        raw = self.inspect(cid, "{{json .State}}")
        return json.loads(raw)


def _wait(predicate, seconds, what):
    deadline = time.monotonic() + seconds
    while True:
        if predicate():
            return
        if time.monotonic() > deadline:
            refuse("%s did not happen within %d s" % (what, seconds))
        time.sleep(1)


PG_SQL = 'exec psql -X -q -tA -v ON_ERROR_STOP=1 -U "$POSTGRES_USER" -d "$POSTGRES_DB" -f -'


def pg_query(docker, cid, sql):
    return docker.run("exec", "-i", cid, "sh", "-c", 'unset PGHOST PGHOSTADDR PGPORT PGSERVICE PGOPTIONS; ' + PG_SQL,
                      data=sql.encode("utf-8"))


def pg_inventory(docker, cid):
    version = pg_query(docker, cid, "SHOW server_version;")
    history = []
    has_history = pg_query(docker, cid, "SELECT to_regclass('public.flyway_schema_history') IS NOT NULL;")
    if has_history == "t":
        rows = pg_query(docker, cid, "SELECT installed_rank, coalesce(version,''), coalesce(checksum::text,''), "
                                     "success FROM public.flyway_schema_history ORDER BY installed_rank;")
        for line in (rows or "").splitlines():
            rank, ver, checksum, success = line.split("|")
            history.append({"rank": int(rank), "version": ver,
                            "checksum": int(checksum) if checksum else None, "success": success == "t"})
    tables = pg_query(docker, cid, "SELECT tablename FROM pg_tables WHERE schemaname='public' ORDER BY 1;")
    counts = {}
    for table in (tables or "").splitlines():
        if not re.match(r"^[a-z_][a-z0-9_]{0,62}$", table):
            refuse("unexpected table name in schema public")
        counts[table] = int(pg_query(docker, cid, 'SELECT count(*) FROM public."%s";' % table))
    return {"server_version": version, "flyway_history": history, "row_counts": counts}


def pg_other_clients(docker, cid):
    """Client sessions other than the inspecting one: any is a potential writer.

    The container healthcheck (pg_isready) opens a momentary connection; it is excluded
    by name and a non-zero count must persist across a second sample to count."""
    sql = ("SELECT count(*) FROM pg_stat_activity WHERE backend_type='client backend' "
           "AND pid <> pg_backend_pid() AND coalesce(application_name, '') <> 'pg_isready';")
    first = int(pg_query(docker, cid, sql))
    if not first:
        return 0
    time.sleep(1)
    return int(pg_query(docker, cid, sql))


def volume_inventory(docker, name):
    raw = docker.run("volume", "inspect", "--format",
                     '{"name":{{json .Name}},"created":{{json .CreatedAt}},"driver":{{json .Driver}},'
                     '"mountpoint":{{json .Mountpoint}},"options":{{json .Options}}}', name)
    data = json.loads(raw)
    if data["driver"] != "local" or data.get("options"):
        refuse("volume %s is not a plain local volume; an external dependency is not inventoried" % name)
    return data


def container_mount(docker, cid, target):
    mounts = json.loads(docker.inspect(cid, "{{json .Mounts}}"))
    names = [m.get("Name") for m in mounts if m.get("Destination") == target and m.get("Type") == "volume"]
    if len(names) != 1:
        refuse("expected exactly one named volume at %s" % target)
    return names[0]


def tar_producer(docker, volume, mountpoint, mode):
    """Opaque cold copy of the whole volume, numeric owners, modes, ACLs and xattrs (GNU tar)."""
    flags = ["--numeric-owner", "--xattrs", "--xattrs-include=*", "--acls", "--sparse", "--one-file-system",
             "--sort=name", "--format=posix", "-cf", "-"]
    if mode == "host":
        return ["tar", "-C", mountpoint] + flags + ["."]
    if mode.startswith("container:"):
        image = mode.split(":", 1)[1]
        return [docker.binary, "run", "--rm", "--network", "none", "--user", "0:0", "--read-only",
                "--entrypoint", "tar", "-v", "%s:/data:ro" % volume, image, "-C", "/data"] + flags + ["."]
    refuse("unknown tar mode")


def capture(kind, hold, backup_root=VM_BACKUP_ROOT, recipient_cert=VM_RECIPIENT_CERT, producer_key=VM_PRODUCER_KEY,
            project=PROJECT, tar_mode="host", stop_timeout=60, docker=None, deployment=None, log=print):
    """ADR009 section 5 steps 1-5 on the VM. The caller holds the operations lock."""
    if kind not in KINDS:
        refuse("unknown capture kind")
    if hold and kind != "pre-update":
        refuse("only a pre-update capture holds the application stopped")
    docker = docker or Docker()
    for path in (recipient_cert, producer_key):
        if not os.path.isfile(path):
            refuse("%s is missing: recovery custody is not provisioned" % path)
    sets_dir = private_dir(os.path.join(backup_root, "sets"))
    hold_path = os.path.join(backup_root, "hold.json")
    if os.path.exists(hold_path):
        refuse("an application hold is active; deploy, resume or release it before another capture")
    binary, openssl_version = openssl_bin()
    found = docker.project_containers(project)
    for service in ("postgres", "minio"):
        if service not in found:
            refuse("no %s container in project %s" % (service, project))
    unknown = set(found) - {"postgres", "minio", "minio-init", "backend", "frontend"}
    if unknown:
        refuse("unexpected services in project %s: %s" % (project, ", ".join(sorted(unknown))))
    pg, mi = found["postgres"], found["minio"]
    if not docker.state(pg).get("Running") or (docker.state(pg).get("Health") or {}).get("Status") != "healthy":
        refuse("PostgreSQL is not running and healthy")
    if not docker.state(mi).get("Running"):
        refuse("MinIO is not running")
    if "minio-init" in found and docker.state(found["minio-init"]).get("Running"):
        refuse("the MinIO initializer is running (a storage writer)")
    pg_volume = container_mount(docker, pg, "/var/lib/postgresql/data")
    mi_volume = container_mount(docker, mi, "/data")
    volumes = [volume_inventory(docker, pg_volume), volume_inventory(docker, mi_volume)]
    images = {s: docker.inspect(cid, "{{.Image}}") for s, cid in sorted(found.items())}
    writers = [found[s] for s in ("frontend", "backend") if s in found and docker.state(found[s]).get("Running")]
    set_id = "CMRS-%s-%s-%s" % (utc_now().strftime("%Y%m%dT%H%M%SZ"), kind, secrets.token_hex(4))
    partial = os.path.join(sets_dir, set_id + ".partial")
    os.mkdir(partial, 0o700)
    times = {"capture_started_at": iso(utc_now())}
    stopped_minio = False
    started = {}
    try:
        log("capture %s: stopping application writers (frontend, backend)" % set_id)
        for cid in writers:
            docker.run("stop", "--time", str(stop_timeout), cid)
        for s in ("frontend", "backend"):
            if s in found:
                st = docker.state(found[s])
                if st.get("Running"):
                    refuse("%s is still running after stop" % s)
                started[s] = st.get("StartedAt")
        times["writers_stopped_at"] = iso(utc_now())
        network = json.loads(docker.run("network", "inspect", "--format", "{{json .Containers}}",
                                        "%s_default" % project) or "{}")
        attached = sorted(network)
        if sorted(attached) != sorted([pg, mi]):
            refuse("containers other than PostgreSQL and MinIO are attached to the project network")
        if pg_other_clients(docker, pg):
            refuse("PostgreSQL has client sessions after writer exclusion")
        cut = utc_now()
        time.sleep(1)  # the cut is strictly after exclusion and before both snapshots
        pg_info = pg_inventory(docker, pg)
        pg_dump_version = docker.run("exec", pg, "pg_dump", "--version")
        log("capture %s: PostgreSQL custom-format dump" % set_id)
        components = []
        meta = encrypt_stream([docker.binary, "exec", pg, "sh", "-c",
                               'exec pg_dump -U "$POSTGRES_USER" -d "$POSTGRES_DB" -Fc --no-password'],
                              recipient_cert, os.path.join(partial, COMPONENTS[0]), "PostgreSQL dump")
        components.append(dict(meta, name=COMPONENTS[0]))
        if pg_other_clients(docker, pg):
            refuse("a PostgreSQL client connected during the dump; the cut is not consistent")
        if pg_inventory(docker, pg) != pg_info:
            refuse("PostgreSQL content changed during the dump; the cut is not consistent")
        minio_version = docker.run("exec", mi, "/usr/bin/minio", "--version", ok_fail=True) or "unavailable"
        minio_env_keys = sorted(k.split("=", 1)[0] for k in json.loads(docker.inspect(mi, "{{json .Config.Env}}")))
        external = sorted(k for k in minio_env_keys if k.startswith("MINIO_") and k not in
                          ("MINIO_ROOT_USER", "MINIO_ROOT_PASSWORD", "MINIO_BROWSER"))
        if external:
            refuse("MinIO has configuration outside the inventoried set: %s" % ", ".join(external))
        log("capture %s: stopping MinIO for the cold volume copy" % set_id)
        docker.run("stop", "--time", str(stop_timeout), mi)
        stopped_minio = True
        if docker.state(mi).get("Running"):
            refuse("MinIO is still running after stop")
        tar_version = (subprocess.run(["tar", "--version"], stdout=subprocess.PIPE, stderr=subprocess.DEVNULL)
                       .stdout.decode("ascii", "replace").splitlines() or ["unavailable"])[0] if tar_mode == "host" \
            else "GNU tar in %s" % tar_mode
        meta = encrypt_stream(tar_producer(docker, mi_volume, volumes[1]["mountpoint"], tar_mode), recipient_cert,
                              os.path.join(partial, COMPONENTS[1]), "MinIO cold copy")
        components.append(dict(meta, name=COMPONENTS[1]))
        for s, cid in found.items():
            if s in ("frontend", "backend") and docker.state(cid).get("StartedAt") != started[s]:
                refuse("%s was restarted during the capture; the cut is not consistent" % s)
        docker.run("start", mi)
        stopped_minio = False
        _wait(lambda: docker.state(mi).get("Running"), stop_timeout, "MinIO restart")
        times["capture_finished_at"] = iso(utc_now())
        manifest = {
            "schema": SCHEMA, "set_id": set_id, "kind": kind, "t_cut": iso(cut), "times": times,
            "host": {"hostname": os.uname()[1], "platform": "%s/%s" % (os.uname()[0].lower(), os.uname()[4])},
            "deployment": deployment or {},
            "volumes": [{k: v[k] for k in ("name", "created", "driver")} for v in volumes],
            "tools": {"openssl": openssl_version, "pg_dump": pg_dump_version, "tar": tar_version,
                      "minio": minio_version.splitlines()[0] if minio_version else "unavailable"},
            "postgres": dict(pg_info, container_image=images["postgres"]),
            "minio": {"container_image": images["minio"], "volume": mi_volume, "env_keys": minio_env_keys,
                      "mechanism": "opaque cold copy of the whole /data volume (ADR009 section 9.5)"},
            "components": [dict(c, format="CMREC1", encryption=ENCRYPTION) for c in components],
            "custody": {"recipient_key_sha256": certificate_key_fingerprint(recipient_cert),
                        "producer_key_sha256": public_key_fingerprint(producer_key, private=True),
                        "signing": SIGNING},
            "writer_exclusion": {"stopped": sorted(s for s in ("frontend", "backend") if s in found),
                                 "network_members": ["minio", "postgres"], "postgres_other_clients": 0,
                                 "minio": "server stopped for the copy", "images": images},
            "qualification": "CAPTURED_UNPUBLISHED",
            "external_dependencies": {"minio_kms": "none configured (no MINIO_KMS_* keys)",
                                      "minio_identity": "built-in IAM inside .minio.sys",
                                      "runtime_secrets": "not in the set; Leader escrow (ADR009 section 5)"},
        }
        validate_manifest(manifest)
        write_private(os.path.join(partial, MANIFEST), dump_json(manifest))
        sign_file(os.path.join(partial, MANIFEST), producer_key, os.path.join(partial, SIGNATURE))
        os.chmod(os.path.join(partial, SIGNATURE), 0o600)
        final = os.path.join(sets_dir, set_id)
        os.rename(partial, final)
        manifest_sha = set_manifest_sha256(final)
        if hold:
            write_private(hold_path, dump_json({"set_id": set_id, "manifest_sha256": manifest_sha, "t_cut": iso(cut),
                                                "held": {s: found[s] for s in ("frontend", "backend") if s in found},
                                                "started_at": started, "created_at": iso(utc_now())}))
            log("capture %s: application held stopped for the update (maintenance window open)" % set_id)
        else:
            for s in ("backend", "frontend"):
                if s in found and found[s] in writers:
                    docker.run("start", found[s])
            times["resumed_at"] = iso(utc_now())
        status = {"last_capture": {"set_id": set_id, "kind": kind, "t_cut": iso(cut), "manifest_sha256": manifest_sha,
                                   "result": "CAPTURED", "at": iso(utc_now())}}
        write_private(os.path.join(backup_root, "status.json"), dump_json(status))
        return {"set_id": set_id, "manifest_sha256": manifest_sha, "t_cut": iso(cut), "hold": bool(hold),
                "components": manifest["components"]}
    except BaseException as exc:
        shutil.rmtree(partial, ignore_errors=True)
        if stopped_minio:
            docker.run("start", mi, ok_fail=True)
        for s in ("backend", "frontend"):
            if s in found and found[s] in writers:
                docker.run("start", found[s], ok_fail=True)
        try:
            write_private(os.path.join(backup_root, "status.json"), dump_json(
                {"last_capture": {"kind": kind, "result": "FAILED", "reason": str(exc)[:300], "at": iso(utc_now())}}))
        except OSError:
            pass
        raise


def list_staged(backup_root=VM_BACKUP_ROOT):
    sets_dir = os.path.join(backup_root, "sets")
    result = []
    if not os.path.isdir(sets_dir):
        return result
    for name in sorted(os.listdir(sets_dir)):
        path = os.path.join(sets_dir, name)
        if not SET_ID.match(name) or not os.path.isdir(path) or os.path.islink(path):
            continue
        files = {}
        for f in SET_FILES:
            p = os.path.join(path, f)
            if os.path.isfile(p) and not os.path.islink(p):
                files[f] = os.path.getsize(p)
        ack = os.path.join(backup_root, "acks", name + ".json")
        result.append({"set_id": name, "files": files, "complete": sorted(files) == sorted(SET_FILES),
                       "manifest_sha256": set_manifest_sha256(path) if MANIFEST in files else None,
                       "acknowledged": load_json(ack, "ack") if os.path.isfile(ack) else None})
    return result


def staged_file(set_id, name, backup_root=VM_BACKUP_ROOT):
    if not SET_ID.match(set_id or "") or name not in SET_FILES:
        refuse("unknown set or file")
    path = os.path.join(backup_root, "sets", set_id, name)
    if os.path.islink(path) or not os.path.isfile(path):
        refuse("no such staged file")
    return path


def acknowledge(set_id, manifest_sha256, level, by, backup_root=VM_BACKUP_ROOT):
    """The publisher's receipt: the set was verified off the VM and is in independent custody."""
    base, _, extra = level.partition("+")
    if base not in LEVELS or extra not in ("", "RESTORE_TESTED"):
        refuse("unknown acknowledgement level")
    path = os.path.join(backup_root, "sets", set_id)
    if not SET_ID.match(set_id) or not os.path.isdir(path):
        refuse("no such staged set")
    if set_manifest_sha256(path) != manifest_sha256:
        refuse("the acknowledged manifest is not the staged one")
    acks = private_dir(os.path.join(backup_root, "acks"))
    record = {"schema": ACK_SCHEMA, "set_id": set_id, "manifest_sha256": manifest_sha256, "level": level,
              "acknowledged_by": by, "acknowledged_at": iso(utc_now())}
    write_private(os.path.join(acks, set_id + ".json"), dump_json(record))
    return record


def prune_staged(backup_root=VM_BACKUP_ROOT, log=print):
    """Delete acknowledged staged sets older than the newest acknowledged one.

    Unacknowledged sets, the held set and the newest acknowledged set always stay."""
    staged = list_staged(backup_root)
    hold_path = os.path.join(backup_root, "hold.json")
    held = load_json(hold_path, "hold")["set_id"] if os.path.isfile(hold_path) else None
    acked = [s for s in staged if s["acknowledged"] and s["complete"]]
    if not acked:
        return []
    newest = max(s["set_id"].split("-")[1] for s in acked)
    removed = []
    for s in acked:
        if s["set_id"].split("-")[1] < newest and s["set_id"] != held:
            shutil.rmtree(os.path.join(backup_root, "sets", s["set_id"]))
            removed.append(s["set_id"])
            log("pruned staged set %s (acknowledged, superseded)" % s["set_id"])
    for name in os.listdir(os.path.join(backup_root, "sets")):
        if name.endswith(".partial"):
            shutil.rmtree(os.path.join(backup_root, "sets", name), ignore_errors=True)
            log("removed interrupted capture %s" % name)
    return removed


# --------------------------------------------------------------------------- retention

def retention_keep(points, now, holds=()):
    """ADR009 section 4.2: newest 3 intraday, newest per UTC day for 7 days, newest per
    ISO week for the 4 completed weeks before the current one, every pre-update/manual
    point not explicitly released, and always the newest point. points: [{set_id, t_cut, kind}]."""
    pts = sorted(points, key=lambda p: p["t_cut"])
    keep = set(p["set_id"] for p in pts[-3:])
    if pts:
        keep.add(pts[-1]["set_id"])
    today = now.date()
    for offset in range(7):
        day = today - datetime.timedelta(days=offset)
        same = [p for p in pts if parse_iso(p["t_cut"], "t_cut").date() == day]
        if same:
            keep.add(same[-1]["set_id"])
    year, week, _ = today.isocalendar()
    monday = today - datetime.timedelta(days=today.isocalendar()[2] - 1)
    for back in range(1, 5):
        start = monday - datetime.timedelta(weeks=back)
        end = start + datetime.timedelta(days=7)
        same = [p for p in pts if start <= parse_iso(p["t_cut"], "t_cut").date() < end]
        if same:
            keep.add(same[-1]["set_id"])
    released = set(holds)
    for p in pts:
        if p["kind"] in ("pre-update", "manual") and p["set_id"] not in released:
            keep.add(p["set_id"])
    return keep


def point_age_status(newest_cut, now):
    if newest_cut is None:
        return "MISSING", None
    hours = (now - newest_cut).total_seconds() / 3600.0
    if hours > BREACH_AGE_HOURS:
        return "BREACH", hours
    if hours >= WARN_AGE_HOURS:
        return "WARNING", hours
    return "OK", hours


# --------------------------------------------------------------------------- workstation

DEFAULT_HOME = os.path.expanduser("~/CodeMasteryRecovery")
KEYCHAIN_ACCOUNT = "codemastery-recovery"
KEYCHAIN_SERVICE = "codemastery-recovery-recipient"


class Workstation(object):
    def __init__(self, home=DEFAULT_HOME):
        self.home = home
        self.config_path = os.path.join(home, "config.json")

    def path(self, *parts):
        return os.path.join(self.home, *parts)

    def config(self):
        cfg = load_json(self.config_path, "workstation config")
        for key in ("vm", "producer_key_sha256"):
            if key not in cfg:
                refuse("workstation config lacks %s" % key)
        return cfg

    def passphrase(self):
        override = os.environ.get("CODEMASTERY_RECOVERY_PASSPHRASE_FILE")
        if override:
            with open(override, "rb") as f:
                return f.read().rstrip(b"\n")
        result = subprocess.run(["security", "find-generic-password", "-a", KEYCHAIN_ACCOUNT, "-s", KEYCHAIN_SERVICE,
                                 "-w"], stdout=subprocess.PIPE, stderr=subprocess.DEVNULL)
        if result.returncode != 0:
            refuse("the recipient key passphrase is not available from the Keychain (locked or missing)")
        return result.stdout.rstrip(b"\n")

    def decrypt_args(self):
        return (self.path("keys", "recipient.encrypted.pem"), self.passphrase(), self.path("keys", "recipient.crt"))

    def ledger(self):
        path = self.path("ledger", "ledger.jsonl")
        entries = []
        if os.path.isfile(path):
            with open(path, "rb") as f:
                for n, line in enumerate(f, 1):
                    if line.strip():
                        entry = load_json_bytes(line, "ledger line %d" % n)
                        if entry.get("schema") != LEDGER_SCHEMA:
                            refuse("ledger line %d has an unknown schema" % n)
                        entries.append(entry)
        return entries

    def append_ledger(self, entry):
        private_dir(self.path("ledger"))
        path = self.path("ledger", "ledger.jsonl")
        line = json.dumps(entry, sort_keys=True, ensure_ascii=True) + "\n"
        fd = os.open(path, os.O_WRONLY | os.O_APPEND | os.O_CREAT, 0o600)
        try:
            os.write(fd, line.encode("ascii"))
            os.fsync(fd)
        finally:
            os.close(fd)


def init_workstation(home, log=print):
    """One-time: recipient key (RSA-3072, encrypted PKCS#8: AES-256-CBC, PBKDF2-HMAC-SHA256, 600,000
    iterations), its certificate, the Keychain passphrase and owner-only directories."""
    ws = Workstation(home)
    for sub in ("", "keys", "sets", "ledger", "trust", "incoming"):
        private_dir(ws.path(sub) if sub else home)
    key = ws.path("keys", "recipient.encrypted.pem")
    if os.path.exists(key):
        refuse("a recipient key already exists; refusing to replace it")
    binary, _ = openssl_bin()
    passphrase = secrets.token_urlsafe(32).encode("ascii")
    plain = _run([binary, "genpkey", "-algorithm", "RSA", "-pkeyopt", "rsa_keygen_bits:3072"], what="key generation")
    # The certificate is made from the in-memory key (stdin), before the key is wrapped.
    cert = _run([binary, "req", "-x509", "-new", "-key", "/dev/stdin", "-subj", "/CN=CodeMastery recovery recipient",
                 "-days", "3650", "-sha256"], data=plain, what="certificate")
    r, w = os.pipe()
    os.write(w, passphrase + b"\n")
    os.close(w)
    try:
        encrypted = _run([binary, "pkcs8", "-topk8", "-v2", "aes-256-cbc", "-v2prf", "hmacWithSHA256", "-iter", "600000",
                          "-passout", "fd:%d" % r], data=plain, what="key encryption", pass_fds=(r,))
    finally:
        os.close(r)
    del plain
    write_private(key, encrypted)
    write_private(ws.path("keys", "recipient.crt"), cert, 0o644)
    if sys.platform == "darwin":
        proc = subprocess.run(["security", "-i"], input=("add-generic-password -U -a %s -s %s -w %s\n" % (
            KEYCHAIN_ACCOUNT, KEYCHAIN_SERVICE, passphrase.decode("ascii"))).encode("ascii"),
            stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)
        if proc.returncode != 0:
            refuse("could not store the passphrase in the Keychain")
        log("Passphrase stored in the login Keychain (service %s)." % KEYCHAIN_SERVICE)
    else:
        write_private(ws.path("keys", "passphrase"), passphrase + b"\n")
        log("Passphrase written to %s (non-macOS host)." % ws.path("keys", "passphrase"))
    del passphrase
    log("Recipient certificate: %s" % ws.path("keys", "recipient.crt"))
    log("Recipient key SHA-256: %s" % certificate_key_fingerprint(ws.path("keys", "recipient.crt")))
    log("Next: copy the encrypted key and its passphrase to separate offline media (ADR009 section 9.5).")


def trust_producer(home, pem, fingerprint):
    ws = Workstation(home)
    private_dir(ws.path("trust"))
    if public_key_fingerprint(pem) != fingerprint:
        refuse("the producer key does not have the fingerprint you confirmed")
    target = ws.path("trust", "producer.pub.pem")
    if os.path.exists(target) and public_key_fingerprint(target) != fingerprint:
        refuse("another producer key is already trusted; rotate it out of band (ADR009 section 9.5)")
    with open(pem, "rb") as f:
        write_private(target, f.read(), 0o644)
    return target


class Remote(object):
    """The VM side of the publisher, through infrastructure/vm/cd/vm-transport.sh (pinned host key)."""

    def __init__(self, cfg):
        vm = cfg["vm"]
        here = os.path.dirname(os.path.abspath(__file__))
        self.transport = vm.get("transport") or os.path.join(here, "..", "cd", "vm-transport.sh")
        self.base = ["bash", self.transport, "--host", vm["host"], "--user", vm["user"], "--identity",
                     os.path.expanduser(vm["identity"])]
        if vm.get("known_hosts"):
            self.base += ["--known-hosts", os.path.expanduser(vm["known_hosts"])]

    def ops(self, *args, **kw):
        result = subprocess.run(self.base + ["ops"] + list(args), stdout=kw.get("stdout", subprocess.PIPE),
                                stderr=subprocess.PIPE)
        if result.returncode != 0:
            refuse("remote %s failed (exit %d): %s" % (args[0], result.returncode,
                                                         result.stderr.decode("utf-8", "replace").strip()[-300:]))
        return result.stdout

    def fetch(self, set_id, name, dest):
        with open(dest, "xb") as f:
            os.fchmod(f.fileno(), 0o600)
            self.ops("recovery-send", set_id, name, stdout=f)


class Blob(object):
    """Azure Blob over HTTPS with an Entra bearer token (ADR009 section 9.4): conditional create,
    readback, listing and explicit deletion. The token never appears in argv, logs or files."""

    VERSION = "2023-11-03"

    def __init__(self, azure):
        self.account = azure["account"]
        self.container = azure["container"]
        if not re.match(r"^[a-z0-9]{3,24}$", self.account) or not re.match(r"^[a-z0-9](?:[a-z0-9-]{1,61}[a-z0-9])$",
                                                                            self.container):
            refuse("malformed Azure account or container name")
        self.endpoint = (azure.get("endpoint") or "https://%s.blob.core.windows.net" % self.account).rstrip("/")
        if not self.endpoint.startswith("https://"):
            refuse("the Azure endpoint must be HTTPS")
        self.context = ssl.create_default_context(cafile=azure.get("ca_file"))
        self.context.minimum_version = ssl.TLSVersion.TLSv1_2
        command = azure.get("token_command") or \
            "az account get-access-token --resource https://storage.azure.com/ --query accessToken --output tsv"
        result = subprocess.run(shlex.split(command), stdout=subprocess.PIPE, stderr=subprocess.DEVNULL)
        token = result.stdout.decode("ascii", "replace").strip()
        if result.returncode != 0 or not token or any(c.isspace() for c in token):
            refuse("no Entra access token for Azure Storage (token command failed)")
        self._token = token

    def url(self, name, query=""):
        return "%s/%s/%s%s" % (self.endpoint, self.container, urllib.parse.quote(name, safe="/"), query)

    def request(self, method, url, body=None, headers=None, ok=(200, 201)):
        h = {"Authorization": "Bearer " + self._token, "x-ms-version": self.VERSION,
             "x-ms-date": utc_now().strftime("%a, %d %b %Y %H:%M:%S GMT")}
        h.update(headers or {})
        req = urllib.request.Request(url, data=body, method=method, headers=h)
        try:
            resp = urllib.request.urlopen(req, context=self.context, timeout=300)
        except urllib.error.HTTPError as exc:
            if exc.code in ok:
                return exc
            refuse("Azure %s %s answered HTTP %d" % (method, urllib.parse.urlsplit(url).path, exc.code))
        except (urllib.error.URLError, OSError) as exc:
            refuse("Azure %s failed (%s)" % (method, exc.__class__.__name__))
        if resp.status not in ok:
            refuse("Azure %s answered HTTP %d" % (method, resp.status))
        return resp

    def create(self, name, path):
        """Never replaces an existing blob (If-None-Match: *); a conflict is a refusal."""
        size = os.path.getsize(path)
        with open(path, "rb") as f:
            data = f.read() if size <= (256 << 20) else None
        if data is not None:
            self.request("PUT", self.url(name), body=data, headers={
                "x-ms-blob-type": "BlockBlob", "If-None-Match": "*", "Content-Type": "application/octet-stream",
                "Content-Length": str(len(data))}, ok=(201,))
            return
        ids = []
        with open(path, "rb") as f:
            for n, block in enumerate(iter(lambda: f.read(64 << 20), b"")):
                bid = base64.b64encode(("%08d" % n).encode("ascii")).decode("ascii")
                ids.append(bid)
                self.request("PUT", self.url(name, "?comp=block&blockid=" + urllib.parse.quote(bid)), body=block,
                             headers={"Content-Length": str(len(block))}, ok=(201,))
        body = ("<?xml version=\"1.0\" encoding=\"utf-8\"?><BlockList>%s</BlockList>" %
                "".join("<Latest>%s</Latest>" % i for i in ids)).encode("ascii")
        self.request("PUT", self.url(name, "?comp=blocklist"), body=body, headers={
            "If-None-Match": "*", "Content-Length": str(len(body)), "Content-Type": "application/xml"}, ok=(201,))

    def sha256(self, name):
        resp = self.request("GET", self.url(name), ok=(200,))
        h, n = hashlib.sha256(), 0
        for block in iter(lambda: resp.read(CHUNK), b""):
            h.update(block)
            n += len(block)
        return n, h.hexdigest()

    def exists(self, name):
        try:
            self.request("HEAD", self.url(name), ok=(200,))
            return True
        except RecoveryError:
            return False

    def list(self, prefix):
        names, marker = [], ""
        while True:
            q = "?restype=container&comp=list&prefix=%s%s" % (urllib.parse.quote(prefix),
                                                              "&marker=" + urllib.parse.quote(marker) if marker else "")
            body = self.request("GET", "%s/%s%s" % (self.endpoint, self.container, q), ok=(200,)).read().decode("utf-8")
            names += re.findall(r"<Name>([^<]+)</Name>", body)
            m = re.search(r"<NextMarker>([^<]+)</NextMarker>", body)
            if not m:
                return names
            marker = m.group(1)

    def delete(self, name):
        self.request("DELETE", self.url(name), ok=(202,))


def _copy_verified(src_dir, dst_dir, files):
    tmp = dst_dir + ".partial"
    shutil.rmtree(tmp, ignore_errors=True)
    os.mkdir(tmp, 0o700)
    for name in files:
        shutil.copyfile(os.path.join(src_dir, name), os.path.join(tmp, name))
        os.chmod(os.path.join(tmp, name), 0o600)
        if sha256_file(os.path.join(tmp, name)) != sha256_file(os.path.join(src_dir, name)):
            refuse("independent copy readback failed for %s" % name)
    if os.path.exists(dst_dir):
        refuse("%s already exists in independent custody; it is never replaced" % os.path.basename(dst_dir))
    os.rename(tmp, dst_dir)


def publish_set(ws, cfg, remote, entry, blob=None, restore=None, allow_older=False, log=print):
    set_id, manifest_sha = entry["set_id"], entry["manifest_sha256"]
    ledger = ws.ledger()
    done = [e for e in ledger if e["set_id"] == set_id]
    if done:
        if done[-1]["manifest_sha256"] != manifest_sha:
            refuse("set %s on the VM differs from the published one (substitution)" % set_id)
        return done[-1]
    if not entry.get("complete"):
        refuse("set %s is incomplete on the VM" % set_id)
    incoming = ws.path("incoming", set_id)
    shutil.rmtree(incoming, ignore_errors=True)
    os.mkdir(incoming, 0o700)
    try:
        for name in SET_FILES:
            remote.fetch(set_id, name, os.path.join(incoming, name))
            if os.path.getsize(os.path.join(incoming, name)) != entry["files"].get(name):
                refuse("%s/%s transfer was interrupted or truncated" % (set_id, name))
        producer = ws.path("trust", "producer.pub.pem")
        manifest = verify_set(incoming, producer, cfg["producer_key_sha256"], decrypt=ws.decrypt_args(),
                              expected_manifest_sha256=manifest_sha)
        if manifest["set_id"] != set_id:
            refuse("the manifest names another set")
        newest = max([e["t_cut"] for e in ledger] or [""])
        if manifest["t_cut"] <= newest and not allow_older:
            refuse("set %s is not newer than the ledger's newest cut %s (rollback or replay)" % (set_id, newest))
        restore_result = None
        if restore is not None:
            restore_result = restore(incoming, manifest)
        target = ws.path("sets", set_id)
        _copy_verified(incoming, target, SET_FILES)
        azure_state = "NOT_CONFIGURED"
        if blob is not None:
            prefix = "sets/%s/" % set_id
            for name in SET_FILES:
                blob.create(prefix + name, os.path.join(target, name))
            for name in SET_FILES:
                n, digest = blob.sha256(prefix + name)
                if n != os.path.getsize(os.path.join(target, name)) or digest != sha256_file(os.path.join(target, name)):
                    refuse("Azure readback of %s differs" % name)
            marker = dump_json({"set_id": set_id, "manifest_sha256": manifest_sha, "files": {
                n: sha256_file(os.path.join(target, n)) for n in SET_FILES}, "completed_at": iso(utc_now())})
            mpath = os.path.join(incoming, COMPLETE_MARKER)
            write_private(mpath, marker)
            blob.create(prefix + COMPLETE_MARKER, mpath)
            n, digest = blob.sha256(prefix + COMPLETE_MARKER)
            if digest != hashlib.sha256(marker).hexdigest():
                refuse("Azure completion marker readback differs")
            azure_state = "PUBLISHED"
        level = "VERIFIED_INDEPENDENT_AZURE" if blob is not None else "VERIFIED_INDEPENDENT"
        if restore_result and restore_result.get("postgres") == "PASS":
            level += "+RESTORE_TESTED"
        record = {"schema": LEDGER_SCHEMA, "set_id": set_id, "manifest_sha256": manifest_sha, "kind": manifest["kind"],
                  "t_cut": manifest["t_cut"], "independent": "VERIFIED", "azure": azure_state,
                  "restore_test": restore_result or "NOT_RUN", "level": level, "published_at": iso(utc_now())}
        ws.append_ledger(record)
        remote.ops("recovery-ack", set_id, manifest_sha, level)
        log("published %s (%s, cut %s)" % (set_id, level, manifest["t_cut"]))
        return record
    finally:
        shutil.rmtree(incoming, ignore_errors=True)


# --------------------------------------------------------------------------- restore test

def restore_test(set_dir, manifest, ws, postgres_image, minio_image=None, docker=None, log=print):
    """Isolated restore qualification on the workstation (ADR009 section 5, Q03/Q04 scope):

    * PostgreSQL: decrypted only into a tmpfs inside a disposable --network none container,
      used only after authentication and hash checks, restored under F1 (--no-owner --no-acl
      --exit-on-error) as a non-superuser migrator; Flyway history and every table's row
      count must equal the manifest.
    * MinIO: the decrypted tar is inspected in memory (paths, numeric owner 65532, hidden
      .minio.sys tree, format.json); a server boot needs the runtime root credentials from the
      Leader's escrow and is reported NOT_RUN without them."""
    docker = docker or Docker()
    key, passphrase, cert = ws.decrypt_args()
    result = {"postgres": "NOT_RUN", "minio": "NOT_RUN"}
    comp = {c["name"]: c for c in manifest["components"]}
    size_mb = max(64, (comp[COMPONENTS[0]]["payload_length"] >> 20) * 3 + 64)
    name = "cm-restore-%s" % secrets.token_hex(4)
    bootstrap = secrets.token_hex(24)
    migrator = secrets.token_hex(24)
    docker.run("run", "-d", "--name", name, "--network", "none", "--tmpfs", "/stage:rw,size=%dm,mode=0700" % size_mb,
               "--tmpfs", "/var/lib/postgresql/data:rw,size=%dm" % (size_mb * 4), "-e", "POSTGRES_USER=cm_bootstrap",
               "-e", "POSTGRES_PASSWORD=" + bootstrap, "-e", "POSTGRES_DB=codemastery", postgres_image)
    try:
        _wait(lambda: docker.run("exec", name, "pg_isready", "-U", "cm_bootstrap", "-d", "codemastery",
                                 ok_fail=True) is not None, 120, "restore PostgreSQL readiness")
        sink = subprocess.Popen([docker.binary, "exec", "-i", name, "sh", "-c", "umask 077; cat > /stage/postgres.dump"],
                                stdin=subprocess.PIPE, stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)
        length, digest = decrypt_stream(os.path.join(set_dir, COMPONENTS[0]), key, passphrase, cert,
                                        consume=sink.stdin.write)
        sink.stdin.close()
        if sink.wait() != 0:
            refuse("staging the authenticated dump failed")
        if length != comp[COMPONENTS[0]]["payload_length"] or digest != comp[COMPONENTS[0]]["payload_sha256"]:
            refuse("the decrypted dump differs from the manifest")
        staged = docker.run("exec", name, "sha256sum", "/stage/postgres.dump").split()[0]
        if staged != digest:
            refuse("the staged dump differs from the authenticated payload")
        sql = ("CREATE ROLE cm_migrator LOGIN NOSUPERUSER NOCREATEDB NOCREATEROLE PASSWORD '%s';"
               "REVOKE CREATE ON SCHEMA public FROM PUBLIC; GRANT CONNECT ON DATABASE codemastery TO cm_migrator;"
               "GRANT USAGE, CREATE ON SCHEMA public TO cm_migrator;" % migrator)
        pg_query(docker, name, sql)
        restore = subprocess.run([docker.binary, "exec", "-i", name, "sh", "-c",
                                  "IFS= read -r PGPASSWORD; export PGPASSWORD; exec pg_restore --no-owner --no-acl "
                                  "--exit-on-error -h 127.0.0.1 -U cm_migrator -d codemastery /stage/postgres.dump"],
                                 input=(migrator + "\n").encode("ascii"), stdout=subprocess.DEVNULL,
                                 stderr=subprocess.PIPE)
        if restore.returncode != 0:
            refuse("pg_restore under F1 failed (exit %d)" % restore.returncode)
        restored = pg_inventory(docker, name)
        if restored["flyway_history"] != manifest["postgres"]["flyway_history"]:
            refuse("restored Flyway history differs from the manifest")
        if restored["row_counts"] != manifest["postgres"]["row_counts"]:
            refuse("restored row counts differ from the manifest")
        result["postgres"] = "PASS"
        result["postgres_tables"] = len(restored["row_counts"])
    finally:
        docker.run("rm", "-f", "-v", name, ok_fail=True)
    result["minio"] = inspect_minio_tar(os.path.join(set_dir, COMPONENTS[1]), comp[COMPONENTS[1]], key, passphrase,
                                        cert)
    result["minio_boot"] = "NOT_RUN (needs the runtime root credentials from Leader escrow)"
    log("restore test %s: postgres %s, minio %s" % (manifest["set_id"], result["postgres"], result["minio"]))
    return result


class _Pipe(object):
    """Collects an authenticated payload in memory for inspection (bounded by the caller)."""

    def __init__(self):
        self.buf = bytearray()

    def write(self, data):
        self.buf += data


def inspect_minio_tar(path, component, key, passphrase, cert):
    if component["payload_length"] > (2 << 30):
        return "NOT_RUN (larger than the 2 GiB in-memory inspection bound)"
    collected = _Pipe()
    length, digest = decrypt_stream(path, key, passphrase, cert, consume=collected.write)
    if length != component["payload_length"] or digest != component["payload_sha256"]:
        refuse("the decrypted MinIO copy differs from the manifest")
    import io
    names = set()
    with tarfile.open(fileobj=io.BytesIO(bytes(collected.buf)), mode="r:") as tf:
        for member in tf:
            norm = os.path.normpath(member.name)
            if member.name.startswith("/") or norm.startswith("..") or member.issym() and \
                    os.path.isabs(member.linkname):
                refuse("the MinIO copy contains an unsafe path")
            if member.uid != 65532 and member.name not in (".", "./"):
                refuse("the MinIO copy has an unexpected numeric owner")
            names.add(norm)
            if norm == ".minio.sys/format.json":
                load_json_bytes(tf.extractfile(member).read(), "format.json")
    del collected
    if ".minio.sys" not in names or ".minio.sys/format.json" not in names:
        refuse("the MinIO copy lacks the hidden .minio.sys state")
    return "STRUCTURE_PASS (%d entries)" % len(names)


# --------------------------------------------------------------------------- CLI (workstation)

def cmd_publish(args):
    ws = Workstation(args.home)
    cfg = ws.config()
    remote = Remote(cfg)
    staged = json.loads(remote.ops("recovery-list").decode("utf-8"))
    blob = Blob(cfg["azure"]) if cfg.get("azure") else None
    restore = None
    if cfg.get("restore_test", {}).get("postgres_image"):
        image = cfg["restore_test"]["postgres_image"]
        restore = lambda d, m: restore_test(d, m, ws, image)  # noqa: E731
    published, failures = [], []
    for entry in sorted(staged, key=lambda e: e["set_id"]):
        if entry.get("acknowledged") and any(e["set_id"] == entry["set_id"] for e in ws.ledger()):
            continue
        if args.set and entry["set_id"] != args.set:
            continue
        try:
            published.append(publish_set(ws, cfg, remote, entry, blob=blob, restore=restore,
                                         allow_older=args.allow_older))
        except RecoveryError as exc:
            failures.append("%s: %s" % (entry["set_id"], exc))
            print("PUBLISH FAILED %s: %s" % (entry["set_id"], exc), file=sys.stderr)
    status = workstation_status(ws, blob)
    print(json.dumps({"published": published, "failures": failures, "status": status}, indent=2, sort_keys=True))
    return 1 if failures or status["independent"]["state"] in ("MISSING", "BREACH") else 0


def workstation_status(ws, blob=None, now=None):
    now = now or utc_now()
    ledger = ws.ledger()
    independent = [e for e in ledger if e.get("independent") == "VERIFIED"
                   and os.path.isdir(ws.path("sets", e["set_id"]))]
    azure = [e for e in ledger if e.get("azure") == "PUBLISHED"]
    out = {}
    for label, entries in (("independent", independent), ("azure", azure)):
        newest = max([parse_iso(e["t_cut"], "t_cut") for e in entries] or [None]) if entries else None
        state, hours = point_age_status(newest, now)
        out[label] = {"state": state if (label == "independent" or blob is not None or entries) else "NOT_CONFIGURED",
                      "newest_cut": iso(newest) if newest else None,
                      "age_hours": round(hours, 2) if hours is not None else None, "points": len(entries)}
    return out


def cmd_status(args):
    ws = Workstation(args.home)
    print(json.dumps(workstation_status(ws), indent=2, sort_keys=True))
    return 0


def cmd_verify(args):
    ws = Workstation(args.home)
    cfg = ws.config()
    manifest = verify_set(args.directory, ws.path("trust", "producer.pub.pem"), cfg["producer_key_sha256"],
                          decrypt=None if args.no_decrypt else ws.decrypt_args(),
                          expected_manifest_sha256=args.manifest_sha256)
    print("VERIFIED %s (cut %s, %s)" % (manifest["set_id"], manifest["t_cut"], manifest["kind"]))
    return 0


def cmd_restore_test(args):
    ws = Workstation(args.home)
    cfg = ws.config()
    manifest = verify_set(args.directory, ws.path("trust", "producer.pub.pem"), cfg["producer_key_sha256"],
                          decrypt=ws.decrypt_args())
    print(json.dumps(restore_test(args.directory, manifest, ws, args.postgres_image), indent=2, sort_keys=True))
    return 0


def cmd_prune(args):
    ws = Workstation(args.home)
    cfg = ws.config()
    ledger = ws.ledger()
    points = [{"set_id": e["set_id"], "t_cut": e["t_cut"], "kind": e["kind"]} for e in ledger
              if os.path.isdir(ws.path("sets", e["set_id"]))]
    keep = retention_keep(points, utc_now(), holds=args.release or ())
    delete = sorted(p["set_id"] for p in points if p["set_id"] not in keep)
    blob = Blob(cfg["azure"]) if cfg.get("azure") else None
    if blob is not None:
        newest = max(points, key=lambda p: p["t_cut"])["set_id"] if points else None
        if newest and not blob.exists("sets/%s/%s" % (newest, COMPLETE_MARKER)):
            refuse("the newest point is not complete in Azure; pruning is refused")
    print(json.dumps({"keep": sorted(keep), "delete": delete, "confirmed": bool(args.confirm)}, indent=2))
    if not args.confirm:
        return 0
    for set_id in delete:
        if blob is not None:
            for name in SET_FILES + (COMPLETE_MARKER,):
                blob.delete("sets/%s/%s" % (set_id, name))
        shutil.rmtree(ws.path("sets", set_id))
    return 0


def main(argv=None):
    parser = argparse.ArgumentParser(prog="recovery.py", description=__doc__.split("\n\n")[0])
    parser.add_argument("--home", default=os.environ.get("CODEMASTERY_RECOVERY_HOME", DEFAULT_HOME))
    sub = parser.add_subparsers(dest="command", required=True)
    sub.add_parser("init-workstation")
    s = sub.add_parser("trust-producer")
    s.add_argument("pem")
    s.add_argument("--fingerprint", required=True)
    s = sub.add_parser("publish")
    s.add_argument("--set")
    s.add_argument("--allow-older", action="store_true")
    sub.add_parser("status")
    s = sub.add_parser("verify")
    s.add_argument("directory")
    s.add_argument("--manifest-sha256")
    s.add_argument("--no-decrypt", action="store_true")
    s = sub.add_parser("restore-test")
    s.add_argument("directory")
    s.add_argument("--postgres-image", required=True)
    s = sub.add_parser("prune")
    s.add_argument("--release", action="append", help="release a pre-update/manual point from retention")
    s.add_argument("--confirm", action="store_true")
    args = parser.parse_args(argv)
    try:
        if args.command == "init-workstation":
            init_workstation(args.home)
            return 0
        if args.command == "trust-producer":
            print("trusted producer key: %s" % trust_producer(args.home, args.pem, args.fingerprint))
            return 0
        return {"publish": cmd_publish, "status": cmd_status, "verify": cmd_verify,
                "restore-test": cmd_restore_test, "prune": cmd_prune}[args.command](args)
    except RecoveryError as exc:
        print("RECOVERY REFUSED: %s" % exc, file=sys.stderr)
        return 2


if __name__ == "__main__":
    sys.exit(main())
