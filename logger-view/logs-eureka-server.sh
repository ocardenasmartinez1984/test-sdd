#!/usr/bin/env bash
# View logs for the saga-eureka-server container.
# Usage: ./logs-eureka-server.sh [-N no-follow] [-n N] [-s TIME]   (live by default; see ./README.md)
CONTAINER=saga-eureka-server source "$(dirname "$0")/_common.sh"
