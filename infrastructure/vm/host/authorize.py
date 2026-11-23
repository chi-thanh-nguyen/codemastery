#!/usr/bin/env python3
"""Fixed, operator-installed SSH authorization verifier; never loaded from uploaded tooling.

The SSH key authenticates transport only. Each cmdeploy command additionally needs a
GitHub-signed OIDC token whose audience binds its exact argv. Tokens travel on stdin,
expire in minutes and are consumed once. Only public GitHub metadata is fetched here;
no image, executable, package, permanent service or cloud credential is added.
"""
import base64
import hashlib
import json
import os
import re
import ssl
import subprocess
import sys
import tempfile
import time
import urllib.request

ISSUER = "https://token.actions.githubusercontent.com"
REPOSITORY = "chi-thanh-nguyen/codemastery"
REPOSITORY_ID = "1391772534"
OWNER_ID = "212181152"
WORKFLOW = REPOSITORY + "/.github/workflows/deploy.yml@refs/heads/main"
STATE = "/var/lib/codemastery/authorization"


class Refused(Exception):
    pass


def require(ok, message):
    if not ok:
        raise Refused(message)


def audience(argv):
    data = json.dumps(argv, separators=(",", ":"), ensure_ascii=True).encode("ascii")
    return "codemastery:ssh:" + hashlib.sha256(data).hexdigest()


def unique(pairs):
    result = {}
    for k, v in pairs:
        require(k not in result, "duplicate JSON key")
        result[k] = v
    return result


def unbase(value):
    require(isinstance(value, str) and re.fullmatch(r"[A-Za-z0-9_-]+", value), "invalid JWT encoding")
    return base64.urlsafe_b64decode(value + "=" * (-len(value) % 4))


def get_json(url):
    # All URLs are constructed here, never taken from the JWT (including jku/x5u).
    context = ssl.create_default_context()
    context.minimum_version = ssl.TLSVersion.TLSv1_2
    request = urllib.request.Request(url, headers={"User-Agent": "codemastery-ssh-authorizer"})
    with urllib.request.urlopen(request, context=context, timeout=20) as response:
        require(response.url == url, "authorization metadata redirected")
        raw = response.read((1 << 20) + 1)
    require(len(raw) <= 1 << 20, "authorization metadata too large")
    return json.loads(raw, object_pairs_hook=unique)


def der(tag, data):
    size = len(data)
    length = bytes([size]) if size < 128 else bytes([0x80 + (size.bit_length() + 7) // 8]) + size.to_bytes(
        (size.bit_length() + 7) // 8, "big")
    return bytes([tag]) + length + data


def rsa_pem(jwk):
    require(jwk.get("kty") == "RSA" and jwk.get("alg") == "RS256" and jwk.get("use") == "sig",
            "unsupported signing key")
    integers = []
    for k in ("n", "e"):
        value = unbase(jwk[k]).lstrip(b"\x00")
        require(bool(value), "empty RSA parameter")
        if k == "n":
            require(len(value) >= 256, "RSA key too small")
        integers.append(der(2, (b"\x00" if value[0] & 0x80 else b"") + value))
    rsa = der(0x30, b"".join(integers))
    algorithm = bytes.fromhex("300d06092a864886f70d0101010500")
    encoded = base64.b64encode(der(0x30, algorithm + der(3, b"\x00" + rsa)))
    return b"-----BEGIN PUBLIC KEY-----\n" + b"\n".join(encoded[i:i+64] for i in range(0, len(encoded), 64)) + \
        b"\n-----END PUBLIC KEY-----\n"


def verify(token, argv, fetch=get_json, now=None):
    require(isinstance(token, str) and 0 < len(token) <= 16384, "missing GitHub OIDC command authorization")
    parts = token.split(".")
    require(len(parts) == 3, "malformed GitHub OIDC token")
    header, claims = (json.loads(unbase(p), object_pairs_hook=unique) for p in parts[:2])
    require(isinstance(header, dict) and header.get("alg") == "RS256" and header.get("typ") == "JWT"
            and not header.get("crit"), "unsupported JWT header")
    keys = fetch(ISSUER + "/.well-known/jwks")["keys"]
    matches = [k for k in keys if k.get("kid") == header.get("kid")]
    require(len(matches) == 1, "GitHub signing key not found")
    with tempfile.TemporaryDirectory(prefix="cm-oidc-") as d:
        key, signature = os.path.join(d, "key.pem"), os.path.join(d, "signature")
        with open(key, "wb") as f:
            f.write(rsa_pem(matches[0]))
        with open(signature, "wb") as f:
            f.write(unbase(parts[2]))
        result = subprocess.run(["openssl", "dgst", "-sha256", "-verify", key, "-signature", signature],
                                input=(parts[0] + "." + parts[1]).encode("ascii"),
                                stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)
        require(result.returncode == 0, "GitHub OIDC signature does not verify")
    require(isinstance(claims, dict), "invalid JWT claims")
    expected = {"iss": ISSUER, "aud": audience(argv), "repository": REPOSITORY,
                "repository_id": REPOSITORY_ID, "repository_owner_id": OWNER_ID,
                "actor": "chi-thanh-nguyen", "actor_id": OWNER_ID, "environment": "production",
                "event_name": "workflow_dispatch", "ref": "refs/heads/main", "ref_type": "branch",
                "workflow_ref": WORKFLOW, "run_attempt": "1", "runner_environment": "github-hosted"}
    for key, value in expected.items():
        require(claims.get(key) == value, "GitHub OIDC claim rejected: " + key)
    now = time.time() if now is None else now
    require(all(type(claims.get(k)) is int for k in ("exp", "iat", "nbf")), "missing token lifetime")
    require(claims["nbf"] <= now < claims["exp"] and claims["iat"] <= now
            and 0 < claims["exp"] - claims["iat"] <= 600, "GitHub OIDC token is expired or not yet valid")
    sha = claims.get("sha", "")
    require(re.fullmatch(r"[0-9a-f]{40}", sha) and claims.get("workflow_sha") == sha, "workflow/source SHA mismatch")
    require(re.fullmatch(r"[1-9][0-9]*", claims.get("run_id", "")), "invalid run identity")
    require(isinstance(claims.get("jti"), str) and 0 < len(claims["jti"]) <= 200, "missing token ID")
    if argv[0] == "install-tooling":
        require(len(argv) == 3 and argv[1] == sha, "tooling must be the authorized workflow commit")
        commit = fetch("https://api.github.com/repos/" + REPOSITORY + "/git/commits/" + sha)
        require(commit.get("sha") == sha and (commit.get("tree") or {}).get("sha") == argv[2],
                "tooling tree is not GitHub's authorized commit tree")
    return claims


def consume(claims, directory=STATE):
    os.makedirs(directory, mode=0o700, exist_ok=True)
    # O_EXCL is the cross-process replay barrier. Keep IDs after expiry; pruning is
    # an operator maintenance task, never authorization based on a caller's clock.
    name = hashlib.sha256(claims["jti"].encode("utf-8")).hexdigest()
    try:
        fd = os.open(os.path.join(directory, name), os.O_WRONLY | os.O_CREAT | os.O_EXCL, 0o600)
    except FileExistsError:
        raise Refused("GitHub OIDC command token was already consumed (replay)")
    with os.fdopen(fd, "w") as f:
        json.dump({k: claims[k] for k in ("run_id", "sha", "exp")}, f)
        f.flush()
        os.fsync(f.fileno())
    fd = os.open(directory, os.O_RDONLY | os.O_DIRECTORY)
    try:
        os.fsync(fd)
    finally:
        os.close(fd)


def request_token(argv):
    import urllib.parse
    url, bearer = os.environ.get("ACTIONS_ID_TOKEN_REQUEST_URL"), os.environ.get("ACTIONS_ID_TOKEN_REQUEST_TOKEN")
    require(url and bearer and url.startswith("https://"), "no GitHub OIDC endpoint; use the operator fallback outside Actions")
    request = urllib.request.Request(url + "&audience=" + urllib.parse.quote(audience(argv), safe=""),
                                     headers={"Authorization": "bearer " + bearer})
    with urllib.request.urlopen(request, timeout=20) as response:
        token = json.load(response)["value"]
    require(isinstance(token, str) and re.fullmatch(r"[A-Za-z0-9_.-]+", token) and len(token) <= 16384,
            "invalid OIDC response")
    return token


def main():
    try:
        mode, argv = sys.argv[1], sys.argv[2:]
        require(bool(argv), "missing command")
        if mode == "request":
            print(request_token(argv))
        else:
            require(mode == "verify", "unknown authorization mode")
            token = sys.stdin.buffer.read(16385).decode("ascii").strip()
            consume(verify(token, argv))
        return 0
    except (Refused, ValueError, KeyError, TypeError, OSError) as exc:
        # Never include the JWT, untrusted claim values, or endpoint bearer in diagnostics.
        print("AUTHORIZATION REFUSED: " + (str(exc) if isinstance(exc, Refused) else type(exc).__name__), file=sys.stderr)
        return 2


if __name__ == "__main__":
    sys.exit(main())
