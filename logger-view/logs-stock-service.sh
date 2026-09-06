#!/usr/bin/env bash
# View logs for the saga-stock-service container.
# Usage: ./logs-stock-service.sh [-N no-follow] [-n N] [-s TIME]   (live by default; see ./README.md)
CONTAINER=saga-stock-service source "$(dirname "$0")/_common.sh"
