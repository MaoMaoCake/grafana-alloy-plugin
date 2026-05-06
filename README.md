# Grafana Alloy — JetBrains Plugin

Language support for [Grafana Alloy](https://grafana.com/docs/alloy/latest/)
configuration files (`*.alloy`) in IntelliJ IDEA, GoLand, PyCharm, WebStorm,
and other IntelliJ-family IDEs.

> This plugin is free and built in spare time. If it's useful to you, please
> consider sponsoring — see [Sponsor](#sponsor) below.

## Features

- **Parser + syntax highlighting** with distinct per-namespace colors
  (`prometheus.*`, `loki.*`, `otelcol.*`, `discovery.*`, …). Nested blocks
  (`endpoint`, `basic_auth`, …) and attribute keys (`url`, `targets`,
  `forward_to`, …) each get their own color.
- **Completion**:
  - Component names at file top level and inside `declare` module bodies.
  - Arguments and nested blocks inside a known component body (scope-aware —
    `loki.write` won't suggest `prometheus.*`).
  - **Port-type-aware reference completion**: inside
    `forward_to = [ … ]` only components exporting a compatible
    `MetricsReceiver` / `LogsReceiver` / `otelcol.Consumer` / etc. show up.
  - Value templates per attribute type: strings get `""`, lists get `[]`, etc.
- **Navigation + refactoring**:
  - Go-to-definition on dotted references
    (`prometheus.remote_write.rw.receiver` → the declaring block).
  - `declare "foo" { … }` ↔ `foo "instance" { … }` navigation for modules.
  - Rename a block label and every reference updates (across files in the
    same directory).
  - Find Usages.
- **Cross-file support**: all of the above works across `*.alloy` files in
  the same directory, matching `alloy validate <dir>`'s scoping rule.
- **Inspections**:
  - Duplicate labels, unresolved references, unknown components.
  - Unknown or missing-required arguments (catalog-driven).
  - Port-type mismatches on references.
  - Stability warnings for public-preview / experimental components.
- **Inline docs** (Ctrl/Cmd-Q) on component blocks, attribute keys and dotted
  references: stability, Go type, port types, arg tables, docs URL.
- **Editor essentials**: brace matcher, folding, commenter (`//`, `/* */`),
  structure view grouped by component.

## Installation

Install from a zip (Marketplace listing is on its way):

1. Download `grafana-alloy-plugin-<version>.zip` from
   [build/distributions/](./build/distributions/) or a GitHub release.
2. In your IDE: **Settings → Plugins → ⚙ → Install Plugin from Disk…** and
   pick the zip.
3. Restart when prompted.

Compatibility verified against every minor IntelliJ Platform release from
**2023.1 through 2025.2** (`IC`, `IU`, and sibling IDEs like GoLand,
PyCharm, WebStorm that share the platform).

## The component catalog

The plugin bundles a JSON catalog of every registered Alloy component —
name, namespace, stability, args, nested blocks, exports, and accepted /
exported port types. This is what powers completion, inspections and inline
docs, and it's why all of that works offline with no network calls.

The catalog is generated from the upstream Alloy Go source (not scraped from
docs — Alloy's argument tables are hand-written), pinned to a tagged
release. See [`catalog-generator/`](./catalog-generator/) for the generator;
regenerate on every Alloy version bump.

## Developing

Use the Gradle wrapper:

```bash
./gradlew test -x buildSearchableOptions          # JUnit suite (40 tests)
./gradlew runIde -x buildSearchableOptions        # sandbox IDE with the plugin
./gradlew verifyPlugin -x buildSearchableOptions  # Plugin Verifier vs all target IDEs
./gradlew buildPlugin -x buildSearchableOptions   # build the distributable zip
```

Always pass `-x buildSearchableOptions` — that task spawns a sandbox IDE
that collides with any IDE you already have open.

See [`CLAUDE.md`](./CLAUDE.md) for build/toolchain details (Kotlin + JVM
target, Grammar-Kit wiring, etc.) and [`PLAN.md`](./PLAN.md) for the
longer-term roadmap (external `alloy validate` shellout, embedded Alloy web
UI tool window, …).

## Sponsor

Donations go toward the AI-assist bill that makes this plugin feasible in
spare time. If the plugin helps you, a few bucks helps keep it shipping:

[ko-fi.com/jirapongp](https://ko-fi.com/jirapongp)

You can also sponsor from inside the IDE: **Help → Sponsor the Grafana
Alloy Plugin**.

## Status and scope

Shipped as of v0.1.0: parser, highlighting, completion, inspections, inline
docs, cross-file references, IDE essentials. Planned (see `PLAN.md`):

- **External validator**: on-demand / on-save `alloy validate` shellout,
  gated behind OS detection (macOS/Linux only — Windows binaries don't ship
  the subcommand) and a configurable binary path, with stderr parsed into
  editor annotations.
- **Embedded Alloy web UI**: a tool window backed by `JBCefBrowser`
  pointed at `http://localhost:12345`.
- **Richer inline docs**: per-argument prose descriptions parsed from the
  upstream `//` Go comments.

## License

TBD — currently unreleased. The upstream struct-tag grammar
([`catalog-generator/syntaxtags/syntaxtags.go`](./catalog-generator/syntaxtags/syntaxtags.go))
is vendored verbatim from `grafana/alloy` under Apache-2.0.
