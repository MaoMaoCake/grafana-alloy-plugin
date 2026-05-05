#!/usr/bin/env bash
# Builds src/main/resources/alloy/components.json.
#
# Requires: go (1.24+), git. Does NOT modify your plugin checkout other than
# (re)writing the JSON output. Uses a temp clone of grafana/alloy.
#
# Usage:
#   ./build-catalog.sh              # uses default pinned version
#   ALLOY_VERSION=v1.9.2 ./build-catalog.sh
set -euo pipefail

ALLOY_VERSION="${ALLOY_VERSION:-v1.9.2}"
HERE="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PLUGIN_ROOT="$(cd "$HERE/.." && pwd)"
OUT_FILE="$PLUGIN_ROOT/src/main/resources/alloy/components.json"

WORK="$(mktemp -d)"
trap 'rm -rf "$WORK"' EXIT
echo "[build-catalog] ALLOY_VERSION=$ALLOY_VERSION" >&2
echo "[build-catalog] scratch dir: $WORK" >&2

echo "[build-catalog] cloning Alloy..." >&2
git clone --depth 1 --branch "$ALLOY_VERSION" https://github.com/grafana/alloy.git "$WORK/alloy" >&2

STAGE_DIR="$WORK/alloy/internal/cmd/catalog-generator"
mkdir -p "$STAGE_DIR"
cp "$HERE/main.go" "$STAGE_DIR/main.go"
cp -R "$HERE/syntaxtags" "$STAGE_DIR/syntaxtags"

mkdir -p "$(dirname "$OUT_FILE")"
echo "[build-catalog] compiling + running generator (downloading Alloy deps, may take a while)..." >&2
( cd "$WORK/alloy" && ALLOY_VERSION="$ALLOY_VERSION" go run ./internal/cmd/catalog-generator ) > "$OUT_FILE"

echo "[build-catalog] wrote $OUT_FILE" >&2
