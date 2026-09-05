#!/usr/bin/env bash
#
# sonar-down.sh - Stop [Tooling] SonarQube + its PostgreSQL (profile: sonar)
#
# Stops and removes SonarQube and its dedicated Postgres. Data volumes
# (sonarqube_data/extensions/logs, postgres_sonar_data) are preserved.
# Use --volumes to also remove them (DESTRUCTIVE: wipes SonarQube data).
#
# Usage:
#   ./sonar-down.sh            Stop SonarQube + Postgres
#   ./sonar-down.sh --volumes  Also remove Sonar volumes (DESTROYS data)
#   ./sonar-down.sh --help     Show this help
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

echo -e "${C_YELLOW}[Tooling] Stopping SonarQube + Postgres${C_RESET}"
compose --profile sonar stop sonarqube postgres-sonar
compose --profile sonar rm -f sonarqube postgres-sonar

if [ "$VOLUMES" -eq 1 ]; then
  echo -e "${C_RED}Removing SonarQube volumes (data will be lost)...${C_RESET}"
  for v in sonarqube_data sonarqube_extensions sonarqube_logs postgres_sonar_data; do
    docker volume rm "test-sdd_${v}" 2>/dev/null || true
  done
fi

echo -e "${C_GREEN}[Tooling] SonarQube stopped.${C_RESET}"
