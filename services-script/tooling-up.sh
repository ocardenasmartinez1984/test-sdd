#!/usr/bin/env bash
#
# tooling-up.sh - Bring the CI/CD tooling up: Jenkins + SonarQube.
#
# Convenience wrapper that starts both tooling stacks (behind the "tooling"
# Compose profile). Equivalent to running sonar-up.sh and jenkins-up.sh.
#
# Usage:
#   ./tooling-up.sh           Start Jenkins + SonarQube (prebuilt images)
#   ./tooling-up.sh --build   Rebuild images first
#   ./tooling-up.sh --help    Show this help
#
set -euo pipefail
source "$(dirname "${BASH_SOURCE[0]}")/lib.sh"

parse_build_flag "$@" || { [ $? -eq 10 ] && { print_help "$0"; exit 0; }; }

DIR="$(dirname "${BASH_SOURCE[0]}")"
BUILD_ARGS=(); [ "${BUILD:-0}" -eq 1 ] && BUILD_ARGS+=(--build)

echo -e "${C_MAGENTA}=== Starting CI/CD tooling (Jenkins + SonarQube) ===${C_RESET}"
bash "$DIR/sonar-up.sh" "${BUILD_ARGS[@]}"
bash "$DIR/jenkins-up.sh" "${BUILD_ARGS[@]}"

echo -e "\n${C_GREEN}=== Tooling up ===${C_RESET}"
echo -e "${C_CYAN}
  Jenkins     http://localhost:8888   (user: admin / pass: admin123)
  SonarQube   http://localhost:9000   (default login: admin / admin)${C_RESET}"
