#!/usr/bin/env bash
# View logs for the saga-api-gateway container.
# Usage: ./logs-api-gateway.sh [-N no-follow] [-n N] [-s TIME]   (live by default; see ./README.md)
CONTAINER=saga-api-gateway source "$(dirname "$0")/_common.sh"
