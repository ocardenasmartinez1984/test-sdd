#!/usr/bin/env bash
#
# _common.sh — shared log viewer used by every per-container script in this
# folder. Not meant to be run directly; each logs-<service>.sh sources it.
#
# Usage (from a per-container script):
#   CONTAINER=saga-<name> source "$(dirname "$0")/_common.sh"
#
# Flags forwarded from the per-container script:
#   -f, --follow        Stream logs live (default: ON)
#   -N, --no-follow     Print current logs and exit (disable live streaming)
#   -n, --tail N        Show the last N lines (default: 200)
#   -s, --since TIME    Show logs since TIME (e.g. 10m, 1h, 2026-09-05T12:00)
#   -h, --help          Show help
#
set -euo pipefail

: "${CONTAINER:?_common.sh must be sourced with CONTAINER set}"

TAIL=200
FOLLOW=1
SINCE=""

while [[ $# -gt 0 ]]; do
  case "$1" in
    -f|--follow) FOLLOW=1; shift ;;
    -N|--no-follow) FOLLOW=0; shift ;;
    -n|--tail)   TAIL="${2:?--tail needs a value}"; shift 2 ;;
    -s|--since)  SINCE="${2:?--since needs a value}"; shift 2 ;;
    -h|--help)
      echo "View logs for container: $CONTAINER (live by default)"
      echo "Options: -N/--no-follow  -n/--tail N (default 200)  -s/--since TIME"
      exit 0 ;;
    *) echo "Unknown option: $1" >&2; exit 2 ;;
  esac
done

# Verify the container exists so we can give a friendly message.
if ! docker inspect "$CONTAINER" >/dev/null 2>&1; then
  echo "Container '$CONTAINER' not found. Is the stack running? (docker compose ps)" >&2
  exit 1
fi

ARGS=(logs)
[[ "$FOLLOW" -eq 1 ]] && ARGS+=(--follow)
[[ -n "$SINCE" ]] && ARGS+=(--since "$SINCE")
ARGS+=(--tail "$TAIL" "$CONTAINER")

exec docker "${ARGS[@]}"
