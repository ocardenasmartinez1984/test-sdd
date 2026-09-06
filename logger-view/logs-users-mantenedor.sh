#!/usr/bin/env bash
# View logs for the saga-users-mantenedor container.
# Usage: ./logs-users-mantenedor.sh [-N no-follow] [-n N] [-s TIME]   (live by default; see ./README.md)
CONTAINER=saga-users-mantenedor source "$(dirname "$0")/_common.sh"
