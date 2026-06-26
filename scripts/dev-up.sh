#!/usr/bin/env bash
#
# dev-up.sh — build & run the local stack with git-derived, per-service versions.
#
# Lightweight automatic versioning for local demo / portfolio use (no registry).
# Each service's version is computed on the host from git — the short SHA of the
# last commit that touched that service's directory — and passed into the build
# as APP_VERSION (-> -Drevision -> pom version + build-info -> /actuator/info) and
# as its docker image tag. The Docker build container has no .git, which is exactly
# why the version is resolved here and injected as a build arg.
#
# Usage:
#   bash scripts/dev-up.sh                # build + up (foreground)
#   bash scripts/dev-up.sh -d             # build + up (detached)
#   bash scripts/dev-up.sh -d email-service   # only one service
# Any extra args are passed straight through to `docker compose up --build`.
#
# Plain `docker compose up --build` still works without this script — it falls
# back to the VERSION defaults in .env (currently 1.0.0).

set -euo pipefail
cd "$(dirname "$0")/.."   # repo root

# Human-controlled semantic base; bump for a "real" release. The git SHA after it
# is fully automatic, so day-to-day you never edit a version by hand.
VERSION_BASE="1.0.0"

# Echo "<base>-<short-sha>[-dirty]" for the service directory passed as $1.
service_version() {
  local dir="$1" sha dirty=""
  sha="$(git log -1 --format=%h -- "$dir" 2>/dev/null || true)"
  [ -z "$sha" ] && sha="nogit"
  if ! git diff --quiet -- "$dir" || ! git diff --cached --quiet -- "$dir"; then
    dirty="-dirty"   # uncommitted changes in this service → mark the image
  fi
  printf '%s-%s%s' "$VERSION_BASE" "$sha" "$dirty"
}

export WALLET_VERSION="$(service_version wallet_system)"
export PAYMENT_VERSION="$(service_version payment-service)"
export EMAIL_VERSION="$(service_version email-service)"
export AUDIT_VERSION="$(service_version audit-service)"
export MOCKBANK_VERSION="$(service_version mock-bank)"

echo "Building with git-derived versions:"
printf '  %-16s %s\n' \
  "wallet_system"   "$WALLET_VERSION" \
  "payment-service" "$PAYMENT_VERSION" \
  "email-service"   "$EMAIL_VERSION" \
  "audit-service"   "$AUDIT_VERSION" \
  "mock-bank"       "$MOCKBANK_VERSION"

exec docker compose up --build "$@"
