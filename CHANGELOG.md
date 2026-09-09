# Changelog

All notable changes to the Grafana Alloy JetBrains plugin are documented here.
The format roughly follows [Keep a Changelog](https://keepachangelog.com/en/1.1.0/);
versions track the JetBrains Marketplace releases.

## [Unreleased]

### Added

- **Multi-version Alloy catalog support.** The plugin now bundles component
  catalogs for several Alloy releases (v1.9.2, v1.17.1, v1.18.1, v1.19.2) instead
  of a single pinned one. A new *Settings → Languages & Frameworks → Alloy →
  Version* page lets each project choose which catalog drives completion,
  inspections, and inline docs:
  - **Latest bundled** (default) — always the newest catalog shipped in the plugin.
  - **Auto-detect** — probes the configured `alloy` binary's `--version` (off the
    EDT) and picks the nearest bundled catalog.
  - a **pinned version** — for teams targeting an older/other release than the one
    installed locally.

  Changing the version — or the binary path on the *Validate* page while in
  auto-detect mode — re-runs inspections against the newly-selected catalog. This
  stops false positives on older deployments (a v1.19-only argument flagged in a
  v1.9 config) and surfaces the completions that match the version you actually
  run. The catalog service is now project-level, so different projects can target
  different Alloy versions.

- **Version-mismatch inspection with a "switch version" quick-fix.** When a
  component, argument, or nested block isn't in the selected catalog but *is*
  available in another bundled version, it's now flagged as an **error** that
  names the versions offering it (e.g. `Component otelcol.exporter.file is not
  available in the selected Alloy version (v1.9.2). Available in: v1.17.1,
  v1.18.1, v1.19.2.`) — instead of the previous vague "unknown" — with a
  one-click **Switch Alloy catalog version to vX** fix per candidate version.
  This works in both directions: features added in a newer release *and* features
  removed from it. Names unknown in every bundled version keep the plain "unknown"
  warning.

## [0.3.3] — 2026-09-07

### Fixed

- **Duplicate-label errors were file-scoped instead of block-scoped.** A
  label only has to be unique within its enclosing scope, so two `declare`
  modules could each legitimately declare `export "environment"` and
  `argument "filename"` — but both were wrongly flagged as duplicates.
  Duplicate detection is now keyed by the enclosing block (or the file, for
  top-level components), so a label only clashes with a sibling in the same
  scope.

## [0.3.2] — 2026-09-07

### Fixed

- **False `Unknown nested block` warnings on dotted `loki.process` stages.**
  Blocks written with a dotted name — `stage.json`, `stage.labels`, and the
  other `stage.*` variants — were flagged as unknown even though they're
  valid. The catalog models these as nested paths (`stage > json`), while the
  PSI exposes the written name as a single dotted segment; the lookup now
  expands dotted nested-block names against the catalog when there's no exact
  match. As a side effect, the contents of these blocks (e.g. `expressions`,
  `values`) are now validated too. Thanks @ArmandoSchiano (#2).

## [0.3.1] — 2026-06-10

### Changed

- Raised the minimum supported IDE to the 2025.2 branch
  (`sinceBuild` 231 → 252) and refreshed the generated lexer against the
  newer platform.

## [0.3.0] — 2026-06-09

This release is about meeting Alloy where it actually runs in production:
out of the editor and into a Kubernetes ConfigMap, or as a live process
behind a debug UI. Three big additions land together — `alloy validate`,
`alloy run`, and full editor support inside YAML ConfigMaps.

### Added

- **External `alloy validate` integration** (macOS / Linux only — the
  `validate` subcommand doesn't ship in Windows binaries).
  - Right-click an `*.alloy` file or a folder in the Project View →
    *Validate '…'* runs `alloy validate` against just that target.
    Available from the editor right-click and *Tools* menus too.
  - **Optional on-idle trigger** that re-runs the validator after typing
    pauses, mapping each error onto the right `line:col` as a red
    squiggle.
  - **Alloy Validate tool window** — each diagnostic is a clickable
    `path:line:col` hyperlink that jumps the editor to the offending
    location, with the raw stderr (carets, surrounding lines) below.
  - Notification balloon summarising up to 5 issues, with a
    *Show details* button that opens the console.
  - Configurable in *Settings → Languages & Frameworks → Alloy →
    Validate*: binary path (blank uses `PATH`), trigger mode,
    `--stability.level`, `--feature.community-components.enabled`,
    plus a *Test binary* probe.
- **`alloy run` integration** — launch a real Alloy instance from the IDE.
  - Right-click an `*.alloy` file or a folder → *Run with Alloy* spawns
    `alloy run` against the target.
  - Process output streams into the standard **Run** tool window
    (Stop / Rerun toolbar wired automatically) — config errors that
    prevent Alloy from coming up are visible in real time.
  - **Auto port selection** starting at `12345` (Alloy's default),
    walking up to the first free port. Bound to `127.0.0.1` only.
  - **Embedded Alloy UI** in a dedicated tool window backed by
    `JBCefBrowser`, with Back / Forward / Reload / Home buttons (Home
    jumps back to the running instance after you click out to
    grafana.com docs). Falls back to "Open in system browser" on IDE
    runtimes without JCEF.
  - Per-run `--storage.path` pointed at a tempdir that's cleaned up on
    stop, so `data-alloy/` never appears next to your config.
- **Kubernetes ConfigMap support** — every existing feature
  (highlighting, completion, references, inspections, Cmd-Q docs) now
  works inside YAML scalars under keys named `config.alloy` or `*.alloy`
  (the default key in the upstream Alloy Helm chart).
  - **Block scalars** (`|`, `|-`, `|+`, `>`) — what `helm template` and
    hand-authored ConfigMaps usually produce.
  - **Multi-line quoted scalars** wrapped across editor lines — what
    `kubectl get cm -o yaml` and the bundled Kubernetes plugin's
    *Services* tool window produce when round-tripping a live ConfigMap.
  - **Auto-convert on file open** for single-line quoted scalars (rare,
    pasted-snippet shape) — they're rewritten to `|` block form so the
    editor experience is live the moment you open the file. The
    conversion drops only meaningless trailing whitespace; Alloy doesn't
    care about it. Read-only files are skipped, and the behaviour can be
    disabled under *Settings → Languages & Frameworks → Alloy →
    ConfigMap injection* if bytes-exact cluster round-trips matter.
  - Inspection + manual *Convert to `|` block scalar* quick fix as
    fallback for read-only files or when auto-convert is off.

### Changed

- Settings tree under *Languages & Frameworks → Alloy* now nests
  *Validate* and *ConfigMap injection* pages alongside the existing
  envfile settings.
- Notification group for Alloy actions consolidated under
  `Grafana Alloy` so users can mute it in a single setting.

### Notes

- The Kubernetes plugin (Ultimate-only) is not a hard dependency. The
  injection works in any IntelliJ-family IDE that bundles YAML; the
  *Services*-tool-window integration for live cluster editing is a
  separate Phase-2 item still on the roadmap.
- The `alloy run` Run Configuration item from the original M8 plan is
  obsolete — the right-click action covers that use case directly.

## [0.2.0] — earlier

- Per-namespace highlighting, parser, completion (component / argument /
  reference), port-type-aware reference completion, declare-aware scoping.
- Cross-file completion across `*.alloy` files in the same directory.
- Inspections: duplicate labels, unresolved references, unknown
  components, unknown / missing-required arguments, port-type mismatches,
  stability warnings.
- Inline docs (Cmd/Ctrl-Q) with stability, port types, arg tables,
  docs-page links.
- Envfile templating: completion inside `${…}` and unknown-var warnings.
- Editor essentials: brace matcher, folding, commenter, structure view.
- Plugin Verifier compatibility from IDEA 2023.1 through 2026.1.

## [0.1.0]

- Initial release scaffolded from the IntelliJ Platform Plugin Template.

[0.3.0]: https://github.com/maomaocake/grafana-alloy-plugin/releases/tag/v0.3.0
[0.2.0]: https://github.com/maomaocake/grafana-alloy-plugin/releases/tag/v0.2.0
[0.1.0]: https://github.com/maomaocake/grafana-alloy-plugin/releases/tag/v0.1.0
