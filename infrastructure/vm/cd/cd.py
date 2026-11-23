#!/usr/bin/env python3
"""CodeMastery SSH continuous deployment: the GitHub-runner side of the Deploy workflow (ADR017).

Subcommands (standard library only):

  authenticate  who dispatched this run (three GitHub views agree; pinned deployment authority;
                first attempt of deploy.yml on main) and the validated request
  approval      the protected `production` environment is real (required reviewer = the
                authority, no administrator bypass, main only) and this run was approved by it
  context       the non-secret deployment context passed to the VM (authority references)
  external      checks from outside the VM network: served certificate trust/hostname/expiry,
                HTTPS answer, and which TCP ports answer on the public address
  report        the job summary from the VM's JSON result and the external checks

The authority and its account ID come from infrastructure/release/release.py (the same
pinned Leader as the release admission); changing them is a reviewed code change.
"""
import argparse
import base64
import datetime
import hashlib
import json
import os
import re
import socket
import ssl
import sys
import urllib.request

sys.path.insert(0, os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "..", "release"))
import release as rel  # noqa: E402

WORKFLOW_PATH = ".github/workflows/deploy.yml"
WORKFLOW_REF = rel.REPOSITORY + "/" + WORKFLOW_PATH + "@refs/heads/main"
ENVIRONMENT = "production"
DEPLOYMENT_AUTHORITY = dict(rel.ADMISSION_AUTHORITY)
OPERATIONS = ("plan", "prepare", "deploy", "verify", "resume")
SET_ID = re.compile(r"^CMRS-[0-9]{8}T[0-9]{6}Z-pre-update-[0-9a-f]{8}$")
HEX64 = re.compile(r"^[0-9a-f]{64}$")
PUBLIC_PORTS = (22, 80, 443, 5432, 8080, 9000, 9001)
ALLOWED_OPEN = (22, 443)


def refuse(message):
    rel.refuse(message)


def is_authority(account):
    return isinstance(account, dict) and DEPLOYMENT_AUTHORITY.get(account.get("login")) == account.get("id") \
        and not isinstance(account.get("id"), bool)


def parse_request(inputs):
    if not isinstance(inputs, dict):
        refuse("the dispatch carries no inputs")
    op, version = inputs.get("operation"), inputs.get("version")
    rset, rsha = inputs.get("recovery_set") or "", inputs.get("recovery_manifest_sha256") or ""
    if op not in OPERATIONS:
        refuse("operation must be one of %s" % ", ".join(OPERATIONS))
    if op != "resume" and (not isinstance(version, str) or not rel.VERSION.match(version)):
        refuse("version must be a v<major>.<minor>.<patch>[-pre] release tag")
    if op == "resume":
        version = version if isinstance(version, str) and rel.VERSION.match(version) else ""
    if op == "deploy":
        if rset or rsha:
            if not SET_ID.match(rset) or not HEX64.match(rsha):
                refuse("recovery_set must be a pre-update set ID and recovery_manifest_sha256 its manifest SHA-256")
    elif rset or rsha:
        refuse("recovery_set and recovery_manifest_sha256 are inputs of the deploy operation only")
    return {"operation": op, "version": version, "recovery_set": rset, "recovery_manifest_sha256": rsha,
            "note": rel._text(inputs.get("note"), "note", 300)}


def authenticate(env, event, run_record):
    """The first attempt of a workflow_dispatch of deploy.yml on main, dispatched by the authority."""
    checks = (("GITHUB_REPOSITORY", rel.REPOSITORY), ("GITHUB_REPOSITORY_ID", rel.REPOSITORY_ID),
              ("GITHUB_EVENT_NAME", "workflow_dispatch"), ("GITHUB_REF", "refs/heads/main"),
              ("GITHUB_WORKFLOW_REF", WORKFLOW_REF), ("GITHUB_RUN_ATTEMPT", "1"))
    for key, want in checks:
        if (env or {}).get(key) != want:
            refuse("%s is %r, not %r: deployments run only as the first attempt of deploy.yml dispatched on main"
                   % (key, (env or {}).get(key), want))
    sha, rid = env.get("GITHUB_SHA") or "", env.get("GITHUB_RUN_ID") or ""
    if not rel.REVISION.match(sha) or not rel.RUN_ID.match(rid):
        refuse("the run's commit or ID is malformed")
    if not isinstance(event, dict) or event.get("ref") != "refs/heads/main" \
            or (event.get("repository") or {}).get("full_name") != rel.REPOSITORY \
            or str((event.get("repository") or {}).get("id")) != rel.REPOSITORY_ID:
        refuse("the event payload does not describe a dispatch on main of this repository")
    sender = rel._account(event.get("sender"), "the event sender")
    if not isinstance(run_record, dict):
        refuse("the run record is missing")
    actor = rel._account(run_record.get("actor"), "the run record's actor")
    triggering = rel._account(run_record.get("triggering_actor"), "the run record's triggering actor")
    repo = run_record.get("repository") or {}
    if run_record.get("id") != int(rid) or run_record.get("run_attempt") != 1 \
            or run_record.get("event") != "workflow_dispatch" or run_record.get("head_branch") != "main" \
            or run_record.get("head_sha") != sha or run_record.get("path") != WORKFLOW_PATH \
            or repo.get("full_name") != rel.REPOSITORY or str(repo.get("id")) != rel.REPOSITORY_ID:
        refuse("GitHub's run record does not match this run")
    logins = {env.get("GITHUB_ACTOR"), env.get("GITHUB_TRIGGERING_ACTOR"), sender["login"], actor["login"],
              triggering["login"]}
    ids = {str(env.get("GITHUB_ACTOR_ID")), str(sender["id"]), str(actor["id"]), str(triggering["id"])}
    if len(logins) != 1 or len(ids) != 1:
        refuse("the run's actor, triggering actor and event sender are not one account")
    if not is_authority(sender):
        refuse("only the deployment authority may dispatch a deployment; %s is not it" % sender["login"])
    return {"request": parse_request(event.get("inputs")), "dispatched_by": sender, "run_id": rid,
            "tooling_sha": sha, "run_url": rel.run_url(rid, 1).replace("/attempts/1", "")}


def check_environment(environment, branch_policies, approvals):
    """Same fail-closed reading as the release admission (ADR015 A1.4), for `production`."""
    if not isinstance(environment, dict) or environment.get("name") != ENVIRONMENT:
        refuse("the %s environment does not exist" % ENVIRONMENT)
    reviewer_ids = sorted({(r.get("reviewer") or {}).get("id")
                           for rule in environment.get("protection_rules") or [] if isinstance(rule, dict)
                           and rule.get("type") == "required_reviewers"
                           for r in rule.get("reviewers") or [] if isinstance(r, dict) and r.get("type") == "User"}
                          - {None})
    if not set(reviewer_ids) & set(DEPLOYMENT_AUTHORITY.values()):
        refuse("the %s environment does not require the deployment authority's review" % ENVIRONMENT)
    if environment.get("can_admins_bypass") is not False:
        refuse("administrators can bypass the %s environment's protection" % ENVIRONMENT)
    policy = environment.get("deployment_branch_policy")
    names = sorted((p.get("name"), p.get("type", "branch")) for p in (branch_policies or {}).get("branch_policies") or []
                   if isinstance(p, dict))
    if not isinstance(policy, dict) or policy.get("protected_branches") is not False \
            or policy.get("custom_branch_policies") is not True or names != [("main", "branch")]:
        refuse("the %s environment must accept deployments from the main branch only" % ENVIRONMENT)
    entries = [a for a in (approvals if isinstance(approvals, list) else [])
               if isinstance(a, dict) and any(isinstance(e, dict) and e.get("name") == ENVIRONMENT
                                              for e in a.get("environments") or [])]
    if any(a.get("state") != "approved" for a in entries):
        refuse("the run has a %s review that is not an approval" % ENVIRONMENT)
    approvers = [rel._account(a.get("user"), "the approving account") for a in entries]
    if not approvers:
        refuse("GitHub records no approval of this run in the %s environment" % ENVIRONMENT)
    if not all(is_authority(u) for u in approvers):
        refuse("the run was approved by an account that is not the deployment authority")
    return {"name": ENVIRONMENT, "id": environment.get("id"), "required_reviewer_ids": reviewer_ids,
            "approved_by": approvers[0]}


def build_context(dispatch, approval, exposure, packet_filter, external=None):
    """Non-secret references handed to deploy.sh; the VM validates their shape again."""
    for name, value in (("PUBLIC_EXPOSURE_AUTHORIZATION", exposure), ("PACKET_FILTER_VERIFICATION", packet_filter)):
        if not isinstance(value, str) or not value.strip() or "\n" in value or len(value) > 300:
            refuse("the production environment variable %s (a Leader record reference) is not set; the "
                   "deployment needs it and does not invent it" % name)
    who = approval["approved_by"]
    authorization = ("GitHub Deploy run %s: %s dispatched by %s (%d), approved in environment %s by %s (%d)"
                     % (dispatch["run_url"], dispatch["request"]["operation"], dispatch["dispatched_by"]["login"],
                        dispatch["dispatched_by"]["id"], ENVIRONMENT, who["login"], who["id"]))
    scan = ""
    if external:
        scan = "; outside scan by %s: open %s" % (dispatch["run_url"], ",".join(map(str, external.get("open_ports", []))))
    ctx = {"authorization": authorization, "public_exposure_authorization": exposure.strip(),
           "packet_filter_verification": (packet_filter.strip() + scan)[:500]}
    return ctx, base64.urlsafe_b64encode(json.dumps(ctx, sort_keys=True).encode("utf-8")).decode("ascii").rstrip("=")


def external_checks(origin, address, ports=PUBLIC_PORTS, timeout=8):
    """Evidence from outside the VM's network; it says nothing about features."""
    host = origin[len("https://"):]
    out = {"origin": origin, "address": address, "checked_at": rel.utc_now(), "problems": []}
    try:
        resolved = sorted({ai[4][0] for ai in socket.getaddrinfo(host, 443, socket.AF_INET)})
        out["dns_a"] = resolved
        if address not in resolved:
            out["problems"].append("%s does not resolve to %s" % (host, address))
    except OSError:
        out["problems"].append("%s does not resolve" % host)
    try:
        context = ssl.create_default_context()
        context.minimum_version = ssl.TLSVersion.TLSv1_2
        with socket.create_connection((address, 443), timeout=timeout) as raw:
            with context.wrap_socket(raw, server_hostname=host) as conn:
                der = conn.getpeercert(binary_form=True)
                info = conn.getpeercert()
        not_after = datetime.datetime.strptime(info["notAfter"], "%b %d %H:%M:%S %Y %Z")
        days = (not_after - datetime.datetime.utcnow()).days
        out["tls"] = {"trusted": True, "sha256": hashlib.sha256(der).hexdigest(), "not_after": info["notAfter"],
                      "days_left": days, "san": [v for k, v in info.get("subjectAltName", ()) if k == "DNS"]}
        if days < 14:
            out["problems"].append("the served certificate expires in %d days" % days)
    except (OSError, ssl.SSLError, ValueError) as exc:
        out["tls"] = {"trusted": False, "error": exc.__class__.__name__}
        out["problems"].append("no trusted TLS for %s at %s" % (host, address))
    try:
        req = urllib.request.Request(origin + "/", method="GET", headers={"User-Agent": "codemastery-deploy-check"})
        with urllib.request.urlopen(req, timeout=timeout * 2) as resp:
            out["https_root_status"] = resp.status
            if resp.status != 200:
                out["problems"].append("GET / answered %d" % resp.status)
    except Exception as exc:  # noqa: BLE001 - any failure is a finding
        out["https_root_status"] = None
        out["problems"].append("GET / failed (%s)" % exc.__class__.__name__)
    open_ports = []
    for port in ports:
        try:
            with socket.create_connection((address, port), timeout=3):
                open_ports.append(port)
        except OSError:
            pass
    out["open_ports"] = open_ports
    unexpected = [p for p in open_ports if p not in ALLOWED_OPEN]
    if unexpected:
        out["problems"].append("unexpected public TCP ports answer: %s" % ", ".join(map(str, unexpected)))
    return out


def render_report(dispatch, result, external):
    req = dispatch["request"] if dispatch else {}
    lines = ["## CodeMastery deployment: %s %s" % (req.get("operation", "?"), req.get("version", "")), ""]
    res = (result or {}).get("result", "NO_RESULT")
    lines.append("**Result: `%s`**" % res)
    lines.append("")
    if dispatch:
        lines.append("- Dispatched by `%s`; run %s; deployment tooling `%s`" % (
            dispatch["dispatched_by"]["login"], dispatch["run_url"], dispatch["tooling_sha"]))
    if result:
        for key in ("version", "source_revision", "image_store", "existing_state"):
            if key in result:
                lines.append("- %s: `%s`" % (key.replace("_", " "), result[key]))
        comp = result.get("compatibility")
        if comp:
            lines.append("- Schema: `%s`, applied V%03d, pending %s, deploy.sh deferral rule `%s`" % (
                comp.get("state"), comp.get("applied") or 0, comp.get("pending"), comp.get("deferral_rule", "?")))
        if result.get("preflight"):
            lines.append("- deploy.sh preflight: `%s`" % result["preflight"])
        cap = result.get("capacity")
        if cap:
            lines.append("- Capacity: required %s bytes; %s" % (cap.get("required_bytes"), json.dumps(cap.get("disk"))))
        cap_set = result.get("capture")
        if isinstance(cap_set, dict):
            lines += ["", "### Pre-update recovery point (held)", "",
                      "- Set: `%s`" % cap_set.get("set_id"), "- Manifest SHA-256: `%s`" % cap_set.get("manifest_sha256"),
                      "- Cut: `%s`" % cap_set.get("t_cut"), "", result.get("next", "")]
        gate = result.get("recovery_gate")
        if gate:
            lines.append("- Recovery gate: point `%s`, acknowledged `%s`" % (
                gate.get("set_id"), (gate.get("ack") or {}).get("level")))
        ver = result.get("verification")
        if ver:
            lines += ["", "### Verification (kept separate)", "",
                      "- Infrastructure: %d container checks, Flyway `%s`" % (
                          len(ver["infrastructure"].get("containers", {})), ver["infrastructure"].get("flyway")),
                      "- Runtime readiness: `%s`" % ver.get("readiness"),
                      "- Local TLS readback: `%s`" % (ver.get("tls") or {}).get("match"),
                      "- Feature behavior: `%s`" % ver.get("feature_behavior")]
        blockers = result.get("blockers") or []
        problems = (ver or {}).get("problems") or [] if ver else []
        if blockers or problems or result.get("reason"):
            lines += ["", "### Blockers and findings", ""]
            lines += ["- %s" % b for b in blockers + problems]
            if result.get("reason"):
                lines.append("- %s" % result["reason"])
        if result.get("log_tail"):
            lines += ["", "<details><summary>VM log tail</summary>", "", "```text"] + result["log_tail"] + \
                     ["```", "</details>"]
    if external:
        lines += ["", "### External checks (from the GitHub runner)", "",
                  "- TLS: `%s`" % json.dumps(external.get("tls")), "- GET /: `%s`" % external.get("https_root_status"),
                  "- Public TCP ports answering: `%s` (allowed: 22 only while the run's SSH gate is open, 443)"
                  % external.get("open_ports")]
        lines += ["- finding: %s" % p for p in external.get("problems", [])]
    lines += ["", "Infrastructure health, runtime readiness and external HTTPS are reported separately; none of "
              "them is application feature verification."]
    return "\n".join(lines) + "\n"


def main(argv=None):
    p = argparse.ArgumentParser(prog="cd.py")
    sub = p.add_subparsers(dest="cmd", required=True)
    s = sub.add_parser("authenticate")
    s.add_argument("--event", required=True)
    s.add_argument("--run-record", required=True)
    s.add_argument("--output", required=True)
    s = sub.add_parser("approval")
    s.add_argument("--environment", required=True)
    s.add_argument("--branch-policies", required=True)
    s.add_argument("--approvals", required=True)
    s.add_argument("--output", required=True)
    s = sub.add_parser("context")
    s.add_argument("--dispatch", required=True)
    s.add_argument("--approval", required=True)
    s.add_argument("--external")
    s.add_argument("--output", required=True)
    s = sub.add_parser("external")
    s.add_argument("--origin", default="https://codemastery.app")
    s.add_argument("--address", default="20.205.56.236")
    s.add_argument("--output", required=True)
    s = sub.add_parser("report")
    s.add_argument("--dispatch")
    s.add_argument("--result")
    s.add_argument("--external")
    a = p.parse_args(argv)
    try:
        if a.cmd == "authenticate":
            out = authenticate(rel.runner_env(), rel.load_json(a.event, "event"), rel.load_json(a.run_record, "run record"))
            rel.write_json(a.output, out)
            r = out["request"]
            print("Deployment request by %s: %s %s%s" % (out["dispatched_by"]["login"], r["operation"], r["version"],
                                                       " with recovery point %s" % r["recovery_set"]
                                                       if r["recovery_set"] else ""))
        elif a.cmd == "approval":
            out = check_environment(rel.load_json(a.environment, "environment"),
                                    rel.load_json(a.branch_policies, "branch policies"),
                                    rel.load_json(a.approvals, "approvals"))
            rel.write_json(a.output, out)
            print("Approved in the protected %s environment by %s." % (out["name"], out["approved_by"]["login"]))
        elif a.cmd == "context":
            ext = rel.load_json(a.external, "external checks") if a.external else None
            ctx, blob = build_context(rel.load_json(a.dispatch, "dispatch"), rel.load_json(a.approval, "approval"),
                                      os.environ.get("PUBLIC_EXPOSURE_AUTHORIZATION", ""),
                                      os.environ.get("PACKET_FILTER_VERIFICATION", ""), ext)
            rel.write_json(a.output, {"context": ctx, "encoded": blob})
        elif a.cmd == "external":
            out = external_checks(a.origin, a.address)
            rel.write_json(a.output, out)
            print(json.dumps(out, indent=2))
        elif a.cmd == "report":
            load = lambda path, what: rel.load_json(path, what) if path and os.path.exists(path) else None  # noqa
            sys.stdout.write(render_report(load(a.dispatch, "dispatch"), load(a.result, "result"),
                                           load(a.external, "external")))
    except rel.Refused as exc:
        print("REFUSED: %s" % exc, file=sys.stderr)
        return 2
    return 0


if __name__ == "__main__":
    sys.exit(main())
