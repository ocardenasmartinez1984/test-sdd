#!/usr/bin/env bash
#
# build-images-fast.sh — fast Docker image builds for the Java services.
#
# Why: `docker compose build` starts a *separate* Gradle build inside every
# service container. On this 4-CPU host, shared with the running stack, that is
# very slow (~5 min per service on code change). Instead we:
#   1. Build ALL service JARs in ONE host Gradle invocation (they share the
#      configuration/compilation cache -> ~1.5-2 min for all six).
#   2. Build tiny images that only COPY the prebuilt JAR (Dockerfile.prebuilt) —
#      each image finishes in seconds because there is no Gradle inside.
#
# Usage:
#   ./build-images-fast.sh                 # build all six service images
#   ./build-images-fast.sh stock-service   # build only the given service(s)
#
set -euo pipefail

SERVICES=(eureka-server api-gateway auth-service stock-service venta-service despacho-service)
if [[ $# -gt 0 ]]; then
  SERVICES=("$@")
fi

# --- Locate a JDK 21 (Gradle 8.9 does not support Java 24) ------------------
if [[ -z "${JAVA_HOME:-}" ]] || ! "${JAVA_HOME}/bin/java" -version 2>&1 | grep -q '"21'; then
  for candidate in \
    /home/octavio/.vscode/extensions/redhat.java-*/jre/21.*-linux-x86_64 \
    /usr/lib/jvm/*-21-* /usr/lib/jvm/java-21-* \
    "$HOME"/.sdkman/candidates/java/21* ; do
    if [[ -x "$candidate/bin/javac" ]] && "$candidate/bin/java" -version 2>&1 | grep -q '"21'; then
      export JAVA_HOME="$candidate"
      break
    fi
  done
fi
echo "Using JAVA_HOME=${JAVA_HOME:-<default>}"

# --- 1) Build all JARs in a single Gradle run -------------------------------
BOOTJAR_TASKS=()
for s in "${SERVICES[@]}"; do
  BOOTJAR_TASKS+=(":${s}:bootJar")
done
echo "==> Building JARs: ${BOOTJAR_TASKS[*]}"
PATH="${JAVA_HOME:+$JAVA_HOME/bin:}$PATH" sh gradlew "${BOOTJAR_TASKS[@]}" --build-cache

# --- 2) Build slim images that just package the prebuilt JAR ----------------
export DOCKER_BUILDKIT=1
for s in "${SERVICES[@]}"; do
  echo "==> Building image test-sdd-${s} (prebuilt)"
  docker build \
    -f "${s}/Dockerfile.prebuilt" \
    -t "test-sdd-${s}" \
    "${s}"
done

echo "Done. Recreate containers with:"
echo "  docker compose up -d --no-deps --force-recreate ${SERVICES[*]}"
