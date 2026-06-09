# Grafana Alloy — JetBrains Plugin

Language support for [Grafana Alloy](https://grafana.com/docs/alloy/latest/)
configuration files (`*.alloy`) in IntelliJ IDEA, GoLand, PyCharm, WebStorm,
and other IntelliJ-family IDEs.

[![Install from JetBrains Marketplace](https://img.shields.io/badge/Install%20from-JetBrains%20Marketplace-000000?logo=jetbrains&logoColor=white)](https://plugins.jetbrains.com/plugin/31630-grafana-alloy-configuration)
[![Version](https://img.shields.io/jetbrains/plugin/v/31630-grafana-alloy-configuration?logo=jetbrains)](https://plugins.jetbrains.com/plugin/31630-grafana-alloy-configuration)
[![Downloads](https://img.shields.io/jetbrains/plugin/d/31630-grafana-alloy-configuration?logo=jetbrains)](https://plugins.jetbrains.com/plugin/31630-grafana-alloy-configuration)
[![Rating](https://img.shields.io/jetbrains/plugin/r/rating/31630-grafana-alloy-configuration?logo=jetbrains)](https://plugins.jetbrains.com/plugin/31630-grafana-alloy-configuration)

> This plugin is free and built in spare time. If it's useful to you, please
> consider sponsoring — see [Sponsor](#sponsor) below.

## Install

From inside your JetBrains IDE:

`Settings → Plugins → Marketplace → search Grafana Alloy Configuration`

Or install from a zip (`Settings → Plugins → ⚙ → Install Plugin from Disk…`)
using a release zip from [GitHub Releases](https://github.com/maomaocake/grafana-alloy-plugin/releases).

**Compatibility**: verified against every minor IntelliJ Platform release
from **2023.1 through 2026.1** (`IC`, `IU`, and sibling IDEs like GoLand,
PyCharm, WebStorm, CLion, and others that share the platform).

## Features

- **Parser + syntax highlighting** with distinct per-namespace colors
  (`prometheus.*`, `loki.*`, `otelcol.*`, `discovery.*`, …). Nested blocks
  (`endpoint`, `basic_auth`, …) and attribute keys (`url`, `targets`,
  `forward_to`, …) each get their own color.
- **Completion**
  - Component names at file top level and inside `declare` module bodies.
  - Arguments and nested blocks inside a known component body (scope-aware —
    `loki.write` won't suggest `prometheus.*`).
  - **Port-type-aware reference completion**: inside `forward_to = [ … ]`
    only components exporting a compatible `MetricsReceiver` /
    `LogsReceiver` / `otelcol.Consumer` / `Targets` / `ProfilesReceiver`
    show up.
  - Value templates per attribute type: strings get `""`, lists get `[]`,
    etc.
  - Automatic `}` closing when accepting an env-var completion.
- **Envfile templating**
  - Point *Settings → Languages & Frameworks → Alloy* at a dotenv file.
  - `${<caret>}` inside any Alloy string completes from the envfile's keys.
  - Unknown `${VAR}` placeholders get a yellow warning.
  - Optional "show values in completion" toggle (off by default so secret
    values don't leak into the popup on a screenshare).
  - Envfile is constrained to the project root and size-capped at 1 MiB for
    safety.
- **Navigation + refactoring**
  - Go-to-definition on dotted references
    (`prometheus.remote_write.rw.receiver` → the declaring block).
  - `declare "foo" { … }` ↔ `foo "instance" { … }` navigation for modules.
  - Rename a block label and every reference updates — across files and
    across `declare` module scopes.
  - Find Usages.
- **Scoping**: references respect Alloy's `declare` module boundaries. A
  reference inside a `declare "foo" { … }` body resolves only to blocks
  inside that same module; top-level references skip module-internal blocks.
- **Cross-file support**: all of the above works across `*.alloy` files in
  the same directory, matching `alloy validate <dir>`'s scoping rule.
- **Inspections**
  - Duplicate labels, unresolved references, unknown components.
  - Unknown / missing-required arguments (catalog-driven).
  - Port-type mismatches on references.
  - Stability warnings for public-preview / experimental components.
- **External validator** (macOS / Linux only — `alloy validate` doesn't ship
  in Windows binaries)
  - Right-click an `*.alloy` file or a folder in the Project View →
    *Validate '…'* runs `alloy validate` against just that target.
  - Also available from *Tools → Validate Alloy Config* and from the editor
    right-click menu.
  - Optional **on-idle** trigger that re-runs the validator after typing
    pauses, mapping each error onto the right `line:col` as a red squiggle.
  - Results land in an **Alloy Validate** tool window: each diagnostic is a
    clickable `path:line:col` hyperlink that jumps the editor to the
    offending location, with the raw stderr (carets, surrounding lines)
    rendered underneath.
  - A balloon summarises up to 5 issues with a *Show details* button that
    opens the console.
  - Configurable in *Settings → Languages & Frameworks → Alloy → Validate*:
    binary path (blank uses `PATH`), trigger mode, `--stability.level`,
    `--feature.community-components.enabled`, plus a *Test binary* probe.
- **Run with Alloy** — launch a real Alloy instance from the IDE
  - Right-click an `*.alloy` file or a folder → *Run with Alloy* spawns
    `alloy run` against the target. Also available from *Tools* and the
    editor right-click menu.
  - Process output streams into the standard **Run** tool window (Stop /
    Rerun toolbar wired automatically) — config errors that prevent Alloy
    from coming up are visible in real time, with line/column prefixes.
  - **Auto port selection**: starts at `12345` (Alloy's default) and walks
    up to the first free port if it's busy. Bound to `127.0.0.1` only.
  - **Embedded Alloy UI** in a dedicated tool window backed by `JBCefBrowser`,
    with Back / Forward / Reload / Home buttons (Home jumps back to the
    running instance after you click out to grafana.com docs). Falls back
    to "Open in system browser" on IDE runtimes without JCEF.
  - Per-run `--storage.path` pointed at a tempdir, cleaned up on stop, so
    `data-alloy/` never appears next to your config or in your VCS diff.
- **Kubernetes ConfigMap support**: every feature above (highlighting,
  completion, references, inspections, Cmd-Q docs) works inside YAML
  scalars under keys named `config.alloy` or `*.alloy` — the default
  key in the upstream Alloy Helm chart. Open a `ConfigMap` and edit
  its embedded Alloy as if it were a `*.alloy` file. Both shapes are
  handled:
  - **Block scalars** (`|`, `|-`, `|+`, `>`) — what `helm template`
    and hand-authored ConfigMaps usually produce.
  - **Multi-line quoted scalars** (`"...\n..."` wrapped across lines)
    — what `kubectl get cm -o yaml` and the Kubernetes plugin's
    *Services* tool window produce when round-tripping a live
    ConfigMap.

  Single-line quoted scalars (rare — usually pasted snippets) are
  **auto-converted** to `|` block scalars on file open, so the full
  editor experience kicks in immediately. The conversion is byte-
  identical apart from dropping meaningless trailing whitespace
  (Alloy doesn't care about whitespace, so config behaviour is
  unchanged). Read-only files are skipped, and you can disable the
  behaviour under *Settings → Languages & Frameworks → Alloy →
  ConfigMap injection* if you want bytes-exact round-trips with the
  cluster — the manual *Convert to `|` block scalar* quick fix still
  shows on the inspection in that case.
- **Inline docs** (Ctrl/Cmd-Q) on component blocks, attribute keys, and
  dotted references: stability, Go type, port types, arg tables, docs URL.
  All content is HTML-escaped to avoid popup injection from malicious
  labels.
- **Editor essentials**: brace matcher, folding, commenter (`//`, `/* */`),
  structure view grouped by component.

## The component catalog

The plugin bundles a JSON catalog of every registered Alloy component —
name, namespace, stability, args, nested blocks, exports, and accepted /
exported port types. This powers completion, inspections and inline docs,
and it's why all of that works offline with no network calls.

The catalog is generated from the upstream Alloy Go source (not scraped
from docs — Alloy's argument tables are hand-written), pinned to a tagged
release. See [`catalog-generator/`](./catalog-generator/) for the
generator; regenerate on every Alloy version bump.

## Developing

Use the Gradle wrapper:

```bash
./gradlew test -x buildSearchableOptions          # JUnit suite
./gradlew runIde -x buildSearchableOptions        # sandbox IDE with the plugin
./gradlew verifyPlugin -x buildSearchableOptions  # Plugin Verifier vs all target IDEs
./gradlew buildPlugin -x buildSearchableOptions   # build the distributable zip
```

Always pass `-x buildSearchableOptions` — that task spawns a sandbox IDE
that collides with any IDE you already have open.

See [`CLAUDE.md`](./CLAUDE.md) for build/toolchain details (Kotlin + JVM
target, Grammar-Kit wiring, etc.) and [`PLAN.md`](./PLAN.md) for the
longer-term roadmap (external `alloy validate` shellout, embedded Alloy
web UI tool window, multi-version catalog support, …).

## Sponsor

Donations go toward the AI-assist bill that makes this plugin feasible in
spare time. If the plugin helps you, a few bucks helps keep it shipping:

[ko-fi.com/jirapongp](https://ko-fi.com/jirapongp)

You can also sponsor from inside the IDE: **Help → Sponsor the Grafana
Alloy Plugin**.

## Status and scope

Shipped so far: parser, per-namespace highlighting, completion (including
port-type- and declare-aware reference completion), inspections, inline
docs, cross-file references, envfile templating, the `alloy validate`
shellout integration, the `alloy run` integration with embedded UI tool
window, Kubernetes ConfigMap injection, and IDE essentials.

Planned (see [`PLAN.md`](./PLAN.md)):

- **Richer inline docs**: per-argument prose descriptions parsed from the
  upstream `//` Go comments.
- **Multi-version catalog**: project-level setting to pick which Alloy
  version's schema drives completion and inspections, so configs targeted
  at older fleets don't false-positive on newer-only arguments.
- **No-destination / dead-config warnings**: flag a `prometheus.remote_write`
  block that nothing forwards to, or a labeled block that's never referenced.
- **Secrets-in-plaintext detector**: catalog already knows which arguments
  are `alloytypes.Secret`; warn when one gets a literal string value
  instead of `sys.env(…)` / `local.file(…)` / `${VAR}`.
- **Kubernetes Services-tool-window integration** (Phase 2 of ConfigMap
  support): right-click a live `ConfigMap` in PyCharm Pro / IDEA Ultimate's
  *Services* tree → edit the embedded Alloy with full plugin support, run
  validate against the live content, push back to the cluster.

## License

Licensed under the [Apache License 2.0](https://www.apache.org/licenses/LICENSE-2.0).

The upstream struct-tag grammar
([`catalog-generator/syntaxtags/syntaxtags.go`](./catalog-generator/syntaxtags/syntaxtags.go))
is vendored verbatim from [`grafana/alloy`](https://github.com/grafana/alloy),
also under Apache-2.0.
