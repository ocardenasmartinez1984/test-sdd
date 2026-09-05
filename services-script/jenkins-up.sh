#!/usr/bin/env bash
#
# jenkins-up.sh - [Tooling] Jenkins CI server (profile: ci)
#
# Starts the Jenkins container. Jenkins sits behind the "ci"/"tooling" Compose
# profiles, so it is not part of the default stack. Plugins are baked into the
# image (jenkins/Dockerfile) and the setup wizard is disabled; admin user is
# provisioned via env (JENKINS_ADMIN_ID / JENKINS_ADMIN_PASSWORD).
#
# Usage:
#   ./jenkins-up.sh           Start Jenkins (prebuilt image)
#   ./jenkins-up.sh --build   Rebuild the Jenkins image first
#   ./jenkins-up.sh --help    Show this help
#
set -euo pipefail
source "$(dirname "${BASH_SOURCE[0]}")/lib.sh"

parse_build_flag "$@" || { [ $? -eq 10 ] && { print_help "$0"; exit 0; }; }

UP_ARGS=(up -d); [ "${BUILD:-0}" -eq 1 ] && UP_ARGS+=(--build)

echo -e "${C_YELLOW}[Tooling] Jenkins (profile: ci)${C_RESET}"
compose --profile ci "${UP_ARGS[@]}" jenkins
wait_healthy 180 jenkins
echo -e "${C_GREEN}[Tooling] Jenkins is up.${C_RESET}"
echo -e "${C_CYAN}
  Jenkins   http://localhost:8888   (user: admin / pass: admin123)${C_RESET}"
