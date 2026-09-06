#!/usr/bin/env bash
# View logs for the saga-postgres container.
# Usage: ./logs-postgres.sh [-N no-follow] [-n N] [-s TIME]   (live by default; see ./README.md)
CONTAINER=saga-postgres source "$(dirname "$0")/_common.sh"
