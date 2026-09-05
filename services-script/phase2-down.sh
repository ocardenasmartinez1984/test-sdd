#!/usr/bin/env bash
#
# phase2-down.sh - Stop [Phase 2/3] Backend: eureka, api-gateway, auth, stock, venta, despacho
#
# Stops and removes the backend microservices. Infra (Phase 1) is left running.
#
# Usage:
#   ./phase2-down.sh          Stop backend containers
#   ./phase2-down.sh --help   Show this help
#
set -euo pipefail
source "$(dirname "${BASH_SOURCE[0]}")/lib.sh"

case "${1:-}" in -h|--help) print_help "$0"; exit 0 ;; esac

BACKEND=(api-gateway auth-service stock-service venta-service despacho-service eureka-server)

echo -e "${C_YELLOW}[Phase 2/3] Stopping backend: eureka, api-gateway, auth, stock, venta, despacho${C_RESET}"
compose stop "${BACKEND[@]}"
compose rm -f "${BACKEND[@]}"
echo -e "${C_GREEN}[Phase 2/3] Backend stopped.${C_RESET}"
