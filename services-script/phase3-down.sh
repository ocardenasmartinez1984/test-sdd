#!/usr/bin/env bash
#
# phase3-down.sh - Stop [Phase 3/3] Frontends: pos-frontend, ventas-mantenedor, users-mantenedor
#
# Stops and removes the frontend containers. Backend and infra are left running.
#
# Usage:
#   ./phase3-down.sh          Stop frontend containers
#   ./phase3-down.sh --help   Show this help
#
set -euo pipefail
source "$(dirname "${BASH_SOURCE[0]}")/lib.sh"

case "${1:-}" in -h|--help) print_help "$0"; exit 0 ;; esac

echo -e "${C_YELLOW}[Phase 3/3] Stopping frontends: pos-frontend, ventas-mantenedor, users-mantenedor${C_RESET}"
compose stop pos-frontend ventas-mantenedor users-mantenedor
compose rm -f pos-frontend ventas-mantenedor users-mantenedor
echo -e "${C_GREEN}[Phase 3/3] Frontends stopped.${C_RESET}"
