#!/usr/bin/env bash
# Builds src/main/resources/alloy/catalogs/<version>/components.json for one or more
# Alloy releases, then refreshes the manifest.json next to them.
#
# Requires: go (1.24+), git, python3. Does NOT modify your plugin checkout other than
# (re)writing the per-version JSON + manifest.json. Uses a temp clone of grafana/alloy.
#
# Usage:
#   ./build-catalog.sh                              # default pinned version
#   ALLOY_VERSION=v1.19.2 ./build-catalog.sh        # single version via env var
#   ./build-catalog.sh v1.17.1 v1.18.1 v1.19.2      # several versions in one run
#   ./build-catalog.sh --manifest-only              # just rebuild manifest.json from disk
set -euo pipefail

HERE="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PLUGIN_ROOT="$(cd "$HERE/.." && pwd)"
CATALOGS_DIR="$PLUGIN_ROOT/src/main/resources/alloy/catalogs"

# Scratch dir of the in-progress version, cleaned up on exit even when `set -e` aborts mid-run
# (git clone / go run can fail on a flaky network) so we never leak a multi-MB Alloy clone.
CURRENT_WORK=""
cleanup() { [ -n "$CURRENT_WORK" ] && rm -rf "$CURRENT_WORK"; CURRENT_WORK=""; }
trap cleanup EXIT

# Rebuild manifest.json by scanning every catalogs/<version>/components.json on disk.
# The default version is the highest by semantic-version order. Keeping the manifest
# filesystem-derived means it can never list a version whose catalog isn't actually present.
regen_manifest() {
  python3 - "$CATALOGS_DIR" <<'PY'
import json, os, re, sys
root = sys.argv[1]
entries = []
if os.path.isdir(root):
    for name in os.listdir(root):
        path = os.path.join(root, name, "components.json")
        if not os.path.isfile(path):
            continue
        try:
            with open(path) as fh:
                cat = json.load(fh)
        except Exception as exc:
            # A single empty/corrupt catalog dir must not sink the whole manifest rebuild.
            sys.stderr.write("[build-catalog] skipping unreadable %s: %s\n" % (path, exc))
            continue
        entries.append({
            "version": cat.get("alloyVersion", name),
            "components": len(cat.get("components", [])),
            "generatedAt": cat.get("generatedAt", ""),
        })

def semver_key(entry):
    m = re.match(r"v?(\d+)\.(\d+)\.(\d+)", entry["version"])
    return tuple(int(x) for x in m.groups()) if m else (0, 0, 0)

entries.sort(key=semver_key)
manifest = {
    "defaultVersion": entries[-1]["version"] if entries else "",
    "versions": entries,
}
out = os.path.join(root, "manifest.json")
with open(out, "w") as fh:
    json.dump(manifest, fh, indent=2)
    fh.write("\n")
sys.stderr.write("[build-catalog] wrote manifest with %d version(s): %s\n"
                 % (len(entries), ", ".join(e["version"] for e in entries)))
PY
}

generate_one() {
  local version="$1"
  local out stage tmp
  CURRENT_WORK="$(mktemp -d)"
  out="$CATALOGS_DIR/$version/components.json"
  echo "[build-catalog] === $version ===" >&2
  echo "[build-catalog] scratch dir: $CURRENT_WORK" >&2

  echo "[build-catalog] cloning Alloy $version..." >&2
  git clone --depth 1 --branch "$version" https://github.com/grafana/alloy.git "$CURRENT_WORK/alloy" >&2

  stage="$CURRENT_WORK/alloy/internal/cmd/catalog-generator"
  mkdir -p "$stage"
  cp "$HERE/main.go" "$stage/main.go"
  cp -R "$HERE/syntaxtags" "$stage/syntaxtags"

  echo "[build-catalog] compiling + running generator (downloading Alloy deps, may take a while)..." >&2
  # Generate into a scratch file and promote to the committed resource only on success. Under
  # `set -e` a failed `go run` aborts before the `mv`, so a bad run never leaves a truncated
  # (0-byte) components.json in the resources tree.
  tmp="$CURRENT_WORK/components.json"
  ( cd "$CURRENT_WORK/alloy" && ALLOY_VERSION="$version" go run ./internal/cmd/catalog-generator ) > "$tmp"
  mkdir -p "$(dirname "$out")"
  mv "$tmp" "$out"

  echo "[build-catalog] wrote $out" >&2
  rm -rf "$CURRENT_WORK"; CURRENT_WORK=""
}

# --------------------------------------------------------------------------------------------

if [ "${1:-}" = "--manifest-only" ]; then
  regen_manifest
  exit 0
fi

if [ "$#" -gt 0 ]; then
  VERSIONS=("$@")
else
  VERSIONS=("${ALLOY_VERSION:-v1.19.2}")
fi

for v in "${VERSIONS[@]}"; do
  generate_one "$v"
done

regen_manifest
