#!/usr/bin/env bash
#
# phase2-up.sh - [Phase 2/3] Backend: eureka, api-gateway, auth, stock, venta, despacho
#
# Starts service discovery first (so the rest can register), then the backend
# microservices, waiting for each step to become healthy.
#
# Requires Phase 1 (infra) to be up. Run ./phase1-up.sh first, or use up-all.sh.
#
# Usage:
#   ./phase2-up.sh           Start backend (prebuilt images)
#   ./phase2-up.sh --build   Rebuild service images first (shared BuildKit cache)
#   ./phase2-up.sh --help    Show this help
#
set -euo pipefail
source "$(dirname "${BASH_SOURCE[0]}")/lib.sh"

parse_build_flag "$@" || { [ $? -eq 10 ] && { print_help "$0"; exit 0; }; }

UP_ARGS=(up -d); [ "${BUILD:-0}" -eq 1 ] && UP_ARGS+=(--build)

echo -e "${C_YELLOW}[Phase 2/3] Backend: eureka, api-gateway, auth, stock, venta, despacho${C_RESET}"

# Eureka first so the others can register.
compose "${UP_ARGS[@]}" eureka-server
wait_healthy 180 eureka-server

compose "${UP_ARGS[@]}" api-gateway auth-service stock-service venta-service despacho-service
wait_healthy 240 api-gateway auth-service stock-service venta-service despacho-service
echo -e "${C_GREEN}[Phase 2/3] Backend is up.${C_RESET}"
