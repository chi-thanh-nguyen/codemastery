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
            accepted = {("backend", "CVE-1", "libc6")}
            self.assertEqual(release.check_rescan(accepted, f), [("backend", "GO-2", "x/crypto")])
            self.assertEqual(release.check_rescan(accepted | {("backend", "GO-2", "x/crypto")}, f), [])
            dump({"SchemaVersion": 1}, a)
            with self.assertRaises(release.Refused):
                release.normalize_findings("backend", [a])

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
