#!/usr/bin/env bash
#
# tooling-down.sh - Tear down the CI/CD tooling: Jenkins + SonarQube.
#
# Convenience wrapper that stops both tooling stacks. Data volumes are
# preserved. Use --volumes to also remove Jenkins/SonarQube volumes
# (DESTRUCTIVE: wipes all tooling data).
#
# Usage:
#   ./tooling-down.sh            Stop Jenkins + SonarQube
#   ./tooling-down.sh --volumes  Also remove tooling volumes (DESTROYS data)
#   ./tooling-down.sh --help     Show this help
#
set -euo pipefail
source "$(dirname "${BASH_SOURCE[0]}")/lib.sh"

PASS_ARGS=()
for arg in "$@"; do
  case "$arg" in
    --volumes) PASS_ARGS+=(--volumes) ;;
    -h|--help) print_help "$0"; exit 0 ;;
    *) echo -e "${C_RED}Unknown option: $arg${C_RESET}" >&2; exit 2 ;;
  esac
done

DIR="$(dirname "${BASH_SOURCE[0]}")"

echo -e "${C_MAGENTA}=== Stopping CI/CD tooling (Jenkins + SonarQube) ===${C_RESET}"
bash "$DIR/jenkins-down.sh" "${PASS_ARGS[@]}"
bash "$DIR/sonar-down.sh" "${PASS_ARGS[@]}"
echo -e "${C_GREEN}=== Tooling stopped ===${C_RESET}"
