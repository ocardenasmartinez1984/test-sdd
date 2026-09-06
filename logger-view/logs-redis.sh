#!/usr/bin/env bash
# View logs for the saga-redis container.
# Usage: ./logs-redis.sh [-N no-follow] [-n N] [-s TIME]   (live by default; see ./README.md)
CONTAINER=saga-redis source "$(dirname "$0")/_common.sh"
