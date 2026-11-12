#!/usr/bin/env bash
# CI regression test for job "VM deployment PostgreSQL and containment tests" (deployment-script-postgres),
# step "Check the PostgreSQL image is byte-clean and equivalent to its layered build" in .github/workflows/ci-vm.yml.
# Body moved verbatim from that step's former inline `run:` script.
# CI runs it from the repository root, where its relative paths resolve:
#   bash infrastructure/vm/tests/test-postgres-image-layer-equivalence.sh
set -euo pipefail
# infrastructure/postgres ships the layered stage's merged filesystem as one
# layer (ADR003 amendment): same files, owners, modes and configuration, and no
# layer that still holds the upstream gosu.
flat=codemastery-ci/postgres:flat
layered=codemastery-ci/postgres:layered
work="$(mktemp -d)"
cleanup() { docker image rm "$flat" "$layered" >/dev/null 2>&1 || true; rm -rf "$work"; }
trap cleanup EXIT
docker build -q -t "$flat" infrastructure/postgres >/dev/null
docker build -q --target layered -t "$layered" infrastructure/postgres >/dev/null
docker image inspect --format '{{json .Config}}' "$flat" > "$work/flat.config"
docker image inspect --format '{{json .Config}}' "$layered" > "$work/layered.config"
for image in flat layered; do
  ref="$flat"; [[ $image == layered ]] && ref="$layered"
  cid="$(docker create "$ref")"
  docker export -o "$work/$image.export.tar" "$cid"
  docker rm -v "$cid" >/dev/null
done
docker save -o "$work/flat.image.tar" "$flat"
python3 - "$work" <<'PY'
import gzip, hashlib, io, json, sys, tarfile
work = sys.argv[1]
flat, layered = (json.load(open("%s/%s.config" % (work, n))) for n in ("flat", "layered"))
build_only = {"GOSU_VERSION", "DOCKER_PG_LLVM_DEPS"}
env = [e for e in layered["Env"] if e.split("=", 1)[0] not in build_only]
assert len(env) == len(layered["Env"]) - 2, layered["Env"]
assert flat["Env"] == env, (flat["Env"], env)
for key in ("User", "Entrypoint", "Cmd", "Volumes", "ExposedPorts", "StopSignal", "WorkingDir",
            "Labels", "Healthcheck", "OnBuild", "Shell"):
    assert flat.get(key) == layered.get(key), (key, flat.get(key), layered.get(key))
assert flat["Entrypoint"] == ["codemastery-postgres-entrypoint"] and flat["Cmd"] == ["postgres"]
print("ok: configuration equals the layered build except GOSU_VERSION and DOCKER_PG_LLVM_DEPS")

runtime = {".dockerenv", "dev", "proc", "sys", "etc/hostname", "etc/hosts", "etc/mtab", "etc/resolv.conf"}
def inventory(path):  # path -> (type, mode, owner, link target, content hash); hard links resolved
    out, hard = {}, {}
    with tarfile.open(path, mode="r|") as t:
        for m in t:
            name = (m.name[2:] if m.name.startswith("./") else m.name).strip("/")
            if not name or name in runtime or name.startswith(("dev/", "proc/", "sys/")):
                continue
            if m.islnk():
                hard[name] = (m.linkname[2:] if m.linkname.startswith("./") else m.linkname).strip("/")
                continue
            sha = hashlib.sha256(t.extractfile(m).read()).hexdigest() if m.isreg() else None
            out[name] = (m.type.decode(), m.mode & 0o7777, m.uid, m.gid, m.linkname if m.issym() else "", sha,
                         tuple(sorted((k, v) for k, v in m.pax_headers.items() if k.startswith("SCHILY.xattr."))))
    for name, target in hard.items():
        out[name] = out[target]
    return out
a, b = inventory(work + "/flat.export.tar"), inventory(work + "/layered.export.tar")
diff = sorted(set(a) ^ set(b)) or sorted(k for k in a if a[k] != b[k])
assert not diff, "merged filesystems differ at %s: %s / %s" % (diff[0], a.get(diff[0]), b.get(diff[0]))
assert a["usr/local/bin/gosu"][4] == "/sbin/su-exec", a["usr/local/bin/gosu"]
print("ok: merged filesystem equals the layered build (%d entries: content, owners, modes, links, xattrs)" % len(a))

gosu_sha = "52c8749d0142edd234e9d6bd5237dff2d81e71f43537e2f4f66f75dd4b243dd0"
with tarfile.open(work + "/flat.image.tar") as o:
    manifest = json.load(o.extractfile("manifest.json"))
    layers = manifest[0]["Layers"]
    assert len(layers) == 1, "expected one layer, found %d" % len(layers)
    data = o.extractfile(layers[0]).read()
if data[:2] == b"\x1f\x8b":
    data = gzip.decompress(data)
with tarfile.open(fileobj=io.BytesIO(data)) as t:
    for m in t:
        assert ".wh." not in m.name, m.name
        if m.isreg():
            body = t.extractfile(m).read()
            assert hashlib.sha256(body).hexdigest() != gosu_sha and b"github.com/tianon/gosu" not in body, m.name
print("ok: one layer, no whiteout, and no file holding the upstream gosu")
PY
