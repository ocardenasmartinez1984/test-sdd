#!/usr/bin/env bash
#
# phase3-up.sh - [Phase 3/3] Frontends: pos-frontend, ventas-mantenedor, users-mantenedor
#
# Starts the Angular frontends and waits until they are healthy.
#
# Requires Phase 2 (backend) to be up for the apps to work end-to-end.
#
# Usage:
#   ./phase3-up.sh           Start frontends (prebuilt images)
#   ./phase3-up.sh --build   Rebuild frontend images first
#   ./phase3-up.sh --help    Show this help
#
set -euo pipefail
source "$(dirname "${BASH_SOURCE[0]}")/lib.sh"

parse_build_flag "$@" || { [ $? -eq 10 ] && { print_help "$0"; exit 0; }; }

UP_ARGS=(up -d); [ "${BUILD:-0}" -eq 1 ] && UP_ARGS+=(--build)

echo -e "${C_YELLOW}[Phase 3/3] Frontends: pos-frontend, ventas-mantenedor, users-mantenedor${C_RESET}"
compose "${UP_ARGS[@]}" pos-frontend ventas-mantenedor users-mantenedor
wait_healthy 180 pos-frontend ventas-mantenedor users-mantenedor
echo -e "${C_GREEN}[Phase 3/3] Frontends are up.${C_RESET}"
echo -e "${C_CYAN}
  POS Frontend        http://localhost:4300
  Ventas Mantenedor   http://localhost:4200
  Users Mantenedor    http://localhost:4400${C_RESET}"
