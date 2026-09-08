# catalog-generator

Builds the plugin's offline component catalogs under
`src/main/resources/alloy/catalogs/<version>/components.json`, plus the
`manifest.json` that lists which versions are bundled and which is the default.
The plugin ships **several** catalogs so users can pick the Alloy version the
IDE validates against (see PLAN.md §8).

## How to run

```
./build-catalog.sh                            # default pinned version
ALLOY_VERSION=v1.19.2 ./build-catalog.sh      # single version via env var
./build-catalog.sh v1.17.1 v1.18.1 v1.19.2    # several versions in one run
./build-catalog.sh --manifest-only            # rebuild manifest.json from disk
```

Each version is written to its own `catalogs/<version>/components.json`. The
`manifest.json` is regenerated afterwards by scanning the directories on disk,
so it can never list a version whose catalog isn't actually present; the
`defaultVersion` is the highest bundled version by semantic-version order.

Commit the updated JSON (and, if you bumped the pin, this script). Keep the set
small — a rolling window of 3–4 recent minor releases plus whatever older
version you still support (~500 KB each; 4 versions ≈ 2 MB).

## Requirements

- **Go 1.24+** and **git** — to clone Alloy and run the reflection-based generator.
- **python3** — to (re)generate `manifest.json`.

## Why the indirection

Alloy's `internal/component` package can only be imported from inside the
`github.com/grafana/alloy` module — and Alloy's `go.mod` has ~24 `replace`
directives that don't transit to downstream modules. Rather than fight that,
the script:

1. Shallow-clones Alloy at the pinned tag into a temp dir.
2. Stages `main.go` into `alloy/internal/cmd/catalog-generator/`.
3. Runs it from inside the Alloy checkout, so the generator uses Alloy's
   fully-resolved `go.mod`.
4. Writes the JSON into this plugin's resources.

Nothing is committed inside the Alloy checkout; the temp dir is removed on
exit.

## What gets emitted

For each registered component: name, namespace, stability, community flag,
`args`, nested `blocks` (recursively), `exports`, and the accepted/exported
port types as surfaced by `internal/component/metadata`. See the Kotlin
`AlloyCatalog` / `AlloyCatalogManifest` data classes for the consumer side.
