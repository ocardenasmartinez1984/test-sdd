#!/usr/bin/env bash
# View logs for the saga-pos-frontend container.
# Usage: ./logs-pos-frontend.sh [-N no-follow] [-n N] [-s TIME]   (live by default; see ./README.md)
CONTAINER=saga-pos-frontend source "$(dirname "$0")/_common.sh"
