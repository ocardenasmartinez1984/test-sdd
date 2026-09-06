#!/usr/bin/env bash
# View logs for the saga-ventas-mantenedor container.
# Usage: ./logs-ventas-mantenedor.sh [-N no-follow] [-n N] [-s TIME]   (live by default; see ./README.md)
CONTAINER=saga-ventas-mantenedor source "$(dirname "$0")/_common.sh"
