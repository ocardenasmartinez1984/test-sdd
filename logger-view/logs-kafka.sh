#!/usr/bin/env bash
# View logs for the saga-kafka container.
# Usage: ./logs-kafka.sh [-N no-follow] [-n N] [-s TIME]   (live by default; see ./README.md)
CONTAINER=saga-kafka source "$(dirname "$0")/_common.sh"
