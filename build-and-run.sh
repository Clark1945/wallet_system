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

# ── Build (parallel) ─────────────────────────────────────────────────────────
SERVICES=("wallet_system" "payment-service" "email-service" "mock-bank")

declare -A pids
declare -A logs

for svc in "${SERVICES[@]}"; do
  svc_dir="$ROOT_DIR/$svc"
  if [[ ! -d "$svc_dir" ]]; then
    info "Skipping $svc (directory not found)"
    continue
  fi
  mvn_cmd="./mvnw"; [[ -f "$svc_dir/mvnw" ]] || mvn_cmd="mvn"
  log_file=$(mktemp)
  logs[$svc]="$log_file"
  info "Building $svc (parallel) ..."
  (cd "$svc_dir" && $mvn_cmd $MVN_ARGS > "$log_file" 2>&1) &
  pids[$svc]=$!
done

failed=false
for svc in "${!pids[@]}"; do
  if wait "${pids[$svc]}"; then
    ok "$svc built"
  else
    echo -e "${RED}[FAIL]${NC} Build failed: $svc — last 20 lines:"
    tail -20 "${logs[$svc]}"
    failed=true
  fi
  rm -f "${logs[$svc]}"
done
[[ "$failed" == true ]] && exit 1

echo ""
ok "All services built. Starting Docker Compose ..."
echo ""

# ── Run ──────────────────────────────────────────────────────────────────────
cd "$ROOT_DIR"
docker compose $COMPOSE_ARGS
