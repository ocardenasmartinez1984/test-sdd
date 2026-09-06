#!/usr/bin/env bash
# View logs for the saga-despacho-service container.
# Usage: ./logs-despacho-service.sh [-N no-follow] [-n N] [-s TIME]   (live by default; see ./README.md)
CONTAINER=saga-despacho-service source "$(dirname "$0")/_common.sh"
