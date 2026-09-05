#!/usr/bin/env bash
#
# jenkins-down.sh - Stop [Tooling] Jenkins (profile: ci)
#
# Stops and removes the Jenkins container. The jenkins_home volume is preserved
# (jobs, config). Use --volumes to also remove it (DESTRUCTIVE: wipes Jenkins
# home, forcing a fresh first-boot).
#
# Usage:
#   ./jenkins-down.sh            Stop Jenkins
#   ./jenkins-down.sh --volumes  Also remove the jenkins_home volume (DESTROYS data)
#   ./jenkins-down.sh --help     Show this help
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

echo -e "${C_YELLOW}[Tooling] Stopping Jenkins${C_RESET}"
compose --profile ci stop jenkins
compose --profile ci rm -f jenkins

if [ "$VOLUMES" -eq 1 ]; then
  echo -e "${C_RED}Removing jenkins_home volume (Jenkins data will be lost)...${C_RESET}"
  docker volume rm test-sdd_jenkins_home 2>/dev/null || true
fi

echo -e "${C_GREEN}[Tooling] Jenkins stopped.${C_RESET}"
