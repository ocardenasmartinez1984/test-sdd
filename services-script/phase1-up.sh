#!/usr/bin/env bash
#
# phase1-up.sh - [Phase 1/3] Infrastructure: postgres, mongodb, kafka, redis
#
# Starts the data + messaging layer and waits until it is healthy.
#
# Usage:
#   ./phase1-up.sh           Start infra (prebuilt images)
#   ./phase1-up.sh --build   Rebuild images first (no-op for infra, kept for symmetry)
#   ./phase1-up.sh --help    Show this help
#
set -euo pipefail
source "$(dirname "${BASH_SOURCE[0]}")/lib.sh"

parse_build_flag "$@" || { [ $? -eq 10 ] && { print_help "$0"; exit 0; }; }

UP_ARGS=(up -d); [ "${BUILD:-0}" -eq 1 ] && UP_ARGS+=(--build)

echo -e "${C_YELLOW}[Phase 1/3] Infrastructure: postgres, mongodb, kafka, redis${C_RESET}"
compose "${UP_ARGS[@]}" postgres mongodb kafka redis
wait_healthy 180 postgres mongodb kafka redis
echo -e "${C_GREEN}[Phase 1/3] Infrastructure is up.${C_RESET}"
