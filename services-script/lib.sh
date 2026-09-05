#!/usr/bin/env bash
#
# lib.sh - Shared helpers for the phased up/down scripts.
#
# Sourced by every script in services-script/. Provides:
#   - Colored output (auto-disabled when not a TTY)
#   - Docker Compose v2/v1 detection (compose ...)
#   - wait_healthy <timeout_s> <service...>  (waits for health/running)
#   - BuildKit env + repo-root aware compose (-f docker-compose.yml)
#
# Not meant to be run directly.

set -euo pipefail

# --- Colors (fall back to no color if not a TTY) ---
if [ -t 1 ]; then
  C_RESET='\033[0m'; C_YELLOW='\033[1;33m'; C_GREEN='\033[1;32m'
  C_CYAN='\033[1;36m'; C_MAGENTA='\033[1;35m'; C_GREY='\033[0;90m'; C_RED='\033[1;31m'
else
  C_RESET=''; C_YELLOW=''; C_GREEN=''; C_CYAN=''; C_MAGENTA=''; C_GREY=''; C_RED=''
fi

# --- Resolve the repository root (parent of services-script/) ---
# BASH_SOURCE[0] is this file regardless of the caller's location.
LIB_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "$LIB_DIR/.." && pwd)"
COMPOSE_FILE="$REPO_ROOT/docker-compose.yml"

# --- Enable BuildKit so the Dockerfiles' cache mounts are used ---
export DOCKER_BUILDKIT=1
export COMPOSE_DOCKER_CLI_BUILD=1

# --- Detect 'docker compose' (v2) vs 'docker-compose' (v1) ---
if docker compose version >/dev/null 2>&1; then
  COMPOSE=(docker compose)
elif command -v docker-compose >/dev/null 2>&1; then
  COMPOSE=(docker-compose)
else
  echo -e "${C_RED}Neither 'docker compose' nor 'docker-compose' is available.${C_RESET}" >&2
  exit 1
fi

# --- compose wrapper: always targets the repo's docker-compose.yml ---
compose() {
  "${COMPOSE[@]}" -f "$COMPOSE_FILE" "$@"
}

# --- Wait until the given services are healthy (or running if no healthcheck) ---
# Usage: wait_healthy <timeout_seconds> <service> [service...]
# Service names are the compose service names (container is "saga-<service>").
wait_healthy() {
  local timeout="$1"; shift
  local services=("$@")
  echo -e "${C_CYAN}  Waiting for healthy: ${services[*]}${C_RESET}"
  local deadline=$(( $(date +%s) + timeout ))
  while [ "$(date +%s)" -lt "$deadline" ]; do
    local all_ready=1
    for svc in "${services[@]}"; do
      local name="saga-${svc}"
      local status
      status=$(docker inspect --format '{{if .State.Health}}{{.State.Health.Status}}{{else}}{{.State.Status}}{{end}}' "$name" 2>/dev/null || echo "missing")
      if [ "$status" != "healthy" ] && [ "$status" != "running" ]; then
        all_ready=0
        break
      fi
    done
    if [ "$all_ready" -eq 1 ]; then
      echo -e "${C_GREEN}  OK: services healthy.${C_RESET}"
      return 0
    fi
    sleep 3
  done
  echo -e "${C_YELLOW}  Timeout waiting for: ${services[*]}. Continuing anyway.${C_RESET}" >&2
  return 0
}

# --- Print the leading comment block of a script as help text ---
# Usage: print_help "<script_path>"
print_help() {
  local script="$1"
  sed -n '2,/^set -euo/{/^set -euo/d; s/^# \{0,1\}//; p}' "$script"
}

# --- Parse common flags (--build, --down/-h/--help handled by caller). ---
# Sets BUILD=1 when --build is present. Unknown flags -> error.
parse_build_flag() {
  BUILD=0
  for arg in "$@"; do
    case "$arg" in
      --build) BUILD=1 ;;
      -h|--help) return 10 ;;   # signal: show help
      *)
        echo -e "${C_RED}Unknown option: $arg${C_RESET}" >&2
        echo "Use --help to see available options." >&2
        exit 2
        ;;
    esac
  done
  return 0
}
