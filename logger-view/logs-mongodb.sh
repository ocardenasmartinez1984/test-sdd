#!/usr/bin/env bash
# View logs for the saga-mongodb container.
# Usage: ./logs-mongodb.sh [-N no-follow] [-n N] [-s TIME]   (live by default; see ./README.md)
CONTAINER=saga-mongodb source "$(dirname "$0")/_common.sh"
