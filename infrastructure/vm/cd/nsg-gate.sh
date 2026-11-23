#!/usr/bin/env bash
# CodeMastery - infrastructure/vm/cd/nsg-gate.sh   (ADR017, optional network gate)
#
# GitHub-hosted runners have changing public addresses, while the VM's Azure Network
# Security Group admits SSH only from the operator's address. When the protected
# `production` environment defines the variables below, the Deploy workflow opens SSH for
# exactly this runner's /32 for the duration of the job and always removes the rule:
#
#   nsg-gate.sh open    create inbound rule cm-deploy-run-<run id>: TCP 22 from <runner>/32
#   nsg-gate.sh close   delete that rule (idempotent; also run when the job fails)
#
# Required environment (protected environment variables, not secrets):
#   AZURE_CLIENT_ID, AZURE_TENANT_ID, AZURE_SUBSCRIPTION_ID   federated identity (GitHub OIDC,
#     subject repo:chi-thanh-nguyen/codemastery:environment:production) whose only role is a
#     custom role with Microsoft.Network/networkSecurityGroups/securityRules/{read,write,delete}
#     on this NSG (no VM, storage or role-assignment rights)
#   NSG_RESOURCE_GROUP, NSG_NAME, NSG_RULE_PRIORITY            the rule's NSG and a free priority
# Without them the gate is disabled and the job needs an SSH path that already admits it.
set -Eeuo pipefail
fail() { printf 'NSG GATE FAILED: %s\n' "$*" >&2; exit 2; }
action="${1:-}"
for v in AZURE_CLIENT_ID AZURE_TENANT_ID AZURE_SUBSCRIPTION_ID NSG_RESOURCE_GROUP NSG_NAME NSG_RULE_PRIORITY GITHUB_RUN_ID; do
  [[ -n "${!v:-}" ]] || fail "${v} is not set."
done
[[ "${NSG_RULE_PRIORITY}" =~ ^[1-9][0-9]{2,3}$ ]] || fail "NSG_RULE_PRIORITY must be 100-4096."
(( NSG_RULE_PRIORITY >= 100 && NSG_RULE_PRIORITY <= 4096 )) || fail "NSG_RULE_PRIORITY must be 100-4096."
[[ "${GITHUB_RUN_ID}" =~ ^[0-9]+$ ]] || fail "malformed run ID."
rule="cm-deploy-run-${GITHUB_RUN_ID}"
command -v az >/dev/null || fail "the Azure CLI is required."

login() {
  [[ -n "${ACTIONS_ID_TOKEN_REQUEST_URL:-}" && -n "${ACTIONS_ID_TOKEN_REQUEST_TOKEN:-}" ]] \
    || fail "no GitHub OIDC token endpoint (the job needs id-token: write)."
  local token
  token="$(curl -fsS -H "Authorization: bearer ${ACTIONS_ID_TOKEN_REQUEST_TOKEN}" \
    "${ACTIONS_ID_TOKEN_REQUEST_URL}&audience=api://AzureADTokenExchange" | python3 -I -c 'import json,sys; print(json.load(sys.stdin)["value"])')" \
    || fail "could not obtain a GitHub OIDC token."
  echo "::add-mask::${token}"
  az login --service-principal --username "${AZURE_CLIENT_ID}" --tenant "${AZURE_TENANT_ID}" \
    --federated-token "${token}" --allow-no-subscriptions --output none || fail "Azure federated login failed."
  az account set --subscription "${AZURE_SUBSCRIPTION_ID}" --output none
}

case "${action}" in
  (open)
    ip="$(curl -fsS --max-time 10 https://api.ipify.org)" || fail "cannot determine the runner's public address."
    [[ "${ip}" =~ ^([0-9]{1,3}\.){3}[0-9]{1,3}$ ]] || fail "unexpected runner address."
    login
    az network nsg rule create --resource-group "${NSG_RESOURCE_GROUP}" --nsg-name "${NSG_NAME}" --name "${rule}" \
      --priority "${NSG_RULE_PRIORITY}" --direction Inbound --access Allow --protocol Tcp \
      --source-address-prefixes "${ip}/32" --source-port-ranges '*' --destination-address-prefixes '*' \
      --destination-port-ranges 22 --description "CodeMastery Deploy run ${GITHUB_RUN_ID} (temporary)" --output none \
      || fail "could not create the temporary SSH rule."
    echo "NSG gate open: ${rule} admits ${ip}/32 on TCP 22."
    ;;
  (close)
    login
    # A failed lookup is not evidence of absence (auth/network errors previously
    # left a rule open while reporting success). List and verify the final state.
    rules="$(az network nsg rule list --resource-group "${NSG_RESOURCE_GROUP}" --nsg-name "${NSG_NAME}" --output json)" \
      || fail "cannot inspect temporary rules; remove ${rule} manually."
    if printf '%s' "${rules}" | python3 -I -c 'import json,sys; sys.exit(0 if any(x["name"]==sys.argv[1] for x in json.load(sys.stdin)) else 1)' "${rule}"; then
      az network nsg rule delete --resource-group "${NSG_RESOURCE_GROUP}" --nsg-name "${NSG_NAME}" --name "${rule}" --output none \
        || fail "could not delete ${rule}; remove it manually."
    fi
    rules="$(az network nsg rule list --resource-group "${NSG_RESOURCE_GROUP}" --nsg-name "${NSG_NAME}" --output json)" \
      || fail "cannot verify rule removal; inspect ${rule} manually."
    printf '%s' "${rules}" | python3 -I -c 'import json,sys; sys.exit(1 if any(x["name"]==sys.argv[1] for x in json.load(sys.stdin)) else 0)' "${rule}" \
      || fail "${rule} is still present or removal cannot be verified."
    echo "NSG gate closed: ${rule} absent."
    ;;
  (*) fail "usage: nsg-gate.sh open|close" ;;
esac
