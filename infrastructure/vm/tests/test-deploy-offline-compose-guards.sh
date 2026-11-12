#!/usr/bin/env bash
# CI regression test for job "VM deployment script" (deployment-script),
# step "Check offline Compose and provisioning safety guards" in .github/workflows/ci-vm.yml.
# Body moved verbatim from that step's former inline `run:` script.
# CI runs it from the repository root, where its relative paths resolve:
#   bash infrastructure/vm/tests/test-deploy-offline-compose-guards.sh
set -euo pipefail
script=infrastructure/vm/deploy.sh
bash -c '
  source "$1"
  mock_compose() { printf "container-id\\n"; }
  COMPOSE=(mock_compose)
  if output="$(require_application_stopped 2>&1)"; then
    echo "Provisioning must reject a running application service." >&2
    exit 1
  fi
  [[ "$output" == *"Application services are running"* ]]
  mock_compose() { :; }
  require_application_stopped
' _ "$script"
python3 - "$script" <<'PY'
import json
import subprocess
import sys

source = open(sys.argv[1], encoding="utf-8").read()
env_marker = "read -r -d '' PY_ENV_POLICY <<'PYEOF' || true\n"
marker = "read -r -d '' PY_CHECK <<'PYEOF' || true\n"
env_policy = source.split(env_marker, 1)[1].split("\nPYEOF", 1)[0]
check_policy = source.split(marker, 1)[1].split("\nPYEOF", 1)[0]
policy = env_policy + "\n" + check_policy
cases = {
    "cap_add": ["SYS_ADMIN"],
    "devices": ["/dev/sda:/dev/sda:rwm"],
    "security_opt": ["apparmor:unconfined"],
    "userns_mode": "host",
    "volumes_from": ["host-container"],
    "user": "0:0",
    "env_file": ["/tmp/unreviewed.env"],
    "pid": "host",
}
for option, value in cases.items():
    config = {"services": {"postgres": {"image": "postgres:17", option: value}}}
    result = subprocess.run(
        [sys.executable, "-c", policy, "https://example.invalid", "/dev/null"],
        input=json.dumps(config), text=True, capture_output=True, check=False,
    )
    if result.returncode == 0 or "unapproved privilege" not in result.stderr:
        raise SystemExit(f"Compose policy did not reject {option}")
config = {"services": {"backend": {"image": "backend:reviewed",
                                     "environment": {"LD_PRELOAD": "/tmp/unreviewed.so"}}}}
result = subprocess.run(
    [sys.executable, "-c", policy, "https://example.invalid", "/dev/null"],
    input=json.dumps(config), text=True, capture_output=True, check=False,
)
if result.returncode == 0 or "unapproved environment variables" not in result.stderr:
    raise SystemExit("Compose policy did not reject an unapproved environment variable")
# The release MinIO server must run with exactly MINIO_BROWSER=off.
console_problem = "minio must disable the embedded Console with MINIO_BROWSER=off"
for environment, accepted in (
    ({"MINIO_BROWSER": "off"}, True),
    ({}, False),
    ({"MINIO_BROWSER": "on"}, False),
    ({"MINIO_BROWSER": "OFF"}, False),
    ({"MINIO_BROWSER": "false"}, False),
    ({"MINIO_BROWSER": ""}, False),
):
    config = {"services": {"minio": {"image": "minio:reviewed", "environment": environment}}}
    result = subprocess.run(
        [sys.executable, "-c", policy, "https://example.invalid", "/dev/null"],
        input=json.dumps(config), text=True, capture_output=True, check=False,
    )
    if result.returncode == 0 or (console_problem not in result.stderr) != accepted:
        raise SystemExit(f"MinIO Console policy mismatch for {environment}")
# A config env file, credential files or Console settings could undo it.
for key in ("MINIO_CONFIG_ENV_FILE", "MINIO_ROOT_PASSWORD_FILE", "MINIO_BROWSER_REDIRECT_URL"):
    config = {"services": {"minio": {"image": "minio:reviewed",
                                     "environment": {"MINIO_BROWSER": "off", key: "/data/x"}}}}
    result = subprocess.run(
        [sys.executable, "-c", policy, "https://example.invalid", "/dev/null"],
        input=json.dumps(config), text=True, capture_output=True, check=False,
    )
    if (result.returncode == 0
            or "service 'minio' contains unapproved environment variables" not in result.stderr
            or "minio contains unapproved environment bindings" not in result.stderr):
        raise SystemExit(f"Compose policy did not reject MinIO environment {key}")
image_marker = "read -r -d '' PY_IMAGE_POLICY <<'PYEOF' || true\n"
image_policy = env_policy + "\n" + source.split(image_marker, 1)[1].split("\nPYEOF", 1)[0]
release_minio_env = {"MINIO_ROOT_USER": "x", "MINIO_ROOT_PASSWORD": "y", "MINIO_BROWSER": "off"}
for baked, accepted in (
    ([], True),
    (["MINIO_CONFIG_ENV_FILE=/etc/minio/config.env"], False),
    (["MINIO_BROWSER=on"], False),
    (["MINIO_ROOT_USER_FILE=access_key"], False),
):
    data = {"model": {"services": {"minio": {"environment": release_minio_env}}},
            "image": {"Entrypoint": ["/usr/bin/minio"],
                      "Env": ["PATH=/usr/bin", "SSL_CERT_FILE=/etc/ssl/certs/ca-certificates.crt"] + baked}}
    result = subprocess.run([sys.executable, "-c", image_policy, "minio"],
                            input=json.dumps(data), text=True, capture_output=True, check=False)
    if (result.returncode == 0) != accepted:
        raise SystemExit(f"MinIO image environment policy mismatch for {baked}")
for option, value in (
    ("pre_start", [{"command": ["sh", "-c", "true"]}]),
    ("post_start", [{"command": ["sh", "-c", "true"], "privileged": True}]),
    ("pre_stop", [{"command": ["sh", "-c", "true"]}]),
    ("post_stop", [{"command": ["sh", "-c", "true"]}]),
    ("deploy", {"replicas": 2, "privileged": True}),
    ("logging", {"driver": "syslog"}),
    ("tmpfs", ["/run"]),
    ("secrets", ["unreviewed"]),
    ("configs", ["unreviewed"]),
    ("develop", {"watch": [{"path": ".", "action": "sync"}]}),
):
    config = {"services": {"postgres": {"image": "postgres:17", option: value}}}
    result = subprocess.run(
        [sys.executable, "-c", policy, "https://example.invalid", "/dev/null"],
        input=json.dumps(config), text=True, capture_output=True, check=False,
    )
    if result.returncode == 0 or "unsupported Compose options" not in result.stderr:
        raise SystemExit(f"Compose policy did not reject unapproved {option}")
network_problem = "the Compose default network contains unapproved network options"
for network, approved in (
    ({"name": "codemastery_default", "ipam": {}}, True),
    ({"name": "codemastery_default"}, True),
    ({"name": "codemastery_default", "ipam": {"driver": "default"}}, False),
    ({"name": "codemastery_default", "ipam": {"config": [{"subnet": "10.231.0.0/24"}]}}, False),
    ({"name": "codemastery_default", "ipam": {"options": {"x": "y"}}}, False),
    ({"name": "codemastery_default", "attachable": True}, False),
):
    config = {"services": {}, "networks": {"default": network}}
    result = subprocess.run(
        [sys.executable, "-c", policy, "https://example.invalid", "/dev/null"],
        input=json.dumps(config), text=True, capture_output=True, check=False,
    )
    if (network_problem not in result.stderr) != approved:
        raise SystemExit(f"Default network policy mismatch for {network}")
# B2 publishes 443 without naming an interface; a declared host_ip must
# be a real non-loopback address. This proves nothing about firewalls.
host_ip_problems = ("loopback-only", "host_ip must be an IP address")
for host_ip, problem in (
    (None, None), ("0.0.0.0", None), ("203.0.113.10", None), ("2001:db8::10", None),
    ("127.0.0.1", "loopback-only"), ("127.0.0.2", "loopback-only"),
    ("::1", "loopback-only"), ("not-an-address", "host_ip must be an IP address"),
):
    port = {"target": 8080, "published": "443", "protocol": "tcp"}
    if host_ip:
        port["host_ip"] = host_ip
    config = {"services": {"frontend": {"image": "frontend:reviewed", "ports": [port]}}}
    result = subprocess.run(
        [sys.executable, "-c", policy, "https://example.invalid", "/dev/null"],
        input=json.dumps(config), text=True, capture_output=True, check=False,
    )
    found = [p for p in host_ip_problems if p in result.stderr]
    if found != ([problem] if problem else []) or "exactly host port 443" in result.stderr:
        raise SystemExit(f"host_ip policy mismatch for {host_ip}: {found}")
port_marker = "read -r -d '' PY_PORT_POLICY <<'PYEOF' || true\n"
port_policy = source.split(port_marker, 1)[1].split("\nPYEOF", 1)[0]
unspecified = [{"HostIp": "", "HostPort": "443"}]
dual_stack = [{"HostIp": "0.0.0.0", "HostPort": "443"}, {"HostIp": "::", "HostPort": "443"}]
def ports(service, host, actual, running=True, host_ip=None):
    port = {"target": 8080, "published": "443"}
    if host_ip:
        port["host_ip"] = host_ip
    data = {"model": {"services": {"frontend": {"ports": [port]}}},
            "container": {"host": host, "ports": actual, "running": running}}
    return subprocess.run([sys.executable, "-c", port_policy, service],
                          input=json.dumps(data), text=True, capture_output=True, check=False)
for name, service, host, actual, kwargs, accepted in (
    ("frontend dual-stack 443", "frontend", {"PortBindings": {"8080/tcp": unspecified}},
     {"8080/tcp": dual_stack, "80/tcp": None}, {}, True),
    ("frontend declared address", "frontend",
     {"PortBindings": {"8080/tcp": [{"HostIp": "203.0.113.10", "HostPort": "443"}]}},
     {"8080/tcp": [{"HostIp": "203.0.113.10", "HostPort": "443"}]}, {"host_ip": "203.0.113.10"}, True),
    ("frontend actual address differs", "frontend", {"PortBindings": {"8080/tcp": unspecified}},
     {"8080/tcp": dual_stack}, {"host_ip": "203.0.113.10"}, False),
    ("frontend extra published port", "frontend", {"PortBindings": {"8080/tcp": unspecified}},
     {"8080/tcp": dual_stack, "80/tcp": [{"HostIp": "0.0.0.0", "HostPort": "80"}]}, {}, False),
    ("frontend wrong host port", "frontend", {"PortBindings": {"8080/tcp": [{"HostIp": "", "HostPort": "8443"}]}},
     {"8080/tcp": [{"HostIp": "0.0.0.0", "HostPort": "8443"}]}, {}, False),
    ("frontend not running", "frontend", {"PortBindings": {"8080/tcp": unspecified}}, {}, {"running": False}, False),
    ("frontend publish-all", "frontend", {"PublishAllPorts": True, "PortBindings": {"8080/tcp": unspecified}},
     {"8080/tcp": dual_stack}, {}, False),
    ("backend private", "backend", {"PortBindings": {}}, {"8080/tcp": None}, {}, True),
    ("backend published", "backend", {"PortBindings": {"8080/tcp": unspecified}},
     {"8080/tcp": dual_stack}, {}, False),
    ("postgres host network", "postgres", {"NetworkMode": "host"}, {}, {}, False),
    ("minio publish-all", "minio", {"PublishAllPorts": True}, {}, {}, False),
):
    result = ports(service, host, actual, **kwargs)
    if (result.returncode == 0) != accepted:
        raise SystemExit(f"Published-port policy mismatch: {name}")
if ports("frontend", {"PortBindings": {"8080/tcp": unspecified}}, {"8080/tcp": dual_stack}).stdout.strip() \
        != "0.0.0.0:443, [::]:443":
    raise SystemExit("Published-port evidence summary is not recorded")
# Runtime hardening: exactly the reviewed values per service, nothing
# broader, and nothing missing.
pg_caps = ["CHOWN", "DAC_OVERRIDE", "FOWNER", "SETGID", "SETUID"]
reviewed = {
    "postgres": {"cap_add": pg_caps, "tmpfs": ["/var/run/postgresql"]},
    "minio": {},
    "minio-init": {"tmpfs": ["/tmp"]},
    "backend": {"tmpfs": ["/tmp"]},
    "frontend": {"tmpfs": ["/tmp"]},
}
hardening_problems = ("must run with exactly no-new-privileges", "unapproved privilege",
                      "unsupported Compose options")
def hardening(service, **changes):
    s = {"image": "reviewed:image", "read_only": True, "cap_drop": ["ALL"],
         "security_opt": ["no-new-privileges:true"], **reviewed[service]}
    for key, value in changes.items():
        if value is None:
            s.pop(key, None)
        else:
            s[key] = value
    result = subprocess.run(
        [sys.executable, "-c", policy, "https://example.invalid", "/dev/null"],
        input=json.dumps({"services": {service: s}}), text=True, capture_output=True, check=False,
    )
    return [p for p in hardening_problems if f"service '{service}' " in result.stderr and p in result.stderr]
for service in reviewed:
    if hardening(service):
        raise SystemExit(f"Reviewed runtime hardening rejected for {service}")
for name, service, changes in (
    ("missing no-new-privileges", "backend", {"security_opt": None}),
    ("no-new-privileges disabled", "minio-init", {"security_opt": ["no-new-privileges:false"]}),
    ("unconfined seccomp added", "frontend", {"security_opt": ["no-new-privileges:true", "seccomp=unconfined"]}),
    ("unconfined AppArmor added", "minio", {"security_opt": ["no-new-privileges:true", "apparmor=unconfined"]}),
    ("missing cap_drop", "minio", {"cap_drop": None}),
    ("partial cap_drop", "frontend", {"cap_drop": ["NET_RAW"]}),
    ("cap_add outside PostgreSQL", "backend", {"cap_add": ["NET_BIND_SERVICE"]}),
    ("extra PostgreSQL capability", "postgres", {"cap_add": pg_caps + ["SYS_ADMIN"]}),
    ("missing PostgreSQL capability", "postgres", {"cap_add": ["CHOWN", "FOWNER", "SETGID", "SETUID"]}),
    ("writable root filesystem", "postgres", {"read_only": False}),
    ("missing read_only", "backend", {"read_only": None}),
    ("extra tmpfs path", "backend", {"tmpfs": ["/tmp", "/run"]}),
    ("tmpfs with exec", "frontend", {"tmpfs": ["/tmp:exec"]}),
    ("missing tmpfs", "minio-init", {"tmpfs": None}),
    ("tmpfs on MinIO", "minio", {"tmpfs": ["/tmp"]}),
    ("tmpfs over PostgreSQL data", "postgres", {"tmpfs": ["/var/run/postgresql", "/var/lib/postgresql/data"]}),
):
    if not hardening(service, **changes):
        raise SystemExit(f"Compose policy accepted runtime hardening change: {name}")
PY
