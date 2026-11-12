#!/usr/bin/env bash
# CI regression test for job "VM HTTPS ingress" (deployment-ingress),
# step "Check the release image CA store against the deployed public chain" in .github/workflows/ci-vm.yml.
# Body moved verbatim from that step's former inline `run:` script.
# CI runs it from the repository root, where its relative paths resolve:
#   bash infrastructure/vm/tests/test-ingress-release-ca-store.sh
set -euo pipefail
# The served fullchain.pem shape reported for the VM, rebuilt from public
# sources only (CT log and the issuers' AIA URLs; no key material):
#   leaf     e33d698a2f76433a9ef4540392f519baf7e10ecfaf1608fd10a825d6f4855152
#   YE2      97658de8c68dfa98ace1e5028a63d54a1aae911b3e21471076c6850cd08cbab4
#   Root YE  0fc0901cca2bae9e9fdbb02d50d02f1094f7b36672086991b9e897626dc485f0 (cross-signed by ISRG Root X2)
#   Root X2  ee5f7abd6981bb0255632cd8f49283451b4b18844d12040b44ee00f07b8fe2c6 (cross-signed by ISRG Root X1)
# curl in the release image does not fetch missing issuers, so its baked
# healthcheck can trust this chain only through the image CA store and the
# served cross-sign. A fixed verification time keeps the check deterministic.
host=codemastery.eastasia.cloudapp.azure.com
at=1794700800  # 2026-11-15T00:00:00Z, inside every certificate's validity
work="$(mktemp -d)"; trap 'rm -rf "$work"' EXIT
docker run --rm --entrypoint cat "$FRONTEND_RELEASE_IMAGE" /etc/ssl/certs/ca-certificates.crt > "$work/image-ca.pem"
cat > "$work/leaf.pem" <<'PEM'
-----BEGIN CERTIFICATE-----
MIIDuzCCA0KgAwIBAgISBYtYw9pkPnIgNMvm5V8VU6I0MAoGCCqGSM49BAMDMDMx
CzAJBgNVBAYTAlVTMRYwFAYDVQQKEw1MZXQncyBFbmNyeXB0MQwwCgYDVQQDEwNZ
RTIwHhcNMjYxMDMwMjMxMjU3WhcNMjcwMTI4MjMxMjU2WjAyMTAwLgYDVQQDEydj
b2RlbWFzdGVyeS5lYXN0YXNpYS5jbG91ZGFwcC5henVyZS5jb20wWTATBgcqhkjO
PQIBBggqhkjOPQMBBwNCAARLndkVmg0qTNUpY9ncbGbezUPrPjZvdkZ2pprBlmj8
0HK0QD90qoe5fC520b8E7oPC1ozwI6h8WUVu3TI1J2z6o4ICNTCCAjEwDgYDVR0P
AQH/BAQDAgeAMBMGA1UdJQQMMAoGCCsGAQUFBwMBMAwGA1UdEwEB/wQCMAAwHQYD
VR0OBBYEFM5n29lRSBkyvC0WoK6dkYARUskfMB8GA1UdIwQYMBaAFLlZ8o7PIvCG
0zdI/3YUGLqC2FWHMDMGCCsGAQUFBwEBBCcwJTAjBggrBgEFBQcwAoYXaHR0cDov
L3llMi5pLmxlbmNyLm9yZy8wMgYDVR0RBCswKYInY29kZW1hc3RlcnkuZWFzdGFz
aWEuY2xvdWRhcHAuYXp1cmUuY29tMBMGA1UdIAQMMAowCAYGZ4EMAQIBMC4GA1Ud
HwQnMCUwI6AhoB+GHWh0dHA6Ly95ZTIuYy5sZW5jci5vcmcvNTMuY3JsMIIBDAYK
KwYBBAHWeQIEAgSB/QSB+gD4AHcATGPcmOWcHauI9h6KPd6uj6tEozd7X5uUw/uh
nPzBviYAAAGhj0wK9gAABAMASDBGAiEAuJTQaOgDTJCdcanXc9BeFaOciDcbXwd2
RROIDpnY2K8CIQCnui2KUeaY4rFENYwvJ759TBeiOSQZUH3p1H+LsUZchAB9AKKB
ABhzThduHUfglUDzgbpUZpfNY6hDUHFuuAlO2vENAAABoY9MDm8ACAAABQAzE4Hl
BAMARjBEAiAk+pCJWIYQGlfHPrX92tPH1MzK9wrrkwmDi0dszkHiWwIgSI70O0MT
1mi94yfNlhkyGQgUjjfVvo0RwSNXTcsPKcYwCgYIKoZIzj0EAwMDZwAwZAIwWKPe
oMTSZWSjpzsW4AYnqRJV/cOdzMjP63Dx/CPS4/5pZWJyHu0QUeQzyjSZ8kc0AjAO
woVAVDJSub8gBMKeb39Gb60UxlGN1nXol2jnZu7CFKDdVkmwQe0DrQYt+hoDoPQ=
-----END CERTIFICATE-----
PEM
cat > "$work/chain.pem" <<'PEM'
-----BEGIN CERTIFICATE-----
MIICjDCCAhGgAwIBAgIQTfOxXdbAeExQfNN7WObxFTAKBggqhkjOPQQDAzAuMQsw
CQYDVQQGEwJVUzENMAsGA1UEChMESVNSRzEQMA4GA1UEAxMHUm9vdCBZRTAeFw0y
NTA5MDMwMDAwMDBaFw0yODA5MDIyMzU5NTlaMDMxCzAJBgNVBAYTAlVTMRYwFAYD
VQQKEw1MZXQncyBFbmNyeXB0MQwwCgYDVQQDEwNZRTIwdjAQBgcqhkjOPQIBBgUr
gQQAIgNiAARxmrQzkdbEEL3MqXt3dJQttYc47axkdDTHud5TPqM2z5uSD5cmk0Wr
HlWXvnlvqBLqiB34kluxIbmMyAiq3/YD6e80/vV259K8XQIdjFXloYOa0mIU71f7
HQ09PvYDlw+jge4wgeswDgYDVR0PAQH/BAQDAgGGMBMGA1UdJQQMMAoGCCsGAQUF
BwMBMBIGA1UdEwEB/wQIMAYBAf8CAQAwHQYDVR0OBBYEFLlZ8o7PIvCG0zdI/3YU
GLqC2FWHMB8GA1UdIwQYMBaAFKPIJlqOoUzQNWP8myPIOq5W809WMDIGCCsGAQUF
BwEBBCYwJDAiBggrBgEFBQcwAoYWaHR0cDovL3llLmkubGVuY3Iub3JnLzATBgNV
HSAEDDAKMAgGBmeBDAECATAnBgNVHR8EIDAeMBygGqAYhhZodHRwOi8veWUuYy5s
ZW5jci5vcmcvMAoGCCqGSM49BAMDA2kAMGYCMQDIcnw5dcZLN9ffynXnnkLD/itS
JEycJPb3sRkzeqBowup7vOsAwaqoCnNn/jh9wycCMQCJM6CPlaOC4pQYYbJtVPYb
DKrIb2EKk5NpOpE6/XttQYZV/3gilB9l+Cc/DOVwmyg=
-----END CERTIFICATE-----
-----BEGIN CERTIFICATE-----
MIICpjCCAiugAwIBAgIRAIchZfw0tuX7qK3Vs3BftTowCgYIKoZIzj0EAwMwTzEL
MAkGA1UEBhMCVVMxKTAnBgNVBAoTIEludGVybmV0IFNlY3VyaXR5IFJlc2VhcmNo
IEdyb3VwMRUwEwYDVQQDEwxJU1JHIFJvb3QgWDIwHhcNMjYwNTEzMDAwMDAwWhcN
MzIwOTAyMjM1OTU5WjAuMQswCQYDVQQGEwJVUzENMAsGA1UEChMESVNSRzEQMA4G
A1UEAxMHUm9vdCBZRTB2MBAGByqGSM49AgEGBSuBBAAiA2IABDwS/6vhrcVqcbBo
+wgdI3fwn9x7DNJJOY/lTOti0vkwuRN87RhEhTH17E7XyFjWsPYhIPt/wzOqxTd2
b+4ZJNy9ID04YywF9U5zasDVyGSNErVNtz8uSGh5izW87j77GaOB6zCB6DAOBgNV
HQ8BAf8EBAMCAQYwEwYDVR0lBAwwCgYIKwYBBQUHAwEwDwYDVR0TAQH/BAUwAwEB
/zAdBgNVHQ4EFgQUo8gmWo6hTNA1Y/ybI8g6rlbzT1YwHwYDVR0jBBgwFoAUfEKW
rt5LSDv6kviejM9ti6lyN5UwMgYIKwYBBQUHAQEEJjAkMCIGCCsGAQUFBzAChhZo
dHRwOi8veDIuaS5sZW5jci5vcmcvMBMGA1UdIAQMMAowCAYGZ4EMAQIBMCcGA1Ud
HwQgMB4wHKAaoBiGFmh0dHA6Ly94Mi5jLmxlbmNyLm9yZy8wCgYIKoZIzj0EAwMD
aQAwZgIxAMU19WCtmxVND8UHBZRoma49Z7jPs64Dma0eTu1OChVbB/2J7GV3nvYK
Ax54uk1G9QIxAO0miLVJu8PLNiXXXkiE/gsK3CTRTF/aeo4bMX42Zw40csRU6AC2
6hSW1/IWaas6dg==
-----END CERTIFICATE-----
-----BEGIN CERTIFICATE-----
MIIEcDCCAligAwIBAgIQbI8dxyfHEX97r4U6yYD5zTANBgkqhkiG9w0BAQsFADBP
MQswCQYDVQQGEwJVUzEpMCcGA1UEChMgSW50ZXJuZXQgU2VjdXJpdHkgUmVzZWFy
Y2ggR3JvdXAxFTATBgNVBAMTDElTUkcgUm9vdCBYMTAeFw0yNjA1MTMwMDAwMDBa
Fw0zMjA5MDIyMzU5NTlaME8xCzAJBgNVBAYTAlVTMSkwJwYDVQQKEyBJbnRlcm5l
dCBTZWN1cml0eSBSZXNlYXJjaCBHcm91cDEVMBMGA1UEAxMMSVNSRyBSb290IFgy
MHYwEAYHKoZIzj0CAQYFK4EEACIDYgAEzZvVn4CDCuwJSvMWSj5cz3es3mcFDR0H
ttwW+1qLFNvicWDEukWVEYmO6gbf9yoWHKS5xcUy4APgHoIYOIvXRdgKam7mAHf7
AlF9ItgKbppbd9/w+kHsOdx1ymgHDB/qo4H1MIHyMA4GA1UdDwEB/wQEAwIBBjAd
BgNVHSUEFjAUBggrBgEFBQcDAQYIKwYBBQUHAwIwDwYDVR0TAQH/BAUwAwEB/zAd
BgNVHQ4EFgQUfEKWrt5LSDv6kviejM9ti6lyN5UwHwYDVR0jBBgwFoAUebRZ5nu2
5eQBc4AIiMgaWPbpm24wMgYIKwYBBQUHAQEEJjAkMCIGCCsGAQUFBzAChhZodHRw
Oi8veDEuaS5sZW5jci5vcmcvMBMGA1UdIAQMMAowCAYGZ4EMAQIBMCcGA1UdHwQg
MB4wHKAaoBiGFmh0dHA6Ly94MS5jLmxlbmNyLm9yZy8wDQYJKoZIhvcNAQELBQAD
ggIBAD2/e9frmMxNpCV03qUHegg+MV2wz9644YoXdqtH8RyWYcBO7xfjjGEXdU1e
/o0OkEFiynUCOSIk/vLLo7ttz6CPAeNlWfC0XNkoGeWgK6jjXvozBaGuGH5n0Ufo
shMeWTuURqNN5G00sSXDTBrpp2+mgvdZQjb8K11TYMA25QA+YHNfbIEL0BniAhKS
2gsnJjSzrdZLI+EZ7SEyqdR2rkjd1KutLDU+n3TFyxjniZVGur4YlhMP3mY/dV95
IruAkkjOZier6hGBdEgZXXvaCz9u9iVEadsIE75pAGL8oHV5vxdARDiotRpul1IN
/UZwzAbrfUFcw1HkAcYD/mlZfnQ2ieCF2MS7j3Vhv7JPDKp45fmykmzYNSrumRW0
upFFKDBOoF7hsOb7oLyHS+Uft6jOUfOrogj8YUx38hKb2K20r42OgsSdDdxdeYWc
MS3Sb6mwJeSZEYxJ2gaXnDSPaKhhrNkYwljyVQyr4Nq+MEJytXNTnHqaAcrNwZlV
pcJL1KBnMrMjP7eanvUwL3FYj3cF17jtboLt7gLoi4+2rWZFvn+w54jmd/FIuhhZ
cEaU/wvU6BUNMtcVquVGHp7itQeDth5j+XL3j4WJ2SABwzUl6OeYdgpIt/ITZa+p
TT0mQ/r5XyA4MEAiabn7XJjvCERlF2dcn2wqJw+CreTkkQ2R
-----END CERTIFICATE-----
PEM
verify() {  # hostname untrusted-chain
  openssl verify -attime "$at" -purpose sslserver -verify_hostname "$1" \
    -CAfile "$work/image-ca.pem" -untrusted "$2" "$work/leaf.pem"
}
verify "$host" "$work/chain.pem" >/dev/null
echo "ok: the release image CA store trusts the YE2 chain through the Root YE cross-sign"
# The served cross-sign is what makes it trusted: YE2 alone is not.
sed -n '1,/END CERTIFICATE/p' "$work/chain.pem" > "$work/ye2-only.pem"
if verify "$host" "$work/ye2-only.pem" >/dev/null 2>&1; then
  echo "A chain without the Root YE cross-sign was trusted." >&2; exit 1
fi
echo "ok: without the Root YE cross-sign the image CA store rejects the chain"
if verify other.codemastery.test "$work/chain.pem" >/dev/null 2>&1; then
  echo "The chain was accepted for another hostname." >&2; exit 1
fi
echo "ok: the leaf is accepted only for the approved hostname"
