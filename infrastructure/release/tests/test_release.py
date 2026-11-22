"""Unit tests for infrastructure/release/release.py (ADR015).

Run from the repository root:
    python3 -m unittest discover -s infrastructure/release/tests -v

Synthetic fixtures only: OCI-layout archives built in memory, fabricated check
runs, scanner reports and admission records. No Docker or network access.
"""

import copy
import hashlib
import io
import json
import os
import subprocess
import sys
import tarfile
import tempfile
import unittest

sys.path.insert(0, os.path.join(os.path.dirname(os.path.abspath(__file__)), ".."))
import release  # noqa: E402

REV = "a" * 40
SERVICES = release.SERVICES


def put(path, data, mode="w"):
    with open(path, mode) as f:
        f.write(data)


def dump(obj, path):
    with open(path, "w") as f:
        json.dump(obj, f)


def read_json(path):
    with open(path) as f:
        return json.load(f)


def digest(data):
    return "sha256:" + hashlib.sha256(data).hexdigest()


def build_archive(path, images, extra_index=None, tamper=None, duplicate=False, platform=("linux", "amd64"),
                  wrap_index=False, two_amd64=False, referrer=False):
    """images: list of (name, payload). Writes a docker-save-like OCI archive."""
    blobs, index, manifest_json = {}, [], []

    def put(data):
        d = digest(data)
        blobs[d] = data
        return d

    for name, payload in images:
        config = json.dumps({"os": platform[0], "architecture": platform[1], "config": {}, "id": payload}).encode()
        layer = payload.encode()
        cd, ld = put(config), put(layer)
        manifest = json.dumps({"schemaVersion": 2, "mediaType": "application/vnd.oci.image.manifest.v1+json",
                               "config": {"mediaType": "application/vnd.oci.image.config.v1+json", "digest": cd, "size": len(config)},
                               "layers": [{"mediaType": "application/vnd.oci.image.layer.v1.tar", "digest": ld, "size": len(layer)}]}).encode()
        md = put(manifest)
        top = {"mediaType": "application/vnd.oci.image.manifest.v1+json", "digest": md, "size": len(manifest),
               "annotations": {"io.containerd.image.name": "docker.io/" + name if "." not in name.split("/")[0] else name}}
        if wrap_index:
            children = [{"mediaType": "application/vnd.oci.image.manifest.v1+json", "digest": md, "size": len(manifest),
                         "platform": {"os": "linux", "architecture": "amd64"}}]
            if two_amd64:
                children.append(dict(children[0]))
            idx = json.dumps({"schemaVersion": 2, "mediaType": "application/vnd.oci.image.index.v1+json", "manifests": children}).encode()
            top = {"mediaType": "application/vnd.oci.image.index.v1+json", "digest": put(idx), "size": len(idx),
                   "annotations": top["annotations"]}
        index.append(top)
        if referrer:
            index.append({"mediaType": "application/vnd.oci.image.manifest.v1+json", "digest": md, "size": len(manifest),
                          "annotations": {"io.containerd.manifest.subject": md}})
        manifest_json.append({"Config": "blobs/sha256/" + cd[7:], "RepoTags": [name], "Layers": ["blobs/sha256/" + ld[7:]]})
    index.extend(extra_index or [])
    files = {"index.json": json.dumps({"schemaVersion": 2, "manifests": index}).encode(),
             "manifest.json": json.dumps(manifest_json).encode()}
    for d, data in blobs.items():
        files["blobs/sha256/" + d[7:]] = data
    if tamper:
        files["blobs/sha256/" + tamper[7:]] = b"tampered"
    with tarfile.open(path, "w") as t:
        for name, data in files.items():
            info = tarfile.TarInfo(name)
            info.size = len(data)
            t.addfile(info, io.BytesIO(data))
            if duplicate and name == "index.json":
                t.addfile(info, io.BytesIO(data))


class ArchiveIdentityTests(unittest.TestCase):
    def setUp(self):
        self.tmp = tempfile.TemporaryDirectory()
        self.path = os.path.join(self.tmp.name, "a.tar")
        self.refs = ["codemastery.invalid/backend:release", "codemastery-candidate/frontend:abc"]
        self.images = [(self.refs[0], "be"), (self.refs[1], "fe")]

    def tearDown(self):
        self.tmp.cleanup()

    def test_identities_for_both_stores(self):
        build_archive(self.path, self.images)
        ids = release.archive_identities(self.path, self.refs)
        self.assertEqual(set(ids), set(self.refs))
        for v in ids.values():
            self.assertRegex(v["containerd"], release.DIGEST)
            self.assertRegex(v["graphdriver"], release.DIGEST)
            self.assertNotEqual(v["containerd"], v["graphdriver"])

    def test_index_wrapper_and_referrer(self):
        build_archive(self.path, self.images, wrap_index=True, referrer=True)
        self.assertEqual(set(release.archive_identities(self.path, self.refs)), set(self.refs))

    def test_refusals(self):
        cases = {
            "extra unrequested image": dict(images=self.images + [("codemastery.invalid/other:release", "x")]),
            "wrong platform": dict(images=self.images, platform=("linux", "arm64")),
            "duplicate entries": dict(images=self.images, duplicate=True),
            "two amd64 manifests": dict(images=self.images, wrap_index=True, two_amd64=True),
        }
        for label, kwargs in cases.items():
            with self.subTest(label):
                images = kwargs.pop("images")
                build_archive(self.path, images, **kwargs)
                with self.assertRaises(release.Refused):
                    release.archive_identities(self.path, self.refs)

    def test_missing_reference_and_tampered_blob(self):
        build_archive(self.path, self.images[:1])
        with self.assertRaises(release.Refused):
            release.archive_identities(self.path, self.refs)
        build_archive(self.path, self.images)
        some = release.archive_identities(self.path, self.refs)[self.refs[0]]["graphdriver"]
        build_archive(self.path, self.images, tamper=some)
        with self.assertRaises(release.Refused):
            release.archive_identities(self.path, self.refs)

    def test_reference_normalization_matches_deploy_sh(self):
        self.assertEqual(release.normalize_reference("busybox"), ("docker.io/library/busybox", "latest", ""))
        self.assertEqual(release.normalize_reference("codemastery.invalid/backend:release"),
                         ("codemastery.invalid/backend", "release", ""))


class CheckTests(unittest.TestCase):
    rules = [{"type": "pull_request"}, {"type": "required_status_checks", "parameters": {"required_status_checks": [
        {"context": "Backend", "integration_id": 15368}, {"context": "CodeQL", "integration_id": 57789}]}}]

    def run_(self, name, app, conclusion, started="2026-11-22T00:00:00Z", status="completed", rid=1):
        return {"name": name, "app": {"id": app}, "status": status, "conclusion": conclusion, "started_at": started, "id": rid}

    def test_all_success(self):
        runs = [self.run_("Backend", 15368, "success"), self.run_("CodeQL", 57789, "success")]
        self.assertEqual(len(release.evaluate_checks(self.rules, runs)), 2)

    def test_refusals(self):
        ok = self.run_("CodeQL", 57789, "success")
        cases = {
            "missing": [ok],
            "failed": [self.run_("Backend", 15368, "failure"), ok],
            "wrong app": [self.run_("Backend", 1, "success"), ok],
            "in progress": [self.run_("Backend", 15368, None, status="in_progress"), ok],
            "newest failed": [self.run_("Backend", 15368, "success", "2026-11-22T00:00:00Z", rid=1),
                              self.run_("Backend", 15368, "failure", "2026-11-22T01:00:00Z", rid=2), ok],
        }
        for label, runs in cases.items():
            with self.subTest(label), self.assertRaises(release.Refused):
                release.evaluate_checks(self.rules, runs)
        with self.assertRaises(release.Refused):
            release.evaluate_checks([{"type": "pull_request"}], [ok])

    def test_merged_pull_request_fallback(self):
        pr = {"number": 7, "merged_at": "x", "merge_commit_sha": REV, "base": {"ref": "main"}, "head": {"sha": "b" * 40}}
        self.assertIs(release.merged_pull_request([pr, dict(pr, merge_commit_sha="c" * 40)], REV), pr)
        self.assertIsNone(release.merged_pull_request([dict(pr, merged_at=None)], REV))
        self.assertIsNone(release.merged_pull_request([dict(pr, base={"ref": "other"})], REV))
        with self.assertRaises(release.Refused):
            release.merged_pull_request([pr, dict(pr, number=8)], REV)
        backend = self.run_("Backend", 15368, "success")
        codeql_on_pr = self.run_("CodeQL", 57789, "success")
        result = release.evaluate_checks(self.rules, [backend], [codeql_on_pr], pr)
        self.assertEqual([r["evidence"].split()[0] for r in result], ["revision", "merged"])
        # The pull request never overrides a run that exists on the revision.
        with self.assertRaises(release.Refused):
            release.evaluate_checks(self.rules, [backend, self.run_("CodeQL", 57789, "failure")], [codeql_on_pr], pr)
        with self.assertRaises(release.Refused):  # no merged PR: no fallback
            release.evaluate_checks(self.rules, [backend], [codeql_on_pr], None)
        with self.assertRaises(release.Refused):
            release.evaluate_checks(self.rules, [backend], [self.run_("CodeQL", 57789, "neutral")], pr)

    def test_a_later_success_never_overrides_a_failure(self):
        failed = self.run_("Backend", 15368, "failure", started="2026-11-22T00:00:00Z", rid=1)
        forged = self.run_("Backend", 15368, "success", started="2099-01-01T00:00:00Z", rid=2)
        codeql = self.run_("CodeQL", 57789, "success")
        with self.assertRaises(release.Refused):
            release.evaluate_checks(self.rules, [failed, forged, codeql])
        rerun_cancelled = self.run_("Backend", 15368, "cancelled", rid=3)
        with self.assertRaises(release.Refused):
            release.evaluate_checks(self.rules, [rerun_cancelled, forged, codeql])
        pr = {"number": 7, "merged_at": "x", "merge_commit_sha": REV, "base": {"ref": "main"}, "head": {"sha": "b" * 40}}
        with self.assertRaises(release.Refused):  # the same rule on the merged pull request's head
            release.evaluate_checks(self.rules, [forged], [self.run_("CodeQL", 57789, "failure"), codeql], pr)

    def test_an_unfinished_run_is_never_overridden(self):
        # F1: a queued or running real run, with a newer success beside it (for example a
        # run forged by a workflow with checks: write), still refuses the revision.
        codeql = self.run_("CodeQL", 57789, "success")
        forged = self.run_("Backend", 15368, "success", started="2099-01-01T00:00:00Z", rid=9)
        for status in ("queued", "in_progress", "waiting", "requested", "pending", None):
            real = self.run_("Backend", 15368, None, started=None if status in ("queued", None) else "2026-11-22T00:00:00Z",
                             status=status, rid=1)
            with self.subTest(status=status):
                with self.assertRaises(release.Refused) as raised:
                    release.evaluate_checks(self.rules, [real, forged, codeql])
                self.assertIn("has not completed", str(raised.exception))
                with self.assertRaises(release.Refused):   # order of the API listing does not matter
                    release.evaluate_checks(self.rules, [forged, real, codeql])
        # The same rule on the merged pull request's head.
        pr = {"number": 7, "merged_at": "x", "merge_commit_sha": REV, "base": {"ref": "main"}, "head": {"sha": "b" * 40}}
        backend = self.run_("Backend", 15368, "success")
        with self.assertRaises(release.Refused):
            release.evaluate_checks(self.rules, [backend],
                                    [self.run_("CodeQL", 57789, None, status="in_progress", rid=3), codeql], pr)
        # Several completed successful runs (re-runs that all passed) still qualify.
        again = self.run_("Backend", 15368, "success", started="2026-11-22T02:00:00Z", rid=4)
        self.assertEqual(release.evaluate_checks(self.rules, [backend, again, codeql])[0]["check_run_id"], 4)

    def test_fallback_requires_the_same_tree(self):
        pr = {"number": 7, "merged_at": "x", "merge_commit_sha": REV, "base": {"ref": "main"}, "head": {"sha": "b" * 40}}
        with tempfile.TemporaryDirectory() as d:
            paths = {n: os.path.join(d, n + ".json") for n in ("rules", "runs", "pulls", "pr_runs")}
            dump(self.rules, paths["rules"])
            dump([{"check_runs": [self.run_("Backend", 15368, "success")]}], paths["runs"])
            dump([pr], paths["pulls"])
            dump([{"check_runs": [self.run_("CodeQL", 57789, "success")]}], paths["pr_runs"])
            base = ["evaluate-checks", "--revision", REV, "--rules", paths["rules"], "--check-runs", paths["runs"],
                    "--pulls", paths["pulls"], "--pr-head", "b" * 40, "--pr-check-runs", paths["pr_runs"]]
            self.assertEqual(release.main(base + ["--revision-tree", "1" * 40, "--pr-head-tree", "1" * 40]), 0)
            self.assertEqual(release.main(base + ["--revision-tree", "1" * 40, "--pr-head-tree", "2" * 40]), 3)
            self.assertEqual(release.main(base), 3)


def trivy_report(rows):
    return {"SchemaVersion": 2, "Results": [{"Target": "t", "Vulnerabilities": [
        {"VulnerabilityID": i, "PkgName": p, "InstalledVersion": "1", "Severity": s} for i, p, s in rows]}]}


class FindingsAndMigrationTests(unittest.TestCase):
    def test_findings_and_rescan(self):
        with tempfile.TemporaryDirectory() as d:
            a, b = os.path.join(d, "a.json"), os.path.join(d, "b.json")
            dump(trivy_report([("CVE-1", "libc6", "MEDIUM")]), a)
            dump(trivy_report([("CVE-1", "libc6", "HIGH"), ("GO-2", "x/crypto", "UNKNOWN")]), b)
            f = release.normalize_findings("backend", [a, b])
            self.assertEqual([(x["id"], x["severity"]) for x in f], [("CVE-1", "HIGH"), ("GO-2", "UNKNOWN")])
            accepted = {("backend", "CVE-1", "libc6"), ("backend", "GO-2", "x/crypto")}
            self.assertEqual(release.check_rescan(accepted, f, f), [])
            reasons = [r[3] for r in release.check_rescan(accepted - {("backend", "GO-2", "x/crypto")}, f, f)]
            self.assertEqual(reasons, ["not accepted"])
            dump({"SchemaVersion": 1}, a)
            with self.assertRaises(release.Refused):
                release.normalize_findings("backend", [a])

    def test_rescan_is_bound_to_the_admitted_rows(self):
        candidate = [{"service": "backend", "id": "CVE-1", "package": "libc6", "installed": "1", "severity": "MEDIUM"}]
        accepted = {("backend", "CVE-1", "libc6")}
        same = [dict(candidate[0])]
        lower = [dict(candidate[0], severity="LOW")]
        raised = [dict(candidate[0], severity="CRITICAL")]
        other_version = [dict(candidate[0], installed="2")]
        new_advisory = [dict(candidate[0], id="CVE-2")]
        self.assertEqual(release.check_rescan(accepted, candidate, same + lower), [])
        self.assertEqual(release.check_rescan(accepted, candidate, []), [])
        for label, rows in (("raised", raised), ("other version", other_version), ("new advisory", new_advisory)):
            with self.subTest(label):
                self.assertEqual(len(release.check_rescan(accepted, candidate, rows)), 1)
        unknown = [dict(candidate[0], severity="UNKNOWN")]
        self.assertEqual(len(release.check_rescan(accepted, unknown, same)), 1)  # any reclassification of UNKNOWN

    def test_scan_coverage(self):
        with tempfile.TemporaryDirectory() as d:
            a, b = os.path.join(d, "a.json"), os.path.join(d, "b.json")
            dump(dict(trivy_report([]), Metadata={"OS": {"Family": "alpine", "Name": "3.24.3"}}), a)
            dump(dict(trivy_report([]), Metadata={"OS": {"Family": "alpine", "Name": "3.24.3", "EOSL": True}}), b)
            c = release.scan_coverage([a, b])
            self.assertEqual((c["os"], c["os_end_of_support"], c["targets"]), ("alpine 3.24.3", True, ["/:t"]))
            dump(trivy_report([]), a)
            self.assertIsNone(release.scan_coverage([a])["os"])
            dump(dict(trivy_report([]), Metadata={"OS": {"Family": "debian", "Name": "12"}}), a)
            with self.assertRaises(release.Refused):
                release.scan_coverage([a, b])
        with tempfile.TemporaryDirectory() as d:
            dump(trivy_report([]), os.path.join(d, "r.json"))
            args = ["findings", "--output", os.path.join(d, "f.json"), "--coverage-output", os.path.join(d, "c.json")]
            self.assertEqual(release.main(args + ["--report", "backend=" + os.path.join(d, "r.json")]), 3)  # services missing
            for s in SERVICES:
                args += ["--report", "%s=%s" % (s, os.path.join(d, "r.json"))]
            self.assertEqual(release.main(args), 0)
            self.assertEqual(set(read_json(os.path.join(d, "c.json"))), set(SERVICES))

    def test_migrations(self):
        base = [{"version": 1, "file": "V001__a.sql", "sha256": "1"}, {"version": 2, "file": "V002__b.sql", "sha256": "2"}]
        ok = base + [{"version": 3, "file": "V003__c.sql", "sha256": "3"}]
        self.assertEqual(release.migration_compatibility(ok, base), {"added": ["V003__c.sql"], "problems": []})
        modified = [base[0], dict(base[1], sha256="x")]
        self.assertTrue(release.migration_compatibility(modified, base)["problems"])
        self.assertTrue(release.migration_compatibility(base[:1], base)["problems"])
        older = base + [{"version": 2, "file": "V002__z.sql", "sha256": "z"}]
        self.assertTrue(release.migration_compatibility(older, base)["problems"])
        with tempfile.TemporaryDirectory() as d:
            put(os.path.join(d, "V001__a.sql"), "x")
            put(os.path.join(d, "notes.txt"), "x")
            with self.assertRaises(release.Refused):
                release.migration_inventory(d)


def candidate_manifest(rehearsal=False, verified=True):
    images = {s: {"containerd": digest(("c" + s).encode()), "graphdriver": digest(("g" + s).encode()),
                  "candidate_ref": "codemastery-candidate/%s:%s" % (s, REV[:12]), "release_ref": release.RELEASE_REFS[s]}
              for s in SERVICES}
    return {"schema": release.CANDIDATE_SCHEMA, "repository": "chi-thanh-nguyen/codemastery",
            "source_revision": REV, "public_origin": "https://codemastery.app",
            "run": {"rehearsal": rehearsal}, "images": images,
            "archive": {"name": "a.tar", "sha256": "b" * 64},
            "security": {"findings": [{"service": "backend", "id": "CVE-1", "package": "libc6", "severity": "MEDIUM"}]},
            "source_qualification": {"verified": verified}}


def coverage_report(archive, os_family="alpine", os_name="3.24.3", eosl=False, jar="app/app.jar"):
    """A Trivy report as `trivy image --input ARCHIVE` writes it: the OS result is named after the archive."""
    results = []
    meta = {}
    if os_family:
        meta = {"OS": {"Family": os_family, "Name": os_name, "EOSL": eosl}}
        results.append({"Target": "%s (%s %s)" % (archive, os_family, os_name), "Class": "os-pkgs", "Type": os_family})
    if jar:
        results.append({"Target": jar, "Class": "lang-pkgs", "Type": "jar"})
    return dict({"SchemaVersion": 2, "Results": results}, Metadata=meta)


class CoverageGateTests(unittest.TestCase):
    """F3: the promotion rescan must analyse at least what the candidate scan analysed."""

    def coverage(self, d, name, **variants):
        """Coverage of all five services; variants[service] overrides that service's report."""
        out = {}
        for s in SERVICES:
            path = os.path.join(d, "%s-%s.json" % (name, s))
            dump(coverage_report(**dict({"archive": "/work/%s.tar" % s}, **variants.get(s, {}))), path)
            out[s] = release.scan_coverage([path])
        return out

    def test_equal_coverage_with_different_image_names_and_paths(self):
        with tempfile.TemporaryDirectory() as d:
            cand = self.coverage(d, "cand", backend={"archive": "/work/codemastery-candidate-backend-abc.tar"})
            rescan = self.coverage(d, "rescan", backend={"archive": "/tmp/scan/codemastery.invalid_backend_release.tar"})
            self.assertEqual(cand["backend"]["targets"], ["lang-pkgs/jar:app/app.jar", "os-pkgs/alpine"])
            self.assertEqual(release.check_coverage(cand, rescan), [])
            # More coverage at promotion is not a loss; OS names compare without case or spacing noise.
            more = copy.deepcopy(cand)
            more["minio"]["targets"].append("gobinary/gobinary:usr/bin/extra")
            self.assertEqual(release.check_coverage(cand, more), [])
            rescan["postgres"]["os"] = "  Alpine   3.24.3 "
            self.assertEqual(release.check_coverage(cand, rescan), [])
            no_os = self.coverage(d, "noos", minio={"os_family": None})
            self.assertEqual(release.check_coverage(no_os, cand), [])   # an OS newly detected is more coverage

    def test_lost_coverage_is_refused(self):
        with tempfile.TemporaryDirectory() as d:
            cand = self.coverage(d, "cand")
            cases = {
                "os no longer detected": {"backend": {"os_family": None}},
                "another os": {"backend": {"os_family": "debian", "os_name": "12"}},
                "newly end of support": {"backend": {"eosl": True}},
                "missing target": {"backend": {"jar": None}},
                "moved target": {"backend": {"jar": "app/other.jar"}},
            }
            for label, variants in cases.items():
                with self.subTest(label):
                    problems = release.check_coverage(cand, self.coverage(d, label.replace(" ", "-"), **variants))
                    self.assertTrue(problems)
                    self.assertEqual({p[0] for p in problems}, {"backend"})
            for label, rescan in (("missing", None), ("service missing", {s: cand[s] for s in SERVICES[1:]}),
                                  ("malformed", dict(cand, backend={"os": None, "targets": "x"}))):
                with self.subTest(label):
                    self.assertTrue(release.check_coverage(cand, rescan))
                    self.assertTrue(release.check_coverage(rescan, cand))

    def test_check_rescan_cli(self):
        script = os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "release.py")
        with tempfile.TemporaryDirectory() as d:
            cand = candidate_manifest()
            cand["security"]["coverage"] = self.coverage(d, "cand")
            cand["security"]["findings"][0]["installed"] = "1"
            paths = {n: os.path.join(d, n + ".json") for n in ("cand", "adm", "same", "new", "cov", "lost")}
            dump(cand, paths["cand"])
            dump({"accepted_findings": [{"service": "backend", "id": "CVE-1", "package": "libc6"}]}, paths["adm"])
            dump(cand["security"]["findings"], paths["same"])
            dump(cand["security"]["findings"] + [dict(cand["security"]["findings"][0], id="CVE-2")], paths["new"])
            dump(self.coverage(d, "rescan", frontend={"archive": "/elsewhere/frontend.tar"}), paths["cov"])
            dump(self.coverage(d, "lost", frontend={"os_family": None}), paths["lost"])

            def run(findings, coverage):
                return subprocess.run([sys.executable, script, "check-rescan", "--admission", paths["adm"],
                                       "--candidate-manifest", paths["cand"], "--findings", paths[findings],
                                       "--rescan-coverage", paths[coverage]], capture_output=True, text=True)
            self.assertEqual(run("same", "cov").returncode, 0)
            p = run("new", "cov")   # F2: the remedy named is a new candidate, then a new admission
            self.assertEqual(p.returncode, 3)
            self.assertIn("cannot be promoted", p.stderr)
            self.assertIn("new candidate", p.stderr)
            p = run("same", "lost")
            self.assertEqual(p.returncode, 3)
            self.assertIn("COVERAGE LOST: frontend", p.stderr)
            self.assertNotIn("Traceback", p.stderr)
            del cand["security"]["coverage"]   # a candidate without recorded coverage cannot be promoted
            dump(cand, paths["cand"])
            self.assertEqual(run("same", "cov").returncode, 3)
            p = subprocess.run([sys.executable, script, "check-rescan", "--admission", paths["adm"],
                                "--candidate-manifest", paths["cand"], "--findings", paths["same"]],
                               capture_output=True, text=True)
            self.assertNotEqual(p.returncode, 0)   # the coverage argument is required


class AdmissionTests(unittest.TestCase):
    def setUp(self):
        self.tmp = tempfile.TemporaryDirectory()
        self.root = self.tmp.name
        os.makedirs(os.path.join(self.root, ".github"))
        os.makedirs(os.path.join(self.root, release.ADMISSION_DIR))
        self.codeowners = os.path.join(self.root, ".github", "CODEOWNERS")
        put(self.codeowners, "/docs/decisions/   @leader\n/docs/  @someone\n")
        self.candidate = candidate_manifest()
        self.cand_sha = "d" * 64
        self.admission = release.admission_template(self.candidate, "candidate-%s-1-1" % REV[:12], "v0.2.0")
        self.admission["candidate"]["manifest_sha256"] = self.cand_sha
        self.admission.update(decision="ADMITTED", decided_by="leader", decided_on="2026-11-25",
                              scope="Limited academic preview", record_id="CM-REL-ADMISSION-TEST-0001")
        self.path = os.path.join(self.root, release.ADMISSION_DIR, "CM-REL-ADMISSION-TEST-0001.json")

    def tearDown(self):
        self.tmp.cleanup()

    def write_and_commit(self, data, commit=True):
        dump(data, self.path)
        if commit:
            git = ["git", "-C", self.root, "-c", "user.name=t", "-c", "user.email=t@example.test"]
            subprocess.run(git[:3] + ["init", "-q"], check=True)
            subprocess.run(git + ["add", "-A"], check=True)
            subprocess.run(git + ["commit", "-qm", "admission"], check=True)

    def verify(self, data, candidate=None, rehearsal=False, path=None):
        return release.verify_admission(data, path or self.path, candidate or self.candidate, self.cand_sha,
                                        self.codeowners, rehearsal, self.root)

    def test_valid_committed_admission(self):
        self.write_and_commit(self.admission)
        result = self.verify(self.admission)
        self.assertEqual(result["version"], "v0.2.0")

    def test_uncommitted_or_modified_record_is_refused(self):
        self.write_and_commit(self.admission, commit=False)
        with self.assertRaises(release.Refused):
            self.verify(self.admission)
        self.write_and_commit(self.admission)
        put(self.path, " ", "a")
        with self.assertRaises(release.Refused):
            self.verify(self.admission)

    def test_refusals(self):
        self.write_and_commit(self.admission)
        mutations = {
            "pending decision": lambda a: a.update(decision="PENDING"),
            "rejected decision": lambda a: a.update(decision="REJECTED"),
            "rehearsal flag": lambda a: a.update(rehearsal=True),
            "unknown field": lambda a: a.update(extra=1),
            "decider not a code owner": lambda a: a.update(decided_by="someone"),
            "other revision": lambda a: a.update(source_revision="b" * 40),
            "other origin": lambda a: a.update(public_origin="https://example.org"),
            "other candidate manifest": lambda a: a["candidate"].update(manifest_sha256="e" * 64),
            "other archive": lambda a: a["candidate"].update(archive_sha256="e" * 64),
            "swapped identity": lambda a: a["images"]["backend"].update(containerd=a["images"]["frontend"]["containerd"]),
            "missing service": lambda a: a["images"].pop("minio"),
            "finding not accepted": lambda a: a.update(accepted_findings=[]),
            "finding the candidate did not report": lambda a: a["accepted_findings"].append(
                {"service": "backend", "id": "CVE-9", "package": "libc6"}),
            "finding listed twice": lambda a: a["accepted_findings"].append(dict(a["accepted_findings"][0])),
            "bad version": lambda a: a["release"].update(version="0.2.0"),
            "not_granted incomplete": lambda a: a.update(not_granted=["deployment"]),
            "empty scope": lambda a: a.update(scope=" "),
            "bad tag": lambda a: a["candidate"].update(release_tag="v0.2.0"),
        }
        for label, mutate in mutations.items():
            with self.subTest(label):
                data = copy.deepcopy(self.admission)
                mutate(data)
                with self.assertRaises(release.Refused):
                    self.verify(data)

    def test_record_id_must_match_file_name(self):
        data = dict(self.admission, record_id="CM-REL-ADMISSION-OTHER-0002")
        with self.assertRaises(release.Refused):
            self.verify(data)

    def test_candidate_must_be_dispatched_and_source_verified(self):
        self.write_and_commit(self.admission)
        for cand in (candidate_manifest(rehearsal=True), candidate_manifest(verified=False)):
            with self.subTest(cand["run"]), self.assertRaises(release.Refused):
                self.verify(self.admission, candidate=cand)

    def test_rehearsal_mode(self):
        cand = candidate_manifest(rehearsal=True, verified=False)
        data = dict(copy.deepcopy(self.admission), record_id="REHEARSAL-CI-0001", rehearsal=True, decided_by="ci")
        path = os.path.join(self.tmp.name, "REHEARSAL-CI-0001.json")
        self.assertEqual(self.verify(data, candidate=cand, rehearsal=True, path=path)["version"], "v0.2.0")
        with self.assertRaises(release.Refused):  # a real record cannot be used as a rehearsal
            self.verify(self.admission, candidate=cand, rehearsal=True)
        with self.assertRaises(release.Refused):  # a rehearsal record cannot promote for real
            self.verify(data, candidate=cand, rehearsal=False, path=path)


class AdmissionProvenanceTests(unittest.TestCase):
    PATH = release.ADMISSION_DIR + "/CM-REL-ADMISSION-TEST-0001.json"
    COMMIT, BLOB, HEAD = "c" * 40, "b" * 40, "e" * 40

    def evidence(self):
        pulls = [{"number": 300, "merged_at": "2026-11-25T01:00:00Z", "merge_commit_sha": self.COMMIT,
                  "base": {"ref": "main"}, "head": {"sha": self.HEAD}}]
        pull = dict(pulls[0], merged=True, merged_by={"login": "leader"})
        files = [[{"filename": self.PATH, "status": "added", "sha": self.BLOB}]]
        return pulls, pull, files

    def check(self, pulls, pull, files, admission=None):
        return release.admission_provenance(admission or {"decided_by": "leader"}, self.PATH, self.COMMIT, self.BLOB,
                                            pulls, pull, files)

    def test_merged_by_the_decider(self):
        result = self.check(*self.evidence())
        self.assertEqual((result["pull_request"], result["merged_by"], result["head_sha"]), (300, "leader", self.HEAD))

    def test_refusals(self):
        def other_merger(p, q, f): q["merged_by"] = {"login": "contributor"}
        def not_merged(p, q, f): q["merged"] = False
        def no_pull_request(p, q, f): p.clear()
        def other_base(p, q, f): p[0]["base"]["ref"] = "release"
        def other_merge_commit(p, q, f): q["merge_commit_sha"] = "d" * 40
        def two_pull_requests(p, q, f): p.append(dict(p[0], number=301))
        def other_number(p, q, f): q["number"] = 301
        def extra_file(p, q, f): f[0].append({"filename": "deploy.sh", "status": "modified", "sha": "1" * 40})
        def other_file(p, q, f): f[0][0]["filename"] = release.ADMISSION_DIR + "/OTHER-RECORD-0001.json"
        def removed(p, q, f): f[0][0]["status"] = "removed"
        def other_content(p, q, f): f[0][0]["sha"] = "2" * 40
        for mutate in (other_merger, not_merged, no_pull_request, other_base, other_merge_commit, two_pull_requests,
                       other_number, extra_file, other_file, removed, other_content):
            with self.subTest(mutate.__name__):
                pulls, pull, files = self.evidence()
                mutate(pulls, pull, files)
                with self.assertRaises(release.Refused):
                    self.check(pulls, pull, files)
        with self.subTest("decided_by names someone else"), self.assertRaises(release.Refused):
            self.check(*self.evidence(), admission={"decided_by": "someone"})


class PackageTests(unittest.TestCase):
    def make(self, d):
        for name, body in (("a.tar", b"archive"), ("mc-source.tar.gz", b"src"), ("findings.json", b"[]")):
            put(os.path.join(d, name), body, "wb")
        manifest = {"schema": release.CANDIDATE_SCHEMA, "source_revision": REV,
                    "archive": {"name": "a.tar", "sha256": hashlib.sha256(b"archive").hexdigest()}}
        manifest["files"] = release.file_map(d, {"release-candidate.json", "SHA256SUMS"})
        release.write_json(os.path.join(d, "release-candidate.json"), manifest)
        release.write_sha256sums(d)

    def test_package_verification(self):
        with tempfile.TemporaryDirectory() as d:
            self.make(d)
            release.verify_package(d, "candidate")
            put(os.path.join(d, "extra.txt"), "x")
            with self.assertRaises(release.Refused):
                release.verify_package(d, "candidate")
            release.verify_package(d, "candidate", ("extra.txt",))
            os.remove(os.path.join(d, "extra.txt"))
            put(os.path.join(d, "a.tar"), b"substituted", "wb")
            with self.assertRaises(release.Refused):
                release.verify_package(d, "candidate")

    def test_manifest_and_sums_must_agree(self):
        with tempfile.TemporaryDirectory() as d:
            self.make(d)
            m = read_json(os.path.join(d, "release-candidate.json"))
            m["files"]["findings.json"] = "0" * 64
            release.write_json(os.path.join(d, "release-candidate.json"), m)
            release.write_sha256sums(d)
            with self.assertRaises(release.Refused):
                release.verify_package(d, "candidate")


class ReleaseChainTests(unittest.TestCase):
    """A release package built by the real release-manifest command, then verified."""

    def build(self, d):
        pkg = os.path.join(d, "release")
        os.makedirs(pkg)
        cand = candidate_manifest()
        cand["schema"] = release.CANDIDATE_SCHEMA
        cand.update(source_tree="t" * 40, mc_source={"name": "mc-source.tar.gz", "sha256": hashlib.sha256(b"src").hexdigest()},
                    compatibility={"deploy_sh_sha256": "0" * 64}, run={"rehearsal": False, "id": "1"},
                    security={"findings": cand["security"]["findings"], "scanner": {"tool": "trivy"}})
        dump(cand, os.path.join(pkg, "release-candidate.json"))
        adm = release.admission_template(cand, "candidate-%s-1-1" % REV[:12], "v0.2.0")
        adm["candidate"]["manifest_sha256"] = release.sha256_file(os.path.join(pkg, "release-candidate.json"))
        adm.update(decision="ADMITTED", decided_by="leader", decided_on="2026-11-25", scope="preview", record_id="CM-REL-ADMISSION-TEST-0001")
        dump(adm, os.path.join(pkg, "admission.json"))
        put(os.path.join(pkg, "codemastery-release-v0.2.0-linux-amd64.tar"), b"archive", "wb")
        put(os.path.join(pkg, "mc-source.tar.gz"), b"src", "wb")
        for store in ("containerd", "graphdriver"):
            put(os.path.join(pkg, "expected-images.%s.txt" % store),
                "".join("%s=%s\n" % (release.RELEASE_REFS[s], cand["images"][s][store]) for s in SERVICES))
        ids = {release.RELEASE_REFS[s]: {"containerd": cand["images"][s]["containerd"], "graphdriver": cand["images"][s]["graphdriver"]} for s in SERVICES}
        dump(ids, os.path.join(d, "ids.json"))
        dump([], os.path.join(d, "rescan.json"))
        dump({"tool": "trivy"}, os.path.join(d, "scanner.json"))
        dump({s: {"os": None, "os_end_of_support": False, "targets": []} for s in SERVICES}, os.path.join(d, "coverage.json"))
        dump({"tooling": {}, "run": {"rehearsal": False}, "verification": {}, "admission_commit": "c" * 40,
              "admission_provenance": {"merged_by": "leader", "merge_commit": "c" * 40, "pull_request": 300}},
             os.path.join(d, "ctx.json"))
        rc = release.main(["release-manifest", "--package", pkg, "--archive-name", "codemastery-release-v0.2.0-linux-amd64.tar",
                           "--context", os.path.join(d, "ctx.json"), "--identities", os.path.join(d, "ids.json"),
                           "--findings", os.path.join(d, "rescan.json"), "--scanner", os.path.join(d, "scanner.json"),
                           "--coverage", os.path.join(d, "coverage.json")])
        self.assertEqual(rc, 0)
        return pkg

    def test_valid_chain_and_tampering(self):
        repo = "chi-thanh-nguyen/codemastery"
        with tempfile.TemporaryDirectory() as d:
            pkg = self.build(d)
            self.assertEqual(release.verify_release_chain(pkg, repo)["version"], "v0.2.0")
            with self.assertRaises(release.Refused):
                release.verify_release_chain(pkg, "someone/else")
            put(os.path.join(pkg, "release.sigstore.json"), "{}")   # the bundle sits outside SHA256SUMS
            release.verify_release_chain(pkg, repo)
            path = os.path.join(pkg, "expected-images.containerd.txt")
            with open(path) as f:
                original = f.read()
            lines = original.splitlines()
            lines[0], lines[1] = lines[0].split("=")[0] + "=" + lines[1].split("=")[1], lines[1].split("=")[0] + "=" + lines[0].split("=")[1]
            put(path, "\n".join(lines) + "\n")
            release.write_sha256sums(pkg)   # even with consistent sums, release.json's file map and identities disagree
            with self.assertRaises(release.Refused):
                release.verify_release_chain(pkg, repo)

    def test_release_without_decider_merge_evidence_is_refused(self):
        with tempfile.TemporaryDirectory() as d:
            pkg = self.build(d)
            manifest = read_json(os.path.join(pkg, "release.json"))
            for provenance in (None, {"merged_by": "contributor", "merge_commit": "c" * 40}):
                with self.subTest(provenance):
                    manifest["admission"]["provenance"] = provenance
                    dump(manifest, os.path.join(pkg, "release.json"))
                    release.write_sha256sums(pkg)
                    with self.assertRaises(release.Refused):
                        release.verify_release_chain(pkg, "chi-thanh-nguyen/codemastery")

    def test_admission_swap_is_refused(self):
        with tempfile.TemporaryDirectory() as d:
            pkg = self.build(d)
            adm = read_json(os.path.join(pkg, "admission.json"))
            adm["decision"] = "PENDING"
            dump(adm, os.path.join(pkg, "admission.json"))
            release.write_sha256sums(pkg)
            with self.assertRaises(release.Refused):
                release.verify_release_chain(pkg, "chi-thanh-nguyen/codemastery")


class CliTests(unittest.TestCase):
    def test_refusal_exit_code_and_no_traceback(self):
        script = os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "release.py")
        with tempfile.TemporaryDirectory() as d:
            p = subprocess.run([sys.executable, script, "verify-package", d, "--kind", "release"],
                               capture_output=True, text=True)
        self.assertEqual(p.returncode, 3)
        self.assertIn("REFUSED", p.stderr)
        self.assertNotIn("Traceback", p.stderr)


if __name__ == "__main__":
    unittest.main()
