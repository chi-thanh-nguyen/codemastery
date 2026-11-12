#!/usr/bin/env bash
# CI regression test for job "VM deployment script" (deployment-script),
# step "Check release archive identity models" in .github/workflows/ci-vm.yml.
# Body moved verbatim from that step's former inline `run:` script.
# CI runs it from the repository root, where its relative paths resolve:
#   bash infrastructure/vm/tests/test-deploy-release-archive-identity.sh
set -euo pipefail
script=infrastructure/vm/deploy.sh
work="$(mktemp -d)"
trap 'rm -rf "$work"' EXIT
# Synthetic layouts shaped like Docker's own saves. With the containerd
# image store a registry image is a multi-platform index whose other
# platforms are absent from the archive, with in-toto attestation
# manifests; --platform saves add those attestations as top-level
# referrers. The classic store identifies images by config digest.
python3 - "$work" <<'PY'
import copy, hashlib, io, json, sys, tarfile

work = sys.argv[1]
INDEX = "application/vnd.oci.image.index.v1+json"
MANIFEST = "application/vnd.oci.image.manifest.v1+json"
CONFIG = "application/vnd.oci.image.config.v1+json"
LAYER = "application/vnd.oci.image.layer.v1.tar+gzip"
IN_TOTO = "application/vnd.in-toto+json"
NAME, REF, SUBJECT = "io.containerd.image.name", "org.opencontainers.image.ref.name", "io.containerd.manifest.subject"

def put(blobs, document):
    data = json.dumps(document, sort_keys=True).encode()
    digest = "sha256:" + hashlib.sha256(data).hexdigest()
    blobs[digest] = data
    return {"digest": digest, "size": len(data)}

def attestation(blobs, subject, config_platform=("unknown", "unknown"), layer_type=IN_TOTO):
    statement = put(blobs, {"_type": "https://in-toto.io/Statement/v1",
                            "subject": [{"digest": {"sha256": subject[7:]}}]})
    config = put(blobs, {"os": config_platform[0], "architecture": config_platform[1],
                         "config": {}, "rootfs": {"type": "layers", "diff_ids": []}})
    return put(blobs, {"schemaVersion": 2, "mediaType": MANIFEST,
                       "config": dict(config, mediaType=CONFIG),
                       "layers": [dict(statement, mediaType=layer_type)]})

def image(tag, platforms=(("amd64", None), ("arm64", "v8")), present=("amd64",),
          attest=True, attestation_platform=("unknown", "unknown"), nested=False):
    saved, children, out = {}, [], {"tag": tag}
    for arch, variant in platforms:
        blobs = {}
        config = put(blobs, {"architecture": arch, "os": "linux", "fixture": tag,
                             "config": {"Cmd": ["/payload"]}, "rootfs": {"type": "layers", "diff_ids": []}})
        manifest = put(blobs, {"schemaVersion": 2, "mediaType": MANIFEST, "layers": [],
                               "config": dict(config, mediaType=CONFIG)})
        platform = {"os": "linux", "architecture": arch}
        if variant:
            platform["variant"] = variant
        children.append(dict(manifest, platform=platform,
                             mediaType=INDEX if nested and arch == "amd64" else MANIFEST))
        if attest:
            statement = attestation(blobs, manifest["digest"])
            children.append(dict(statement, mediaType=MANIFEST,
                                 platform={"os": attestation_platform[0], "architecture": attestation_platform[1]},
                                 annotations={"vnd.docker.reference.type": "attestation-manifest",
                                              "vnd.docker.reference.digest": manifest["digest"]}))
            out.setdefault("attestations", {})[arch] = statement
        if arch in present:
            saved.update(blobs)
            out.setdefault("config", config["digest"])
        out.setdefault("manifests", {})[arch] = manifest
    out["index"] = put(saved, {"schemaVersion": 2, "mediaType": INDEX, "manifests": children})
    out["blobs"] = saved
    return out

def default_tops(images):
    return [dict(i["index"], mediaType=INDEX,
                 annotations={NAME: "docker.io/library/" + i["tag"], REF: i["tag"].split(":")[1]})
            for i in images]

def platform_tops(images):
    tops = []
    for i in images:
        tops.append(dict(i["manifests"]["amd64"], mediaType=MANIFEST,
                         annotations={NAME: "docker.io/library/" + i["tag"], REF: i["tag"].split(":")[1]}))
        tops.append(dict(i["attestations"]["amd64"], mediaType=MANIFEST,
                         annotations={SUBJECT: i["manifests"]["amd64"]["digest"]}))
    return tops

def items(images):
    return [{"Config": "blobs/sha256/" + i["config"][7:], "RepoTags": [i["tag"]], "Layers": []}
            for i in images]

def archive(name, images, tops, entries, blobs=None, tamper=False, duplicate=False):
    everything = dict(blobs or {})
    for i in images:
        everything.update(i["blobs"])
    with tarfile.open(f"{work}/{name}.tar", "w") as t:
        def add(path, data):
            info = tarfile.TarInfo(path)
            info.size = len(data)
            t.addfile(info, io.BytesIO(data))
        for key, data in sorted(everything.items()):
            if tamper and key == images[0]["index"]["digest"]:
                data = data.replace(b'"schemaVersion": 2', b'"schemaVersion":  2')
            add("blobs/sha256/" + key[7:], data)
        if duplicate:
            add("blobs/sha256/" + images[0]["config"][7:], everything[images[0]["config"]])
        add("index.json", json.dumps({"schemaVersion": 2, "mediaType": INDEX, "manifests": tops}).encode())
        add("manifest.json", json.dumps(entries).encode())

backend, frontend = image("backend:r1"), image("frontend:r1")
release = [backend, frontend]
archive("registry", release, default_tops(release), items(release))
archive("registry-platform", release, platform_tops(release), items(release))
built = [image("backend:r1", platforms=(("amd64", None),)), image("frontend:r1", platforms=(("amd64", None),))]
archive("built", built, default_tops(built), items(built))
plain = [image("backend:r1", attest=False), image("frontend:r1", attest=False)]
archive("unattested", plain, default_tops(plain), items(plain))
nameless = copy.deepcopy(default_tops(release))
for top in nameless:
    del top["annotations"]
unnamed = items(release)
for entry in unnamed:
    entry["RepoTags"] = None
archive("nameless", release, nameless, unnamed)

variants = {}
def variant(name, **options):
    # Each case is checked against its own identities, so a rejection
    # cannot come from an unrelated identity mismatch.
    variants[name] = image("backend:r1", **options)
    images = [variants[name], frontend]
    archive(name, images, default_tops(images), items(images))
variant("ambiguous-amd64", platforms=(("amd64", None), ("amd64", "v3")))
variant("amd64-variant", platforms=(("amd64", "v3"), ("arm64", "v8")))
variant("amd64-absent", present=("arm64",))
variant("arm64-only", platforms=(("arm64", "v8"),), present=("arm64",))
variant("attestation-platform", attestation_platform=("linux", "amd64"))
variant("nested-index", nested=True)
tops = default_tops(release); tops[0]["size"] += 1
archive("size-mismatch", release, tops, items(release))
tops = default_tops(release); tops[0]["mediaType"] = "application/vnd.example.unknown+json"
archive("media-type", release, tops, items(release))

def referrer(name, change, blobs=None):
    tops = platform_tops(release)
    change(tops[1])
    archive(name, release, tops, items(release), blobs=blobs)
referrer("referrer-unknown-subject",
         lambda top: top["annotations"].update({SUBJECT: "sha256:" + "1" * 64}))
referrer("referrer-other-platform",
         lambda top: top["annotations"].update({SUBJECT: backend["manifests"]["arm64"]["digest"]}))
referrer("referrer-named", lambda top: top["annotations"].update({NAME: "docker.io/library/backend:r1"}))
extra_blobs = {}
runnable = attestation(extra_blobs, backend["manifests"]["amd64"]["digest"], layer_type=LAYER)
referrer("referrer-runnable-layers", lambda top: top.update(runnable), extra_blobs)
linux_blobs = {}
linux = attestation(linux_blobs, backend["manifests"]["amd64"]["digest"], config_platform=("linux", "amd64"))
referrer("referrer-linux-config", lambda top: top.update(linux), linux_blobs)

extra = image("extra:r1")
archive("orphan-index", release + [extra], default_tops(release + [extra]), items(release))
archive("unexpected", release + [extra], default_tops(release + [extra]), items(release + [extra]))
tops = default_tops(release); tops[0]["annotations"][NAME] = "docker.io/library/frontend:r1"
archive("name-mismatch", release, tops, items(release))
entries = items(release); entries[0]["RepoTags"] = ["backend:latest"]
archive("unapproved-repotag", release, default_tops(release), entries)
archive("tampered", release, default_tops(release), items(release), tamper=True)
archive("duplicate", release, default_tops(release), items(release), duplicate=True)

ids = {}
for key, item in [("backend", backend), ("frontend", frontend), ("extra", extra),
                  ("built-backend", built[0]), ("built-frontend", built[1]),
                  ("plain-backend", plain[0]), ("plain-frontend", plain[1])] + list(variants.items()):
    ids[key] = {"index": item["index"]["digest"], "config": item["config"],
                "manifest": item["manifests"].get("amd64", {}).get("digest", "")}
json.dump(ids, open(f"{work}/ids.json", "w"))
PY
id() { python3 -c 'import json,sys; print(json.load(open(sys.argv[1]))[sys.argv[2]][sys.argv[3]])' "$work/ids.json" "$1" "$2"; }
archive_check() {  # expected-result store archive [sha256|-] REF=ID...
  local want="$1" store="$2" name="$3" archive="$work/$3.tar" sha="$4" result=accepted
  shift 4
  [[ "$sha" != - ]] || sha="$(sha256sum "$archive" | awk '{print $1}')"
  if ! bash -c '
    source "$1"; IMAGE_STORE="$2"; ARCHIVE="$3"; ARCHIVE_SHA256="$4"; shift 4
    EXPECT_IMAGES=("$@"); RELEASE_IMAGES="$(printf "%s\n" "${EXPECT_IMAGES[@]%%=*}")"
    verify_archive' _ "$script" "$store" "$archive" "$sha" "$@" >/dev/null 2>&1; then
    result=rejected
  fi
  echo "$result: ${store:-<none>} $name"
  [[ "$result" == "$want" ]] || { echo "Expected $want for ${store:-<none>} $name." >&2; exit 1; }
}
be_index="backend:r1=$(id backend index)"; fe_index="frontend:r1=$(id frontend index)"
be_config="backend:r1=$(id backend config)"; fe_config="frontend:r1=$(id frontend config)"
be_manifest="backend:r1=$(id backend manifest)"; fe_manifest="frontend:r1=$(id frontend manifest)"
# Registry multi-platform indexes, as saved by the containerd store.
archive_check accepted containerd registry - "$be_index" "$fe_index"
archive_check accepted containerd registry-platform - "$be_manifest" "$fe_manifest"
archive_check accepted containerd built - "backend:r1=$(id built-backend index)" "frontend:r1=$(id built-frontend index)"
archive_check accepted containerd unattested - "backend:r1=$(id plain-backend index)" "frontend:r1=$(id plain-frontend index)"
archive_check accepted containerd nameless - "$be_index" "$fe_index"
archive_check accepted containerd registry - "backend:r1@$(id backend index)=$(id backend index)" "$fe_index"
archive_check accepted graphdriver registry - "$be_config" "$fe_config"
# Store-specific identity kinds, checksum and mapping.
archive_check rejected containerd registry - "$be_config" "$fe_config"
archive_check rejected graphdriver registry - "$be_index" "$fe_index"
archive_check rejected containerd registry-platform - "$be_index" "$fe_index"
archive_check rejected containerd registry "$(printf '0%.0s' {1..64})" "$be_index" "$fe_index"
archive_check rejected containerd registry - "backend:r1=$(id extra index)" "$fe_index"
archive_check rejected containerd registry - "$be_index"
archive_check rejected containerd registry - "$be_index" "$fe_index" "extra:r1=$(id extra index)"
archive_check rejected containerd registry - "backend:r1=$(id frontend index)" "frontend:r1=$(id backend index)"
archive_check rejected graphdriver registry - "backend:r1=$(id frontend config)" "frontend:r1=$(id backend config)"
archive_check rejected "" registry - "$be_config" "$fe_config"
# Platform selection and digest-chain integrity.
for case in ambiguous-amd64 amd64-variant amd64-absent arm64-only attestation-platform nested-index; do
  archive_check rejected containerd "$case" - "backend:r1=$(id "$case" index)" "$fe_index"
done
archive_check rejected graphdriver arm64-only - "backend:r1=$(id arm64-only config)" "$fe_config"
for case in size-mismatch media-type tampered duplicate; do
  archive_check rejected containerd "$case" - "$be_index" "$fe_index"
done
# Referrers, extra images and names applied by the archive.
for case in referrer-unknown-subject referrer-other-platform referrer-named \
            referrer-runnable-layers referrer-linux-config; do
  archive_check rejected containerd "$case" - "$be_manifest" "$fe_manifest"
done
for case in orphan-index unexpected name-mismatch unapproved-repotag; do
  archive_check rejected containerd "$case" - "$be_index" "$fe_index"
done
archive_check rejected graphdriver unapproved-repotag - "$be_config" "$fe_config"
