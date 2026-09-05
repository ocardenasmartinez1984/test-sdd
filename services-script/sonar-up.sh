#!/usr/bin/env bash
#
# sonar-up.sh - [Tooling] SonarQube + its PostgreSQL (profile: sonar)
#
# Starts the SonarQube stack: its dedicated Postgres first (so Sonar can
# connect on boot), then SonarQube itself. Both sit behind the "sonar"/"tooling"
# Compose profiles, so they are not part of the default stack.
#
# Usage:
#   ./sonar-up.sh           Start SonarQube + Postgres (prebuilt images)
#   ./sonar-up.sh --build   Rebuild images first (no-op for stock images)
#   ./sonar-up.sh --help    Show this help
#
set -euo pipefail
source "$(dirname "${BASH_SOURCE[0]}")/lib.sh"

parse_build_flag "$@" || { [ $? -eq 10 ] && { print_help "$0"; exit 0; }; }

UP_ARGS=(up -d); [ "${BUILD:-0}" -eq 1 ] && UP_ARGS+=(--build)

echo -e "${C_YELLOW}[Tooling] SonarQube (profile: sonar)${C_RESET}"

# Dedicated Postgres first so SonarQube can connect on boot.
compose --profile sonar "${UP_ARGS[@]}" postgres-sonar
wait_healthy 120 postgres-sonar

compose --profile sonar "${UP_ARGS[@]}" sonarqube
wait_healthy 240 sonarqube
echo -e "${C_GREEN}[Tooling] SonarQube is up.${C_RESET}"
echo -e "${C_CYAN}
  SonarQube   http://localhost:9000   (default login: admin / admin)${C_RESET}"
