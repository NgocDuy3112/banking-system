#!/usr/bin/env bash

set -euo pipefail

cd "$(dirname "$0")"

if [[ -z "${DOCKER_HOST:-}" ]]; then
  SOCK="$(find ~/.local/share/containers -name 'podman.sock' 2>/dev/null | head -1)"
  if [[ -z "$SOCK" ]]; then
    echo "Np podman.sock found. Run podman machine start" >&2
    exit 1
  fi
  DOCKER_HOST="unix://$SOCK"
fi
export DOCKER_HOST

export TESTCONTAINERS_RYUK_DISABLED=true

./gradlew --stop >/dev/null 2>&1 || true

exec ./gradlew test "$@"