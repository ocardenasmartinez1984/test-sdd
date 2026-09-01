#!/usr/bin/env bash
#
# start-stack.sh - Phased startup of the POS stack (Linux/macOS).
#
# Brings the essential stack up in layers, waiting for each phase to become
# healthy before continuing. This avoids overwhelming the machine by starting
# 14+ containers (6 JVMs + Kafka + databases) simultaneously.
#
# Heavy tooling (Jenkins, SonarQube, Prometheus, Grafana, Zipkin) sits behind
# Docker Compose profiles and does NOT start by default. Use --tooling to add it.
#
# Usage:
#   ./start-stack.sh            Start the essential stack (prebuilt images)
#   ./start-stack.sh --build    Rebuild images first (shared BuildKit cache)
#   ./start-stack.sh --tooling   Also start Jenkins/SonarQube/Prometheus/Grafana/Zipkin
#   ./start-stack.sh --down      Stop and remove all containers (incl. profiles)
#   ./start-stack.sh --help      Show this help
#
set -euo pipefail

# --- Colors (fall back to no color if not a TTY) ---
if [ -t 1 ]; then
  C_RESET='\033[0m'; C_YELLOW='\033[1;33m'; C_GREEN='\033[1;32m'
  C_CYAN='\033[1;36m'; C_MAGENTA='\033[1;35m'; C_GREY='\033[0;90m'; C_RED='\033[1;31m'
else
  C_RESET=''; C_YELLOW=''; C_GREEN=''; C_CYAN=''; C_MAGENTA=''; C_GREY=''; C_RED=''
fi

# --- Parse flags ---
BUILD=0
TOOLING=0
DOWN=0
for arg in "$@"; do
  case "$arg" in
    --build)   BUILD=1 ;;
    --tooling) TOOLING=1 ;;
    --down)    DOWN=1 ;;
    -h|--help)
      # Print only the leading comment block (stops at the first non-comment line)
      sed -n '2,/^set -euo/{/^set -euo/d; s/^# \{0,1\}//; p}' "$0"
      exit 0
      ;;
    *)
      echo -e "${C_RED}Unknown option: $arg${C_RESET}" >&2
      echo "Use --help to see available options." >&2
      exit 2
      ;;
  esac
done

# --- Enable BuildKit so the Dockerfiles' cache mounts are used ---
export DOCKER_BUILDKIT=1
export COMPOSE_DOCKER_CLI_BUILD=1

# --- Detect 'docker compose' (v2) vs 'docker-compose' (v1) ---
if docker compose version >/dev/null 2>&1; then
  COMPOSE=(docker compose)
elif command -v docker-compose >/dev/null 2>&1; then
  COMPOSE=(docker-compose)
else
  echo -e "${C_RED}Neither 'docker compose' nor 'docker-compose' is available.${C_RESET}" >&2
  exit 1
fi

compose() {
  "${COMPOSE[@]}" "$@"
}

# --- Wait until the given services are healthy (or running if no healthcheck) ---
wait_healthy() {
  local timeout="$1"; shift
  local services=("$@")
  echo -e "${C_CYAN}  Waiting for healthy: ${services[*]}${C_RESET}"
  local deadline=$(( $(date +%s) + timeout ))
  while [ "$(date +%s)" -lt "$deadline" ]; do
    local all_ready=1
    for svc in "${services[@]}"; do
      local name="saga-${svc}"
      local status
      status=$(docker inspect --format '{{if .State.Health}}{{.State.Health.Status}}{{else}}{{.State.Status}}{{end}}' "$name" 2>/dev/null || echo "missing")
      if [ "$status" != "healthy" ] && [ "$status" != "running" ]; then
        all_ready=0
        break
      fi
    done
    if [ "$all_ready" -eq 1 ]; then
      echo -e "${C_GREEN}  OK: phase healthy.${C_RESET}"
      return 0
    fi
    sleep 3
  done
  echo -e "${C_YELLOW}  Timeout waiting for: ${services[*]}. Continuing anyway.${C_RESET}" >&2
}

# --- Tear down ---
if [ "$DOWN" -eq 1 ]; then
  echo -e "${C_YELLOW}Tearing down the whole stack (including profiles)...${C_RESET}"
  compose --profile tooling down
  echo -e "${C_GREEN}Stack stopped.${C_RESET}"
  exit 0
fi

# --- Optional build args ---
UP_ARGS=(up -d)
if [ "$BUILD" -eq 1 ]; then
  UP_ARGS+=(--build)
fi

echo -e "${C_MAGENTA}=== Phased startup of the POS stack ===${C_RESET}"
if [ "$BUILD" -eq 1 ]; then
  echo -e "${C_GREY}(rebuilding images with BuildKit cache)${C_RESET}"
fi

# --- Phase 1: base infrastructure (data + messaging) ---
echo -e "\n${C_YELLOW}[Phase 1/3] Infrastructure: postgres, mongodb, kafka, redis${C_RESET}"
compose "${UP_ARGS[@]}" postgres mongodb kafka redis
wait_healthy 180 postgres mongodb kafka redis

# --- Phase 2: service discovery + backend microservices ---
echo -e "\n${C_YELLOW}[Phase 2/3] Backend: eureka, api-gateway, auth, stock, venta, despacho${C_RESET}"
# Eureka first so the others can register.
compose "${UP_ARGS[@]}" eureka-server
wait_healthy 180 eureka-server
compose "${UP_ARGS[@]}" api-gateway auth-service stock-service venta-service despacho-service
wait_healthy 240 api-gateway auth-service stock-service venta-service despacho-service

# --- Phase 3: frontends ---
echo -e "\n${C_YELLOW}[Phase 3/3] Frontends: pos-frontend, ventas-mantenedor, users-mantenedor${C_RESET}"
compose "${UP_ARGS[@]}" pos-frontend ventas-mantenedor users-mantenedor
wait_healthy 180 pos-frontend ventas-mantenedor users-mantenedor

# --- Optional tooling ---
if [ "$TOOLING" -eq 1 ]; then
  echo -e "\n${C_YELLOW}[Extra] Tooling: jenkins, sonarqube, prometheus, grafana, zipkin${C_RESET}"
  compose --profile tooling up -d
fi

echo -e "\n${C_GREEN}=== Stack up ===${C_RESET}"
echo -e "${C_CYAN}
  POS Frontend        http://localhost:4300
  Ventas Mantenedor   http://localhost:4200
  Users Mantenedor    http://localhost:4400
  API Gateway         http://localhost:8080
  Eureka Dashboard    http://localhost:8761${C_RESET}"

if [ "$TOOLING" -eq 1 ]; then
  echo -e "${C_CYAN}  Jenkins             http://localhost:8888
  SonarQube           http://localhost:9000
  Prometheus          http://localhost:9090
  Grafana             http://localhost:3001
  Zipkin              http://localhost:9411${C_RESET}"
fi

echo -e "${C_GREY}\nStatus:    ${COMPOSE[*]} ps"
echo -e "Tear down: ./start-stack.sh --down${C_RESET}"
