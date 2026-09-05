#!/usr/bin/env bash
#
# phase1-down.sh - Stop [Phase 1/3] Infrastructure: postgres, mongodb, kafka, redis
#
# Stops and removes the infra containers. Data volumes are preserved.
# Note: bringing down infra while the backend is still running will break it;
# use down-all.sh (or stop phases 3 -> 2 -> 1) for a clean teardown.
#
# Usage:
#   ./phase1-down.sh          Stop infra containers
#   ./phase1-down.sh --help   Show this help
#
set -euo pipefail
source "$(dirname "${BASH_SOURCE[0]}")/lib.sh"

case "${1:-}" in -h|--help) print_help "$0"; exit 0 ;; esac

echo -e "${C_YELLOW}[Phase 1/3] Stopping infrastructure: postgres, mongodb, kafka, redis${C_RESET}"
compose stop postgres mongodb kafka redis
compose rm -f postgres mongodb kafka redis
echo -e "${C_GREEN}[Phase 1/3] Infrastructure stopped.${C_RESET}"
