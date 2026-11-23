"""Unit tests for infrastructure/release/release.py (ADR015).

Run from the repository root:
    python3 -m unittest discover -s infrastructure/release/tests -v

Synthetic fixtures only: OCI-layout archives built in memory, fabricated check
runs, scanner reports, GitHub run, environment and approval records, and admission
records. No Docker or network access (one test runs release.sh against a stub gh).
"""

import copy
import hashlib
import io
import json
import os
import shlex
import shutil
import subprocess
import sys
import tarfile
import tempfile
import textwrap
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


class SourceChecks:
    """A synthetic GitHub world for one source revision, shaped like the REST responses
    the candidate job reads (check runs, workflow runs, job records, branch rules)."""

    API = "https://api.github.com/repos/chi-thanh-nguyen/codemastery"
    WEB = "https://github.com/chi-thanh-nguyen/codemastery"
    REPO = {"id": 1391772534, "full_name": "chi-thanh-nguyen/codemastery"}
    HEAD = "b" * 40
    FILES = [".github/workflows/ci-backend.yml", ".github/workflows/ci-vm.yml", ".github/workflows/ci-extra.yml",
             ".github/workflows/release.yml"]
    RULES = [{"type": "pull_request"}, {"type": "required_status_checks", "parameters": {"required_status_checks": [
        {"context": "Backend", "integration_id": 15368}, {"context": "VM A", "integration_id": 15368},
        {"context": "VM B", "integration_id": 15368}, {"context": "CodeQL", "integration_id": 57789}]}}]

    def __init__(self):
        self.runs, self.jobs, self.extra, self.pr_runs = {}, [], [], []
        self.next_id = 1000
        self.add_run(100, ".github/workflows/ci-backend.yml", ["Backend"])
        self.add_run(200, ".github/workflows/ci-vm.yml", ["VM A", "VM B"])
        self.pr_runs.append(self.app_run("CodeQL", "success", sha=self.HEAD))

    def new_id(self):
        self.next_id += 1
        return self.next_id

    def add_run(self, rid, path, names, conclusions=None, event="push", branch="main", created="2026-11-23T14:30:06Z"):
        self.runs[rid] = {"id": rid, "name": path.rsplit("/", 1)[1], "path": path, "event": event, "head_branch": branch,
                          "head_sha": REV, "run_attempt": 1, "status": "completed", "conclusion": "success",
                          "repository": dict(self.REPO), "head_repository": dict(self.REPO), "workflow_id": 7 + rid,
                          "check_suite_id": 9000 + rid, "created_at": created, "run_started_at": created}
        for name in names:
            self.add_job(rid, name, 1, (conclusions or {}).get(name, "success"))

    def add_job(self, rid, name, attempt, conclusion="success", status="completed", started=None):
        jid = self.new_id()
        job = {"id": jid, "run_id": rid, "run_attempt": attempt, "name": name, "head_sha": REV, "status": status,
               "conclusion": conclusion, "head_branch": "main", "started_at": started or self.runs[rid]["run_started_at"],
               "completed_at": "2026-11-23T15:00:00Z", "check_run_url": "%s/check-runs/%d" % (self.API, jid),
               "html_url": "%s/actions/runs/%d/job/%d" % (self.WEB, rid, jid)}
        self.jobs.append(job)
        return job

    def rerun(self, rid, results, started="2026-11-23T15:10:00Z", status="completed", kept=()):
        """A new attempt; `results` maps each job to its conclusion, `kept` jobs keep their earlier start (re-run failed jobs)."""
        run = self.runs[rid]
        run["run_attempt"] += 1
        old_start = run["run_started_at"]
        run.update(run_started_at=started, status=status, conclusion=None if status != "completed" else "success")
        for name, conclusion in results.items():
            done = conclusion is not None
            self.add_job(rid, name, run["run_attempt"], conclusion, "completed" if done else "in_progress",
                         started=old_start if name in kept else started)

    def check_run(self, job):
        rid = job["run_id"]
        return {"id": job["id"], "name": job["name"], "app": {"id": 15368, "slug": "github-actions"}, "head_sha": job["head_sha"],
                "status": job["status"], "conclusion": job["conclusion"], "check_suite": {"id": self.runs[rid]["check_suite_id"]},
                "url": job["check_run_url"], "html_url": job["html_url"], "details_url": job["html_url"],
                "started_at": job["started_at"]}

    def app_run(self, name, conclusion, sha=REV, status="completed", app=57789):
        cid = self.new_id()
        return {"id": cid, "name": name, "app": {"id": app}, "head_sha": sha, "status": status, "conclusion": conclusion,
                "check_suite": {"id": 5}, "url": "%s/check-runs/%d" % (self.API, cid),
                "html_url": "%s/runs/%d" % (self.WEB, cid), "started_at": "2026-11-23T13:00:00Z"}

    def forged(self, name, conclusion="success", like=None):
        """A check run a workflow with checks: write could create: same name and app, any URL it likes."""
        cid = self.new_id()
        return {"id": cid, "name": name, "app": {"id": 15368, "slug": "github-actions"}, "head_sha": REV,
                "status": "completed", "conclusion": conclusion, "check_suite": {"id": 1},
                "url": "%s/check-runs/%d" % (self.API, cid), "html_url": (like or {}).get("html_url", "https://example.invalid"),
                "details_url": (like or {}).get("html_url", "https://example.invalid"), "started_at": "2099-01-01T00:00:00Z"}

    def pr(self):
        return {"number": 274, "merged_at": "x", "merge_commit_sha": REV, "head": {"sha": self.HEAD},
                "base": {"ref": "main", "repo": dict(self.REPO)}}

    def actions(self):
        runs = list(self.runs.values())
        return {"workflow_files": list(self.FILES), "runs": copy.deepcopy(runs), "runs_after": copy.deepcopy(runs),
                "attempt_jobs": {rid: [j for j in self.jobs if j["run_id"] == rid and j["run_attempt"] == r["run_attempt"]]
                                 for rid, r in self.runs.items()},
                "all_jobs": {rid: [j for j in self.jobs if j["run_id"] == rid] for rid in self.runs}}

    def check_runs(self):
        return [self.check_run(j) for j in self.jobs] + list(self.extra)

    def evaluate(self, rules=None, check_runs=None, actions=None, pr="default", pr_runs=None):
        return release.evaluate_checks(REV, self.RULES if rules is None else rules,
                                       self.check_runs() if check_runs is None else check_runs,
                                       self.actions() if actions is None else actions,
                                       self.pr() if pr == "default" else pr, self.pr_runs if pr_runs is None else pr_runs)

    def by_context(self, **kw):
        return {c["context"]: c for c in self.evaluate(**kw)["required_checks"]}


def pages(key, items, total=None):
    return [{"total_count": len(items) if total is None else total, key: items}]


class CheckTests(unittest.TestCase):
    """ADR015 D2 (A2): the latest authenticated evaluation of each required check decides."""

    def refused(self, world, text=None, **kw):
        with self.assertRaises(release.Refused) as raised:
            world.evaluate(**kw)
        if text:
            self.assertIn(text, str(raised.exception))
        return str(raised.exception)

    # --- revisions that qualify ---------------------------------------------------

    def test_all_required_checks_pass_on_the_first_attempt(self):
        w = SourceChecks()
        result = w.evaluate()
        self.assertEqual(result["schema"], release.SOURCE_CHECKS_SCHEMA)
        self.assertIn("latest valid evaluation", result["policy"])
        self.assertEqual(result["source_revision"], REV)
        self.assertEqual(result["repository_id"], "1391772534")
        checks = {c["context"]: c for c in result["required_checks"]}
        self.assertEqual(sorted(checks), ["Backend", "CodeQL", "VM A", "VM B"])
        vm = checks["VM A"]
        job = [j for j in w.jobs if j["name"] == "VM A"][0]
        self.assertEqual((vm["provenance"], vm["evidence"], vm["run_id"], vm["run_attempt"], vm["job_id"], vm["check_run_id"]),
                         ("github-actions-job", "revision", 200, 1, job["id"], job["id"]))
        self.assertEqual(vm["workflow"]["path"], ".github/workflows/ci-vm.yml")
        self.assertEqual((vm["workflow"]["event"], vm["workflow"]["ref"], vm["source_sha"]), ("push", "refs/heads/main", REV))
        self.assertEqual(vm["run_url"], SourceChecks.WEB + "/actions/runs/200/attempts/1")
        self.assertEqual((vm["url"], vm["superseded"], vm["result_kept_from_earlier_attempt"]), (job["html_url"], [], False))
        codeql = checks["CodeQL"]
        self.assertEqual((codeql["evaluated_sha"], codeql["source_sha"], codeql["pull_request"]["number"]),
                         (SourceChecks.HEAD, REV, 274))
        self.assertTrue(codeql["evidence"].startswith("merged pull request #274"))
        self.assertEqual(result["ignored_check_runs"], [])

    def test_a_verified_rerun_supersedes_a_failed_attempt(self):
        w = SourceChecks()
        w.runs[200]["conclusion"] = "failure"
        failed = [j for j in w.jobs if j["name"] == "VM A"][0]
        failed["conclusion"] = "failure"
        w.rerun(200, {"VM A": "success", "VM B": "success"})        # re-run all jobs
        vm = w.by_context()["VM A"]
        self.assertEqual((vm["run_attempt"], vm["run_url"]), (2, SourceChecks.WEB + "/actions/runs/200/attempts/2"))
        self.assertNotEqual(vm["job_id"], failed["id"])
        # The failure stays in the evidence.
        self.assertEqual([(h["run_attempt"], h["job_id"], h["conclusion"]) for h in vm["superseded"]],
                         [(1, failed["id"], "failure")])

    def test_rerun_of_failed_jobs_keeps_github_s_record_of_the_others(self):
        # "Re-run failed jobs": GitHub records every job again under the new attempt;
        # the ones not re-run keep their earlier result (and start time).
        w = SourceChecks()
        [j for j in w.jobs if j["name"] == "VM A"][0]["conclusion"] = "failure"
        w.rerun(200, {"VM A": "success", "VM B": "success"}, kept=("VM B",))
        checks = w.by_context()
        self.assertEqual((checks["VM A"]["run_attempt"], checks["VM B"]["run_attempt"]), (2, 2))
        self.assertEqual((checks["VM A"]["result_kept_from_earlier_attempt"], checks["VM B"]["result_kept_from_earlier_attempt"]),
                         (False, True))

    def test_several_failed_attempts_no_longer_count_once_the_latest_passes(self):
        w = SourceChecks()
        [j for j in w.jobs if j["name"] == "VM A"][0]["conclusion"] = "failure"
        w.rerun(200, {"VM A": "cancelled", "VM B": "failure"})
        w.rerun(200, {"VM A": "success", "VM B": "success"}, started="2026-11-23T16:00:00Z")
        checks = w.by_context()
        self.assertEqual(checks["VM A"]["run_attempt"], 3)
        self.assertEqual([h["conclusion"] for h in checks["VM A"]["superseded"]], ["failure", "cancelled"])
        self.assertEqual([h["conclusion"] for h in checks["VM B"]["superseded"]], ["success", "failure"])

    def test_a_newer_workflow_run_on_the_same_commit_decides(self):
        w = SourceChecks()
        w.runs[100]["conclusion"] = "failure"
        [j for j in w.jobs if j["name"] == "Backend"][0]["conclusion"] = "failure"
        w.add_run(101, ".github/workflows/ci-backend.yml", ["Backend"], event="workflow_dispatch",
                  created="2026-11-23T16:00:00Z")
        backend = w.by_context()["Backend"]
        self.assertEqual((backend["run_id"], backend["workflow"]["event"]), (101, "workflow_dispatch"))
        self.assertEqual([(h["run_id"], h["conclusion"]) for h in backend["superseded"]], [(100, "failure")])

    def test_codeql_on_the_revision_is_used_without_the_fallback(self):
        w = SourceChecks()
        w.extra.append(w.app_run("CodeQL", "failure"))
        w.extra.append(w.app_run("CodeQL", "success"))                  # newer analysis of the same commit
        codeql = w.by_context()["CodeQL"]
        self.assertEqual((codeql["evidence"], codeql["evaluated_sha"]), ("the revision", REV))
        self.assertNotIn("pull_request", codeql)
        self.assertEqual([h["conclusion"] for h in codeql["superseded"]], ["failure"])

    def test_forged_check_runs_are_ignored_and_recorded(self):
        w = SourceChecks()
        real = [j for j in w.jobs if j["name"] == "VM A"][0]
        w.extra += [w.forged("VM A", "failure"), w.forged("VM A", "success", like=real),
                    dict(w.forged("CodeQL", "failure"))]               # same name, github-actions app
        result = w.evaluate()
        self.assertEqual(sorted((i["name"], i["conclusion"]) for i in result["ignored_check_runs"]),
                         [("CodeQL", "failure"), ("VM A", "failure"), ("VM A", "success")])
        self.assertEqual({c["context"]: c["job_id"] for c in result["required_checks"] if "job_id" in c}["VM A"], real["id"])

    # --- revisions that are refused -----------------------------------------------

    def test_latest_attempt_failed_after_an_earlier_success(self):
        w = SourceChecks()
        w.rerun(200, {"VM A": "failure", "VM B": "success"})
        self.refused(w, "an earlier success never overrides it")

    def test_latest_attempt_pending(self):
        w = SourceChecks()
        w.rerun(200, {"VM A": None, "VM B": None}, status="in_progress")
        self.refused(w, "has not completed")
        w = SourceChecks()
        w.runs[100]["status"] = "queued"                                 # the run itself still running
        self.refused(w, "has not completed")

    def test_latest_attempt_cancelled_skipped_or_otherwise_unsuccessful(self):
        for conclusion in ("cancelled", "skipped", "neutral", "timed_out", "action_required", "stale", None):
            w = SourceChecks()
            w.rerun(200, {"VM A": "success", "VM B": "success"})
            [j for j in w.jobs if j["name"] == "VM B" and j["run_attempt"] == 2][0]["conclusion"] = conclusion
            with self.subTest(conclusion=conclusion):
                self.refused(w, "VM B")

    def test_forged_success_cannot_replace_a_real_failure_or_a_missing_job(self):
        w = SourceChecks()
        real = [j for j in w.jobs if j["name"] == "VM A"][0]
        real["conclusion"] = "failure"
        w.extra.append(w.forged("VM A", "success", like=real))
        self.refused(w, "an earlier success never overrides it")
        # A context no trusted workflow has a job for: a forged success is no evidence.
        w = SourceChecks()
        rules = copy.deepcopy(SourceChecks.RULES)
        rules[1]["parameters"]["required_status_checks"].append({"context": "Lint", "integration_id": 15368})
        w.extra.append(w.forged("Lint", "success"))
        self.refused(w, "has no job in a trusted workflow run", rules=rules)

    def test_check_run_that_does_not_match_the_real_job(self):
        def tamper(field, value):
            w = SourceChecks()
            target = [j for j in w.jobs if j["name"] == "VM A"][0]
            runs = w.check_runs()
            cr = [c for c in runs if c["id"] == target["id"]][0]
            if field == "remove":
                runs.remove(cr)
            elif field == "app":
                cr["app"] = {"id": 1}
            elif field == "check_suite":
                cr["check_suite"] = {"id": 1}
            else:
                cr[field] = value
            return w, runs
        for field, value in (("remove", None), ("name", "VM B"), ("app", None), ("head_sha", "c" * 40),
                             ("conclusion", "failure"), ("status", "in_progress"), ("check_suite", None),
                             ("url", "https://api.github.com/repos/someone/else/check-runs/1"),
                             ("html_url", "https://example.invalid/job")):
            w, runs = tamper(field, value)
            with self.subTest(field=field):
                self.refused(w, "does not match GitHub's job record", check_runs=runs)

    def test_untrusted_workflow_identity_trigger_ref_repository_or_commit(self):
        cases = {
            "workflow file not in the revision": {"path": ".github/workflows/evil.yml"},
            "dynamic workflow": {"path": "dynamic/github-code-scanning/codeql"},
            "pull_request event": {"event": "pull_request"},
            "schedule event": {"event": "schedule"},
            "another branch": {"head_branch": "feature/x"},
            "another repository": {"repository": {"id": 1, "full_name": "someone/codemastery"}},
            "fork head": {"head_repository": {"id": 2}},
            "another commit": {"head_sha": "c" * 40},
            "malformed attempt": {"run_attempt": "2"},
        }
        for label, change in cases.items():
            w = SourceChecks()
            w.runs[200].update(change)
            with self.subTest(label):
                result = self.refused(w)
                self.assertTrue("has no job in a trusted workflow run" in result or "does not belong" in result, result)

    def test_untrusted_runs_are_recorded(self):
        w = SourceChecks()
        w.add_run(300, ".github/workflows/ci-vm.yml", ["VM A"], event="pull_request")
        self.assertEqual(w.evaluate()["untrusted_workflow_runs"], [{"run_id": 300, "reason": "event 'pull_request'"}])

    def test_job_records_inconsistent_with_their_run(self):
        for label, change in {"another commit": {"head_sha": "c" * 40}, "another run": {"run_id": 999},
                              "future attempt": {"run_attempt": 2}, "attempt zero": {"run_attempt": 0},
                              "foreign check run URL": {"check_run_url": "https://api.github.com/repos/x/y/check-runs/1"},
                              "foreign job URL": {"html_url": "https://github.com/x/y/actions/runs/200/job/1"}}.items():
            w = SourceChecks()
            runs, actions = w.check_runs(), w.actions()
            [j for j in w.jobs if j["name"] == "VM A"][0].update(change)   # the listings share the record
            with self.subTest(label):
                self.refused(w, "does not belong to that run", check_runs=runs, actions=actions)

    def test_attempt_listing_must_hold_only_the_newest_attempt_and_agree(self):
        w = SourceChecks()
        w.rerun(200, {"VM A": "success", "VM B": "success"})
        actions = w.actions()
        old = [j for j in actions["all_jobs"][200] if j["run_attempt"] == 1]
        actions["attempt_jobs"][200] = [j for j in actions["attempt_jobs"][200] if j["name"] == "VM A"] + \
            [j for j in old if j["name"] == "VM B"]                     # splice an attempt-1 result into attempt 2
        self.refused(w, "does not belong to that run and attempt", actions=actions)
        actions = w.actions()
        actions["attempt_jobs"][200] = [j for j in actions["attempt_jobs"][200] if j["name"] == "VM A"]
        self.refused(w, "disagree", actions=actions)

    def test_results_of_different_attempts_are_never_combined(self):
        # Attempt 2 passed VM A but failed VM B; attempt 1 had passed VM B.
        w = SourceChecks()
        [j for j in w.jobs if j["name"] == "VM A"][0]["conclusion"] = "failure"
        w.rerun(200, {"VM A": "success", "VM B": "failure"})
        self.refused(w, "VM B")
        # A newest attempt that holds no record of a job: no stitching from attempt 1.
        w = SourceChecks()
        [j for j in w.jobs if j["name"] == "VM A"][0]["conclusion"] = "failure"
        w.rerun(200, {"VM A": "success"})
        self.refused(w, "re-run all jobs")
        # Two runs of one workflow: only the newest counts, even if an older one passed.
        w = SourceChecks()
        w.add_run(201, ".github/workflows/ci-vm.yml", ["VM A", "VM B"], {"VM B": "failure"},
                  event="workflow_dispatch", created="2026-11-23T17:00:00Z")
        self.refused(w, "run 201 attempt 1")

    def test_a_job_name_in_two_workflows_is_ambiguous(self):
        w = SourceChecks()
        w.add_run(300, ".github/workflows/ci-extra.yml", ["Backend"])
        self.refused(w, "more than one workflow")

    def test_release_workflow_runs_are_never_ci_evidence(self):
        # The dispatched candidate run is itself a workflow_dispatch run of release.yml on
        # main at the revision, still running while it evaluates the checks.
        w = SourceChecks()
        w.add_run(300, ".github/workflows/release.yml", ["Release tooling", "Backend"])
        w.runs[300].update(event="workflow_dispatch", status="in_progress", conclusion=None)
        result = w.evaluate()
        self.assertEqual(result["untrusted_workflow_runs"][0]["run_id"], 300)
        self.assertEqual({c["context"]: c.get("run_id") for c in result["required_checks"]}["Backend"], 100)
        rules = copy.deepcopy(SourceChecks.RULES)
        rules[1]["parameters"]["required_status_checks"].append({"context": "Release tooling", "integration_id": 15368})
        self.refused(w, "has no job in a trusted workflow run", rules=rules)

    def test_missing_required_check_or_api_data(self):
        w = SourceChecks()
        rules = copy.deepcopy(SourceChecks.RULES)
        rules[1]["parameters"]["required_status_checks"].append({"context": "Frontend", "integration_id": 15368})
        self.refused(w, "has no job in a trusted workflow run", rules=rules)
        for key in ("attempt_jobs", "all_jobs"):
            actions = w.actions()
            del actions[key][200]
            with self.subTest(key):
                self.refused(w, "were not read", actions=actions)
        actions = w.actions()
        actions["runs_after"] = None
        self.refused(w, "changed while the evidence was read", actions=actions)
        self.refused(w, "no check run on the revision or on its merged pull request", pr_runs=[w.app_run("Other", "success")])
        self.refused(w, "no merged pull request can stand in", pr=None)

    def test_runs_that_change_while_being_read(self):
        w = SourceChecks()
        actions = w.actions()
        actions["runs_after"][1]["run_attempt"] = 2                     # a re-run started meanwhile
        self.refused(w, "changed while the evidence was read", actions=actions)
        actions = w.actions()
        actions["runs_after"].append(dict(actions["runs"][0], id=555))   # a new run appeared
        self.refused(w, "changed while the evidence was read", actions=actions)

    def test_rules_that_cannot_be_authenticated(self):
        w = SourceChecks()
        for item, text in (({"context": "X", "integration_id": None}, "names no app"),
                           ({"context": "X"}, "names no app"),
                           ({"context": "X", "integration_id": 42}, "cannot authenticate"),
                           ({"integration_id": 15368}, "has no context")):
            rules = copy.deepcopy(SourceChecks.RULES)
            rules[1]["parameters"]["required_status_checks"].append(item)
            with self.subTest(item=item):
                self.refused(w, text, rules=rules)
        self.refused(w, "no required status checks", rules=[{"type": "pull_request"}])

    def test_pull_request_fallback_never_overrides_the_revision(self):
        for conclusion, status in (("failure", "completed"), ("cancelled", "completed"), (None, "in_progress")):
            w = SourceChecks()
            w.extra.append(w.app_run("CodeQL", conclusion, status=status))
            with self.subTest(conclusion=conclusion):
                self.refused(w, "the revision")
        # The fallback's own latest evaluation rules.
        w = SourceChecks()
        w.pr_runs.append(w.app_run("CodeQL", "failure", sha=SourceChecks.HEAD))
        self.refused(w, "merged pull request #274")
        w = SourceChecks()
        w.pr_runs.append(w.app_run("CodeQL", None, sha=SourceChecks.HEAD, status="queued"))
        self.refused(w, "has not completed")
        w = SourceChecks()
        self.refused(w, "does not belong to commit", pr_runs=[w.app_run("CodeQL", "success", sha="c" * 40)])
        # Only the required app counts, on the head too.
        w = SourceChecks()
        self.refused(w, "no check run", pr_runs=[w.app_run("CodeQL", "success", sha=SourceChecks.HEAD, app=15368)])

    def test_merged_pull_request(self):
        pr = SourceChecks().pr()
        self.assertIs(release.merged_pull_request([pr, dict(pr, merge_commit_sha="c" * 40)], REV), pr)
        self.assertIsNone(release.merged_pull_request([dict(pr, merged_at=None)], REV))
        self.assertIsNone(release.merged_pull_request([dict(pr, base={"ref": "other"})], REV))
        with self.assertRaises(release.Refused):
            release.merged_pull_request([pr, dict(pr, number=8)], REV)
        with self.assertRaises(release.Refused):
            release.merged_pull_request([dict(pr, base={"ref": "main", "repo": {"id": 1}})], REV)

    def test_api_listings_must_be_complete(self):
        items = [{"id": 1}, {"id": 2}]
        self.assertEqual(release.api_items(pages("jobs", items), "jobs", "x"), items)
        for bad in (pages("jobs", items, total=3), [], [{"jobs": items}], pages("jobs", [{"id": 1}, {"id": 1}]),
                    pages("jobs", [{"id": "1"}]), pages("jobs", [{"id": True}]), [{"total_count": 1, "jobs": None}],
                    pages("jobs", items) + pages("jobs", [{"id": 3}], total=4)):
            with self.subTest(bad=bad), self.assertRaises(release.Refused):
                release.api_items(bad, "jobs", "x")

    def test_cli_with_the_files_the_workflow_writes(self):
        w = SourceChecks()
        [j for j in w.jobs if j["name"] == "VM A"][0]["conclusion"] = "failure"
        w.rerun(200, {"VM A": "success", "VM B": "success"})
        w.extra.append(w.forged("VM B", "success"))
        actions = w.actions()
        with tempfile.TemporaryDirectory() as d:
            p = {n: os.path.join(d, n) for n in ("rules", "runs", "after", "files", "checks", "pulls", "pr_runs")}
            jobs = os.path.join(d, "jobs")
            os.mkdir(jobs)
            dump(w.RULES, p["rules"])
            dump(pages("workflow_runs", actions["runs"]), p["runs"])
            dump(pages("workflow_runs", actions["runs_after"]), p["after"])
            put(p["files"], "\n".join(w.FILES) + "\n")
            dump(pages("check_runs", w.check_runs()), p["checks"])
            dump([w.pr()], p["pulls"])
            dump(pages("check_runs", w.pr_runs), p["pr_runs"])
            for rid in w.runs:
                dump(pages("jobs", actions["attempt_jobs"][rid]), os.path.join(jobs, "%d.attempt.json" % rid))
                dump(pages("jobs", actions["all_jobs"][rid]), os.path.join(jobs, "%d.all.json" % rid))
            base = ["evaluate-checks", "--revision", REV, "--rules", p["rules"], "--check-runs", p["checks"],
                    "--workflow-files", p["files"], "--workflow-runs", p["runs"], "--workflow-runs-after", p["after"],
                    "--jobs-dir", jobs, "--pulls", p["pulls"], "--pr-head", SourceChecks.HEAD, "--pr-check-runs", p["pr_runs"]]
            out = io.StringIO()
            sys_stdout, sys.stdout = sys.stdout, out
            try:
                code = release.main(base + ["--revision-tree", "1" * 40, "--pr-head-tree", "1" * 40])
            finally:
                sys.stdout = sys_stdout
            self.assertEqual(code, 0)
            result = json.loads(out.getvalue())
            self.assertEqual({c["context"]: c.get("run_attempt") for c in result["required_checks"]},
                             {"Backend": 1, "VM A": 2, "VM B": 2, "CodeQL": None})
            codeql = [c for c in result["required_checks"] if c["context"] == "CodeQL"][0]
            self.assertEqual(codeql["pull_request"]["head_tree"], "1" * 40)
            self.assertEqual(len(result["ignored_check_runs"]), 1)
            # CodeQL fallback with a different tree, or without trees, is refused.
            self.assertEqual(release.main(base + ["--revision-tree", "1" * 40, "--pr-head-tree", "2" * 40]), 3)
            self.assertEqual(release.main(base), 3)
            # A missing job file is missing evidence.
            os.remove(os.path.join(jobs, "200.attempt.json"))
            self.assertEqual(release.main(base + ["--revision-tree", "1" * 40, "--pr-head-tree", "1" * 40]), 3)
            # A short listing is refused.
            dump(pages("check_runs", w.check_runs(), total=99), p["checks"])
            self.assertEqual(release.main(base + ["--revision-tree", "1" * 40, "--pr-head-tree", "1" * 40]), 3)

    def test_candidate_manifest_records_only_evidence_of_its_own_revision(self):
        w = SourceChecks()
        [j for j in w.jobs if j["name"] == "VM A"][0]["conclusion"] = "failure"
        w.rerun(200, {"VM A": "success", "VM B": "success"})
        evidence = w.evaluate()

        def build(checks, rehearsal=False):
            d = tempfile.mkdtemp()
            self.addCleanup(shutil.rmtree, d)
            pkg = os.path.join(d, "pkg")
            os.mkdir(pkg)
            put(os.path.join(pkg, "a.tar"), "archive")
            refs = {s: "codemastery-candidate/%s:%s" % (s, REV[:12]) for s in SERVICES}
            ctx = {"candidate_refs": refs, "repository": release.REPOSITORY, "source_revision": REV, "source_tree": "1" * 40,
                   "public_origin": "https://codemastery.app", "tooling": {}, "run": {"rehearsal": rehearsal},
                   "builder": {}, "mc_source": {}, "compatibility": {}}
            files = {"context": ctx, "identities": {refs[s]: {"containerd": digest(b"c"), "graphdriver": digest(b"g")} for s in SERVICES},
                     "findings": [], "migrations": [], "qualification": [{"id": "Q00", "result": "PASS"}], "scanner": {},
                     "coverage": {}}
            args = ["candidate-manifest", "--package", pkg, "--archive-name", "a.tar"]
            for name, value in files.items():
                dump(value, os.path.join(d, name + ".json"))
                args += ["--" + name, os.path.join(d, name + ".json")]
            if checks is not None:
                dump(checks, os.path.join(d, "checks.json"))
                args += ["--source-checks", os.path.join(d, "checks.json")]
            code = release.main(args)
            path = os.path.join(pkg, "release-candidate.json")
            return code, (release.load_json(path, "m") if code == 0 else None)

        code, manifest = build(evidence)
        self.assertEqual(code, 0)
        sq = manifest["source_qualification"]
        self.assertEqual((sq["verified"], sq["schema"], sq["source_revision"]), (True, release.SOURCE_CHECKS_SCHEMA, REV))
        self.assertEqual({c["context"]: c.get("run_attempt") for c in sq["required_checks"]}["VM A"], 2)
        self.assertEqual(build(None, rehearsal=True)[1]["source_qualification"], {"verified": False, "required_checks": []})
        for label, bad in (("another revision", dict(evidence, source_revision="c" * 40)),
                           ("legacy list", evidence["required_checks"]), ("no checks", dict(evidence, required_checks=[])),
                           ("other schema", dict(evidence, schema="codemastery.source-checks/v1"))):
            with self.subTest(label):
                self.assertEqual(build(bad)[0], 3)
        self.assertEqual(build(None)[0], 3)            # a dispatched candidate needs the evidence

    def test_summary_shows_superseded_results(self):
        w = SourceChecks()
        [j for j in w.jobs if j["name"] == "VM A"][0]["conclusion"] = "failure"
        w.rerun(200, {"VM A": "success", "VM B": "success"})
        rows = "\n".join(release._source_check_rows(dict(w.evaluate(), verified=True)))
        self.assertIn("latest authenticated evaluation", rows)
        self.assertRegex(rows, r"\| VM A \| revision \| \[job \d+\]\(.*\), run 200 attempt 2 of `.github/workflows/ci-vm.yml` \| failure \|")
        self.assertIn("legacy record", "\n".join(release._source_check_rows({"verified": True, "required_checks": [{}]})))
        self.assertEqual(release._source_check_rows({"verified": False, "required_checks": []}), [])


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
            self.assertEqual(release.check_rescan(f, f), [])
            reasons = [r[3] for r in release.check_rescan([x for x in f if x["id"] != "GO-2"], f)]
            self.assertEqual(reasons, ["not accepted"])
            dump({"SchemaVersion": 1}, a)
            with self.assertRaises(release.Refused):
                release.normalize_findings("backend", [a])

    def test_rescan_is_bound_to_the_admitted_rows(self):
        admitted = [{"service": "backend", "id": "CVE-1", "package": "libc6", "installed": "1", "severity": "MEDIUM"}]
        same = [dict(admitted[0])]
        lower = [dict(admitted[0], severity="LOW")]
        raised = [dict(admitted[0], severity="CRITICAL")]
        other_version = [dict(admitted[0], installed="2")]
        new_advisory = [dict(admitted[0], id="CVE-2")]
        new_low = [dict(admitted[0], id="CVE-3", severity="LOW")]   # D6: any new row blocks, whatever its severity
        self.assertEqual(release.check_rescan(admitted, same + lower), [])
        self.assertEqual(release.check_rescan(admitted, []), [])
        for label, rows in (("raised", raised), ("other version", other_version), ("new advisory", new_advisory),
                            ("new LOW advisory", new_low)):
            with self.subTest(label):
                self.assertEqual(len(release.check_rescan(admitted, rows)), 1)
        unknown = [dict(admitted[0], severity="UNKNOWN")]
        self.assertEqual(len(release.check_rescan(unknown, same)), 1)  # any reclassification of UNKNOWN

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
            "run": {"rehearsal": rehearsal, "id": "45091629955", "attempt": "1"}, "images": images,
            "archive": {"name": "a.tar", "sha256": "b" * 64},
            "security": {"findings": [
                {"service": "backend", "id": "CVE-1", "package": "libc6", "installed": "2.39", "severity": "MEDIUM"},
                {"service": "minio", "id": "GO-2", "package": "x/crypto", "installed": "v0.58.0", "severity": "UNKNOWN"}],
                "coverage": {s: {"os": None, "os_end_of_support": False, "targets": []} for s in SERVICES}},
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
            dump({"accepted_findings": [{k: f[k] for k in ("service", "id", "package", "installed", "severity")}
                                        for f in cand["security"]["findings"]]}, paths["adm"])
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


LEADER = {"login": "chi-thanh-nguyen", "id": 212181152, "type": "User"}
CAND_SHA = "d" * 64
TOOLING = "f" * 40
RUN = "45200000001"


def runner_env(**over):
    env = {"GITHUB_REPOSITORY": release.REPOSITORY, "GITHUB_REPOSITORY_ID": release.REPOSITORY_ID,
           "GITHUB_EVENT_NAME": "workflow_dispatch", "GITHUB_REF": "refs/heads/main",
           "GITHUB_WORKFLOW_REF": release.WORKFLOW_REF, "GITHUB_SHA": TOOLING, "GITHUB_RUN_ID": RUN,
           "GITHUB_RUN_ATTEMPT": "1", "GITHUB_ACTOR": LEADER["login"], "GITHUB_ACTOR_ID": str(LEADER["id"]),
           "GITHUB_TRIGGERING_ACTOR": LEADER["login"]}
    env.update(over)
    return env


def request_inputs(**over):
    inputs = {"stage": "promote", "revision": "", "public_origin": "https://codemastery.app", "compatibility_baseline": "",
              "candidate": "candidate-%s-45091629955-1" % REV[:12], "candidate_manifest_sha256": CAND_SHA,
              "version": "v0.1.0-rc.1", "accepted_findings": "2", "accept_critical_or_high": False,
              "note": "Leader accepts the listed residual findings."}
    inputs.update(over)
    return inputs


def event(**over):
    e = {"ref": "refs/heads/main", "inputs": request_inputs(),
         "repository": {"full_name": release.REPOSITORY, "id": int(release.REPOSITORY_ID)}, "sender": dict(LEADER)}
    e.update(over)
    return e


def run_record(**over):
    r = {"id": int(RUN), "run_attempt": 1, "event": "workflow_dispatch", "head_branch": "main", "head_sha": TOOLING,
         "path": release.WORKFLOW_PATH, "repository": {"full_name": release.REPOSITORY, "id": int(release.REPOSITORY_ID)},
         "actor": dict(LEADER), "triggering_actor": dict(LEADER)}
    r.update(over)
    return r


def environment(**over):
    e = {"id": 77, "name": release.ADMISSION_ENVIRONMENT, "can_admins_bypass": False,
         "protection_rules": [{"type": "required_reviewers", "prevent_self_review": False,
                               "reviewers": [{"type": "User", "reviewer": dict(LEADER)}]}],
         "deployment_branch_policy": {"protected_branches": False, "custom_branch_policies": True}}
    e.update(over)
    return e


BRANCH_POLICIES = {"total_count": 1, "branch_policies": [{"id": 1, "name": "main", "type": "branch"}]}


def approvals(**over):
    a = {"state": "approved", "comment": "Admitted after review of the candidate summary.",
         "environments": [{"id": 77, "name": release.ADMISSION_ENVIRONMENT}], "user": dict(LEADER)}
    a.update(over)
    return [a]


OWNERS = ["chi-thanh-nguyen"]


class AdmissionAuthenticationTests(unittest.TestCase):
    """Who may admit: the dispatch (three GitHub views) and the protected-environment approval."""

    def test_authorized_leader_dispatch(self):
        d = release.authenticate_dispatch(runner_env(), event(), run_record())
        self.assertEqual(d["dispatched_by"], {"login": "chi-thanh-nguyen", "id": 212181152})
        self.assertEqual(d["request"]["accepted_findings"], 2)
        self.assertIs(d["request"]["accept_critical_or_high"], False)
        self.assertEqual(d["run_url"], "https://github.com/%s/actions/runs/%s/attempts/1" % (release.REPOSITORY, RUN))

    def test_dispatch_refusals(self):
        other = {"login": "contributor", "id": 4242, "type": "User"}
        forged = dict(LEADER, id=999)             # the Leader's login on another account
        bot = {"login": "github-actions[bot]", "id": 41898282, "type": "Bot"}
        cases = {
            "unauthorized actor": (runner_env(GITHUB_ACTOR="contributor", GITHUB_ACTOR_ID="4242",
                                              GITHUB_TRIGGERING_ACTOR="contributor"),
                                   event(sender=other), run_record(actor=other, triggering_actor=other)),
            "forged decision identity (login of the Leader, another account ID)": (
                runner_env(GITHUB_ACTOR_ID="999"), event(sender=forged), run_record(actor=forged, triggering_actor=forged)),
            "wrong repository": (runner_env(GITHUB_REPOSITORY="someone/codemastery"), event(), run_record()),
            "wrong repository ID": (runner_env(GITHUB_REPOSITORY_ID="1"), event(), run_record()),
            "wrong workflow": (runner_env(GITHUB_WORKFLOW_REF=release.REPOSITORY + "/.github/workflows/ci.yml@refs/heads/main"),
                               event(), run_record()),
            "wrong branch": (runner_env(GITHUB_REF="refs/heads/feature",
                                        GITHUB_WORKFLOW_REF=release.REPOSITORY + "/.github/workflows/release.yml@refs/heads/feature"),
                             event(ref="refs/heads/feature"), run_record(head_branch="feature")),
            "wrong workflow event": (runner_env(GITHUB_EVENT_NAME="push"), event(), run_record(event="push")),
            "re-run by another writer": (runner_env(GITHUB_RUN_ATTEMPT="2", GITHUB_TRIGGERING_ACTOR="contributor"),
                                         event(), run_record(run_attempt=2, triggering_actor=other)),
            "re-run by the Leader (replays the original dispatch)": (runner_env(GITHUB_RUN_ATTEMPT="2"), event(),
                                                                     run_record(run_attempt=2)),
            "triggering actor differs": (runner_env(GITHUB_TRIGGERING_ACTOR="contributor"), event(),
                                         run_record(triggering_actor=other)),
            "event sender differs": (runner_env(), event(sender=other), run_record()),
            "dispatched by a workflow token (bot sender)": (runner_env(), event(sender=bot), run_record()),
            "run record of another run": (runner_env(), event(), run_record(id=1)),
            "run record at another commit": (runner_env(), event(), run_record(head_sha="e" * 40)),
            "run record of another workflow file": (runner_env(), event(), run_record(path=".github/workflows/x.yml")),
            "missing run record": (runner_env(), event(), None),
            "missing event inputs": (runner_env(), event(inputs=None), run_record()),
            "event for another ref": (runner_env(), event(ref="refs/heads/other"), run_record()),
            "rehearsal version requested": (runner_env(), event(inputs=request_inputs(version=release.REHEARSAL_VERSION)),
                                            run_record()),
        }
        for label, (env, ev, rec) in cases.items():
            with self.subTest(label), self.assertRaises(release.Refused):
                release.authenticate_dispatch(env, ev, rec)

    def test_untrusted_request_inputs(self):
        cases = {
            "malformed tag": dict(candidate="candidate-x; rm -rf /"),
            "short digest": dict(candidate_manifest_sha256="d" * 63),
            "uppercase digest": dict(candidate_manifest_sha256="D" * 64),
            "malformed version": dict(version="0.1.0"),
            "negative count": dict(accepted_findings="-1"),
            "non-numeric count": dict(accepted_findings="all"),
            "flag text": dict(accept_critical_or_high="yes"),
            "multi-line note": dict(note="ok\nADMITTED"),
            "long note": dict(note="x" * 1001),
        }
        for label, over in cases.items():
            with self.subTest(label), self.assertRaises(release.Refused):
                release.parse_request(request_inputs(**over))
        self.assertIs(release.parse_request(request_inputs(accept_critical_or_high="true"))["accept_critical_or_high"], True)
        omitted = request_inputs()
        del omitted["accept_critical_or_high"], omitted["note"]   # inputs a CLI dispatch may omit
        self.assertEqual(release.parse_request(omitted)["accept_critical_or_high"], False)
        self.assertEqual(release.parse_request(omitted)["note"], "")

    def test_environment_approval(self):
        result = release.check_environment(environment(), BRANCH_POLICIES, approvals())
        self.assertEqual(result["approved_by"], {"login": "chi-thanh-nguyen", "id": 212181152})
        self.assertEqual((result["branch_policy"], result["admins_can_bypass"]), ("main", False))

    def test_environment_misconfiguration_and_invalid_approval(self):
        other = {"login": "contributor", "id": 4242, "type": "User"}
        cases = {
            "environment missing": (None, BRANCH_POLICIES, approvals()),
            "another environment": (environment(name="production"), BRANCH_POLICIES, approvals()),
            "no required reviewers (created unprotected)": (environment(protection_rules=[]), BRANCH_POLICIES, approvals()),
            "only another reviewer required": (environment(protection_rules=[{"type": "required_reviewers", "reviewers": [
                {"type": "User", "reviewer": other}]}]), BRANCH_POLICIES, approvals()),
            "administrators can bypass": (environment(can_admins_bypass=True), BRANCH_POLICIES, approvals()),
            "all branches allowed": (environment(deployment_branch_policy=None), BRANCH_POLICIES, approvals()),
            "protected-branches policy": (environment(deployment_branch_policy={"protected_branches": True,
                                                                                "custom_branch_policies": False}),
                                          BRANCH_POLICIES, approvals()),
            "extra branch pattern": (environment(), {"branch_policies": BRANCH_POLICIES["branch_policies"] + [
                {"name": "release/*", "type": "branch"}]}, approvals()),
            "tag policy instead of main": (environment(), {"branch_policies": [{"name": "main", "type": "tag"}]}, approvals()),
            "no approval record": (environment(), BRANCH_POLICIES, []),
            "approval in another environment": (environment(), BRANCH_POLICIES,
                                                approvals(environments=[{"id": 1, "name": "copilot"}])),
            "approved by another account": (environment(), BRANCH_POLICIES, approvals(user=other)),
            "approved by the Leader's login on another account": (environment(), BRANCH_POLICIES,
                                                                  approvals(user=dict(LEADER, id=999))),
            "rejected review": (environment(), BRANCH_POLICIES, approvals(state="rejected")),
            "approval plus a rejection": (environment(), BRANCH_POLICIES, approvals() + approvals(state="rejected")),
            "malformed approver": (environment(), BRANCH_POLICIES, approvals(user={"login": "chi-thanh-nguyen"})),
        }
        for label, (env, policies, appr) in cases.items():
            with self.subTest(label), self.assertRaises(release.Refused):
                release.check_environment(env, policies, appr)


class AdmissionDecisionTests(unittest.TestCase):
    """What is admitted: the request bound to the attested candidate, and the record re-derived from evidence."""

    def setUp(self):
        self.candidate = candidate_manifest()
        self.dispatch = release.authenticate_dispatch(runner_env(), event(), run_record())
        self.approval = release.check_environment(environment(), BRANCH_POLICIES, approvals())
        self.run = {"run_id": RUN, "run_attempt": "1", "tooling_sha": TOOLING}

    def decide(self, candidate=None, dispatch=None, approval="default", owners=OWNERS, sha=CAND_SHA):
        return release.decide_admission(candidate or self.candidate, sha, dispatch or self.dispatch,
                                        self.approval if approval == "default" else approval, owners, False,
                                        "2026-11-23T10:00:00Z")

    def verify(self, record, candidate=None, sha=CAND_SHA, run="default", owners=OWNERS, rehearsal=False):
        return release.verify_admission(record, candidate or self.candidate, sha, owners, rehearsal,
                                        self.run if run == "default" else run)

    def test_valid_admission(self):
        record = self.decide()
        self.assertEqual(record["record_id"], "CM-REL-ADMISSION-%s-1" % RUN)
        self.assertEqual(record["decided_by"], {"login": "chi-thanh-nguyen", "id": 212181152})
        self.assertEqual(record["candidate"], {"release_tag": "candidate-%s-45091629955-1" % REV[:12],
                                               "manifest_sha256": CAND_SHA, "archive_sha256": "b" * 64,
                                               "run": {"id": "45091629955", "attempt": "1"}})
        self.assertEqual([f["installed"] for f in record["accepted_findings"]], ["2.39", "v0.58.0"])
        self.assertEqual(record["not_granted"], list(release.REQUIRED_NOT_GRANTED))
        self.assertEqual(record["authorization"]["environment"]["name"], "release-admission")
        self.assertEqual(record["statement"], "Leader accepts the listed residual findings.")
        self.assertEqual(self.verify(record)["version"], "v0.1.0-rc.1")
        self.verify(record, run=None, owners=None)   # offline form: pinned authority only

    def test_decision_refusals(self):
        def req(**over):
            return dict(self.dispatch, request=dict(self.dispatch["request"], **over))
        rehearsal_candidate = candidate_manifest(rehearsal=True)
        unverified = candidate_manifest(verified=False)
        severe = candidate_manifest()
        severe["security"]["findings"][0]["severity"] = "HIGH"
        other_repo = dict(candidate_manifest(), repository="someone/else")
        no_coverage = candidate_manifest()
        del no_coverage["security"]["coverage"]
        cases = {
            "wrong candidate manifest digest": dict(sha="e" * 64),
            "wrong candidate tag": dict(dispatch=req(candidate="candidate-%s-45091629955-2" % REV[:12])),
            "tag of another revision": dict(dispatch=req(candidate="candidate-%s-45091629955-1" % ("b" * 12))),
            "findings count not acknowledged": dict(dispatch=req(accepted_findings=1)),
            "CRITICAL/HIGH without explicit acceptance": dict(candidate=severe),
            "rehearsal candidate substituted for a real one": dict(candidate=rehearsal_candidate),
            "candidate without verified source checks": dict(candidate=unverified),
            "candidate from another repository": dict(candidate=other_repo),
            "candidate without scan coverage": dict(candidate=no_coverage),
            "missing approval": dict(approval=None),
            "approver not the authority": dict(approval=dict(self.approval, approved_by={"login": "contributor", "id": 4242})),
            "dispatcher not the authority": dict(dispatch=dict(self.dispatch, dispatched_by={"login": "contributor", "id": 4242})),
            "authority no longer a code owner of /docs/decisions/": dict(owners=["someone"]),
        }
        for label, kwargs in cases.items():
            with self.subTest(label), self.assertRaises(release.Refused):
                self.decide(**kwargs)
        accepted = self.decide(candidate=severe, dispatch=req(accept_critical_or_high=True))
        self.assertEqual(accepted["authorization"]["request"]["accept_critical_or_high"], True)

    def test_tampered_or_replayed_record_is_refused(self):
        record = self.decide()
        other_candidate = candidate_manifest()
        other_candidate["images"]["backend"]["containerd"] = digest(b"other")
        mutations = {
            "decision PENDING": lambda a: a.update(decision="PENDING"),
            "rehearsal flag": lambda a: a.update(rehearsal=True),
            "unknown field": lambda a: a.update(extra=1),
            "forged decided_by": lambda a: a.update(decided_by={"login": "contributor", "id": 4242}),
            "forged approver with matching decided_by": lambda a: (a.update(decided_by={"login": "contributor", "id": 4242}),
                                                                   a["authorization"].update(approved_by={"login": "contributor", "id": 4242})),
            "changed source revision": lambda a: a.update(source_revision="b" * 40),
            "changed origin": lambda a: a.update(public_origin="https://example.org"),
            "changed manifest digest": lambda a: a["candidate"].update(manifest_sha256="e" * 64),
            "changed archive digest": lambda a: a["candidate"].update(archive_sha256="e" * 64),
            "changed candidate tag": lambda a: a["candidate"].update(release_tag="candidate-%s-1-1" % REV[:12]),
            "changed image identity": lambda a: a["images"]["backend"].update(containerd=digest(b"other")),
            "swapped identities": lambda a: a["images"].update(backend=a["images"]["frontend"]),
            "changed version": lambda a: a["release"].update(version="v0.2.0"),
            "changed version in the request too": lambda a: (a["release"].update(version="v0.2.0"),
                                                             a["authorization"]["request"].update(version="v0.2.0")),
            "finding removed": lambda a: a["accepted_findings"].pop(),
            "finding added": lambda a: a["accepted_findings"].append(dict(a["accepted_findings"][0], id="CVE-9")),
            "finding at another installed version": lambda a: a["accepted_findings"][0].update(installed="2.40"),
            "finding at another severity": lambda a: a["accepted_findings"][0].update(severity="LOW"),
            "finding under another service": lambda a: a["accepted_findings"][0].update(service="frontend"),
            "mandatory restriction removed": lambda a: a["not_granted"].remove("public-publication"),
            "scope rewritten": lambda a: a.update(scope="Everything is admitted."),
            "statement rewritten": lambda a: a.update(statement="Accepts any future finding."),
            "malformed decided_at": lambda a: a.update(decided_at="yesterday"),
            "re-run attempt recorded": lambda a: a["authorization"].update(run_attempt="2"),
            "other mechanism": lambda a: a["authorization"].update(mechanism="pull-request"),
            "admin bypass recorded": lambda a: a["authorization"]["environment"].update(admins_can_bypass=True),
            "missing authorization evidence": lambda a: a.pop("authorization"),
            "invalid approval metadata": lambda a: a["authorization"].update(approved_by={"login": "chi-thanh-nguyen"}),
        }
        for label, mutate in mutations.items():
            with self.subTest(label):
                data = copy.deepcopy(record)
                mutate(data)
                with self.assertRaises(release.Refused):
                    self.verify(data)
        with self.subTest("replayed against another candidate"), self.assertRaises(release.Refused):
            self.verify(record, candidate=other_candidate)
        with self.subTest("replayed against another manifest digest"), self.assertRaises(release.Refused):
            self.verify(record, sha="e" * 64)
        for key, value in (("run_id", "45200000002"), ("run_attempt", "2"), ("tooling_sha", "e" * 40)):
            with self.subTest("used by another run: " + key), self.assertRaises(release.Refused):
                self.verify(record, run=dict(self.run, **{key: value}))
        with self.subTest("a real record used as a rehearsal"), self.assertRaises(release.Refused):
            self.verify(record, rehearsal=True)

    def test_rehearsal_admission(self):
        cand = candidate_manifest(rehearsal=True, verified=False)
        dispatch = release.rehearsal_dispatch({"GITHUB_RUN_ID": "7", "GITHUB_RUN_ATTEMPT": "1", "GITHUB_ACTOR": "dev",
                                               "GITHUB_SHA": TOOLING}, cand, CAND_SHA)
        record = release.decide_admission(cand, CAND_SHA, dispatch, None, None, True, "2026-11-23T10:00:00Z")
        self.assertEqual((record["record_id"], record["rehearsal"]), ("REHEARSAL-7-1", True))
        self.assertEqual(release.verify_admission(record, cand, CAND_SHA, None, True)["version"], release.REHEARSAL_VERSION)
        with self.assertRaises(release.Refused):     # a rehearsal record never promotes for real
            release.verify_admission(record, cand, CAND_SHA, None, False, {"run_id": "7", "run_attempt": "1",
                                                                            "tooling_sha": TOOLING})
        with self.assertRaises(release.Refused):     # nor a real candidate
            release.decide_admission(candidate_manifest(), CAND_SHA, dispatch, None, None, True, "2026-11-23T10:00:00Z")

    def test_cli_decide_and_verify(self):
        script = os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "release.py")
        with tempfile.TemporaryDirectory() as d:
            paths = {n: os.path.join(d, n) for n in ("event.json", "run.json", "env.json", "policies.json", "approvals.json",
                                                     "cand.json", "dispatch.json", "approval.json", "admission.json",
                                                     "CODEOWNERS")}
            dump(self.candidate, paths["cand.json"])
            sha = release.sha256_file(paths["cand.json"])
            dump(event(inputs=request_inputs(candidate_manifest_sha256=sha)), paths["event.json"])
            dump(run_record(), paths["run.json"])
            dump(environment(), paths["env.json"])
            dump(BRANCH_POLICIES, paths["policies.json"])
            dump(approvals(), paths["approvals.json"])
            put(paths["CODEOWNERS"], "/docs/decisions/   @chi-thanh-nguyen\n")
            env = dict(os.environ, **runner_env())

            def run(*args, environ=env):
                return subprocess.run([sys.executable, script] + list(args), capture_output=True, text=True, env=environ)
            self.assertEqual(run("admission-request", "--event", paths["event.json"], "--run-record", paths["run.json"],
                                 "--output", paths["dispatch.json"]).returncode, 0)
            self.assertEqual(run("admission-approval", "--environment", paths["env.json"], "--branch-policies",
                                 paths["policies.json"], "--approvals", paths["approvals.json"], "--output",
                                 paths["approval.json"]).returncode, 0)
            p = run("admission-preview", "--dispatch", paths["dispatch.json"], "--candidate-manifest", paths["cand.json"])
            self.assertEqual(p.returncode, 0)
            self.assertIn("v0.1.0-rc.1", p.stdout)
            decide = ["admission-decide", "--dispatch", paths["dispatch.json"], "--approval", paths["approval.json"],
                      "--candidate-manifest", paths["cand.json"], "--codeowners", paths["CODEOWNERS"],
                      "--output", paths["admission.json"]]
            self.assertEqual(run(*decide).returncode, 0)
            verify = ["verify-admission", "--admission", paths["admission.json"], "--candidate-manifest", paths["cand.json"],
                      "--codeowners", paths["CODEOWNERS"]]
            self.assertEqual(run(*(verify + ["--run-id", RUN, "--run-attempt", "1", "--tooling-sha", TOOLING])).returncode, 0)
            p = run(*verify)   # attempt to promote with an admission not bound to the run that uses it
            self.assertEqual(p.returncode, 3)
            self.assertNotIn("Traceback", p.stderr)
            self.assertEqual(run(*(verify + ["--run-id", "1", "--run-attempt", "1", "--tooling-sha", TOOLING])).returncode, 3)
            p = run("admission-request", "--event", paths["event.json"], "--run-record", paths["run.json"], "--output",
                    os.path.join(d, "x.json"), environ=dict(env, GITHUB_ACTOR="contributor", GITHUB_TRIGGERING_ACTOR="contributor"))
            self.assertEqual(p.returncode, 3)
            self.assertIn("REFUSED", p.stderr)
            self.assertEqual(run("admission-decide", "--dispatch", paths["dispatch.json"], "--candidate-manifest",
                                 paths["cand.json"], "--output", os.path.join(d, "y.json")).returncode, 3)  # no approval
            self.assertFalse(os.path.exists(os.path.join(d, "y.json")))


class AttestationAndRunRecordTests(unittest.TestCase):
    URL = "https://github.com/%s/actions/runs/%s/attempts/1" % (release.REPOSITORY, RUN)

    def cert(self, **over):
        c = {"runInvocationURI": self.URL, "buildTrigger": "workflow_dispatch",
             "buildSignerURI": "https://github.com/" + release.WORKFLOW_REF,
             "sourceRepositoryIdentifier": release.REPOSITORY_ID, "runnerEnvironment": "github-hosted"}
        c.update(over)
        return [{"verificationResult": {"signature": {"certificate": c}}}]

    def test_attestation_signed_by_the_admission_run(self):
        self.assertEqual(release.check_attestation_run(self.cert(), self.URL)["buildTrigger"], "workflow_dispatch")
        cases = {
            "another run": dict(runInvocationURI=self.URL.replace(RUN, "1")),
            "a re-run attempt": dict(runInvocationURI=self.URL[:-1] + "2"),
            "pull_request trigger": dict(buildTrigger="pull_request"),
            "another signer workflow": dict(buildSignerURI="https://github.com/%s/.github/workflows/ci.yml@refs/heads/main"
                                            % release.REPOSITORY),
            "a feature-branch signer": dict(buildSignerURI="https://github.com/%s/.github/workflows/release.yml@refs/heads/x"
                                            % release.REPOSITORY),
            "another repository ID": dict(sourceRepositoryIdentifier="1"),
            "self-hosted runner": dict(runnerEnvironment="self-hosted"),
        }
        for label, over in cases.items():
            with self.subTest(label), self.assertRaises(release.Refused):
                release.check_attestation_run(self.cert(**over), self.URL)
        with self.assertRaises(release.Refused):
            release.check_attestation_run([], self.URL)

    def test_online_run_and_approval_records(self):
        t = AdmissionDecisionTests("test_valid_admission")
        t.setUp()
        record = t.decide()
        self.assertEqual(release.check_run_record(record, run_record(), approvals())["approved_by"]["login"], "chi-thanh-nguyen")
        other = {"login": "contributor", "id": 4242, "type": "User"}
        cases = {
            "run deleted or replaced": (run_record(id=1), approvals()),
            "another dispatcher": (run_record(actor=other, triggering_actor=other), approvals()),
            "re-run by another writer": (run_record(triggering_actor=other), approvals()),
            "another commit": (run_record(head_sha="e" * 40), approvals()),
            "no approval record": (run_record(), []),
            "approved by another account": (run_record(), approvals(user=other)),
        }
        for label, (rec, appr) in cases.items():
            with self.subTest(label), self.assertRaises(release.Refused):
                release.check_run_record(record, rec, appr)

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
    """A release package built by the real release-manifest command, then verified offline."""

    REPO = "chi-thanh-nguyen/codemastery"

    def build(self, d, seal=True, run_id=RUN, tooling=TOOLING, source=REV, compatibility=None, approval_rows=None):
        pkg = os.path.join(d, "release")
        os.makedirs(pkg)
        cand = candidate_manifest()
        cand.update(source_revision=source, source_tree="t" * 40,
                    mc_source={"name": "mc-source.tar.gz", "sha256": hashlib.sha256(b"src").hexdigest()},
                    compatibility=compatibility or {"deploy_sh_sha256": "0" * 64})
        cand["security"]["scanner"] = {"tool": "trivy"}
        dump(cand, os.path.join(pkg, "release-candidate.json"))
        sha = release.sha256_file(os.path.join(pkg, "release-candidate.json"))
        inputs = request_inputs(candidate_manifest_sha256=sha, candidate="candidate-%s-45091629955-1" % source[:12])
        dispatch = release.authenticate_dispatch(runner_env(GITHUB_SHA=tooling), event(inputs=inputs),
                                                 run_record(head_sha=tooling))
        approval = release.check_environment(environment(), BRANCH_POLICIES,
                                             approvals() if approval_rows is None else approval_rows)
        adm = release.decide_admission(cand, sha, dispatch, approval, OWNERS, False, "2026-11-23T10:00:00Z")
        dump(adm, os.path.join(pkg, "admission.json"))
        if seal:
            put(os.path.join(pkg, "admission.sigstore.json"), "{}")
        put(os.path.join(pkg, "codemastery-release-v0.1.0-rc.1-linux-amd64.tar"), b"archive", "wb")
        put(os.path.join(pkg, "mc-source.tar.gz"), b"src", "wb")
        for store in ("containerd", "graphdriver"):
            put(os.path.join(pkg, "expected-images.%s.txt" % store),
                "".join("%s=%s\n" % (release.RELEASE_REFS[s], cand["images"][s][store]) for s in SERVICES))
        ids = {release.RELEASE_REFS[s]: {"containerd": cand["images"][s]["containerd"], "graphdriver": cand["images"][s]["graphdriver"]} for s in SERVICES}
        dump(ids, os.path.join(d, "ids.json"))
        dump([], os.path.join(d, "rescan.json"))
        dump({"tool": "trivy"}, os.path.join(d, "scanner.json"))
        dump({s: {"os": None, "os_end_of_support": False, "targets": []} for s in SERVICES}, os.path.join(d, "coverage.json"))
        dump({"tooling": {"sha": tooling, "workflow_ref": release.WORKFLOW_REF},
              "run": {"rehearsal": False, "id": run_id, "attempt": "1"}, "verification": {}},
             os.path.join(d, "ctx.json"))
        rc = release.main(["release-manifest", "--package", pkg, "--archive-name", "codemastery-release-v0.1.0-rc.1-linux-amd64.tar",
                           "--context", os.path.join(d, "ctx.json"), "--identities", os.path.join(d, "ids.json"),
                           "--findings", os.path.join(d, "rescan.json"), "--scanner", os.path.join(d, "scanner.json"),
                           "--coverage", os.path.join(d, "coverage.json")])
        self.assertEqual(rc, 0)
        return pkg

    def rewrite(self, pkg, name, mutate):
        data = read_json(os.path.join(pkg, name))
        mutate(data)
        dump(data, os.path.join(pkg, name))
        if name != "release.json":   # keep release.json's file map consistent with the change
            m = read_json(os.path.join(pkg, "release.json"))
            m["files"][name] = release.sha256_file(os.path.join(pkg, name))
            if name == "admission.json":
                m["admission"]["sha256"] = m["files"][name]
            dump(m, os.path.join(pkg, "release.json"))
        release.write_sha256sums(pkg)

    def test_valid_chain_and_tampering(self):
        with tempfile.TemporaryDirectory() as d:
            pkg = self.build(d)
            manifest = release.verify_release_chain(pkg, self.REPO)
            self.assertEqual(manifest["version"], "v0.1.0-rc.1")
            self.assertEqual(manifest["admission"]["decided_by"], {"login": "chi-thanh-nguyen", "id": 212181152})
            self.assertEqual(manifest["admission"]["seal"], "admission.sigstore.json")
            self.assertIn("by chi-thanh-nguyen", manifest["status"]["security_admission"])
            with self.assertRaises(release.Refused):
                release.verify_release_chain(pkg, "someone/else")
            put(os.path.join(pkg, "release.sigstore.json"), "{}")   # the bundle sits outside SHA256SUMS
            release.verify_release_chain(pkg, self.REPO)
            path = os.path.join(pkg, "expected-images.containerd.txt")
            with open(path) as f:
                lines = f.read().splitlines()
            lines[0], lines[1] = lines[0].split("=")[0] + "=" + lines[1].split("=")[1], lines[1].split("=")[0] + "=" + lines[0].split("=")[1]
            put(path, "\n".join(lines) + "\n")
            release.write_sha256sums(pkg)   # even with consistent sums, release.json's file map and identities disagree
            with self.assertRaises(release.Refused):
                release.verify_release_chain(pkg, self.REPO)

    def test_tampered_admission_or_release_manifest_is_refused(self):
        cases = {
            "admission decided_by forged": ("admission.json", lambda a: a.update(decided_by={"login": "contributor", "id": 4242})),
            "admission accepts an extra finding": ("admission.json", lambda a: a["accepted_findings"].append(
                dict(a["accepted_findings"][0], id="CVE-9"))),
            "admission marked rehearsal": ("admission.json", lambda a: a.update(rehearsal=True)),
            "admission PENDING": ("admission.json", lambda a: a.update(decision="PENDING")),
            "admission restriction removed": ("admission.json", lambda a: a["not_granted"].remove("deployment")),
            "release.json names another decider": ("release.json", lambda m: m["admission"].update(
                decided_by={"login": "contributor", "id": 4242})),
            "release.json names another admission run": ("release.json", lambda m: m["admission"].update(
                run_url=m["admission"]["run_url"].replace(RUN, "1"))),
            "release.json names another version": ("release.json", lambda m: m.update(version="v9.9.9")),
            "release.json from a rehearsal run": ("release.json", lambda m: m["run"].update(rehearsal=True)),
            "release.json without the admission seal": ("release.json", lambda m: m["admission"].update(seal=None)),
        }
        for label, (name, mutate) in cases.items():
            with self.subTest(label), tempfile.TemporaryDirectory() as d:
                pkg = self.build(d)
                self.rewrite(pkg, name, mutate)
                with self.assertRaises(release.Refused):
                    release.verify_release_chain(pkg, self.REPO)

    def test_admission_of_another_run_or_without_seal_is_refused(self):
        with tempfile.TemporaryDirectory() as d:   # promoted by run B with the admission run A made
            pkg = self.build(d, run_id="45200000002")
            with self.assertRaises(release.Refused):
                release.verify_release_chain(pkg, self.REPO)
        with tempfile.TemporaryDirectory() as d:
            pkg = self.build(d, seal=False)
            with self.assertRaises(release.Refused):
                release.verify_release_chain(pkg, self.REPO)


MULTILINE_COMMENT = "Accept the 37 verified residual findings.\nNo deployment or public publication authorized."


class ApprovalCommentTests(unittest.TestCase):
    """F-02: the Environment approval comment is GitHub's free text, kept verbatim; identity rules are unchanged."""

    def check(self, **row):
        return release.check_environment(environment(), BRANCH_POLICIES, approvals(**row))

    def test_legitimate_comments_are_kept_verbatim(self):
        long_text = ("Reviewed line %d of the candidate summary.\n" * 2000) % tuple(range(2000))
        cases = {
            "single line": "Admitted after review of the candidate summary.",
            "multi-line": MULTILINE_COMMENT,
            "CRLF, tab and blank lines": "Accept.\r\n\r\n\tNo deployment.\r\n",
            "empty": "",
            "Unicode": "Chấp nhận 37 phát hiện. ✅ — 承認 \u202eRTL\u202c",
            "longer than the dispatch note limit": long_text,
            "markdown and shell metacharacters": "| `$(id)` | <b>x</b> ${HOME} ; rm -rf / |\n::warning::not a command",
        }
        self.assertGreater(len(long_text), 65536)
        for label, comment in cases.items():
            with self.subTest(label):
                self.assertEqual(self.check(comment=comment)["approval_comment"], comment)
        # GitHub's schema makes the comment a required string; null or absent records as empty.
        self.assertEqual(self.check(comment=None)["approval_comment"], "")
        absent = approvals()
        del absent[0]["comment"]
        self.assertEqual(release.check_environment(environment(), BRANCH_POLICIES, absent)["approval_comment"], "")

    def test_malformed_comment_data_is_refused(self):
        for label, value in (("number", 7), ("boolean", True), ("list", ["ok"]), ("object", {"body": "ok"}),
                             ("beyond the bound", "x" * (release.APPROVAL_COMMENT_MAX + 1))):
            with self.subTest(label), self.assertRaises(release.Refused):
                self.check(comment=value)
        self.assertEqual(len(self.check(comment="x" * release.APPROVAL_COMMENT_MAX)["approval_comment"]),
                         release.APPROVAL_COMMENT_MAX)

    def test_identity_and_state_rules_are_unchanged(self):
        other = {"login": "contributor", "id": 4242, "type": "User"}
        cases = {
            "unauthorized reviewer with a valid comment": approvals(user=other, comment=MULTILINE_COMMENT),
            "the Leader's login on another account": approvals(user=dict(LEADER, id=999), comment=MULTILINE_COMMENT),
            "rejected deployment": approvals(state="rejected", comment=MULTILINE_COMMENT),
            "approval plus a rejection": approvals(comment=MULTILINE_COMMENT) + approvals(state="rejected"),
            "approval in another environment": approvals(environments=[{"id": 1, "name": "copilot"}],
                                                         comment=MULTILINE_COMMENT),
            "no approval": [],
        }
        for label, rows in cases.items():
            with self.subTest(label), self.assertRaises(release.Refused):
                release.check_environment(environment(), BRANCH_POLICIES, rows)

    def test_multiline_approval_through_admission_and_online_verification(self):
        cand = candidate_manifest()
        rows = approvals(comment=MULTILINE_COMMENT)
        dispatch = release.authenticate_dispatch(runner_env(), event(), run_record())
        approval = release.check_environment(environment(), BRANCH_POLICIES, rows)
        record = release.decide_admission(cand, CAND_SHA, dispatch, approval, OWNERS, False, "2026-11-23T10:00:00Z")
        run = {"run_id": RUN, "run_attempt": "1", "tooling_sha": TOOLING}
        with tempfile.TemporaryDirectory() as d:
            path = os.path.join(d, "admission.json")
            release.write_json(path, record)
            with open(path, encoding="utf-8") as f:
                text = f.read()
            self.assertNotIn(MULTILINE_COMMENT, text)                       # stored JSON-escaped, one line
            self.assertIn(json.dumps(MULTILINE_COMMENT), text)
            stored = release.load_json(path, "admission record")
        self.assertEqual(stored["authorization"]["approval_comment"], MULTILINE_COMMENT)
        release.verify_admission(stored, cand, CAND_SHA, OWNERS, False, run)
        release.verify_admission(stored, cand, CAND_SHA, None, False, None)
        self.assertEqual(release.check_run_record(stored, run_record(), rows)["approved_by"]["id"], 212181152)
        other = {"login": "contributor", "id": 4242, "type": "User"}
        for label, online in (("unauthorized reviewer", approvals(user=other, comment=MULTILINE_COMMENT)),
                              ("rejected deployment", approvals(state="rejected", comment=MULTILINE_COMMENT)),
                              ("malformed comment", approvals(comment=["x"]))):
            with self.subTest("online: " + label), self.assertRaises(release.Refused):
                release.check_run_record(stored, run_record(), online)
        for label, value in (("non-text", 7), ("null", None), ("beyond the bound", "x" * (release.APPROVAL_COMMENT_MAX + 1))):
            with self.subTest("recorded comment " + label), self.assertRaises(release.Refused):
                tampered = copy.deepcopy(stored)
                tampered["authorization"]["approval_comment"] = value
                release.verify_admission(tampered, cand, CAND_SHA, OWNERS, False, run)

    def test_cli_keeps_the_comment_out_of_output(self):
        script = os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "release.py")
        comment = MULTILINE_COMMENT + "\n::error::injected\n| forged | row |"
        with tempfile.TemporaryDirectory() as d:
            paths = {n: os.path.join(d, n) for n in ("event.json", "run.json", "env.json", "policies.json", "approvals.json",
                                                     "cand.json", "dispatch.json", "approval.json", "admission.json",
                                                     "CODEOWNERS")}
            dump(candidate_manifest(), paths["cand.json"])
            sha = release.sha256_file(paths["cand.json"])
            dump(event(inputs=request_inputs(candidate_manifest_sha256=sha)), paths["event.json"])
            dump(run_record(), paths["run.json"])
            dump(environment(), paths["env.json"])
            dump(BRANCH_POLICIES, paths["policies.json"])
            dump(approvals(comment=comment), paths["approvals.json"])
            put(paths["CODEOWNERS"], "/docs/decisions/   @chi-thanh-nguyen\n")
            env = dict(os.environ, **runner_env())
            outputs = []

            def run(*args):
                p = subprocess.run([sys.executable, script] + list(args), capture_output=True, text=True, env=env)
                outputs.append(p.stdout + p.stderr)
                self.assertEqual(p.returncode, 0, p.stderr)
            run("admission-request", "--event", paths["event.json"], "--run-record", paths["run.json"],
                "--output", paths["dispatch.json"])
            run("admission-approval", "--environment", paths["env.json"], "--branch-policies", paths["policies.json"],
                "--approvals", paths["approvals.json"], "--output", paths["approval.json"])
            run("admission-decide", "--dispatch", paths["dispatch.json"], "--approval", paths["approval.json"],
                "--candidate-manifest", paths["cand.json"], "--codeowners", paths["CODEOWNERS"],
                "--output", paths["admission.json"])
            run("verify-admission", "--admission", paths["admission.json"], "--candidate-manifest", paths["cand.json"],
                "--codeowners", paths["CODEOWNERS"], "--run-id", RUN, "--run-attempt", "1", "--tooling-sha", TOOLING)
            run("check-run-record", "--admission", paths["admission.json"], "--run-record", paths["run.json"],
                "--approvals", paths["approvals.json"])
            self.assertEqual(read_json(paths["admission.json"])["authorization"]["approval_comment"], comment)
        for out in outputs:
            self.assertNotIn("injected", out)
            self.assertNotIn("Accept the 37", out)


class AdmissionPreviewTests(unittest.TestCase):
    """F-03: the pre-approval summary states only what the request job verified."""

    def test_preview_states_what_was_and_was_not_verified(self):
        script = os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "release.py")
        with tempfile.TemporaryDirectory() as d:
            cand, disp = os.path.join(d, "cand.json"), os.path.join(d, "dispatch.json")
            dump(candidate_manifest(), cand)
            sha = release.sha256_file(cand)
            dump(release.authenticate_dispatch(runner_env(), event(inputs=request_inputs(candidate_manifest_sha256=sha)),
                                               run_record()), disp)
            p = subprocess.run([sys.executable, script, "admission-preview", "--dispatch", disp, "--candidate-manifest", cand],
                               capture_output=True, text=True)
        self.assertEqual(p.returncode, 0, p.stderr)
        out = p.stdout
        self.assertNotIn("package files and source qualification were verified by this job", out)
        self.assertNotIn("verified by this job. ", out)
        for expected in ("**Verified by this job:** it downloaded only `release-candidate.json` and its Sigstore bundle",
                         "verified the manifest's attestation",
                         "equals `candidate_manifest_sha256` in your request",
                         "**Not re-checked by this job (prior candidate evidence):**",
                         "source revision's required checks, the image qualification",
                         "The archive, SBOMs, scan reports and other package files were not downloaded here.",
                         "**Deferred to the protected promotion job, after your approval",
                         "verifies every file against `SHA256SUMS`",
                         "any loss of scan coverage, stops promotion",
                         "Not granted: deployment"):
            self.assertIn(expected, out)
        self.assertIn("`%s` (verified against the request)" % sha, out)


class VerifyReleaseProcedureTests(unittest.TestCase):
    """F-01: verify-release.sh runs only as the release's own tooling, with a separate source checkout.

    A synthetic repository models the first A1 release: commit S (the candidate's
    source revision) predates A1 and still carries a historical verifier that refuses
    every A1 package, as 1d0658a7's does; commit T (the promoting main commit) carries
    this tooling and a changed deploy.sh; a later commit L carries the same tooling at
    another revision. The release package is built by the real release-manifest
    command. gh is a stub that plays GitHub: Sigstore verification succeeds only for the
    tooling digest that "signed" the package, and the run and approval records carry a
    multi-line approval comment. No network, Docker or real attestation is involved.
    """

    HERE = os.path.dirname(os.path.abspath(__file__))
    HISTORICAL_VERIFIER = ("#!/usr/bin/env bash\n# historical pull-request admission verifier (pre-A1)\n"
                           "touch \"$HISTORICAL_MARKER\"\n"
                           "echo 'REFUSED: release.json does not record that decided_by merged the admission record' >&2\n"
                           "exit 3\n")
    DEPLOY_SH = ("# synthetic application deploy.sh (%s)\n"
                 "verify_archive() {\n"
                 "  [[ ${#EXPECT_IMAGES[@]} -eq 5 && \"$(sha256sum \"$ARCHIVE\" | awk '{print $1}')\" == \"$ARCHIVE_SHA256\" ]] || return 1\n"
                 "  printf '%%s %%s\\n' \"$IMAGE_STORE\" '%s' >> \"$DEPLOY_MARKER\"\n"
                 "}\n")

    def git(self, repo, *args):
        return subprocess.run(["git", "-C", repo, "-c", "commit.gpgsign=false", "-c", "core.hooksPath=/dev/null"] + list(args),
                              check=True, capture_output=True, text=True, env=self.genv).stdout.strip()

    def commit(self, repo, files, message):
        for rel, data in files.items():
            path = os.path.join(repo, rel)
            os.makedirs(os.path.dirname(path), exist_ok=True)
            put(path, data)
        self.git(repo, "add", "-A")
        self.git(repo, "commit", "-q", "-m", message)
        return self.git(repo, "rev-parse", "HEAD")

    def setUp(self):
        self.tmp = tempfile.TemporaryDirectory()
        self.addCleanup(self.tmp.cleanup)
        d = self.d = os.path.realpath(self.tmp.name)
        self.genv = dict(os.environ, GIT_CONFIG_NOSYSTEM="1", GIT_CONFIG_GLOBAL=os.devnull, GIT_AUTHOR_NAME="fixture",
                         GIT_AUTHOR_EMAIL="fixture@example.invalid", GIT_COMMITTER_NAME="fixture",
                         GIT_COMMITTER_EMAIL="fixture@example.invalid")
        self.repo = os.path.join(d, "upstream")
        os.makedirs(self.repo)
        self.git(self.repo, "init", "-q", "-b", "main")
        app = {"infrastructure/vm/deploy.sh": self.DEPLOY_SH % ("source revision", "source"),
               "infrastructure/docker-compose.yml": "services: {}\n",
               "infrastructure/vm/docker-compose.vm.yml": "services: {}\n"}
        self.source = self.commit(self.repo, dict(app, **{"infrastructure/release/verify-release.sh": self.HISTORICAL_VERIFIER}),
                                  "historical source revision (pre-A1)")
        tooling = {}
        for name in ("release.py", "release.sh", "verify-release.sh"):
            with open(os.path.join(self.HERE, "..", name)) as f:
                tooling["infrastructure/release/" + name] = f.read()
        tooling["infrastructure/vm/deploy.sh"] = self.DEPLOY_SH % ("later main", "tooling")
        self.tooling = self.commit(self.repo, tooling, "A1 release tooling")
        self.later = self.commit(self.repo, {"NOTES": "later main commit\n"}, "later main commit")
        compat = {key: hashlib.sha256(app[path].encode()).hexdigest() for key, path in (
            ("deploy_sh_sha256", "infrastructure/vm/deploy.sh"), ("compose_base_sha256", "infrastructure/docker-compose.yml"),
            ("compose_override_sha256", "infrastructure/vm/docker-compose.vm.yml"))}
        os.makedirs(os.path.join(d, "fixture"))
        self.pkg = ReleaseChainTests.build(self, os.path.join(d, "fixture"), tooling=self.tooling, source=self.source,
                                           compatibility=compat, approval_rows=approvals(comment=MULTILINE_COMMENT))
        self.stub = os.path.join(d, "stub")
        os.makedirs(self.stub)
        url = release.run_url(RUN, 1)
        dump([{"verificationResult": {"signature": {"certificate": {
            "runInvocationURI": url, "buildTrigger": "workflow_dispatch", "buildSignerURI": "https://github.com/" + release.WORKFLOW_REF,
            "sourceRepositoryIdentifier": release.REPOSITORY_ID, "runnerEnvironment": "github-hosted"}}}}],
            os.path.join(self.stub, "seal.json"))
        dump(run_record(head_sha=self.tooling), os.path.join(self.stub, "run.json"))
        dump(approvals(comment=MULTILINE_COMMENT), os.path.join(self.stub, "approvals.json"))
        put(os.path.join(self.stub, "gh"),
            "#!/bin/sh\n"
            "printf '%s\\n' \"$*\" >> \"$STUB_DIR/gh.log\"\n"
            "[ \"${FAIL_ATTESTATION:-}\" != 1 ] || exit 42\n"
            "case \"$*\" in\n"
            "  'attestation verify '*'--format json'*) cat \"$STUB_DIR/seal.json\" ;;\n"
            "  'attestation verify '*) case \"$*\" in *\"--source-digest $SIGNED_TOOLING \"*) exit 0 ;; *) exit 1 ;; esac ;;\n"
            "  'api '*'/attempts/1') cat \"$STUB_DIR/run.json\" ;;\n"
            "  'api '*'/approvals') cat \"$STUB_DIR/approvals.json\" ;;\n"
            "  *) exit 1 ;;\n"
            "esac\n")
        os.chmod(os.path.join(self.stub, "gh"), 0o755)
        # Observe the actual documented commands; only explicitly selected Git
        # failures are simulated. Every other operation uses the real local Git.
        put(os.path.join(self.stub, "git"),
            "#!/bin/sh\n"
            "printf '%%s\\n' \"$*\" >> \"$STUB_DIR/git.log\"\n"
            "operation=''\n"
            "case \"$*\" in\n"
            "  'clone --quiet '*' tooling') operation=tooling-clone ;;\n"
            "  '-C tooling checkout --quiet --detach '*) operation=tooling-checkout ;;\n"
            "  'clone --quiet '*' source') operation=source-clone ;;\n"
            "  '-C source checkout --quiet --detach '*) operation=source-checkout ;;\n"
            "esac\n"
            "if [ -n \"$operation\" ] && [ \"$operation\" = \"${FAIL_GIT_OPERATION:-}\" ]; then\n"
            "  printf 'Simulated %%s failure\\n' \"$operation\" >&2\n"
            "  exit 42\n"
            "fi\n"
            "exec %s \"$@\"\n" % shlex.quote(shutil.which("git")))
        put(os.path.join(self.stub, "bash"),
            "#!/bin/sh\n"
            "if [ \"${1:-}\" = tooling/infrastructure/release/verify-release.sh ]; then\n"
            "  : > \"$VERIFIER_MARKER\"\n"
            "fi\n"
            "exec %s \"$@\"\n" % shlex.quote(shutil.which("bash")))
        for name in ("git", "bash"):
            os.chmod(os.path.join(self.stub, name), 0o755)
        self.env = dict(self.genv, PATH=self.stub + os.pathsep + os.environ["PATH"], STUB_DIR=self.stub,
                        SIGNED_TOOLING=self.tooling, DEPLOY_MARKER=os.path.join(d, "deploy.log"),
                        HISTORICAL_MARKER=os.path.join(d, "historical-verifier-ran"),
                        VERIFIER_MARKER=os.path.join(d, "verifier-invoked"),
                        UNATTESTED_MARKER=os.path.join(d, "unattested-tooling-ran"))

    def checkout(self, name, revision):
        path = os.path.join(self.d, name)
        self.git(self.d, "clone", "-q", self.repo, path)
        self.git(path, "checkout", "-q", "--detach", revision)
        return path

    def verify(self, tooling_dir, source_dir, *extra):
        return subprocess.run(["bash", os.path.join(tooling_dir, "infrastructure", "release", "verify-release.sh"), self.pkg,
                               "--version", "v0.1.0-rc.1", "--source", source_dir] + list(extra),
                              capture_output=True, text=True, env=self.env, cwd=self.d)

    def gh_log(self):
        path = os.path.join(self.stub, "gh.log")
        if not os.path.exists(path):
            return []
        with open(path) as f:
            return f.read().splitlines()

    def git_log(self):
        path = os.path.join(self.stub, "git.log")
        if not os.path.exists(path):
            return []
        with open(path) as f:
            return f.read().splitlines()

    def documented_procedure(self, **env):
        with open(os.path.join(self.HERE, "..", "..", "..", "docs", "deployment.md"), encoding="utf-8") as f:
            doc = f.read()
        section = doc.split("### Using a release package with `deploy.sh`", 1)[1].split("\n### ", 1)[0]
        self.assertNotIn("--source .", section)
        # Remove only Markdown indentation and the operator's placeholder inputs;
        # preserve Python indentation and execute the actual remaining Bash block.
        block = textwrap.dedent(section.split("```bash\n", 1)[1].split("```", 1)[0])
        lines = block.splitlines()
        self.assertIn('bash tooling/infrastructure/release/verify-release.sh "$pkg" --version "$version" '
                      '--source source --store "$store"', lines)
        script = "\n".join(line for line in lines if not line.startswith(("pkg=", "repo=")))
        self.assertIn('--source-digest "$tooling_sha"', script)
        path = os.path.join(self.d, "procedure.sh")
        put(path, 'pkg=%s; version=v0.1.0-rc.1; store=containerd\nrepo=%s\n' %
            (shlex.quote(self.pkg), shlex.quote(self.repo)) + script + "\n")
        return subprocess.run(["bash", path], capture_output=True, text=True, env=dict(self.env, **env), cwd=self.d)

    def assert_bootstrap_refused(self, p):
        self.assertNotEqual(p.returncode, 0, p.stdout + p.stderr)
        for key in ("UNATTESTED_MARKER", "VERIFIER_MARKER", "HISTORICAL_MARKER", "DEPLOY_MARKER"):
            self.assertFalse(os.path.exists(self.env[key]), key + ": " + p.stdout + p.stderr)

    def assert_verified(self, p):
        self.assertEqual(p.returncode, 0, p.stdout + p.stderr)
        self.assertIn("Verifier: release tooling commit %s" % self.tooling, p.stdout)
        self.assertIn("--source-revision %s" % self.source, p.stdout)
        self.assertIn("VERIFIED.", p.stdout)
        attest = [line for line in self.gh_log() if line.startswith("attestation verify")]
        self.assertTrue(attest)
        self.assertTrue(all("--source-digest %s " % self.tooling in line for line in attest))
        self.assertFalse(any(self.source in line for line in attest))
        with open(self.env["DEPLOY_MARKER"]) as f:   # the source revision's deploy.sh, both stores
            self.assertEqual(f.read().splitlines(), ["containerd source", "graphdriver source"])
        self.assertFalse(os.path.exists(self.env["HISTORICAL_MARKER"]))

    def test_documented_procedure_uses_the_tooling_and_source_revisions(self):
        p = self.documented_procedure()
        self.assert_verified(p)
        self.assertTrue(os.path.exists(self.env["VERIFIER_MARKER"]))
        self.assertEqual(self.git(os.path.join(self.d, "tooling"), "rev-parse", "HEAD"), self.tooling)
        self.assertEqual(self.git(os.path.join(self.d, "source"), "rev-parse", "HEAD"), self.source)
        bootstrap = self.gh_log()[0]   # the operator's own gh runs first, before any checked-out code
        self.assertTrue(bootstrap.startswith("attestation verify %s/release.json " % self.pkg), bootstrap)

    def test_documented_procedure_handles_paths_with_spaces(self):
        pkg, repo = os.path.join(self.d, "assets with spaces"), os.path.join(self.d, "upstream with spaces")
        os.rename(self.pkg, pkg)
        os.rename(self.repo, repo)
        self.pkg, self.repo = pkg, repo
        self.assert_verified(self.documented_procedure())

    def test_bootstrap_refuses_existing_non_git_tooling(self):
        directory = os.path.join(self.d, "tooling", "infrastructure", "release")
        os.makedirs(directory)
        path = os.path.join(directory, "verify-release.sh")
        marker = '#!/bin/sh\n: > "$UNATTESTED_MARKER"\nexit 0\n'
        put(path, marker)
        os.chmod(path, 0o755)
        p = self.documented_procedure()
        self.assert_bootstrap_refused(p)
        self.assertIn("Failed to clone release tooling", p.stderr)
        self.assertEqual(len(self.git_log()), 1)
        self.assertFalse(os.path.exists(os.path.join(self.d, "source")))
        with open(path) as f:
            self.assertEqual(f.read(), marker)  # the occupied destination was not removed or overwritten

    def test_bootstrap_refuses_existing_git_tooling_at_another_commit(self):
        tooling = self.checkout("tooling", self.later)
        wrong = self.commit(tooling, {"infrastructure/release/verify-release.sh":
                                     '#!/bin/sh\n: > "$UNATTESTED_MARKER"\nexit 0\n'}, "unattested verifier")
        self.assertNotEqual(wrong, self.tooling)
        self.assertEqual(self.git(tooling, "status", "--porcelain"), "")
        p = self.documented_procedure()
        self.assert_bootstrap_refused(p)
        self.assertIn("Failed to clone release tooling", p.stderr)
        self.assertEqual(len(self.git_log()), 1)
        self.assertEqual(self.git(tooling, "rev-parse", "HEAD"), wrong)

    def test_bootstrap_refuses_existing_source(self):
        source = self.checkout("source", self.later)
        sentinel = os.path.join(source, "keep-me")
        put(sentinel, "preserve existing source\n")
        p = self.documented_procedure()
        self.assert_bootstrap_refused(p)
        self.assertIn("Failed to clone application source", p.stderr)
        self.assertEqual(len(self.git_log()), 3)
        self.assertEqual(self.git(source, "rev-parse", "HEAD"), self.later)
        with open(sentinel) as f:
            self.assertEqual(f.read(), "preserve existing source\n")

    def assert_git_failure_stops_bootstrap(self, operation, diagnostic, expected_calls):
        p = self.documented_procedure(FAIL_GIT_OPERATION=operation)
        self.assert_bootstrap_refused(p)
        self.assertIn("Simulated %s failure" % operation, p.stderr)
        self.assertIn(diagnostic, p.stderr)
        self.assertEqual(len(self.git_log()), expected_calls)
        self.assertEqual(len(self.gh_log()), 1)  # only the operator's bootstrap attestation

    def test_bootstrap_tooling_clone_failure(self):
        self.assert_git_failure_stops_bootstrap("tooling-clone", "Failed to clone release tooling", 1)

    def test_bootstrap_tooling_checkout_failure(self):
        self.assert_git_failure_stops_bootstrap("tooling-checkout", "Failed to check out attested release tooling", 2)
        self.assertFalse(os.path.exists(os.path.join(self.d, "source")))

    def test_bootstrap_source_clone_failure(self):
        self.assert_git_failure_stops_bootstrap("source-clone", "Failed to clone application source", 3)
        self.assertEqual(self.git(os.path.join(self.d, "tooling"), "rev-parse", "HEAD"), self.tooling)

    def test_bootstrap_source_checkout_failure(self):
        self.assert_git_failure_stops_bootstrap("source-checkout", "Failed to check out the release source revision", 4)

    def test_bootstrap_attestation_failure_precedes_git_and_repository_code(self):
        p = self.documented_procedure(FAIL_ATTESTATION="1")
        self.assert_bootstrap_refused(p)
        self.assertIn("Release manifest attestation verification failed", p.stderr)
        self.assertEqual(len(self.gh_log()), 1)
        self.assertEqual(self.git_log(), [])

    def assert_invalid_manifest_stops_bootstrap(self, contents):
        path = os.path.join(self.pkg, "release.json")
        put(path, contents)
        p = self.documented_procedure()
        self.assert_bootstrap_refused(p)
        self.assertIn("Invalid release manifest or revision SHA", p.stderr)
        self.assertEqual(self.gh_log(), [])
        self.assertEqual(self.git_log(), [])
        self.assertFalse(os.path.exists(os.path.join(self.d, "tooling")))
        self.assertFalse(os.path.exists(os.path.join(self.d, "source")))

    def test_bootstrap_rejects_invalid_sha_strings_and_types_before_external_commands(self):
        manifest = read_json(os.path.join(self.pkg, "release.json"))
        cases = {"empty": "", "truncated": "a" * 39, "too long": "a" * 41,
                 "non-hex": "g" * 40, "uppercase": "A" * 40, "newline": "a" * 20 + "\n" + "a" * 20,
                 "leading space": " " + "a" * 40, "trailing space": "a" * 40 + " ",
                 "leading tab": "\t" + "a" * 40, "trailing newline": "a" * 40 + "\n",
                 "trailing CRLF": "a" * 40 + "\r\n",
                 "shell text": '$(touch "%s"); `id`' % self.env["UNATTESTED_MARKER"],
                 "null": None, "number resembling SHA": int("1" * 40), "boolean": True,
                 "array": ["a" * 40], "object": {"sha": "a" * 40}}
        for field in ("tooling", "source"):
            for label, value in cases.items():
                with self.subTest(field=field, value=label):
                    data = copy.deepcopy(manifest)
                    if field == "tooling":
                        data["tooling"]["sha"] = value
                    else:
                        data["source_revision"] = value
                    self.assert_invalid_manifest_stops_bootstrap(json.dumps(data))

    def test_bootstrap_rejects_missing_fields_and_malformed_json_before_external_commands(self):
        manifest = read_json(os.path.join(self.pkg, "release.json"))
        missing_tooling, missing_source, missing_sha = (copy.deepcopy(manifest) for _ in range(3))
        del missing_tooling["tooling"]
        del missing_source["source_revision"]
        del missing_sha["tooling"]["sha"]
        cases = {"missing tooling": missing_tooling, "missing source SHA": missing_source,
                 "missing tooling SHA": missing_sha, "null tooling": dict(manifest, tooling=None),
                 "array tooling": dict(manifest, tooling=[]), "wrong root type": []}
        for label, value in cases.items():
            with self.subTest(value=label):
                self.assert_invalid_manifest_stops_bootstrap(json.dumps(value))
        for contents in ("{", "", '{"tooling":'):
            with self.subTest(malformed=contents):
                self.assert_invalid_manifest_stops_bootstrap(contents)

    def test_historical_source_verifier_is_the_failure_the_procedure_avoids(self):
        source = self.checkout("source", self.source)
        p = subprocess.run(["bash", os.path.join(source, "infrastructure", "release", "verify-release.sh"), self.pkg,
                            "--version", "v0.1.0-rc.1", "--source", "."], capture_output=True, text=True, env=self.env,
                           cwd=source)
        self.assertNotEqual(p.returncode, 0)
        self.assertIn("merged the admission record", p.stderr)
        os.remove(self.env["HISTORICAL_MARKER"])
        self.assert_verified(self.verify(self.checkout("tooling", self.tooling), source))

    def test_tooling_revision_is_enforced(self):
        source = self.checkout("source", self.source)
        later = self.checkout("later", self.later)
        p = self.verify(later, source)           # A1-capable tooling, but not the commit that attested the package
        self.assertEqual(p.returncode, 1)
        self.assertIn("not the release tooling commit %s" % self.tooling, p.stderr)
        self.assertEqual(self.gh_log(), [])      # refused before anything else ran
        tooling = self.checkout("tooling", self.tooling)
        put(os.path.join(tooling, "infrastructure", "release", "json.py"), "raise SystemExit('planted')\n")
        p = self.verify(tooling, source)
        self.assertEqual(p.returncode, 1)
        self.assertIn("uncommitted or untracked changes", p.stderr)
        os.remove(os.path.join(tooling, "infrastructure", "release", "json.py"))
        put(os.path.join(tooling, "infrastructure", "release", "release.py"), "# modified\n", "a")
        self.assertIn("uncommitted or untracked changes", self.verify(tooling, source).stderr)
        exported = os.path.join(self.d, "exported")   # a copy outside any git checkout is refused
        os.makedirs(os.path.join(exported, "infrastructure", "release"))
        for name in ("release.py", "release.sh", "verify-release.sh"):
            with open(os.path.join(self.HERE, "..", name)) as f:
                put(os.path.join(exported, "infrastructure", "release", name), f.read())
        p = self.verify(exported, source)
        self.assertEqual(p.returncode, 1)
        self.assertIn("git checkout", p.stderr)

    def test_source_revision_is_enforced(self):
        tooling = self.checkout("tooling", self.tooling)
        for label, source in (("the tooling checkout as source", tooling),
                              ("a later commit as source", self.checkout("later", self.later))):
            with self.subTest(label):
                p = self.verify(tooling, source)
                self.assertEqual(p.returncode, 1)
                self.assertIn("--source is not checked out at the release source revision %s" % self.source, p.stderr)
        source = self.checkout("source", self.source)
        put(os.path.join(source, "infrastructure", "vm", "deploy.sh"), "# modified\n", "a")
        self.assertIn("--source has uncommitted changes", self.verify(tooling, source).stderr)

    def test_invalid_admission_chain_or_authority_is_refused(self):
        tooling, source = self.checkout("tooling", self.tooling), self.checkout("source", self.source)
        put(os.path.join(self.stub, "approvals.json"),
            json.dumps(approvals(user={"login": "contributor", "id": 4242, "type": "User"}, comment=MULTILINE_COMMENT)))
        p = self.verify(tooling, source)
        self.assertEqual(p.returncode, 1)
        self.assertIn("GitHub's run or approval record contradicts admission.json", p.stderr)
        ReleaseChainTests.rewrite(self, self.pkg, "admission.json",
                                  lambda a: a.update(decided_by={"login": "contributor", "id": 4242}))
        p = self.verify(tooling, source)
        self.assertEqual(p.returncode, 1)
        self.assertIn("Package records are inconsistent", p.stderr)


class DraftVersionTests(unittest.TestCase):
    """release.sh version-unused against a stub gh: a used release version is refused before admission."""

    def run_probe(self, tags, releases, fail=False):
        here = os.path.dirname(os.path.abspath(__file__))
        with tempfile.TemporaryDirectory() as d:
            stub = os.path.join(d, "gh")
            put(stub, "#!/bin/sh\n%s\ncase \"$*\" in\n  *matching-refs*) echo %s ;;\n  *releases*) printf '%%s' '%s' ;;\nesac\n"
                % ("exit 1" if fail else "", tags, releases))
            os.chmod(stub, 0o755)
            env = dict(os.environ, PATH=d + os.pathsep + os.environ["PATH"], GITHUB_REPOSITORY=release.REPOSITORY)
            return subprocess.run(["bash", os.path.join(here, "..", "release.sh"), "version-unused", "v0.1.0-rc.1"],
                                  capture_output=True, text=True, env=env)

    def test_version_probe(self):
        self.assertEqual(self.run_probe("0", "").returncode, 0)
        self.assertNotEqual(self.run_probe("1", "").returncode, 0)          # tag exists
        self.assertNotEqual(self.run_probe("0", "454878027").returncode, 0)  # duplicate release or draft
        self.assertNotEqual(self.run_probe("0", "", fail=True).returncode, 0)  # an API error never reads as unused

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
