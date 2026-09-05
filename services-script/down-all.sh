#!/usr/bin/env bash
#
# down-all.sh - Tear the whole POS stack down, in reverse phase order.
#
# Stops phases 3 -> 2 -> 1 so dependents go down before their dependencies:
#   [Phase 3/3] Frontends
#   [Phase 2/3] Backend
#   [Phase 1/3] Infrastructure
#
# Data volumes are preserved. Use --volumes to also remove named volumes
# (DESTRUCTIVE: deletes postgres/mongodb data).
#
# Usage:
#   ./down-all.sh            Stop and remove all stack containers
#   ./down-all.sh --volumes  Also remove named volumes (DESTROYS data)
#   ./down-all.sh --help     Show this help
#
set -euo pipefail
source "$(dirname "${BASH_SOURCE[0]}")/lib.sh"

VOLUMES=0
for arg in "$@"; do
  case "$arg" in
    --volumes) VOLUMES=1 ;;
    -h|--help) print_help "$0"; exit 0 ;;
    *) echo -e "${C_RED}Unknown option: $arg${C_RESET}" >&2; exit 2 ;;
  esac
done

DIR="$(dirname "${BASH_SOURCE[0]}")"

echo -e "${C_MAGENTA}=== Tearing down the POS stack (reverse phase order) ===${C_RESET}"
bash "$DIR/phase3-down.sh"
bash "$DIR/phase2-down.sh"
bash "$DIR/phase1-down.sh"

if [ "$VOLUMES" -eq 1 ]; then
  echo -e "${C_RED}Removing named volumes (data will be lost)...${C_RESET}"
  compose down --volumes
fi

echo -e "${C_GREEN}=== Stack stopped ===${C_RESET}"
