#!/usr/bin/env bash
# View logs for the saga-venta-service container.
# Usage: ./logs-venta-service.sh [-N no-follow] [-n N] [-s TIME]   (live by default; see ./README.md)
CONTAINER=saga-venta-service source "$(dirname "$0")/_common.sh"
