#!/usr/bin/env bash
#
# up-all.sh - Bring the whole POS stack up, phase by phase.
#
# Runs the three phases in order, waiting for each to become healthy:
#   [Phase 1/3] Infrastructure: postgres, mongodb, kafka, redis
#   [Phase 2/3] Backend:        eureka, api-gateway, auth, stock, venta, despacho
#   [Phase 3/3] Frontends:      pos-frontend, ventas-mantenedor, users-mantenedor
#
# Usage:
#   ./up-all.sh           Start the whole stack (prebuilt images)
#   ./up-all.sh --build   Rebuild images first (shared BuildKit cache)
#   ./up-all.sh --help    Show this help
#
set -euo pipefail
source "$(dirname "${BASH_SOURCE[0]}")/lib.sh"

parse_build_flag "$@" || { [ $? -eq 10 ] && { print_help "$0"; exit 0; }; }

DIR="$(dirname "${BASH_SOURCE[0]}")"
BUILD_ARGS=(); [ "${BUILD:-0}" -eq 1 ] && BUILD_ARGS+=(--build)

echo -e "${C_MAGENTA}=== Phased startup of the POS stack ===${C_RESET}"
[ "${BUILD:-0}" -eq 1 ] && echo -e "${C_GREY}(rebuilding images with BuildKit cache)${C_RESET}"

bash "$DIR/phase1-up.sh" "${BUILD_ARGS[@]}"
bash "$DIR/phase2-up.sh" "${BUILD_ARGS[@]}"
bash "$DIR/phase3-up.sh" "${BUILD_ARGS[@]}"

echo -e "\n${C_GREEN}=== Stack up ===${C_RESET}"
echo -e "${C_CYAN}
  POS Frontend        http://localhost:4300
  Ventas Mantenedor   http://localhost:4200
  Users Mantenedor    http://localhost:4400
  API Gateway         http://localhost:8080
  Eureka Dashboard    http://localhost:8761${C_RESET}"
echo -e "${C_GREY}\nStatus:    ${COMPOSE[*]} ps"
echo -e "Tear down: ./services-script/down-all.sh${C_RESET}"
