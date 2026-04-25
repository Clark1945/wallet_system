#!/usr/bin/env bash
set -euo pipefail

# ── Colors ──────────────────────────────────────────────────────────────────
GREEN='\033[0;32m'; YELLOW='\033[1;33m'; RED='\033[0;31m'; NC='\033[0m'

ok()   { echo -e "${GREEN}[OK]${NC} $*"; }
info() { echo -e "${YELLOW}[>>]${NC} $*"; }
fail() { echo -e "${RED}[FAIL]${NC} $*"; exit 1; }

ROOT_DIR="$(cd "$(dirname "$0")" && pwd)"

# ── Parse flags ─────────────────────────────────────────────────────────────
SKIP_TESTS=false
DETACH=false

for arg in "$@"; do
  case $arg in
    --skip-tests|-s) SKIP_TESTS=true ;;
    --detach|-d)     DETACH=true ;;
    --help|-h)
      echo "Usage: $0 [--skip-tests|-s] [--detach|-d]"
      echo "  -s  Skip unit tests during Maven build (faster)"
      echo "  -d  Start Docker Compose in detached mode"
      exit 0 ;;
    *) fail "Unknown option: $arg" ;;
  esac
done

MVN_ARGS="clean package --no-transfer-progress"
[[ "$SKIP_TESTS" == true ]] && MVN_ARGS="$MVN_ARGS -DskipTests"

COMPOSE_ARGS="up --build"
[[ "$DETACH" == true ]] && COMPOSE_ARGS="$COMPOSE_ARGS -d"

# ── Build ────────────────────────────────────────────────────────────────────
SERVICES=("wallet_system" "payment-service" "email-service" "mock-bank")

for svc in "${SERVICES[@]}"; do
  svc_dir="$ROOT_DIR/$svc"
  [[ -d "$svc_dir" ]] || { info "Skipping $svc (directory not found)"; continue; }
  info "Building $svc ..."
  (cd "$svc_dir" && ./mvnw $MVN_ARGS) || fail "Build failed: $svc"
  ok "$svc built"
done

echo ""
ok "All services built. Starting Docker Compose ..."
echo ""

# ── Run ──────────────────────────────────────────────────────────────────────
cd "$ROOT_DIR"
docker compose $COMPOSE_ARGS
