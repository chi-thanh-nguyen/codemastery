#!/usr/bin/env python3
"""Unit tests for the ADR017 SSH deployment and recovery tooling (stdlib unittest).

Run from the repository root:  python3 -m unittest -v infrastructure/vm/tests/test_phase5_units.py
Cryptographic cases need OpenSSL 3 (`openssl` on Ubuntu, or CODEMASTERY_OPENSSL on macOS);
they are skipped, and say so, when only LibreSSL is available.
"""
import base64
import copy
import datetime
import hashlib
import http.server
import io
import json
import os
import shutil
import ssl
import subprocess
import sys
import tarfile
import tempfile
import threading
import unittest
from unittest import mock
from types import SimpleNamespace

ROOT = os.path.abspath(os.path.join(os.path.dirname(__file__), "..", "..", ".."))
sys.path.insert(0, os.path.join(ROOT, "infrastructure", "vm", "host"))
sys.path.insert(0, os.path.join(ROOT, "infrastructure", "vm", "cd"))
import cd  # noqa: E402
import ops  # noqa: E402
import recovery  # noqa: E402
import authorize  # noqa: E402
import release as rel  # noqa: E402  (cd.py puts infrastructure/release on the path)

MIGRATIONS = os.path.join(ROOT, "backend", "src", "main", "resources", "db", "migration")
# Flyway checksums recorded by the deployed preview's flyway_schema_history (read-only VM
# inventory, 23 November 2026): the computation must reproduce Flyway exactly.
VM_HISTORY = [("001", "create auth schema", -1213883831), ("002", "create course learning schema", -1362911002),
              ("003", "create skill graph schema", -358699017), ("004", "create assessment schema", -535553374),
              ("005", "create mastery and recommendation schema", 597291802),
              ("006", "create interaction and audit schema", 1554721998)]
R4_SNIPPET = "SELECT CASE WHEN count(*) = ${version} - 1\n"
ROWS = [{"relation": "enrollments", "column": "last_visited_at", "version": 9}]


def have_openssl3():
    try:
        recovery.openssl_bin()
        return True
    except recovery.RecoveryError:
        return False


OPENSSL3 = have_openssl3()


def history(n=6, **change):
    out = [{"version": v, "description": d, "checksum": c, "success": True} for v, d, c in VM_HISTORY[:n]]
    return out


def release_inventory():
    out = []
    for name in sorted(os.listdir(MIGRATIONS)):
        with open(os.path.join(MIGRATIONS, name), "rb") as f:
            out.append({"file": name, "sha256": hashlib.sha256(f.read()).hexdigest(),
                        "version": int(name[1:4])})
    return {"compatibility": {"migrations": {"inventory": out}}}


class FlywayAndCompatibility(unittest.TestCase):
    def setUp(self):
        self.migrations = ops.release_migrations(release_inventory(), ROOT)

    def test_checksums_match_the_deployed_flyway_history(self):
        for (version, description, checksum), m in zip(VM_HISTORY, self.migrations):
            self.assertEqual(m["checksum"], checksum, m["file"])
            self.assertEqual(m["description"], description)

    def test_checksum_ignores_line_endings_and_bom(self):
        self.assertEqual(ops.flyway_checksum(b"a\nb\n"), ops.flyway_checksum(b"\xef\xbb\xbfa\r\nb"))
        self.assertNotEqual(ops.flyway_checksum(b"a\nb"), ops.flyway_checksum(b"a\nc"))

    def test_v006_to_current_with_prefix_rule_is_an_upgrade(self):
        r = ops.compatibility(history(), {"enrollments.last_visited_at": False}, self.migrations, "prefix", ROWS, True)
        self.assertEqual(r["state"], "UPGRADE", r)
        self.assertEqual(r["applied"], 6)
        self.assertEqual(r["pending"][0], 7)

    def test_v006_with_the_r4_exact_rule_is_blocked_before_any_change(self):
        r = ops.compatibility(history(), {"enrollments.last_visited_at": False}, self.migrations, "exact", ROWS, True)
        self.assertEqual(r["state"], "BLOCKED")
        self.assertIn("CT17-P6 r4", " ".join(r["blocked"]))

    def test_exact_rule_accepts_only_the_direct_predecessor(self):
        h = history(6) + [{"version": "%03d" % m["version"], "description": m["description"], "checksum": m["checksum"],
                           "success": True} for m in self.migrations[6:8]]
        r = ops.compatibility(h, {"enrollments.last_visited_at": False}, self.migrations, "exact", ROWS, True)
        self.assertEqual(r["state"], "UPGRADE", r)

    def test_checksum_mismatch_failed_and_gapped_history_are_blocked(self):
        h = history()
        h[2]["checksum"] += 1
        self.assertIn("checksum", " ".join(ops.compatibility(h, {}, self.migrations, "prefix", ROWS, True)["blocked"]))
        h = history()
        h[5]["success"] = False
        self.assertIn("failed", " ".join(ops.compatibility(h, {}, self.migrations, "prefix", ROWS, True)["blocked"]))
        h = history()
        del h[3]
        self.assertIn("prefix", " ".join(ops.compatibility(h, {}, self.migrations, "prefix", ROWS, True)["blocked"]))
        h = history()
        h[1]["description"] = "renamed"
        self.assertIn("description", " ".join(ops.compatibility(h, {}, self.migrations, "prefix", ROWS, True)["blocked"]))
        h = history() + [{"version": None, "description": "r", "checksum": 1, "success": True}]
        self.assertEqual(ops.compatibility(h, {}, self.migrations, "prefix", ROWS, True)["state"], "BLOCKED")

    def test_target_ahead_of_the_release_refuses_image_only_rollback(self):
        older = self.migrations[:6]
        full = [{"version": "%03d" % m["version"], "description": m["description"], "checksum": m["checksum"],
                 "success": True} for m in self.migrations]
        r = ops.compatibility(full, {"enrollments.last_visited_at": True}, older, "prefix", [], True)
        self.assertEqual(r["state"], "BLOCKED")
        self.assertIn("rollback", " ".join(r["blocked"]))

    def test_current_fresh_unknown_and_drift(self):
        full = [{"version": "%03d" % m["version"], "description": m["description"], "checksum": m["checksum"],
                 "success": True} for m in self.migrations]
        self.assertEqual(ops.compatibility(full, {"enrollments.last_visited_at": True}, self.migrations, "prefix", ROWS,
                                           True)["state"], "CURRENT")
        self.assertEqual(ops.compatibility(None, {}, self.migrations, "prefix", ROWS, False)["state"], "FRESH")
        self.assertEqual(ops.compatibility(None, {}, self.migrations, "prefix", ROWS, True)["state"], "BLOCKED")
        drift = ops.compatibility(history(), {"enrollments.last_visited_at": True}, self.migrations, "prefix", ROWS, True)
        self.assertIn("drift", " ".join(drift["blocked"]))
        unknown = ops.compatibility(history(), {"enrollments.last_visited_at": False}, self.migrations, "unknown", ROWS,
                                    True)
        self.assertEqual(unknown["state"], "BLOCKED")

    def test_deferral_rule_detection(self):
        with open(os.path.join(ROOT, "infrastructure", "vm", "deploy.sh")) as f:
            rule, rows = ops.deferral_rule(f.read())
        self.assertEqual(rule, "prefix")
        self.assertEqual(rows, ROWS)
        legacy = "RUNTIME_MIGRATION_ADDED_COLUMNS='\nenrollments              last_visited_at  9\n'\n" + R4_SNIPPET
        self.assertEqual(ops.deferral_rule(legacy)[0], "exact")
        self.assertEqual(ops.deferral_rule("nothing here")[0], "none")

    def test_release_migrations_refuses_substituted_or_extra_files(self):
        inv = release_inventory()
        inv["compatibility"]["migrations"]["inventory"][0]["sha256"] = "0" * 64
        with self.assertRaises(recovery.RecoveryError):
            ops.release_migrations(inv, ROOT)
        inv = release_inventory()
        inv["compatibility"]["migrations"]["inventory"].pop()
        with self.assertRaises(recovery.RecoveryError):
            ops.release_migrations(inv, ROOT)


class Staging(unittest.TestCase):
    def test_relative_symlink_chain_cannot_escape_before_tree_check(self):
        a, b, c = tarfile.TarInfo("a"), tarfile.TarInfo("b"), tarfile.TarInfo("b/escaped")
        a.type, a.linkname = tarfile.SYMTYPE, "."
        b.type, b.linkname = tarfile.SYMTYPE, "a/.."
        c.size = 1
        with tempfile.TemporaryDirectory() as d:
            dest, archive = os.path.join(d, "tree"), os.path.join(d, "archive.tar")
            os.mkdir(dest)
            with open(archive, "wb") as f:
                f.write(self._tar([(a, None), (b, None), (c, b"x")]))
            with self.assertRaises(recovery.RecoveryError):
                ops.safe_extract(archive, dest)
            self.assertFalse(os.path.exists(os.path.join(d, "escaped")))

    def test_git_tree_matches_git_for_an_archive_of_head(self):
        try:
            head_tree = subprocess.run(["git", "-C", ROOT, "rev-parse", "HEAD^{tree}"], check=True,
                                       stdout=subprocess.PIPE).stdout.decode().strip()
            dirty = subprocess.run(["git", "-C", ROOT, "status", "--porcelain"], stdout=subprocess.PIPE).stdout
        except (OSError, subprocess.CalledProcessError):
            self.skipTest("no git checkout")
        with tempfile.TemporaryDirectory() as d:
            tar = os.path.join(d, "src.tar")
            subprocess.run(["git", "-C", ROOT, "archive", "--format=tar", "-o", tar, "HEAD"], check=True)
            dest = os.path.join(d, "x")
            os.mkdir(dest)
            ops.safe_extract(tar, dest)
            self.assertEqual(ops.git_tree(dest), head_tree)
            with open(os.path.join(dest, "README.md"), "a") as f:
                f.write("x")
            self.assertNotEqual(ops.git_tree(dest), head_tree)
        del dirty

    def _tar(self, members):
        buf = io.BytesIO()
        with tarfile.open(fileobj=buf, mode="w") as t:
            for info, data in members:
                t.addfile(info, io.BytesIO(data) if data is not None else None)
        return buf.getvalue()

    def test_safe_extract_refuses_unsafe_members(self):
        cases = []
        a = tarfile.TarInfo("../escape")
        a.size = 1
        cases.append([(a, b"x")])
        b = tarfile.TarInfo("link")
        b.type, b.linkname = tarfile.SYMTYPE, "/etc/passwd"
        cases.append([(b, None)])
        c = tarfile.TarInfo("dev")
        c.type = tarfile.CHRTYPE
        cases.append([(c, None)])
        d = tarfile.TarInfo("up")
        d.type, d.linkname = tarfile.SYMTYPE, "../../x"
        cases.append([(d, None)])
        for members in cases:
            with tempfile.TemporaryDirectory() as tmp:
                path = os.path.join(tmp, "t.tar")
                with open(path, "wb") as f:
                    f.write(self._tar(members))
                with self.assertRaises(recovery.RecoveryError):
                    ops.safe_extract(path, tmp)

    def test_context_roundtrip_and_refusals(self):
        dispatch = {"run_url": "https://github.com/x/y/actions/runs/1", "request": {"operation": "deploy"},
                    "dispatched_by": {"login": "chi-thanh-nguyen", "id": 212181152}}
        approval = {"approved_by": {"login": "chi-thanh-nguyen", "id": 212181152}}
        ctx, blob = cd.build_context(dispatch, approval, "LEADER-EXPOSURE-1", "NSG-RULESET-1", {"open_ports": [443]})
        self.assertEqual(ops.decode_context(blob), ctx)
        self.assertIn("open 443", ctx["packet_filter_verification"])
        with self.assertRaises(rel.Refused):
            cd.build_context(dispatch, approval, "", "NSG")
        bad = base64.urlsafe_b64encode(json.dumps(dict(ctx, authorization="a\nb")).encode()).decode()
        with self.assertRaises(recovery.RecoveryError):
            ops.decode_context(bad)


class TLSRenewalRegression(unittest.TestCase):
    def test_real_pair_replacement_and_partial_rename_rollback(self):
        for fail_chain_rename in (False, True):
            with self.subTest(fail_chain_rename=fail_chain_rename), tempfile.TemporaryDirectory() as d:
                cfg = {"acme_home": d, "origin": "https://codemastery.app", "stop_timeout": 1,
                       "wait_timeout": 1, "tls_backup_root": d, "tls_dir": d}
                for name, value in (("acme.sh", ""), ("fullchain.pem", "old-chain"), ("privkey.pem", "old-key"),
                                    ("issued-chain", "new-chain"), ("issued-key", "new-key")):
                    with open(os.path.join(d, name), "w") as f:
                        f.write(value)
                copyfile, rename = shutil.copyfile, os.rename
                def copy(src, dst, **kw):
                    if src == "/etc/codemastery/renewal-staging/fullchain.pem": src = os.path.join(d, "issued-chain")
                    if src == "/etc/codemastery/renewal-staging/privkey.pem": src = os.path.join(d, "issued-key")
                    return copyfile(src, dst, **kw)
                def replace(src, dst):
                    if fail_chain_rename and src == os.path.join(d, "fullchain.pem.new"):
                        raise OSError("injected pair-replacement interruption")
                    return rename(src, dst)
                def leaf(path):
                    with open(path) as f: return {"sha256": f.read()}
                with mock.patch.object(ops, "config", return_value=cfg), \
                        mock.patch.object(ops, "tls_status", return_value={"renewal_due": True}), \
                        mock.patch.object(ops, "Lock"), mock.patch.object(ops.recovery, "Docker") as docker, \
                        mock.patch.object(ops, "run"), mock.patch.object(ops.subprocess, "run") as acme, \
                        mock.patch.object(ops, "validate_pair"), mock.patch.object(ops.recovery, "_wait"), \
                        mock.patch.object(ops, "frontend_exec", return_value=(0, "")), \
                        mock.patch.object(ops, "served_certificate", side_effect=lambda *a: leaf(os.path.join(d, "fullchain.pem"))), \
                        mock.patch.object(ops, "file_leaf", side_effect=leaf), mock.patch.object(ops.os, "chown"), \
                        mock.patch.object(ops.shutil, "copyfile", side_effect=copy), \
                        mock.patch.object(ops.os, "rename", side_effect=replace), \
                        mock.patch.object(ops, "emit", side_effect=lambda x: x):
                    docker.return_value.project_containers.return_value = {"frontend": "fe"}
                    docker.return_value.state.return_value = {"Running": True}
                    acme.return_value.returncode = 0
                    if fail_chain_rename:
                        with self.assertRaises(recovery.RecoveryError):
                            ops.op_tls_renew(SimpleNamespace(staging=False, force=True))
                    else:
                        self.assertEqual(ops.op_tls_renew(SimpleNamespace(staging=False, force=True))["result"], "RENEWED")
                for name, suffix in (("fullchain.pem", "chain"), ("privkey.pem", "key")):
                    with open(os.path.join(d, name)) as f:
                        self.assertEqual(f.read(), ("old-" if fail_chain_rename else "new-") + suffix)

    def test_staging_must_verify_restarted_frontend(self):
        with tempfile.TemporaryDirectory() as d:
            cfg = {"acme_home": d, "origin": "https://codemastery.app", "stop_timeout": 1,
                   "wait_timeout": 1, "tls_backup_root": d, "tls_dir": d}
            open(os.path.join(d, "acme.sh"), "w").close()
            with mock.patch.object(ops, "config", return_value=cfg), \
                    mock.patch.object(ops, "tls_status", return_value={"renewal_due": True}), \
                    mock.patch.object(ops, "Lock"), mock.patch.object(ops.recovery, "Docker") as docker, \
                    mock.patch.object(ops, "run"), mock.patch.object(ops.subprocess, "run") as acme, \
                    mock.patch.object(ops, "validate_pair"), mock.patch.object(ops, "emit"), \
                    mock.patch.object(ops.recovery, "_wait", side_effect=recovery.RecoveryError("frontend not healthy")):
                docker.return_value.project_containers.return_value = {"frontend": "fe"}
                docker.return_value.state.return_value = {"Running": True}
                acme.return_value.returncode = 0
                with self.assertRaises(recovery.RecoveryError):
                    ops.op_tls_renew(SimpleNamespace(staging=True, force=False))

    def test_staging_success_requires_health_nginx_and_served_readback(self):
        with tempfile.TemporaryDirectory() as d:
            cfg = {"acme_home": d, "origin": "https://codemastery.app", "stop_timeout": 1,
                   "wait_timeout": 1, "tls_backup_root": d, "tls_dir": d}
            open(os.path.join(d, "acme.sh"), "w").close()
            with mock.patch.object(ops, "config", return_value=cfg), \
                    mock.patch.object(ops, "tls_status", return_value={"renewal_due": True}), \
                    mock.patch.object(ops, "Lock"), mock.patch.object(ops.recovery, "Docker") as docker, \
                    mock.patch.object(ops, "run"), mock.patch.object(ops.subprocess, "run") as acme, \
                    mock.patch.object(ops, "validate_pair"), mock.patch.object(ops.recovery, "_wait") as health, \
                    mock.patch.object(ops, "frontend_exec", return_value=(0, "")) as nginx, \
                    mock.patch.object(ops, "served_certificate", return_value={"sha256": "old"}) as served, \
                    mock.patch.object(ops, "file_leaf", return_value={"sha256": "old"}), \
                    mock.patch.object(ops, "emit", side_effect=lambda x: x):
                docker.return_value.project_containers.return_value = {"frontend": "fe"}
                docker.return_value.state.return_value = {"Running": True}
                acme.return_value.returncode = 0
                result = ops.op_tls_renew(SimpleNamespace(staging=True, force=False))
                self.assertEqual(result["result"], "STAGING_CHALLENGE_PASS")
                health.assert_called_once()
                nginx.assert_called_once()
                served.assert_called_once()
                acme.return_value.returncode = 1
                with self.assertRaises(recovery.RecoveryError):
                    ops.op_tls_renew(SimpleNamespace(staging=True, force=False))
                self.assertEqual(health.call_count, 2)  # challenge failure still restores/verifies ingress
                def stop_unknown(argv, **kw):
                    if argv[1] == "stop":
                        raise recovery.RecoveryError("stop outcome unknown")
                    return SimpleNamespace(returncode=0)
                ops.run.side_effect = stop_unknown
                with self.assertRaises(recovery.RecoveryError):
                    ops.op_tls_renew(SimpleNamespace(staging=True, force=False))
                self.assertEqual(health.call_count, 3)  # even a failed stop may have stopped the ingress


class VerificationRegression(unittest.TestCase):
    def test_nsg_cleanup_does_not_turn_lookup_failure_into_success(self):
        with tempfile.TemporaryDirectory() as d:
            for command, script in (("curl", '#!/bin/sh\necho \'{"value":"synthetic-oidc"}\'\n'),
                                    ("az", '#!/bin/sh\ncase "$*" in\n*"rule list"*) if [ "$TEST_LOOKUP_FAILURE" = yes ]; then exit 1; fi; echo "[]";;\nesac\n')):
                path = os.path.join(d, command)
                with open(path, "w") as f: f.write(script)
                os.chmod(path, 0o700)
            env = dict(os.environ, PATH=d + ":" + os.environ["PATH"], AZURE_CLIENT_ID="synthetic", AZURE_TENANT_ID="synthetic",
                       AZURE_SUBSCRIPTION_ID="synthetic", NSG_RESOURCE_GROUP="fixture", NSG_NAME="fixture",
                       NSG_RULE_PRIORITY="100", GITHUB_RUN_ID="77", ACTIONS_ID_TOKEN_REQUEST_URL="https://fixture.invalid/?x=1",
                       ACTIONS_ID_TOKEN_REQUEST_TOKEN="synthetic", TEST_LOOKUP_FAILURE="yes")
            command = ["bash", os.path.join(ROOT, "infrastructure/vm/cd/nsg-gate.sh"), "close"]
            result = subprocess.run(command, env=env, stdout=subprocess.PIPE, stderr=subprocess.PIPE)
            self.assertNotEqual(result.returncode, 0)
            self.assertIn(b"cannot inspect", result.stderr)
            env["TEST_LOOKUP_FAILURE"] = "no"
            self.assertEqual(subprocess.run(command, env=env, stdout=subprocess.PIPE).returncode, 0)
            env["NSG_RULE_PRIORITY"] = "5000"
            self.assertNotEqual(subprocess.run(command, env=env, stdout=subprocess.PIPE, stderr=subprocess.PIPE).returncode, 0)

    def verify(self, health, port="443", restart="unless-stopped"):
        with tempfile.TemporaryDirectory() as d:
            source = os.path.join(d, "a" * 40, "infrastructure", "vm")
            os.makedirs(source)
            open(os.path.join(source, "deploy.sh"), "w").close()
            def text(*args):
                template = args[2]
                return "sha256:" + "1" * 64 if template == "{{.Image}}" else \
                    restart if template == "{{.HostConfig.RestartPolicy.Name}}" else \
                    json.dumps({"8080/tcp": [{"HostPort": port}]})
            with mock.patch.object(ops, "SOURCE_ROOT", d), mock.patch.object(ops, "SERVICES", ("frontend",)), \
                    mock.patch.object(ops, "expected_images", return_value={"codemastery.invalid/frontend:release": "sha256:" + "1" * 64}), \
                    mock.patch.object(ops.recovery, "Docker") as docker, mock.patch.object(ops, "docker_text", side_effect=text), \
                    mock.patch.object(ops, "release_migrations", return_value=[]), mock.patch.object(ops, "target_schema", return_value=([], {}, True, {})), \
                    mock.patch.object(ops, "compatibility", return_value={"state": "CURRENT", "applied": 9}), \
                    mock.patch.object(ops, "frontend_exec", return_value=(0, health)), \
                    mock.patch.object(ops, "served_certificate", return_value={"sha256": "x", "san": ["codemastery.app"]}), \
                    mock.patch.object(ops, "file_leaf", return_value={"sha256": "x"}):
                docker.return_value.project_containers.return_value = {"frontend": "fe"}
                docker.return_value.state.return_value = {"Running": True, "Health": {"Status": "healthy"}}
                return ops.verify_state({"origin": "https://codemastery.app", "tls_dir": d}, {"source_revision": "a" * 40}, d, "graphdriver")

    def test_nested_component_up_does_not_override_overall_down(self):
        result = self.verify('{"status":"DOWN","components":{"db":{"status":"UP"}}}')
        self.assertEqual(result["readiness"], "NOT_UP")
        self.assertTrue(result["problems"])

    def test_wrong_host_port_and_restart_policy_refuse(self):
        result = self.verify('{"status":"UP"}', port="80", restart="always")
        self.assertTrue(any("host port 443" in p for p in result["problems"]))
        self.assertTrue(any("restart policy" in p for p in result["problems"]))
        self.assertEqual(self.verify('{"status":"UP"}')["problems"], [])

    def test_resume_after_any_deployment_attempt_refuses_before_start(self):
        with tempfile.TemporaryDirectory() as d:
            os.makedirs(os.path.join(d, "backup"))
            recovery.write_private(os.path.join(d, "backup", "hold.json"), recovery.dump_json(
                {"deployment_attempt": "now", "set_id": "CMRS-test", "held": {"backend": "be"}}))
            with mock.patch.object(ops, "STATE", d), mock.patch.object(ops, "Lock"), mock.patch.object(ops, "run") as run, \
                    mock.patch.object(ops, "docker_text", return_value="be"):
                with self.assertRaises(recovery.RecoveryError):
                    ops.op_resume(SimpleNamespace())
                run.assert_not_called()

    def test_failed_store_discovery_refuses(self):
        with mock.patch.object(ops, "docker_text", return_value=None), self.assertRaises(recovery.RecoveryError):
            ops.image_store()

    def test_workstation_lock_excludes_prune_and_second_publisher(self):
        with tempfile.TemporaryDirectory() as d:
            ws = recovery.Workstation(d)
            with recovery.WorkstationLock(ws):
                with self.assertRaises(recovery.RecoveryError):
                    with recovery.WorkstationLock(ws):
                        self.fail("second publisher entered")


@unittest.skipUnless(OPENSSL3, "OpenSSL 3 required for signed OIDC fixtures")
class OIDCAuthorization(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.directory = tempfile.TemporaryDirectory()
        cls.key = os.path.join(cls.directory.name, "issuer.key")
        cls.binary = recovery.openssl_bin()[0]
        subprocess.run([cls.binary, "genpkey", "-algorithm", "RSA", "-pkeyopt", "rsa_keygen_bits:2048", "-out", cls.key],
                       stdin=subprocess.DEVNULL, stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL, check=True)
        modulus = subprocess.check_output([cls.binary, "rsa", "-in", cls.key, "-modulus", "-noout"],
                                          stdin=subprocess.DEVNULL).decode().strip().split("=")[1]
        cls.jwk = {"kid": "fixture", "kty": "RSA", "alg": "RS256", "use": "sig",
                   "n": cls.enc(bytes.fromhex(modulus)), "e": cls.enc(b"\x01\x00\x01")}

    @classmethod
    def tearDownClass(cls):
        cls.directory.cleanup()

    @staticmethod
    def enc(data):
        return base64.urlsafe_b64encode(data).decode().rstrip("=")

    def token(self, argv, **changes):
        claims = {"iss": authorize.ISSUER, "aud": authorize.audience(argv), "repository": authorize.REPOSITORY,
                  "repository_id": authorize.REPOSITORY_ID, "repository_owner_id": authorize.OWNER_ID,
                  "actor": "chi-thanh-nguyen", "actor_id": authorize.OWNER_ID, "environment": "production",
                  "event_name": "workflow_dispatch", "ref": "refs/heads/main", "ref_type": "branch",
                  "workflow_ref": authorize.WORKFLOW, "workflow_sha": "a" * 40, "sha": "a" * 40,
                  "run_attempt": "1", "runner_environment": "github-hosted", "run_id": "77",
                  "jti": "fixture-id", "iat": 1000, "nbf": 1000, "exp": 1300}
        claims.update(changes)
        message = self.enc(json.dumps({"alg": "RS256", "typ": "JWT", "kid": "fixture"}).encode()) + "." + \
            self.enc(json.dumps(claims).encode())
        signature = subprocess.check_output([self.binary, "dgst", "-sha256", "-sign", self.key], input=message.encode())
        return message + "." + self.enc(signature)

    def fetch(self, url):
        if url == authorize.ISSUER + "/.well-known/jwks":
            return {"keys": [self.jwk]}
        self.assertEqual(url, "https://api.github.com/repos/" + authorize.REPOSITORY + "/git/commits/" + "a" * 40)
        return {"sha": "a" * 40, "tree": {"sha": "b" * 40}}

    def test_signed_commands_and_exact_trusted_tooling_tree(self):
        for argv in (["status"], ["receive", "package.tar", "0" * 64, "10"],
                     ["install-tooling", "a" * 40, "b" * 40], ["deploy", "--context", "bound-reference"]):
            self.assertEqual(authorize.verify(self.token(argv), argv, self.fetch, 1100)["run_id"], "77")

    def test_rejects_forgery_changed_arguments_and_untrusted_tooling(self):
        argv = ["status"]
        token = self.token(argv)
        for value, args in ((token[:-30] + "A" * 30, argv), (token, ["install-tooling", "a" * 40, "b" * 40]),
                            ("eyJhbGciOiJub25lIn0.e30.eA", argv), ("", argv)):
            with self.assertRaises(authorize.Refused):
                authorize.verify(value, args, self.fetch, 1100)
        for args in (["install-tooling", "c" * 40, "b" * 40], ["install-tooling", "a" * 40, "c" * 40]):
            with self.assertRaises(authorize.Refused):
                authorize.verify(self.token(args), args, self.fetch, 1100)

    def test_wrong_run_repository_actor_environment_time_and_workflow_refuse(self):
        for changes in ({"exp": 1099}, {"nbf": 1101}, {"iat": 1101}, {"exp": 1800}, {"repository_id": "1"},
                        {"repository_owner_id": "1"}, {"actor_id": "1"}, {"actor": "someone"}, {"run_attempt": "2"},
                        {"event_name": "pull_request"}, {"environment": "other"}, {"ref": "refs/heads/feature"},
                        {"workflow_ref": "other"}, {"workflow_sha": "b" * 40}, {"runner_environment": "self-hosted"}):
            with self.subTest(changes=changes), self.assertRaises(authorize.Refused):
                authorize.verify(self.token(["status"], **changes), ["status"], self.fetch, 1100)

    def test_single_use_replay_barrier(self):
        with tempfile.TemporaryDirectory() as d:
            claims = authorize.verify(self.token(["status"]), ["status"], self.fetch, 1100)
            authorize.consume(claims, d)
            with self.assertRaises(authorize.Refused):
                authorize.consume(claims, d)


def env_ok(**over):
    env = {"GITHUB_REPOSITORY": rel.REPOSITORY, "GITHUB_REPOSITORY_ID": rel.REPOSITORY_ID,
           "GITHUB_EVENT_NAME": "workflow_dispatch", "GITHUB_REF": "refs/heads/main",
           "GITHUB_WORKFLOW_REF": cd.WORKFLOW_REF, "GITHUB_RUN_ATTEMPT": "1", "GITHUB_SHA": "a" * 40,
           "GITHUB_RUN_ID": "77", "GITHUB_ACTOR": "chi-thanh-nguyen", "GITHUB_ACTOR_ID": "212181152",
           "GITHUB_TRIGGERING_ACTOR": "chi-thanh-nguyen"}
    env.update(over)
    return env


LEADER = {"login": "chi-thanh-nguyen", "id": 212181152, "type": "User"}


def event_ok(**inputs):
    i = {"operation": "plan", "version": "v0.1.0-rc.1", "recovery_set": "", "recovery_manifest_sha256": "", "note": ""}
    i.update(inputs)
    return {"ref": "refs/heads/main", "repository": {"full_name": rel.REPOSITORY, "id": int(rel.REPOSITORY_ID)},
            "sender": dict(LEADER), "inputs": i}


def run_ok(**over):
    r = {"id": 77, "run_attempt": 1, "event": "workflow_dispatch", "head_branch": "main", "head_sha": "a" * 40,
         "path": cd.WORKFLOW_PATH, "repository": {"full_name": rel.REPOSITORY, "id": int(rel.REPOSITORY_ID)},
         "actor": dict(LEADER), "triggering_actor": dict(LEADER)}
    r.update(over)
    return r


class Authentication(unittest.TestCase):
    def test_valid_dispatch(self):
        out = cd.authenticate(env_ok(), event_ok(), run_ok())
        self.assertEqual(out["request"]["operation"], "plan")
        self.assertEqual(out["run_url"], "https://github.com/%s/actions/runs/77" % rel.REPOSITORY)
        current = cd.authenticate(env_ok(), event_ok(operation="verify", version=""), run_ok())
        self.assertEqual(current["request"]["version"], "")

    def test_refusals(self):
        other = {"login": "someone", "id": 5, "type": "User"}
        bad = [
            (env_ok(GITHUB_RUN_ATTEMPT="2"), event_ok(), run_ok()),
            (env_ok(GITHUB_WORKFLOW_REF=rel.WORKFLOW_REF), event_ok(), run_ok()),
            (env_ok(GITHUB_REF="refs/heads/feature"), event_ok(), run_ok()),
            (env_ok(GITHUB_EVENT_NAME="push"), event_ok(), run_ok()),
            (env_ok(GITHUB_ACTOR="someone", GITHUB_TRIGGERING_ACTOR="someone", GITHUB_ACTOR_ID="5"),
             dict(event_ok(), sender=other), run_ok(actor=other, triggering_actor=other)),
            (env_ok(), event_ok(), run_ok(triggering_actor=other)),
            (env_ok(), event_ok(), run_ok(path=".github/workflows/release.yml")),
            (env_ok(), event_ok(version="latest"), run_ok()),
            (env_ok(), event_ok(operation="destroy"), run_ok()),
            (env_ok(), event_ok(recovery_set="CMRS-20261123T000000Z-pre-update-0011aabb"), run_ok()),
            (env_ok(), event_ok(operation="deploy", recovery_set="x", recovery_manifest_sha256="0" * 64), run_ok()),
            (env_ok(), event_ok(operation="deploy", recovery_set="CMRS-20261123T000000Z-scheduled-0011aabb",
                                recovery_manifest_sha256="0" * 64), run_ok()),
            (env_ok(), event_ok(note="a\nb"), run_ok()),
        ]
        for env, event, run in bad:
            with self.assertRaises(rel.Refused):
                cd.authenticate(env, event, run)

    def test_deploy_request_with_recovery_point(self):
        out = cd.authenticate(env_ok(), event_ok(operation="deploy",
                                                 recovery_set="CMRS-20261123T000000Z-pre-update-0011aabb",
                                                 recovery_manifest_sha256="ab" * 32), run_ok())
        self.assertEqual(out["request"]["recovery_set"], "CMRS-20261123T000000Z-pre-update-0011aabb")


def environment_ok(**over):
    e = {"name": "production", "id": 1, "can_admins_bypass": False,
         "protection_rules": [{"type": "required_reviewers", "reviewers": [{"type": "User", "reviewer": LEADER}]}],
         "deployment_branch_policy": {"protected_branches": False, "custom_branch_policies": True}}
    e.update(over)
    return e


POLICIES = {"branch_policies": [{"name": "main", "type": "branch"}]}
APPROVED = [{"state": "approved", "user": LEADER, "environments": [{"name": "production"}], "comment": ""}]


class EnvironmentGate(unittest.TestCase):
    def test_valid(self):
        self.assertEqual(cd.check_environment(environment_ok(), POLICIES, APPROVED)["approved_by"]["login"],
                         "chi-thanh-nguyen")

    def test_fail_closed(self):
        other = {"login": "someone", "id": 5, "type": "User"}
        cases = [
            (None, POLICIES, APPROVED),
            (environment_ok(name="release-admission"), POLICIES, APPROVED),
            (environment_ok(protection_rules=[]), POLICIES, APPROVED),
            (environment_ok(can_admins_bypass=True), POLICIES, APPROVED),
            (environment_ok(deployment_branch_policy=None), POLICIES, APPROVED),
            (environment_ok(), {"branch_policies": [{"name": "*"}]}, APPROVED),
            (environment_ok(), POLICIES, []),
            (environment_ok(), POLICIES, [dict(APPROVED[0], user=other)]),
            (environment_ok(), POLICIES, [dict(APPROVED[0], state="rejected")]),
            (environment_ok(), POLICIES, [dict(APPROVED[0], environments=[{"name": "release-admission"}])]),
        ]
        for env, pol, appr in cases:
            with self.assertRaises(rel.Refused):
                cd.check_environment(env, pol, appr)

    def test_report_renders_without_secrets(self):
        text = cd.render_report(cd.authenticate(env_ok(), event_ok(), run_ok()),
                                {"result": "BLOCKED", "blockers": ["x"], "compatibility": {"state": "BLOCKED",
                                                                                          "applied": 6, "pending": [7]}},
                                {"tls": {"trusted": True}, "open_ports": [443], "problems": []})
        self.assertIn("BLOCKED", text)
        self.assertIn("feature verification", text)


def now():
    return datetime.datetime(2026, 11, 23, 12, 0, tzinfo=datetime.timezone.utc)


def pt(set_id_time, kind="scheduled"):
    ts = datetime.datetime.strptime(set_id_time, "%Y%m%dT%H%M%SZ").replace(tzinfo=datetime.timezone.utc)
    return {"set_id": "CMRS-%s-%s-00000000" % (set_id_time, kind), "t_cut": recovery.iso(ts), "kind": kind}


class Retention(unittest.TestCase):
    def test_three_intraday_seven_daily_four_weekly_and_holds(self):
        points = []
        for day in range(0, 40):
            for hour in (0, 8, 16):
                ts = now() - datetime.timedelta(days=day, hours=12 - hour if hour < 12 else 0)
                points.append(pt(ts.strftime("%Y%m%dT%H%M%SZ")))
        points.append(pt("20261001T050000Z", "pre-update"))
        keep = recovery.retention_keep(points, now())
        newest = sorted(points, key=lambda p: p["t_cut"])
        for p in newest[-3:]:
            self.assertIn(p["set_id"], keep)
        days = {p["t_cut"][:10] for p in points if p["set_id"] in keep}
        for back in range(7):
            self.assertIn((now() - datetime.timedelta(days=back)).strftime("%Y-%m-%d"), days)
        self.assertIn("CMRS-20261001T050000Z-pre-update-00000000", keep)
        self.assertLessEqual(len(keep), 3 + 7 + 4 + 1)
        released = recovery.retention_keep(points, now(), holds=["CMRS-20261001T050000Z-pre-update-00000000"])
        self.assertNotIn("CMRS-20261001T050000Z-pre-update-00000000", released)

    def test_a_single_old_point_is_never_aged_out(self):
        only = [pt("20260101T000000Z")]
        self.assertEqual(recovery.retention_keep(only, now()), {only[0]["set_id"]})

    def test_point_age_thresholds(self):
        self.assertEqual(recovery.point_age_status(None, now())[0], "MISSING")
        self.assertEqual(recovery.point_age_status(now() - datetime.timedelta(hours=15), now())[0], "OK")
        self.assertEqual(recovery.point_age_status(now() - datetime.timedelta(hours=16), now())[0], "WARNING")
        self.assertEqual(recovery.point_age_status(now() - datetime.timedelta(hours=24, minutes=1), now())[0], "BREACH")
        self.assertEqual(recovery.point_age_status(now() + datetime.timedelta(seconds=1), now())[0], "CLOCK_ERROR")
        with tempfile.TemporaryDirectory() as d:
            ws = recovery.Workstation(d)
            good = dict(pt("20260101T000000Z"), schema=recovery.LEDGER_SCHEMA, independent="VERIFIED",
                        azure="NOT_CONFIGURED", manifest_sha256="0" * 64,
                        restore_test={k: "PASS" for k in ("postgres", "minio_boot", "minio_objects", "minio_iam")})
            entries = [good] + [dict(pt("20261123T%02d0000Z" % hour), schema=recovery.LEDGER_SCHEMA,
                                    independent="VERIFIED", azure="NOT_CONFIGURED", manifest_sha256="1" * 64,
                                    restore_test={"postgres": "PASS", "minio_boot": "NOT_RUN"}) for hour in (1, 2, 3)]
            for e in entries:
                os.makedirs(ws.path("sets", e["set_id"]))
                ws.append_ledger(e)
            status = recovery.workstation_status(ws, now=now())
            self.assertEqual(status["independent"]["newest_cut"], good["t_cut"])
            self.assertEqual(status["independent"]["state"], "BREACH")
            with mock.patch.object(recovery, "Workstation", return_value=ws), \
                    mock.patch.object(ws, "config", return_value={"producer_key_sha256": "0" * 64}), \
                    mock.patch.object(ws, "decrypt_args", return_value=None), \
                    mock.patch.object(recovery, "verify_set"), mock.patch("sys.stdout", new_callable=io.StringIO) as output:
                recovery.cmd_prune(SimpleNamespace(home=d, release=[], confirm=False))
            plan = json.loads(output.getvalue())
            self.assertIn(good["set_id"], plan["keep"])
            self.assertEqual(plan["delete"], [])


def synthetic_manifest(components):
    return {
        "schema": recovery.SCHEMA, "set_id": "CMRS-20261123T100000Z-pre-update-0a1b2c3d", "kind": "pre-update",
        "t_cut": "2026-11-23T10:00:01Z", "times": {"capture_started_at": "2026-11-23T09:59:58Z",
                                                  "writers_stopped_at": "2026-11-23T10:00:00Z",
                                                  "capture_finished_at": "2026-11-23T10:00:30Z"},
        "host": {}, "deployment": {}, "tools": {},
        "volumes": [{"name": "a", "created": "x", "driver": "local"}, {"name": "b", "created": "y", "driver": "local"}],
        "postgres": {"flyway_history": [], "row_counts": {}}, "minio": {}, "components": components,
        "custody": {"recipient_key_sha256": "0" * 64, "producer_key_sha256": "0" * 64, "signing": recovery.SIGNING},
        "writer_exclusion": {}, "qualification": "CAPTURED_UNPUBLISHED", "external_dependencies": {}}


def comp(name, data=b"x"):
    return {"name": name, "format": "CMREC1", "encryption": recovery.ENCRYPTION, "payload_length": 1,
            "payload_sha256": hashlib.sha256(data).hexdigest(), "ciphertext_length": 2, "ciphertext_sha256": "1" * 64}


class ManifestValidation(unittest.TestCase):
    def test_valid_and_mutations(self):
        good = synthetic_manifest([comp(n) for n in recovery.COMPONENTS])
        recovery.validate_manifest(copy.deepcopy(good))
        mutations = [
            lambda m: m.update(extra=1),
            lambda m: m.update(schema="other/v1"),
            lambda m: m.update(set_id="CMRS-../../x"),
            lambda m: m.update(kind="scheduled"),
            lambda m: m.update(t_cut="2026-11-23T09:00:00Z"),
            lambda m: m["components"].reverse(),
            lambda m: m["components"].pop(),
            lambda m: m["components"].append(comp("evil.sh")),
            lambda m: m["components"][0].update(name="../postgres.dump.cms"),
            lambda m: m["components"][0].update(payload_length=-1),
            lambda m: m["components"][0].update(ciphertext_sha256="XYZ"),
            lambda m: m["components"][0].update(encryption="aes-256-cbc"),
            lambda m: m["custody"].update(signing="rsa-pkcs1"),
            lambda m: m.update(qualification="QUALIFIED"),
        ]
        for mutate in mutations:
            m = copy.deepcopy(good)
            mutate(m)
            with self.assertRaises(recovery.RecoveryError):
                recovery.validate_manifest(m)

    def test_duplicate_keys_are_refused(self):
        with self.assertRaises(recovery.RecoveryError):
            recovery.load_json_bytes(b'{"a": 1, "a": 2}', "x")


@unittest.skipUnless(OPENSSL3, "OpenSSL 3 not available (set CODEMASTERY_OPENSSL); crypto cases not run")
class Crypto(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.dir = tempfile.mkdtemp()
        o = recovery.openssl_bin()[0]
        cls.passphrase = b"synthetic-test-passphrase"
        for name in ("rcpt", "other"):
            plain = os.path.join(cls.dir, name + ".plain")
            subprocess.run(stdin=subprocess.DEVNULL, args=[o, "genpkey", "-algorithm", "RSA", "-pkeyopt", "rsa_keygen_bits:3072", "-out", plain],
                           check=True)
            subprocess.run(stdin=subprocess.DEVNULL, args=[o, "req", "-x509", "-new", "-key", plain, "-subj", "/CN=" + name, "-days", "2",
                            "-out", os.path.join(cls.dir, name + ".crt")], check=True, stdout=subprocess.DEVNULL)
            subprocess.run(stdin=subprocess.DEVNULL, args=[o, "pkcs8", "-topk8", "-v2", "aes-256-cbc", "-v2prf", "hmacWithSHA256", "-iter", "1000",
                            "-passout", "pass:" + cls.passphrase.decode(), "-in", plain,
                            "-out", os.path.join(cls.dir, name + ".key")], check=True)
            os.unlink(plain)
        for name in ("producer", "intruder"):
            subprocess.run(stdin=subprocess.DEVNULL, args=[o, "genpkey", "-algorithm", "RSA", "-pkeyopt", "rsa_keygen_bits:3072", "-out",
                            os.path.join(cls.dir, name + ".key")], check=True)
            subprocess.run(stdin=subprocess.DEVNULL, args=[o, "pkey", "-in", os.path.join(cls.dir, name + ".key"), "-pubout", "-out",
                            os.path.join(cls.dir, name + ".pub")], check=True)

    @classmethod
    def tearDownClass(cls):
        shutil.rmtree(cls.dir)

    def p(self, name):
        return os.path.join(self.dir, name)

    def test_pg_only_restore_cannot_be_acknowledged_as_full_restore(self):
        with tempfile.TemporaryDirectory() as d:
            vm, home = os.path.join(d, "vm"), os.path.join(d, "ws")
            sid = "CMRS-20261123T100000Z-pre-update-0a1b2c3d"
            self.build_set(os.path.join(vm, "sets", sid))
            for sub in ("keys", "sets", "ledger", "trust", "incoming"):
                os.makedirs(os.path.join(home, sub))
            shutil.copy(self.p("rcpt.key"), os.path.join(home, "keys", "recipient.encrypted.pem"))
            shutil.copy(self.p("rcpt.crt"), os.path.join(home, "keys", "recipient.crt"))
            shutil.copy(self.p("producer.pub"), os.path.join(home, "trust", "producer.pub.pem"))
            ws = recovery.Workstation(home)
            ws.passphrase = lambda: self.passphrase
            class Remote:
                def fetch(self, set_id, name, dest):
                    shutil.copyfile(recovery.staged_file(set_id, name, vm), dest)
                def ops(self, *a): return b"{}"
            cfg = {"vm": {}, "producer_key_sha256": recovery.public_key_fingerprint(self.p("producer.pub"))}
            record = recovery.publish_set(ws, cfg, Remote(), recovery.list_staged(vm)[0],
                                          restore=lambda *a: {"postgres": "PASS", "minio_boot": "NOT_RUN"}, log=lambda *a: None)
            self.assertNotIn("+RESTORE_TESTED", record["level"])
            self.assertEqual(record["level"], "VERIFIED_INDEPENDENT+PG_RESTORE_TESTED")
            complete = {k: "PASS" for k in ("postgres", "minio_boot", "minio_objects", "minio_iam")}
            entry = recovery.list_staged(vm)[0]
            recovery.publish_set(ws, cfg, Remote(), entry, restore=lambda *a: complete, log=lambda *a: None)
            class Blob:
                def __init__(self): self.files = {}
                def create(self, name, path):
                    with open(path, "rb") as f: self.files[name] = f.read()
                def sha256(self, name):
                    data = self.files[name]
                    return len(data), hashlib.sha256(data).hexdigest()
            copied = recovery.publish_set(ws, cfg, Remote(), entry, blob=Blob(), log=lambda *a: None)
            self.assertEqual(copied["level"], "VERIFIED_INDEPENDENT_AZURE+RESTORE_TESTED")
            self.assertEqual(recovery.workstation_status(ws)["independent"]["points"], 1)
            with mock.patch.object(recovery, "utc_now", return_value=datetime.datetime(2020, 1, 1, tzinfo=datetime.timezone.utc)):
                with self.assertRaisesRegex(recovery.RecoveryError, "future"):
                    recovery.publish_set(ws, cfg, Remote(), entry)

    def encrypt(self, data, out):
        src = out + ".plain"
        with open(src, "wb") as f:
            f.write(data)
        try:
            return recovery.encrypt_stream(["cat", src], self.p("rcpt.crt"), out, "test")
        finally:
            os.unlink(src)

    def test_roundtrip_empty_binary_and_large(self):
        for label, data in (("empty", b""), ("binary", bytes(range(256)) * 50), ("large", os.urandom(3 << 20))):
            out = self.p("c-%s.cms" % label)
            meta = self.encrypt(data, out)
            self.assertEqual(meta["payload_length"], len(data))
            self.assertGreater(meta["ciphertext_length"], 0)
            length, digest = recovery.decrypt_stream(out, self.p("rcpt.key"), self.passphrase, self.p("rcpt.crt"))
            self.assertEqual((length, digest), (len(data), hashlib.sha256(data).hexdigest()))

    def test_wrong_key_corruption_truncation_and_wrong_passphrase_fail(self):
        out = self.p("c-neg.cms")
        self.encrypt(b"secret payload" * 1000, out)
        with self.assertRaises(recovery.RecoveryError):
            recovery.decrypt_stream(out, self.p("other.key"), self.passphrase, self.p("other.crt"))
        with self.assertRaises(recovery.RecoveryError):
            recovery.decrypt_stream(out, self.p("rcpt.key"), b"wrong", self.p("rcpt.crt"))
        with open(out, "rb") as f:
            data = bytearray(f.read())
        data[len(data) // 2] ^= 1
        corrupt = self.p("c-corrupt.cms")
        with open(corrupt, "wb") as f:
            f.write(bytes(data))
        with self.assertRaises(recovery.RecoveryError):
            recovery.decrypt_stream(corrupt, self.p("rcpt.key"), self.passphrase, self.p("rcpt.crt"))
        trunc = self.p("c-trunc.cms")
        with open(trunc, "wb") as f:
            f.write(bytes(data[:-40]))
        with self.assertRaises(recovery.RecoveryError):
            recovery.decrypt_stream(trunc, self.p("rcpt.key"), self.passphrase, self.p("rcpt.crt"))

    def test_failed_producer_yields_no_component(self):
        out = self.p("c-fail.cms")
        with self.assertRaises(recovery.RecoveryError):
            recovery.encrypt_stream([sys.executable, "-c", "import sys; sys.stdout.write('partial'); sys.exit(3)"],
                                    self.p("rcpt.crt"), out, "test")
        self.assertFalse(os.path.exists(out))

    def build_set(self, root):
        os.makedirs(root)
        comps = []
        for name, data in zip(recovery.COMPONENTS, (b"PGDMP-synthetic", b"tar-synthetic" * 10)):
            meta = self.encrypt(data, os.path.join(root, name))
            comps.append(dict(meta, name=name, format="CMREC1", encryption=recovery.ENCRYPTION))
        m = synthetic_manifest(comps)
        m["custody"]["recipient_key_sha256"] = recovery.certificate_key_fingerprint(self.p("rcpt.crt"))
        m["custody"]["producer_key_sha256"] = recovery.public_key_fingerprint(self.p("producer.pub"))
        recovery.write_private(os.path.join(root, recovery.MANIFEST), recovery.dump_json(m))
        recovery.sign_file(os.path.join(root, recovery.MANIFEST), self.p("producer.key"),
                           os.path.join(root, recovery.SIGNATURE))
        return m

    def test_verify_set_and_every_substitution(self):
        fp = recovery.public_key_fingerprint(self.p("producer.pub"))
        decrypt = (self.p("rcpt.key"), self.passphrase, self.p("rcpt.crt"))
        base = self.p("set-base")
        self.build_set(base)
        recovery.verify_set(base, self.p("producer.pub"), fp, decrypt=decrypt,
                            expected_manifest_sha256=recovery.set_manifest_sha256(base))

        def variant(name, change):
            d = self.p(name)
            shutil.copytree(base, d)
            change(d)
            return d
        flip = lambda f: (lambda d: open(os.path.join(d, f), "r+b").write(b"\x00"))  # noqa: E731
        cases = {
            "corrupt-component": flip(recovery.COMPONENTS[1]),
            "truncated": lambda d: open(os.path.join(d, recovery.COMPONENTS[0]), "r+b").truncate(10),
            "forged-manifest": lambda d: open(os.path.join(d, recovery.MANIFEST), "a").write(" "),
            "missing-component": lambda d: os.unlink(os.path.join(d, recovery.COMPONENTS[0])),
            "extra-file": lambda d: open(os.path.join(d, "run.sh"), "w").write("x"),
            "symlink": lambda d: (os.unlink(os.path.join(d, recovery.SIGNATURE)),
                                  os.symlink("/etc/hostname", os.path.join(d, recovery.SIGNATURE))),
            "resigned-by-intruder": lambda d: recovery.sign_file(os.path.join(d, recovery.MANIFEST),
                                                                 self.p("intruder.key"),
                                                                 os.path.join(d, recovery.SIGNATURE)),
        }
        for name, change in cases.items():
            d = variant(name, change)
            with self.assertRaises(recovery.RecoveryError, msg=name):
                recovery.verify_set(d, self.p("producer.pub"), fp, decrypt=decrypt)
        # A substituted trust key does not have the pinned fingerprint.
        with self.assertRaises(recovery.RecoveryError):
            recovery.verify_set(base, self.p("intruder.pub"), fp)
        # The manifest named by the operator must be the one present.
        with self.assertRaises(recovery.RecoveryError):
            recovery.verify_set(base, self.p("producer.pub"), fp, expected_manifest_sha256="0" * 64)

    def test_staged_listing_ack_and_prune(self):
        root = self.p("vm-backup")
        sets = os.path.join(root, "sets")
        os.makedirs(sets)
        ids = ["CMRS-20261123T000000Z-scheduled-00000001", "CMRS-20261123T080000Z-scheduled-00000002",
               "CMRS-20261123T160000Z-scheduled-00000003"]
        for i in ids:
            self.build_set(os.path.join(sets, i))
        os.makedirs(os.path.join(sets, "CMRS-x.partial"))
        staged = recovery.list_staged(root)
        self.assertEqual([s["set_id"] for s in staged], ids)
        self.assertTrue(all(s["complete"] for s in staged))
        with self.assertRaises(recovery.RecoveryError):
            recovery.acknowledge(ids[0], "0" * 64, "VERIFIED_INDEPENDENT", "op", root)
        with self.assertRaises(recovery.RecoveryError):
            recovery.acknowledge(ids[0], staged[0]["manifest_sha256"], "TRUST_ME", "op", root)
        with self.assertRaises(recovery.RecoveryError):
            recovery.acknowledge(ids[0], staged[0]["manifest_sha256"], "VERIFIED_INDEPENDENT+RESTORE_TESTED", "op", root,
                                 {"postgres": "PASS", "minio_boot": "NOT_RUN"})
        recovery.acknowledge(ids[0], staged[0]["manifest_sha256"], "VERIFIED_INDEPENDENT+RESTORE_TESTED", "op", root,
                             {k: "PASS" for k in ("postgres", "minio_boot", "minio_objects", "minio_iam")})
        recovery.acknowledge(ids[1], staged[1]["manifest_sha256"], "VERIFIED_INDEPENDENT", "op", root)
        removed = recovery.prune_staged(root, log=lambda m: None)
        self.assertEqual(removed, [ids[0]])
        self.assertTrue(os.path.isdir(os.path.join(sets, ids[1])))     # newest acknowledged stays
        self.assertTrue(os.path.isdir(os.path.join(sets, ids[2])))     # unacknowledged stays
        self.assertTrue(os.path.exists(os.path.join(sets, "CMRS-x.partial")))
        with self.assertRaises(recovery.RecoveryError):
            recovery.staged_file(ids[1], "../../etc/passwd", root)

    @mock.patch.object(recovery, "utc_now", return_value=datetime.datetime(2026, 11, 24, tzinfo=datetime.timezone.utc))
    def test_publish_to_independent_custody_and_blob_with_faults(self, _clock):
        vm = self.p("pub-vm")
        set_id = "CMRS-20261123T100000Z-pre-update-0a1b2c3d"
        self.build_set(os.path.join(vm, "sets", set_id))
        home = self.p("pub-home")
        for sub in ("keys", "sets", "ledger", "trust", "incoming"):
            os.makedirs(os.path.join(home, sub))
        shutil.copy(self.p("rcpt.key"), os.path.join(home, "keys", "recipient.encrypted.pem"))
        shutil.copy(self.p("rcpt.crt"), os.path.join(home, "keys", "recipient.crt"))
        shutil.copy(self.p("producer.pub"), os.path.join(home, "trust", "producer.pub.pem"))
        cfg = {"vm": {}, "producer_key_sha256": recovery.public_key_fingerprint(self.p("producer.pub"))}
        acks = []

        class FakeRemote(object):
            def fetch(self, sid, name, dest):
                shutil.copyfile(recovery.staged_file(sid, name, vm), dest)

            def ops(self, *args, **kw):
                acks.append(args)
                return b"{}"
        ws = recovery.Workstation(home)
        ws.passphrase = lambda: self.passphrase      # in memory; the Keychain is not used in tests
        entry = [e for e in recovery.list_staged(vm) if e["set_id"] == set_id][0]
        server = FakeBlobServer(self.dir)
        try:
            blob = server.client()
            rec = recovery.publish_set(ws, cfg, FakeRemote(), entry, blob=blob, log=lambda m: None)
            self.assertEqual(rec["level"], "VERIFIED_INDEPENDENT_AZURE")
            self.assertEqual(acks[-1][:2], ("recovery-ack", set_id))
            self.assertTrue(os.path.isdir(os.path.join(home, "sets", set_id)))
            self.assertEqual(sorted(n.split("/")[-1] for n in server.store if n.startswith("sets/" + set_id)),
                             sorted(recovery.SET_FILES + (recovery.COMPLETE_MARKER,)))
            self.assertEqual(server.order[-1], "sets/%s/%s" % (set_id, recovery.COMPLETE_MARKER))
            # Republishing the same set is a no-op; a substituted manifest under the same ID is refused.
            self.assertEqual(recovery.publish_set(ws, cfg, FakeRemote(), entry, blob=blob)["set_id"], set_id)
            with self.assertRaises(recovery.RecoveryError):
                recovery.publish_set(ws, cfg, FakeRemote(), dict(entry, manifest_sha256="0" * 64), blob=blob)
            # An existing blob is never replaced (If-None-Match: *), and the ledger does not advance.
            second = "CMRS-20261123T180000Z-pre-update-0a1b2c3e"
            self.build_set(os.path.join(vm, "sets", second))
            m = json.load(open(os.path.join(vm, "sets", second, recovery.MANIFEST)))
            m["set_id"], m["t_cut"] = second, "2026-11-23T18:00:01Z"
            m["times"] = {"capture_started_at": "2026-11-23T17:59:58Z", "writers_stopped_at": "2026-11-23T18:00:00Z",
                          "capture_finished_at": "2026-11-23T18:00:30Z"}
            recovery.write_private(os.path.join(vm, "sets", second, recovery.MANIFEST), recovery.dump_json(m))
            recovery.sign_file(os.path.join(vm, "sets", second, recovery.MANIFEST), self.p("producer.key"),
                               os.path.join(vm, "sets", second, recovery.SIGNATURE))
            entry2 = [e for e in recovery.list_staged(vm) if e["set_id"] == second][0]
            server.store["sets/%s/%s" % (second, recovery.MANIFEST)] = b"pre-existing"
            with self.assertRaises(recovery.RecoveryError):
                recovery.publish_set(ws, cfg, FakeRemote(), entry2, blob=blob)
            self.assertEqual(len(ws.ledger()), 1)
            del server.store["sets/%s/%s" % (second, recovery.MANIFEST)]
            shutil.rmtree(os.path.join(home, "sets", second), ignore_errors=True)
            # Readback that differs (corrupting store) is refused before the marker and ledger.
            server.corrupt_reads = True
            with self.assertRaises(recovery.RecoveryError):
                recovery.publish_set(ws, cfg, FakeRemote(), entry2, blob=blob)
            self.assertNotIn("sets/%s/%s" % (second, recovery.COMPLETE_MARKER), server.store)
            self.assertEqual(len(ws.ledger()), 1)
            server.corrupt_reads = False
            # Retry the interrupted publication using identical already-created
            # bytes. Neither cloud objects nor the independent copy are replaced.
            second_record = recovery.publish_set(ws, cfg, FakeRemote(), entry2, blob=blob,
                                                 restore=lambda d, m: {"postgres": "PASS", "minio_boot": "NOT_RUN"})
            self.assertEqual(second_record["level"], "VERIFIED_INDEPENDENT_AZURE+PG_RESTORE_TESTED")
            self.assertFalse(recovery.restore_qualified(second_record["restore_test"]))
            # The ledger may have committed before an SSH acknowledgement failed.
            # A retry authenticates existing copies and sends the receipt again.
            count = len(acks)
            recovery.publish_set(ws, cfg, FakeRemote(), entry2, blob=blob)
            self.assertEqual(len(acks), count + 1)
            self.assertEqual(len(ws.ledger()), 2)
            # A signed producer with a bad clock cannot publish a future cut.
            future = "CMRS-20501123T180000Z-pre-update-0a1b2c3e"
            shutil.copytree(os.path.join(vm, "sets", second), os.path.join(vm, "sets", future))
            m = json.load(open(os.path.join(vm, "sets", future, recovery.MANIFEST)))
            m["set_id"], m["t_cut"] = future, "2050-11-23T18:00:01Z"
            m["times"] = {"writers_stopped_at": "2050-11-23T18:00:00Z", "capture_finished_at": "2050-11-23T18:00:30Z"}
            recovery.write_private(os.path.join(vm, "sets", future, recovery.MANIFEST), recovery.dump_json(m))
            recovery.sign_file(os.path.join(vm, "sets", future, recovery.MANIFEST), self.p("producer.key"),
                               os.path.join(vm, "sets", future, recovery.SIGNATURE))
            entry_future = [e for e in recovery.list_staged(vm) if e["set_id"] == future][0]
            with self.assertRaisesRegex(recovery.RecoveryError, "future"):
                recovery.publish_set(ws, cfg, FakeRemote(), entry_future, blob=blob)
            self.assertEqual(len(ws.ledger()), 2)
            # An older cut than the ledger's newest is refused as rollback/replay.
            older = "CMRS-20261123T050000Z-pre-update-0a1b2c3f"
            shutil.copytree(os.path.join(vm, "sets", set_id), os.path.join(vm, "sets", older))
            m = json.load(open(os.path.join(vm, "sets", older, recovery.MANIFEST)))
            m["set_id"], m["t_cut"] = older, "2026-11-23T05:00:01Z"
            m["times"] = {"capture_started_at": "2026-11-23T04:59:58Z", "writers_stopped_at": "2026-11-23T05:00:00Z",
                          "capture_finished_at": "2026-11-23T05:00:30Z"}
            recovery.write_private(os.path.join(vm, "sets", older, recovery.MANIFEST), recovery.dump_json(m))
            recovery.sign_file(os.path.join(vm, "sets", older, recovery.MANIFEST), self.p("producer.key"),
                               os.path.join(vm, "sets", older, recovery.SIGNATURE))
            entry3 = [e for e in recovery.list_staged(vm) if e["set_id"] == older][0]
            with self.assertRaises(recovery.RecoveryError):
                recovery.publish_set(ws, cfg, FakeRemote(), entry3, blob=None)
        finally:
            server.stop()


class FakeBlobServer(object):
    """A local HTTPS Blob endpoint with conditional create, for protocol fault injection."""

    def __init__(self, directory):
        o = recovery.openssl_bin()[0]
        self.cert, self.key = os.path.join(directory, "blob.crt"), os.path.join(directory, "blob.key")
        subprocess.run(stdin=subprocess.DEVNULL, args=[o, "req", "-x509", "-newkey", "rsa:2048", "-nodes", "-keyout", self.key, "-out", self.cert,
                        "-subj", "/CN=127.0.0.1", "-addext", "subjectAltName=IP:127.0.0.1", "-days", "2"],
                       check=True, stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)
        self.store, self.order, self.corrupt_reads = {}, [], False
        outer = self

        class Handler(http.server.BaseHTTPRequestHandler):
            def log_message(self, *a):
                pass

            def name(self):
                return self.path.split("?")[0].split("/", 2)[2]

            def do_PUT(self):
                if self.headers.get("Authorization") != "Bearer synthetic-token":
                    self.send_response(403)
                    self.end_headers()
                    return
                body = self.rfile.read(int(self.headers["Content-Length"]))
                n = self.name()
                if self.headers.get("If-None-Match") == "*" and n in outer.store:
                    self.send_response(409)
                    self.end_headers()
                    return
                outer.store[n] = body
                outer.order.append(n)
                self.send_response(201)
                self.end_headers()

            def do_GET(self):
                n = self.name()
                if n not in outer.store:
                    self.send_response(404)
                    self.end_headers()
                    return
                data = outer.store[n] + (b"!" if outer.corrupt_reads else b"")
                self.send_response(200)
                self.send_header("Content-Length", str(len(data)))
                self.end_headers()
                self.wfile.write(data)

            def do_HEAD(self):
                self.send_response(200 if self.name() in outer.store else 404)
                self.end_headers()

        self.httpd = http.server.HTTPServer(("127.0.0.1", 0), Handler)
        ctx = ssl.SSLContext(ssl.PROTOCOL_TLS_SERVER)
        ctx.minimum_version = ssl.TLSVersion.TLSv1_2
        ctx.load_cert_chain(self.cert, self.key)
        self.httpd.socket = ctx.wrap_socket(self.httpd.socket, server_side=True)
        self.thread = threading.Thread(target=self.httpd.serve_forever, daemon=True)
        self.thread.start()

    def client(self):
        return recovery.Blob({"account": "cmtest", "container": "recovery",
                              "endpoint": "https://127.0.0.1:%d" % self.httpd.server_address[1],
                              "ca_file": self.cert, "token_command": "%s -c \"print('synthetic-token')\"" % sys.executable})

    def stop(self):
        self.httpd.shutdown()
        self.httpd.server_close()


if __name__ == "__main__":
    unittest.main()
