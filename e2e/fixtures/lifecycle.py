#!/usr/bin/env python3
"""Pre-entry lifecycle coordinator for the CodeMastery E2E suite (ADR008 CT34-FIXTURES-2026-11-10-r2).

Every output-producing Playwright invocation enters here *before* the Playwright CLI starts, so
exclusive ownership of the canonical `e2e/` checkout and of its three output roots is established
and persisted before any output cleanup, discovery or report writing can touch them. A check inside
`globalSetup` would run after that point and is explicitly not sufficient (ADR008 Section 10.2).

Exclusivity is a nonblocking POSIX advisory `flock` on a stable lock file inside the private
per-OS-account registry `~/.codemastery-e2e/`, keyed by the checkout directory's device/inode
identity, so path aliases to the same directory contend for the same lock while independently
isolated checkouts run concurrently. `~` is resolved from the effective UID's OS account, never
from a caller's HOME. The lock file is never unlinked, replaced or stolen by age, is not inherited
by children, and is held without interruption through execution, teardown and final reporting.

Modes:
  run -- <command...>   admit this checkout, persist the run record, then execute the command
                        (the Playwright CLI) while holding exclusivity, and finalize afterwards
  recover               acquire the same lock and reconcile the persisted inventory (`stack:down`)
  report -- <command...> read-only pass-through for invocations that produce no output
  proc-identity <pid>   print one process' OS birth identity as JSON (helper for the Node side)
  proc-group <pgid>     print the live (non-zombie) members of one process group as JSON
  commit-record <path> <runId> <lockKey> <fence> <revision>
                        fenced compare-and-swap write of one inventory record (stdin: the record)
  host-identity         print host/boot/PID-namespace identity as JSON
  inspect               print the current inventory record for this checkout

Exit codes: 0 success, 1 failure, 2 another invocation owns this checkout, 3 this checkout is
blocked until recovery completes, 4 the environment is not qualified for safe exclusivity;
commit-record also uses 5 (fenced: the writer no longer owns the record), 6 (revision conflict:
the writer's view is stale) and 7 (the record lock could not be taken in time).

Record ownership protocol. Exclusivity of a checkout is the coordinator's `flock` above. Every
change of a run record additionally happens inside a short critical section under a second stable
lock file next to the record (`.<key>.json.lock`), whoever makes it: the coordinator (admission,
runner and finalizer registration, recovery takeover) or a Node writer through `commit-record`.
Inside it the current record is re-read and the writer's handed-down run ID, lock key and `fence`
must match it, and so must the `revision` the writer last read; the write then bumps `revision`.
Recovery takes the record over by incrementing `fence`, so every writer of an earlier generation is
fenced out permanently, however long it was paused. A new run has a new run ID. Writers (runner and
finalizers) are recorded with their OS birth identity before they write, and recovery never
completes while an earlier one may still be alive.
"""

import ctypes
import ctypes.util
import errno
import fcntl
import json
import os
import platform
import pwd
import re
import signal
import stat as statmodule
import struct
import subprocess
import sys
import time

CONTRACT = "CT34-FIXTURES-2026-11-10-r2"
SCHEMA = "codemastery-e2e-inventory/1"
REGISTRY_NAME = ".codemastery-e2e"
MAX_RECORD_BYTES = 256 * 1024
MAX_INTENTS = 128
NONTERMINAL = ("active", "cleaning", "incomplete")
RUN_ID = re.compile(r"^cm-e2e-[a-z0-9-]{1,64}$")
# How long the coordinator waits, after the runner exits, for the rest of the runner's process group
# (Playwright workers finishing their own teardown; Playwright 1.63 force-exits a disconnected worker
# after 30 s) before finalizing anyway. It bounds waiting only: finalization itself still refuses to
# touch the build or the stack while any member of that group is alive.
GROUP_SETTLE_SECONDS = 45
# Bound on waiting for a record lock; a writer that cannot take it in time fails closed.
RECORD_LOCK_SECONDS = 20
MAX_PROCESSES = 64
EXIT_FENCED, EXIT_CONFLICT_REVISION, EXIT_LOCK_BUSY = 5, 6, 7
# Test hooks of the fixture tests. They are never handed to a coordinated run.
TEST_HOOKS = ("E2E_VITE_BIN", "E2E_VITE_MODULE")
# Output roots of CP-029, relative to the canonical checkout directory. Order is irrelevant; all
# three are owned together by the single admitted invocation.
OUTPUT_ROOTS = ("test-results/artifacts", "test-results/results.json", "test-results/html")
# Filesystems whose advisory-locking or sharing semantics cannot be relied on for ownership state.
UNQUALIFIED_FS = (
    "nfs", "nfs3", "nfs4", "cifs", "smb", "smbfs", "smb2", "afp", "afpfs", "webdav", "davfs",
    "sshfs", "fuse.sshfs", "ftp", "curlftpfs", "9p", "vboxsf", "virtiofs", "afs", "lustre",
    "glusterfs", "ceph", "fuse.gvfsd-fuse", "osxfuse", "macfuse",
)

EXIT_OK, EXIT_FAIL, EXIT_CONFLICT, EXIT_BLOCKED, EXIT_UNQUALIFIED = 0, 1, 2, 3, 4


class Unqualified(Exception):
    """The host or the state/output filesystem cannot support the required ownership guarantees."""


class Blocked(Exception):
    """A previous run's ownership obligation is unresolved; this checkout must not be reused yet."""


class Fenced(Exception):
    """The writer's run ID, lock key or fence no longer matches the record: it lost ownership."""


class StaleRevision(Exception):
    """The record changed since the writer read it; its view is stale and is not written."""


class LockBusy(Exception):
    """The record lock could not be taken within the bound; nothing was written."""


def fail(message):
    sys.stderr.write("e2e-lifecycle: %s\n" % message)


def note(message):
    sys.stdout.write("e2e-lifecycle: %s\n" % message)
    sys.stdout.flush()


# --------------------------------------------------------------------------------------- identity

def account_home():
    """The effective UID's OS account home. A caller's HOME override is deliberately ignored."""
    return pwd.getpwuid(os.geteuid()).pw_dir


def _linux_birth(pid):
    with open("/proc/%d/stat" % pid, "rb") as handle:
        raw = handle.read().decode("utf-8", "replace")
    # The second field (comm) may contain spaces and parentheses; everything after the last ')'
    # is positional, so starttime is field 22 overall = index 19 of the remainder.
    tail = raw[raw.rindex(")") + 1:].split()
    return "linux-starttime:%s" % tail[19]


def _darwin_libc():
    name = ctypes.util.find_library("c") or "/usr/lib/libSystem.B.dylib"
    return ctypes.CDLL(name, use_errno=True)


def _darwin_birth(pid):
    """Native microsecond start time from the kernel's kinfo_proc, not a rounded `ps` timestamp.

    `struct kinfo_proc` begins with `struct extern_proc`, whose first member is the union holding
    `__p_starttime` (a `struct timeval`), so the first 12 bytes of the reply are tv_sec/tv_usec.
    """
    libc = _darwin_libc()
    mib = (ctypes.c_int * 4)(1, 14, 1, pid)  # CTL_KERN, KERN_PROC, KERN_PROC_PID, pid
    buf = ctypes.create_string_buffer(2048)
    size = ctypes.c_size_t(len(buf))
    if libc.sysctl(mib, 4, buf, ctypes.byref(size), None, ctypes.c_size_t(0)) != 0:
        raise OSError(ctypes.get_errno(), "sysctl KERN_PROC_PID failed")
    if size.value == 0:
        raise ProcessLookupError(pid)
    if size.value < 12:
        raise OSError(errno.EINVAL, "kinfo_proc reply too short")
    sec = struct.unpack_from("@q", buf, 0)[0]
    usec = struct.unpack_from("@i", buf, 8)[0]
    return "darwin-starttime:%d.%06d" % (sec, usec)


def process_identity(pid):
    """`{exists, birth, source, reliable}` for `pid`.

    `reliable` is false when the platform birth identity could not be obtained, in which case
    automatic process recovery is unsupported and the caller must preserve the process.
    """
    system = platform.system()
    try:
        if system == "Linux":
            return {"pid": pid, "exists": True, "birth": _linux_birth(pid),
                    "source": "linux-proc-starttime", "reliable": True}
        if system == "Darwin":
            return {"pid": pid, "exists": True, "birth": _darwin_birth(pid),
                    "source": "darwin-kinfo-starttime", "reliable": True}
    except (ProcessLookupError, FileNotFoundError):
        return {"pid": pid, "exists": False, "birth": None,
                "source": "%s-absent" % system.lower(), "reliable": True}
    except OSError as error:
        return {"pid": pid, "exists": None, "birth": None, "source": "unreadable",
                "reliable": False, "detail": "%s" % error}
    return {"pid": pid, "exists": None, "birth": None, "source": "unsupported-platform",
            "reliable": False, "detail": "no qualified birth identity on %s" % system}


def _darwin_kinfo_size(libc):
    """sizeof(struct kinfo_proc), as the running kernel fills it in for this one process (a sizing
    query without a buffer returns an estimate with slack, not the entry size)."""
    mib = (ctypes.c_int * 4)(1, 14, 1, os.getpid())
    buf = ctypes.create_string_buffer(4096)
    size = ctypes.c_size_t(len(buf))
    if libc.sysctl(mib, 4, buf, ctypes.byref(size), None, ctypes.c_size_t(0)) != 0 or size.value < 48:
        raise OSError(ctypes.get_errno(), "cannot size kinfo_proc")
    return size.value


def group_members(pgid):
    """`{reliable, members:[{pid, birth}]}`: the live members of process group `pgid`.

    Zombies are left out (an exited process cannot write). Nothing is signalled: Linux reads
    `/proc/<pid>/stat` (pgrp and start ticks), macOS asks the kernel for the group's `kinfo_proc`
    entries (`KERN_PROC_PGRP`). While any member is alive the kernel never hands the group's ID out
    as a new PID. Callers therefore treat any live member as possibly theirs (fail closed); only a
    leader PID that now carries another birth identity proves the original group is gone.
    """
    system = platform.system()
    members = []
    me = os.getpid()  # this helper may itself run inside the group it inspects
    try:
        if system == "Linux":
            for name in os.listdir("/proc"):
                if not name.isdigit():
                    continue
                try:
                    with open("/proc/%s/stat" % name, "rb") as handle:
                        raw = handle.read().decode("utf-8", "replace")
                except (FileNotFoundError, ProcessLookupError):
                    continue  # verified gone between the listing and the read
                # Any other error (EACCES, EPERM, EIO, ...) means a process could not be classified,
                # so the enumeration is incomplete: it propagates and the answer is unreliable.
                tail = raw[raw.rindex(")") + 1:].split()
                if len(tail) < 20:
                    raise ValueError("truncated /proc/%s/stat" % name)
                if tail[0] == "Z" or int(tail[2]) != pgid or int(name) == me:
                    continue
                members.append({"pid": int(name), "birth": "linux-starttime:%s" % tail[19]})
        elif system == "Darwin":
            libc = _darwin_libc()
            stride = _darwin_kinfo_size(libc)
            mib = (ctypes.c_int * 4)(1, 14, 2, pgid)  # CTL_KERN, KERN_PROC, KERN_PROC_PGRP, pgid
            raw, used = None, 0
            for _ in range(8):
                size = ctypes.c_size_t(0)
                if libc.sysctl(mib, 4, None, ctypes.byref(size), None, ctypes.c_size_t(0)) != 0:
                    raise OSError(ctypes.get_errno(), "sysctl KERN_PROC_PGRP (size) failed")
                capacity = size.value + 16 * stride
                buf = ctypes.create_string_buffer(capacity)
                size = ctypes.c_size_t(capacity)
                if libc.sysctl(mib, 4, buf, ctypes.byref(size), None, ctypes.c_size_t(0)) == 0:
                    raw, used = buf, size.value
                    break
                if ctypes.get_errno() != errno.ENOMEM:
                    raise OSError(ctypes.get_errno(), "sysctl KERN_PROC_PGRP failed")
            if raw is None or used % stride:
                raise OSError(errno.EIO, "unusable KERN_PROC_PGRP reply")
            data = raw.raw[:used]
            for offset in range(0, used, stride):
                # struct extern_proc: p_starttime at 0, p_stat (char) at 36, p_pid at 40; SZOMB == 5.
                if data[offset + 36] == 5:
                    continue
                pid = struct.unpack_from("@i", data, offset + 40)[0]
                if pid <= 0:
                    raise ValueError("implausible PID %d in KERN_PROC_PGRP reply" % pid)
                if pid == me:
                    continue
                sec = struct.unpack_from("@q", data, offset)[0]
                usec = struct.unpack_from("@i", data, offset + 8)[0]
                members.append({"pid": pid, "birth": "darwin-starttime:%d.%06d" % (sec, usec)})
        else:
            return {"pgid": pgid, "reliable": False, "members": [],
                    "detail": "no qualified process-group enumeration on %s" % system}
    except (OSError, ValueError, IndexError) as error:
        return {"pgid": pgid, "reliable": False, "members": [], "detail": "%s" % error}
    return {"pgid": pgid, "reliable": True, "members": members}


def _machine_identity(system):
    """The OS machine identity, which, unlike a network-assigned hostname, survives a network
    change: the macOS host UUID (`gethostuuid`) or the Linux machine-id. None when unavailable."""
    try:
        if system == "Darwin":
            class Timespec(ctypes.Structure):
                _fields_ = [("tv_sec", ctypes.c_long), ("tv_nsec", ctypes.c_long)]
            raw = (ctypes.c_ubyte * 16)()
            if _darwin_libc().gethostuuid(raw, ctypes.byref(Timespec(5, 0))) == 0:
                return "darwin-host:%s" % bytes(raw).hex()
        elif system == "Linux":
            for path in ("/etc/machine-id", "/var/lib/dbus/machine-id"):
                try:
                    with open(path, "rb") as handle:
                        value = handle.read().decode().strip()
                except OSError:
                    continue
                if re.match(r"^[0-9a-f]{32}$", value):
                    return "linux-machine:%s" % value
    except (OSError, AttributeError, ValueError):
        pass
    return None


def host_identity():
    """Host, boot and PID-namespace identity. Resources recorded under another one are foreign."""
    system = platform.system()
    boot, namespace = None, None
    try:
        if system == "Linux":
            with open("/proc/sys/kernel/random/boot_id", "rb") as handle:
                boot = "linux-boot:%s" % handle.read().decode().strip()
            namespace = os.readlink("/proc/self/ns/pid")
        elif system == "Darwin":
            libc = _darwin_libc()
            buf = ctypes.create_string_buffer(32)
            size = ctypes.c_size_t(len(buf))
            if libc.sysctlbyname(b"kern.boottime", buf, ctypes.byref(size), None,
                                 ctypes.c_size_t(0)) == 0 and size.value >= 12:
                boot = "darwin-boot:%d.%06d" % (struct.unpack_from("@q", buf, 0)[0],
                                                struct.unpack_from("@i", buf, 8)[0])
    except OSError:
        boot = boot or None
    return {"system": system, "hostname": platform.node(), "machine": _machine_identity(system),
            "boot": boot, "pidNamespace": namespace, "uid": os.geteuid()}


# ------------------------------------------------------------------------------------ filesystem

def filesystem_type(path):
    """The filesystem type backing `path`, or None when it cannot be determined."""
    target = os.path.realpath(path)
    best, kind = "", None
    try:
        if platform.system() == "Linux":
            with open("/proc/self/mountinfo", "rb") as handle:
                for line in handle.read().decode("utf-8", "replace").splitlines():
                    head, _, tail = line.partition(" - ")
                    fields, rest = head.split(), tail.split()
                    if len(fields) < 5 or not rest:
                        continue
                    point = fields[4].replace("\\040", " ")
                    if (target == point or target.startswith(point.rstrip("/") + "/")) and len(point) > len(best):
                        best, kind = point, rest[0]
        else:
            listing = subprocess.run(["/sbin/mount"], capture_output=True, text=True, timeout=20)
            for line in listing.stdout.splitlines():
                match = re.match(r"^\S+ on (.+?) \(([^,)]+)", line)
                if not match:
                    continue
                point, name = match.group(1), match.group(2)
                if (target == point or target.startswith(point.rstrip("/") + "/")) and len(point) > len(best):
                    best, kind = point, name
    except (OSError, subprocess.SubprocessError):
        return None
    return kind


def require_qualified_filesystem(path, what):
    kind = filesystem_type(path)
    if kind is None:
        raise Unqualified("the filesystem type of %s (%s) cannot be determined, so its locking/sharing "
                          "semantics cannot be qualified" % (what, path))
    if kind.lower() in UNQUALIFIED_FS:
        raise Unqualified("%s is on %s, whose locking/sharing semantics are not qualified: %s"
                          % (what, kind, path))
    return kind


def _secure_dir(path, mode=0o700):
    """Creates `path` if absent and returns it after verifying it is a non-symlinked, own-UID
    directory with exactly `mode`. Never follows a symlink in the final component."""
    try:
        os.mkdir(path, mode)
    except FileExistsError:
        pass
    except OSError as error:
        raise Unqualified("cannot create %s: %s" % (path, error))
    info = os.lstat(path)
    if not statmodule.S_ISDIR(info.st_mode):
        raise Unqualified("%s is not a directory (symlinks and other file types are refused)" % path)
    if info.st_uid != os.geteuid():
        raise Unqualified("%s is owned by uid %d, not the effective uid %d"
                          % (path, info.st_uid, os.geteuid()))
    if statmodule.S_IMODE(info.st_mode) != mode:
        os.chmod(path, mode)
        if statmodule.S_IMODE(os.lstat(path).st_mode) != mode:
            raise Unqualified("%s must have mode %o" % (path, mode))
    return path


class Registry(object):
    """The private `~/.codemastery-e2e/` state directory: one per OS account, outside every
    checkout, test-results tree and disposable build directory."""

    def __init__(self):
        self.root = _secure_dir(os.path.join(account_home(), REGISTRY_NAME))
        require_qualified_filesystem(self.root, "the lifecycle registry")
        self.locks = _secure_dir(os.path.join(self.root, "locks"))
        self.runs = _secure_dir(os.path.join(self.root, "runs"))
        self.archive = _secure_dir(os.path.join(self.root, "archive"))
        self.sockets = _secure_dir(os.path.join(self.root, "sockets"))

    def lock_file(self, key):
        return os.path.join(self.locks, "%s.lock" % key)

    def record_file(self, key):
        return os.path.join(self.runs, "%s.json" % key)


def checkout_identity(directory):
    """`(key, realpath, dev, ino)` for the canonical checkout directory. Aliases to the same
    directory produce the same key and therefore contend for the same lock."""
    real = os.path.realpath(directory)
    info = os.lstat(real)
    if not statmodule.S_ISDIR(info.st_mode):
        raise Unqualified("%s is not a directory" % real)
    return "%d-%d" % (info.st_dev, info.st_ino), real, info.st_dev, info.st_ino


def path_identity(checkout, relative):
    """Identity of one output root, refusing anything that is not an ordinary file or directory
    below `checkout`. Every component is inspected with lstat, so a replacement symlink anywhere
    on the way down is refused instead of followed."""
    absolute = os.path.join(checkout, relative)
    walked = checkout
    for part in relative.split(os.sep):
        walked = os.path.join(walked, part)
        try:
            info = os.lstat(walked)
        except FileNotFoundError:
            return {"path": absolute, "relative": relative, "exists": False,
                    "dev": None, "ino": None, "kind": "absent"}
        except OSError as error:
            raise Unqualified("cannot inspect output path %s: %s" % (walked, error))
        if statmodule.S_ISLNK(info.st_mode):
            raise Unqualified("output path component %s is a symlink; outputs must be ordinary "
                              "files or directories inside this checkout" % walked)
        if info.st_uid != os.geteuid():
            raise Unqualified("output path %s is owned by uid %d, not %d"
                              % (walked, info.st_uid, os.geteuid()))
        if walked != absolute and not statmodule.S_ISDIR(info.st_mode):
            raise Unqualified("output path component %s is not a directory" % walked)
    info = os.lstat(absolute)
    if not (statmodule.S_ISDIR(info.st_mode) or statmodule.S_ISREG(info.st_mode)):
        raise Unqualified("output path %s is neither an ordinary file nor a directory" % absolute)
    return {"path": absolute, "relative": relative, "exists": True, "dev": info.st_dev,
            "ino": info.st_ino, "kind": "directory" if statmodule.S_ISDIR(info.st_mode) else "file",
            "links": info.st_nlink}


def resolve_outputs(checkout, registry, key):
    """Identities of the three owned output roots, rejecting overlap between them, hard-link or
    bind sharing, and any identity already claimed by another checkout's nonterminal run."""
    require_qualified_filesystem(checkout, "the checkout and its output roots")
    outputs = [path_identity(checkout, relative) for relative in OUTPUT_ROOTS]
    for one in outputs:
        for other in outputs:
            if one is other:
                continue
            if one["path"] == other["path"] or other["path"].startswith(one["path"] + os.sep):
                raise Unqualified("output roots overlap: %s contains %s" % (one["path"], other["path"]))
            if one["exists"] and other["exists"] and (one["dev"], one["ino"]) == (other["dev"], other["ino"]):
                raise Unqualified("output roots %s and %s are the same filesystem object"
                                  % (one["path"], other["path"]))
        if one["exists"] and one["kind"] == "file" and one.get("links", 1) != 1:
            raise Unqualified("output file %s has %d links; it may be shared with another checkout"
                              % (one["path"], one["links"]))
    claimed = {}
    for name in sorted(os.listdir(registry.runs)):
        if not name.endswith(".json") or name[:-5] == key:
            continue
        other = read_record(os.path.join(registry.runs, name), tolerate=True)
        if not other or other.get("state") not in NONTERMINAL:
            continue
        for entry in other.get("outputs", []):
            if entry.get("dev") is not None:
                claimed[(entry["dev"], entry["ino"])] = (name[:-5], entry.get("path"))
    for one in outputs:
        if one["exists"] and (one["dev"], one["ino"]) in claimed:
            owner = claimed[(one["dev"], one["ino"])]
            raise Unqualified("output root %s is the same object as %s, owned by the active run of "
                              "checkout %s" % (one["path"], owner[1], owner[0]))
    return outputs


# ---------------------------------------------------------------------------------------- records

def read_record(path, tolerate=False):
    """Reads and validates one inventory record. Returns None when absent. A record that exists
    but is untrusted, corrupt, oversized or foreign raises Blocked unless `tolerate` is set."""
    try:
        handle = os.open(path, os.O_RDONLY | os.O_NOFOLLOW)
    except FileNotFoundError:
        return None
    except OSError as error:
        if tolerate:
            return None
        raise Blocked("inventory record %s cannot be opened (%s); it may describe an incomplete "
                      "run and must be reconciled by hand" % (path, error))
    try:
        info = os.fstat(handle)
        problem = None
        if not statmodule.S_ISREG(info.st_mode) or info.st_uid != os.geteuid() or info.st_nlink != 1:
            problem = "inventory record %s is not a single-link file owned by this account" % path
        elif statmodule.S_IMODE(info.st_mode) != 0o600:
            problem = "inventory record %s has mode %o, not 600" % (path, statmodule.S_IMODE(info.st_mode))
        elif info.st_size > MAX_RECORD_BYTES:
            problem = "inventory record %s is %d bytes, over the %d byte bound" % (path, info.st_size, MAX_RECORD_BYTES)
        if problem:
            # Another checkout's untrusted record is not ours to interpret, and must not block us.
            if tolerate:
                return None
            raise Blocked(problem)
        raw = os.read(handle, MAX_RECORD_BYTES + 1)
    finally:
        os.close(handle)
    try:
        record = json.loads(raw.decode("utf-8"))
    except (ValueError, UnicodeDecodeError) as error:
        if tolerate:
            return None
        raise Blocked("inventory record %s is corrupt (%s). A previous run may be incomplete; its "
                      "resources are preserved. Inspect it and remove it only after confirming "
                      "there are no leftovers." % (path, error))
    if not isinstance(record, dict) or record.get("schema") != SCHEMA:
        if tolerate:
            return None
        raise Blocked("inventory record %s has an unknown schema" % path)
    if record.get("ownerUid") != os.geteuid():
        if tolerate:
            return None
        raise Blocked("inventory record %s belongs to uid %s" % (path, record.get("ownerUid")))
    # Types are checked before any field is used (a malformed field must not crash the caller).
    problem = structure_problem(record)
    if problem:
        # Another checkout's malformed record is not ours to interpret: it is skipped, never
        # dereferenced. Our own blocks reuse until an operator has looked at it.
        if tolerate:
            return None
        raise Blocked("inventory record %s is malformed (%s). A previous run may be incomplete; its "
                      "resources are preserved. Inspect it and remove it only after confirming there "
                      "are no leftovers." % (path, problem))
    return record


def structure_problem(record):
    """Why `record` does not have the shape this lifecycle writes, or None. Checked before any field
    is used, so a malformed record can never crash the coordinator or be half-interpreted."""
    def integer(value):
        return isinstance(value, int) and not isinstance(value, bool)
    if not isinstance(record.get("runId"), str) or not RUN_ID.match(record["runId"]):
        return "run ID"
    if record.get("state") not in NONTERMINAL + ("complete",):
        return "state"
    if not isinstance(record.get("lockKey"), str):
        return "lock key"
    checkout = record.get("checkout")
    if not isinstance(checkout, dict) or not isinstance(checkout.get("path"), str) \
            or not integer(checkout.get("ino")) or not integer(checkout.get("dev")):
        return "checkout identity"
    outputs = record.get("outputs")
    if not isinstance(outputs, list):
        return "outputs"
    for entry in outputs:
        if not isinstance(entry, dict) or not isinstance(entry.get("path"), str):
            return "output entry"
        for field in ("dev", "ino"):
            if entry.get(field) is not None and not integer(entry.get(field)):
                return "output identity"
    processes = record.get("processes")
    if not isinstance(processes, dict):
        return "processes"
    for entry in processes.values():
        if not isinstance(entry, dict) or (entry.get("pid") is not None and not integer(entry.get("pid"))):
            return "process entry"
    if len(processes) > MAX_PROCESSES:
        return "process bound"
    for entry in processes.values():
        if entry.get("group") is not None and not integer(entry.get("group")):
            return "process group"
        if entry.get("spawned") is not None and not isinstance(entry.get("spawned"), bool):
            return "process spawn flag"
    intents = record.get("intents")
    if not isinstance(intents, list) or not all(isinstance(entry, dict) for entry in intents):
        return "intents"
    if len(intents) > MAX_INTENTS:
        return "intent bound"
    for field in ("fence", "revision"):
        if not integer(record.get(field)) or record[field] < 1:
            return field
    return None


def _durable_sync(fd):
    """fsync, plus F_FULLFSYNC on macOS, where fsync alone does not flush the drive's write cache.
    (Node's fs.fsyncSync already uses F_FULLFSYNC there through libuv.)"""
    os.fsync(fd)
    if platform.system() == "Darwin" and hasattr(fcntl, "F_FULLFSYNC"):
        try:
            fcntl.fcntl(fd, fcntl.F_FULLFSYNC)
        except OSError:
            pass


def write_record(path, record):
    """Atomic same-directory replacement with a durable file and directory flush, so a crash leaves
    either the previous or the new valid record and never a truncated one."""
    record["updatedAt"] = time.strftime("%Y-%m-%dT%H:%M:%SZ", time.gmtime())
    payload = json.dumps(record, sort_keys=True, separators=(",", ":")).encode("utf-8")
    if len(payload) > MAX_RECORD_BYTES:
        raise Unqualified("refusing to write a %d byte inventory record" % len(payload))
    directory = os.path.dirname(path)
    temporary = os.path.join(directory, ".%s.%d.tmp" % (os.path.basename(path), os.getpid()))
    try:
        os.unlink(temporary)  # a crashed earlier process with this PID; callers hold the record lock
    except FileNotFoundError:
        pass
    handle = os.open(temporary, os.O_WRONLY | os.O_CREAT | os.O_EXCL | os.O_NOFOLLOW, 0o600)
    try:
        os.write(handle, payload)
        _durable_sync(handle)
    finally:
        os.close(handle)
    os.replace(temporary, path)
    folder = os.open(directory, os.O_RDONLY)
    try:
        _durable_sync(folder)
    finally:
        os.close(folder)


def record_lock_path(record_path):
    """The stable lock file that serializes every change of one record (never unlinked)."""
    return os.path.join(os.path.dirname(record_path), ".%s.lock" % os.path.basename(record_path))


class RecordLock(object):
    """Short, bounded exclusive section around one record change. Never held while waiting for
    another process or another lock, so it cannot take part in a deadlock; a holder that crashes
    releases it with its descriptor, and the atomic replacement leaves the old or the new record."""

    def __init__(self, record_path, timeout=RECORD_LOCK_SECONDS):
        self.path, self.timeout, self.fd = record_lock_path(record_path), timeout, None

    def __enter__(self):
        fd = os.open(self.path, os.O_RDWR | os.O_CREAT | os.O_NOFOLLOW, 0o600)
        try:
            os.set_inheritable(fd, False)
            info = os.fstat(fd)
            if not statmodule.S_ISREG(info.st_mode) or info.st_nlink != 1 or info.st_uid != os.geteuid():
                raise Unqualified("%s must be a single-link ordinary file of this account" % self.path)
            if statmodule.S_IMODE(info.st_mode) != 0o600:
                os.fchmod(fd, 0o600)
            deadline = time.time() + self.timeout
            while True:
                try:
                    fcntl.flock(fd, fcntl.LOCK_EX | fcntl.LOCK_NB)
                    break
                except OSError as error:
                    if error.errno not in (errno.EWOULDBLOCK, errno.EAGAIN, errno.EACCES):
                        raise
                if time.time() > deadline:
                    raise LockBusy("the record lock %s was not released within %d s" % (self.path, self.timeout))
                time.sleep(0.02)
        except BaseException:
            os.close(fd)
            raise
        self.fd = fd
        return self

    def __exit__(self, *_exc):
        os.close(self.fd)
        self.fd = None
        return False


def owner_problem(current, run_id, key, fence):
    """Why a writer handed (run_id, key, fence) does not own `current`, or None."""
    if current is None:
        return "the record no longer exists"
    if current.get("runId") != run_id:
        return "the record belongs to run %s, not %s" % (current.get("runId"), run_id)
    if current.get("lockKey") != key:
        return "the record belongs to lock key %s, not %s" % (current.get("lockKey"), key)
    if current.get("fence") != fence:
        return "the record is at fence %s, not the writer's fence %s (ownership was taken over)" % (current.get("fence"), fence)
    return None


def locked_update(record_path, mutate, owner=None):
    """Reads, mutates and durably rewrites the record under its lock. With `owner` = (run_id, key,
    fence) the change is refused (Fenced) unless that writer still owns the record."""
    with RecordLock(record_path):
        current = read_record(record_path)
        if owner is not None:
            problem = owner_problem(current, *owner)
            if problem:
                raise Fenced(problem)
        if current is None:
            raise Blocked("inventory record %s disappeared" % record_path)
        result = mutate(current)
        current["revision"] = current["revision"] + 1
        write_record(record_path, current)
        return result, current


def commit_record(path, run_id, key, fence, revision, payload):
    """`commit-record`: the Node side's only way to change a record in a coordinated run. Under the
    record lock, the on-disk record must still belong to (run_id, key, fence) and still be at
    `revision`; the payload must describe the same run and generation. Returns the new revision."""
    directory, name = os.path.dirname(path), os.path.basename(path)
    if not os.path.isabs(path) or not re.match(r"^[A-Za-z0-9][A-Za-z0-9._-]*\.json$", name):
        raise Unqualified("%s is not an inventory record path" % path)
    info = os.lstat(directory)
    if not statmodule.S_ISDIR(info.st_mode) or info.st_uid != os.geteuid() or statmodule.S_IMODE(info.st_mode) != 0o700:
        raise Unqualified("%s must be a private (0700) directory of this account" % directory)
    if not isinstance(payload, dict) or payload.get("schema") != SCHEMA or payload.get("ownerUid") != os.geteuid():
        raise Unqualified("the payload is not an inventory record of this account")
    if payload.get("runId") != run_id or payload.get("lockKey") != key or payload.get("fence") != fence:
        raise Fenced("the payload does not describe the writer's own run and generation")
    with RecordLock(path):
        current = read_record(path)
        problem = owner_problem(current, run_id, key, fence)
        if problem:
            raise Fenced(problem)
        if current.get("revision") != revision:
            raise StaleRevision("the record is at revision %s, not the writer's revision %s"
                                % (current.get("revision"), revision))
        payload["revision"] = revision + 1
        problem = structure_problem(payload)
        if problem:
            raise Unqualified("the payload is malformed (%s)" % problem)
        write_record(path, payload)
        return payload["revision"]


def new_run_id(registry, key):
    """A fresh run ID, checked for collision against the registry's records and sockets."""
    for _ in range(64):
        candidate = "cm-e2e-%s-%s" % (format(int(time.time() * 1000), "x"), os.urandom(3).hex())
        if not os.path.exists(os.path.join(registry.archive, "%s.json" % candidate)) \
                and not os.path.exists(os.path.join(registry.sockets, "%s.sock" % candidate)):
            current = read_record(registry.record_file(key), tolerate=True)
            if not current or current.get("runId") != candidate:
                return candidate
        time.sleep(0.002)
    raise Unqualified("could not generate a collision-free run ID")


# ------------------------------------------------------------------------------------ exclusivity

class Exclusivity(object):
    """The exclusive nonblocking advisory lock for one checkout key.

    The lock file is stable: it is created once and then only ever opened. It is never unlinked,
    replaced, truncated or taken over by age, so its inode is the single arbiter for this checkout.
    The descriptor is not inheritable, so a child cannot hold the lock open past the coordinator,
    and the coordinator keeps it from before runner entry until after teardown and reporting.
    """

    def __init__(self, registry, key):
        self.key, self.path, self.fd = key, registry.lock_file(key), None

    def acquire(self):
        fd = os.open(self.path, os.O_RDWR | os.O_CREAT | os.O_NOFOLLOW, 0o600)
        try:
            os.set_inheritable(fd, False)
            info = os.fstat(fd)
            if not statmodule.S_ISREG(info.st_mode) or info.st_nlink != 1:
                raise Unqualified("%s must be a single-link ordinary file" % self.path)
            if info.st_uid != os.geteuid():
                raise Unqualified("%s is owned by uid %d" % (self.path, info.st_uid))
            if statmodule.S_IMODE(info.st_mode) != 0o600:
                os.fchmod(fd, 0o600)
            try:
                fcntl.flock(fd, fcntl.LOCK_EX | fcntl.LOCK_NB)
            except OSError as error:
                if error.errno in (errno.EWOULDBLOCK, errno.EAGAIN, errno.EACCES):
                    os.close(fd)
                    return False
                raise
        except BaseException:
            os.close(fd)
            raise
        self.fd = fd
        self._verify_semantics()
        return True

    def _verify_semantics(self):
        """Proves, on this actual lock file, that a second same-UID process is excluded while we
        hold the lock. This verifies the locking semantics directly instead of inferring them."""
        probe = ("import fcntl,os,sys\n"
                 "fd=os.open(sys.argv[1], os.O_RDWR|os.O_NOFOLLOW)\n"
                 "try:\n"
                 "    fcntl.flock(fd, fcntl.LOCK_EX|fcntl.LOCK_NB)\n"
                 "except OSError:\n"
                 "    print('excluded'); raise SystemExit(0)\n"
                 "print('acquired')\n")
        try:
            result = subprocess.run([sys.executable, "-I", "-c", probe, self.path],
                                    capture_output=True, text=True, timeout=60)
        except (OSError, subprocess.SubprocessError) as error:
            raise Unqualified("could not verify advisory locking semantics: %s" % error)
        if result.stdout.strip() != "excluded":
            raise Unqualified("advisory locking on %s does not exclude a second process (%r); this "
                              "filesystem cannot carry E2E ownership state"
                              % (self.path, result.stdout.strip() or result.stderr.strip()))

    def release(self):
        """Closes the descriptor, which releases the OS lock. The recorded ownership obligation in
        the inventory is *not* released by this; only verified completion clears it."""
        if self.fd is not None:
            os.close(self.fd)
            self.fd = None


# ------------------------------------------------------------------------------------- admission

def earlier_identity_records(registry, key, checkout, ino):
    """Nonterminal records left by *this* checkout under an earlier device identity.

    The lock key is the checkout directory's device/inode identity (Section 10.2), and some hosts
    renumber the device of an unchanged filesystem (macOS assigns APFS device numbers at mount time,
    so a reboot can change `st_dev` while the path and inode stay the same). A record whose checkout
    has this exact canonical path *and* inode under another device number therefore describes this
    same checkout: its ownership obligation still blocks reuse and is reconciled by `recover`. A
    different checkout (another path or another inode) is never matched. Returns `(key, path, record)`.
    """
    found = []
    for name in sorted(os.listdir(registry.runs)):
        if not name.endswith(".json") or name[:-5] == key:
            continue
        path = os.path.join(registry.runs, name)
        record = read_record(path, tolerate=True)
        if not record or record.get("state") not in NONTERMINAL:
            continue
        recorded = record.get("checkout") or {}
        if recorded.get("path") == checkout and recorded.get("ino") == ino and record.get("lockKey") == name[:-5]:
            found.append((name[:-5], path, record))
    return found


def admit(registry, key, checkout, mode):
    """Decides whether this invocation may proceed, under the already-held lock.

    `mode` is "run" (a new Playwright execution) or "recover" (reconcile what is recorded).
    Returns the previous record, or None. Raises Blocked when a nonterminal obligation exists.
    """
    path = registry.record_file(key)
    previous = read_record(path)
    if previous is None:
        return None
    state = previous.get("state")
    if mode == "recover":
        return previous
    if state in NONTERMINAL:
        raise Blocked("checkout %s has an unresolved run (%s, state %s). Run `npm run stack:down` "
                      "to reconcile it; its resources are preserved until then."
                      % (checkout, previous.get("runId"), state))
    if state != "complete":
        raise Blocked("checkout %s has a record in unknown state %r" % (checkout, state))
    archived = os.path.join(registry.archive, "%s.last.json" % key)
    try:
        os.replace(path, archived)
    except OSError as error:
        raise Blocked("could not archive the completed record %s: %s" % (path, error))
    return previous


def initial_record(registry, key, run_id, checkout, dev, ino, outputs, command):
    host = host_identity()
    supervisor = process_identity(os.getpid())
    return {
        "schema": SCHEMA, "contract": CONTRACT, "runId": run_id, "state": "active",
        "ownerUid": os.geteuid(), "lockKey": key, "fence": 1, "revision": 1,
        "host": {"hostname": host["hostname"], "machine": host["machine"], "boot": host["boot"],
                 "pidNamespace": host["pidNamespace"], "system": host["system"]},
        "checkout": {"path": checkout, "dev": dev, "ino": ino},
        "outputs": [{"path": entry["path"], "relative": entry["relative"], "dev": entry["dev"],
                     "ino": entry["ino"], "kind": entry["kind"]} for entry in outputs],
        # The runner's creation intent is part of the very record that admits the run, so it is
        # durable before the runner is spawned. A crash before its PID is recorded leaves a writer
        # with no PID, which recovery preserves (never assumes exited) until an operator reconciles.
        "processes": {"supervisor": {"pid": os.getpid(), "birth": supervisor.get("birth"),
                                    "birthSource": supervisor.get("source"),
                                    "role": "coordinator", "state": "live"},
                      "runner": {"pid": None, "birth": None, "birthSource": None,
                                 "role": "playwright-runner", "state": "starting"}},
        "docker": None, "temp": None, "intents": [],
        "registry": {"root": registry.root, "sockets": registry.sockets},
        "command": [os.path.basename(command[0])] + command[1:],
        "startedAt": time.strftime("%Y-%m-%dT%H:%M:%SZ", time.gmtime()),
        "observations": [],
    }


# ------------------------------------------------------------------------------------- execution

def child_environment(registry, key, run_id, record_path, fence=None, role=None):
    """The environment the admitted Playwright invocation inherits. The ownership token proves to
    `playwright.config.ts` that this process entered through the coordinator; all values are
    non-secret run identity, never credentials."""
    env = dict(os.environ)
    env.update({
        "CODEMASTERY_E2E_OWNERSHIP": "%s:%s" % (key, run_id),
        "CODEMASTERY_E2E_RUN_ID": run_id,
        "CODEMASTERY_E2E_LOCK_KEY": key,
        "CODEMASTERY_E2E_INVENTORY": record_path,
        "CODEMASTERY_E2E_REGISTRY": registry.root,
        "CODEMASTERY_E2E_SOCKETS": registry.sockets,
        "CODEMASTERY_E2E_CONTRACT": CONTRACT,
        "CODEMASTERY_E2E_SUPERVISOR": str(os.getpid()),
        # The Node side asks this same interpreter for process identity and record commits.
        "E2E_PYTHON": sys.executable,
    })
    for name, value in (("CODEMASTERY_E2E_FENCE", fence), ("CODEMASTERY_E2E_ROLE", role)):
        if value is None:
            env.pop(name, None)
        else:
            env[name] = str(value)
    env.pop("CODEMASTERY_E2E_FINALIZED", None)
    for hook in TEST_HOOKS:
        env.pop(hook, None)
    return env


def run_child(command, env, cwd, on_spawn=None):
    """Runs the admitted command to completion, forwarding interruption so its own teardown runs.

    The coordinator keeps the lock for the whole call, including the child's teardown, so no second
    invocation can touch the outputs while this one is still finishing. The runner starts a session
    of its own, so it leads a process group that holds every process it starts (Playwright workers,
    the build and the preview); the coordinator later waits for that group to empty. Interruption is
    forwarded to the runner itself, through the coordinator's own unreaped handle only.

    `on_spawn(child)` records the runner's identity right after the spawn, while the unreaped handle
    guarantees its PID cannot be reused. If that fails, the runner is stopped through the same handle.
    Returns `(status, forwarded signals)`; a runner that could not be started raises OSError.
    """
    holder = {"child": None}
    forwarded = {"count": 0}

    def forward(signum, _frame):
        forwarded["count"] += 1
        child = holder["child"]
        if child is None:
            return
        note("received signal %d; forwarding it to the runner so its teardown runs" % signum)
        try:
            child.send_signal(signum)
        except OSError:
            pass

    previous = {}
    for signum in (signal.SIGINT, signal.SIGTERM, signal.SIGHUP):
        try:
            previous[signum] = signal.signal(signum, forward)
        except (OSError, ValueError):
            pass
    try:
        child = subprocess.Popen(command, env=env, cwd=cwd, close_fds=True, start_new_session=True)
        holder["child"] = child
        if on_spawn is not None:
            try:
                on_spawn(child)
            except Exception as error:  # noqa: BLE001 - any failure leaves the runner unrecorded
                fail("the runner's identity could not be recorded (%s); stopping it through the "
                     "coordinator's own process handle" % error)
                child.terminate()
        while True:
            try:
                return child.wait(), forwarded["count"]
            except KeyboardInterrupt:
                continue
    finally:
        for signum, handler in previous.items():
            try:
                signal.signal(signum, handler)
            except (OSError, ValueError):
                pass


def mark_process(record_path, owner, role, fields):
    """Merges `fields` into one recorded process entry, as the owner (run_id, key, fence)."""
    def change(record):
        record["processes"].setdefault(role, {"role": role}).update(fields)
    locked_update(record_path, change, owner)


def record_spawned(record_path, owner, role, child, group=False):
    """Persists a spawned writer's PID and OS birth identity (and, for the runner, its process
    group). The handle is not yet reaped, so the PID still names this child even if it has already
    exited. With `start_new_session` a successful spawn guarantees that the child leads its own
    process group, whose ID is its PID."""
    identity = process_identity(child.pid)
    reliable = identity.get("reliable") and identity.get("exists")
    fields = {"pid": child.pid, "birth": identity.get("birth") if reliable else None,
              "birthSource": identity.get("source"), "state": "live", "spawned": True}
    if group:
        fields["group"] = child.pid
    mark_process(record_path, owner, role, fields)


def settle_runner_group(record_path, timeout=GROUP_SETTLE_SECONDS):
    """After the runner exits, waits (bounded, without signalling anything) until its process group
    holds no process other than recorded writers that finalization stops itself (the preview), so
    Playwright workers still closing are not raced. Finalization re-checks the group either way."""
    try:
        record = read_record(record_path)
    except Blocked:
        return False
    runner = (record or {}).get("processes", {}).get("runner") or {}
    pgid = runner.get("group")
    if not isinstance(pgid, int):
        return False
    deadline = time.time() + timeout
    while True:
        try:
            record = read_record(record_path) or record
        except Blocked:
            pass
        known = set((entry.get("pid"), entry.get("birth")) for role, entry in record["processes"].items()
                    if role != "runner" and entry.get("pid"))
        group = group_members(pgid)
        if group["reliable"]:
            stray = [m for m in group["members"] if (m["pid"], m["birth"]) not in known]
            if not stray:
                return True
        if time.time() > deadline:
            note("the runner's process group %d still has live members after %d s; finalization "
                 "preserves the build and the stack until they exit" % (pgid, timeout))
            return False
        time.sleep(0.25)


def finalize(checkout, registry, key, record_path, reason, takeover, expected=None):
    """Reconciles and verifies the persisted inventory after the runner has finished or died.

    Delegates to the Node recovery engine, which owns the Docker/process/path protocol, and runs it
    while this coordinator still holds exclusivity. The finalizer is a writer like the runner: its
    creation intent is recorded before it is spawned and its PID and birth identity right after, so
    a later recovery waits for it. With `takeover` (recovery), the record's `fence` is incremented
    first, which fences out every writer of an earlier generation permanently. Returns the engine's
    exit status: nonzero means the run is recorded incomplete and this checkout stays blocked.
    """
    owner, role = None, None
    try:
        with RecordLock(record_path):
            current = read_record(record_path)
            if current is not None and expected is not None:
                problem = owner_problem(current, *expected)
                if problem:
                    raise Fenced(problem)
            if current is not None:
                if takeover:
                    current["fence"] = current["fence"] + 1
                    # Finalizers a previous pass verified as exited are dropped to keep the record bounded.
                    for name in [n for n, e in current["processes"].items()
                                 if n.startswith("finalizer-") and e.get("state") == "exited"]:
                        del current["processes"][name]
                role = "finalizer-%d" % current["fence"]
                current["processes"][role] = {"pid": None, "birth": None, "birthSource": None,
                                              "role": "finalizer", "state": "starting"}
                current["revision"] = current["revision"] + 1
                write_record(record_path, current)
                owner = (current["runId"], key, current["fence"])
    except (Blocked, Unqualified, LockBusy, Fenced) as error:
        fail("the run record could not be prepared for finalization (%s); it stays unresolved" % error)
        return EXIT_FAIL
    env = child_environment(registry, key, owner[0] if owner else "none", record_path,
                            owner[2] if owner else None, role)
    command = ["node", os.path.join("fixtures", "stack-down.mjs"), "--finalize", "--reason", reason]
    try:
        child = subprocess.Popen(command, cwd=checkout, env=env, close_fds=True)
    except OSError as error:
        fail("could not run the recovery engine (%s); inventory %s stays unresolved" % (error, record_path))
        if owner:
            try:
                mark_process(record_path, owner, role, {"state": "exited", "spawned": False,
                                                        "why": "the finalizer could not be started"})
            except (Fenced, Blocked, Unqualified, LockBusy):
                pass
        return EXIT_FAIL
    if owner:
        try:
            record_spawned(record_path, owner, role, child)
        except (Fenced, Blocked, Unqualified, LockBusy) as error:
            # Without a recorded identity it must not write: stop it through our own unreaped handle.
            fail("the finalizer's identity could not be recorded (%s); stopping it" % error)
            child.terminate()
    while True:
        try:
            status = child.wait()
            break
        except KeyboardInterrupt:
            continue
    if owner:
        try:
            # The reaped handle proves it exited; recorded so recovery need not wait for it.
            mark_process(record_path, owner, role, {"state": "exited", "why": "reaped by its coordinator"})
        except (Fenced, Blocked, Unqualified, LockBusy):
            pass
    return status


def mode_run(registry, key, checkout, dev, ino, command):
    outputs = resolve_outputs(checkout, registry, key)
    for earlier_key, _, earlier in earlier_identity_records(registry, key, checkout, ino):
        raise Blocked("checkout %s has an unresolved run (%s, state %s) recorded under its earlier device "
                      "identity %s. Run `npm run stack:down` to reconcile it; its resources are preserved "
                      "until then." % (checkout, earlier.get("runId"), earlier.get("state"), earlier_key))
    record_path = registry.record_file(key)
    with RecordLock(record_path):
        admit(registry, key, checkout, "run")
        run_id = new_run_id(registry, key)
        # Ownership is persisted before the runner is started: a crash from here on leaves a
        # nonterminal record that blocks reuse until recovery verifies completion.
        write_record(record_path, initial_record(registry, key, run_id, checkout, dev, ino, outputs, command))
    owner = (run_id, key, 1)
    env = child_environment(registry, key, run_id, record_path, 1, "runner")
    note("run %s admitted for checkout %s (lock key %s)" % (run_id, checkout, key))
    try:
        status, interrupted = run_child(command, env, checkout,
                                        on_spawn=lambda child: record_spawned(record_path, owner, "runner", child, group=True))
    except OSError as error:
        # Popen reports a failed exec only after the child is gone, so no runner process exists.
        fail("the runner could not be started: %s" % error)
        mark_process(record_path, owner, "runner", {"state": "exited", "spawned": False,
                                                    "why": "the runner could not be started: %s" % error})
        finalize(checkout, registry, key, record_path, "runner-not-started", takeover=False, expected=owner)
        return EXIT_FAIL
    settle_runner_group(record_path)
    cleanup = finalize(checkout, registry, key, record_path, "interrupted" if interrupted else
                       ("runner-exit-%d" % status), takeover=False, expected=owner)
    if status != 0 and cleanup != 0:
        fail("the runner failed (exit %d) and cleanup did not complete (exit %d); both are reported "
             "above and the inventory is retained" % (status, cleanup))
    if status != 0:
        return status
    if cleanup != 0:
        fail("the runner passed but cleanup/recovery did not complete (exit %d)" % cleanup)
        return cleanup
    return EXIT_OK


def mode_recover(registry, key, checkout, ino):
    worst = EXIT_OK
    # Obligations recorded under this checkout's earlier device identity are reconciled first, through
    # their own record and while also holding their own (stable, never replaced) lock file.
    for earlier_key, earlier_path, earlier in earlier_identity_records(registry, key, checkout, ino):
        lock = Exclusivity(registry, earlier_key)
        if not lock.acquire():
            fail("the earlier-identity lock %s of this checkout is held; nothing was touched" % earlier_key)
            return EXIT_CONFLICT
        try:
            note("reconciling run %s, recorded under this checkout's earlier device identity %s"
                 % (earlier.get("runId"), earlier_key))
            status = finalize(checkout, registry, earlier_key, earlier_path, "recover-earlier-identity", takeover=True)
        finally:
            lock.release()
        worst = worst or status
    record_path = registry.record_file(key)
    previous = admit(registry, key, checkout, "recover")
    if previous is None:
        note("no inventory record for checkout %s; checking for unaccounted labelled resources" % checkout)
    status = finalize(checkout, registry, key, record_path, "recover", takeover=True)
    return worst or status


# ------------------------------------------------------------------------------------------- CLI

def split_command(argv):
    if "--" not in argv:
        return []
    return argv[argv.index("--") + 1:]


def main(argv):
    if not argv:
        fail("a mode is required (run, recover, report, proc-identity, proc-group, host-identity, inspect)")
        return EXIT_FAIL
    mode, rest = argv[0], argv[1:]

    if mode == "proc-identity":
        if not rest or not rest[0].isdigit():
            fail("proc-identity needs a PID")
            return EXIT_FAIL
        json.dump(process_identity(int(rest[0])), sys.stdout)
        sys.stdout.write("\n")
        return EXIT_OK
    if mode == "proc-group":
        if not rest or not rest[0].isdigit() or int(rest[0]) <= 1:
            fail("proc-group needs a process-group ID above 1")
            return EXIT_FAIL
        json.dump(group_members(int(rest[0])), sys.stdout)
        sys.stdout.write("\n")
        return EXIT_OK
    if mode == "commit-record":
        if len(rest) != 5 or not rest[3].isdigit() or not rest[4].isdigit():
            fail("commit-record needs <path> <runId> <lockKey> <fence> <revision>")
            return EXIT_FAIL
        try:
            payload = json.loads(sys.stdin.read(MAX_RECORD_BYTES + 1))
            revision = commit_record(rest[0], rest[1], rest[2], int(rest[3]), int(rest[4]), payload)
        except Fenced as error:
            fail("fenced: %s" % error)
            return EXIT_FENCED
        except StaleRevision as error:
            fail("stale: %s" % error)
            return EXIT_CONFLICT_REVISION
        except LockBusy as error:
            fail("%s" % error)
            return EXIT_LOCK_BUSY
        except (Blocked, Unqualified, ValueError, OSError) as error:
            fail("%s" % error)
            return EXIT_FAIL
        json.dump({"revision": revision}, sys.stdout)
        sys.stdout.write("\n")
        return EXIT_OK
    if mode == "host-identity":
        json.dump(host_identity(), sys.stdout)
        sys.stdout.write("\n")
        return EXIT_OK

    # The checkout is the directory holding this coordinator's own fixtures/ directory, resolved
    # through every symlink, so path aliases and the Playwright configuration agree on one identity.
    checkout_dir = os.path.dirname(os.path.dirname(os.path.realpath(__file__)))
    if mode == "report":
        # Reading a written report produces no output of its own and therefore does not need
        # ownership; taking the lock here would make a crashed run's evidence unreadable.
        command = split_command(rest)
        if not command:
            fail("report needs a command after --")
            return EXIT_FAIL
        return subprocess.run(command, cwd=checkout_dir).returncode

    try:
        registry = Registry()
        key, checkout, dev, ino = checkout_identity(checkout_dir)
    except Unqualified as error:
        fail("%s" % error)
        return EXIT_UNQUALIFIED

    if mode == "inspect":
        record = read_record(registry.record_file(key), tolerate=True)
        earlier = [{"lockKey": k, "record": r} for k, _, r in earlier_identity_records(registry, key, checkout, ino)]
        json.dump({"lockKey": key, "checkout": checkout, "record": record, "earlierIdentityRecords": earlier},
                  sys.stdout, indent=2)
        sys.stdout.write("\n")
        return EXIT_OK
    if mode not in ("run", "recover"):
        fail("unknown mode %r" % mode)
        return EXIT_FAIL

    command = split_command(rest)
    if mode == "run" and not command:
        fail("run needs the runner command after --")
        return EXIT_FAIL

    lock = Exclusivity(registry, key)
    try:
        if not lock.acquire():
            fail("another E2E invocation owns this checkout (lock key %s). Nothing was started, no "
                 "output was touched and no resource was signalled." % key)
            return EXIT_CONFLICT
    except Unqualified as error:
        fail("%s" % error)
        return EXIT_UNQUALIFIED
    try:
        if mode == "run":
            return mode_run(registry, key, checkout, dev, ino, command)
        return mode_recover(registry, key, checkout, ino)
    except Blocked as error:
        fail("%s" % error)
        return EXIT_BLOCKED
    except (Unqualified, LockBusy, Fenced) as error:
        fail("%s" % error)
        return EXIT_UNQUALIFIED if isinstance(error, Unqualified) else EXIT_FAIL
    finally:
        lock.release()


if __name__ == "__main__":
    sys.exit(main(sys.argv[1:]))
