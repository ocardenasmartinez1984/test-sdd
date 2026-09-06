#!/usr/bin/env bash
# View logs for the saga-auth-service container.
# Usage: ./logs-auth-service.sh [-N no-follow] [-n N] [-s TIME]   (live by default; see ./README.md)
CONTAINER=saga-auth-service source "$(dirname "$0")/_common.sh"
