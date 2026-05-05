# catalog-generator

Builds `src/main/resources/alloy/components.json`, the plugin's offline
component catalog. Run once per new Alloy release.

## How to run

```
./build-catalog.sh                    # uses the pinned default
ALLOY_VERSION=v1.9.2 ./build-catalog.sh
```

Commit the updated JSON (and, if you bumped the pin, this script).

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
`AlloyCatalog` data classes for the consumer side.
