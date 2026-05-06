# PLAN.md — Grafana Alloy JetBrains Plugin

Implementation plan for a JetBrains IDE plugin that makes writing and editing Grafana Alloy configuration files a first-class experience. This document tracks design decisions; it is not a user-facing changelog.

Reference material:

- IntelliJ Platform SDK: https://plugins.jetbrains.com/docs/intellij/welcome.html
- Alloy syntax: https://grafana.com/docs/alloy/latest/get-started/configuration-syntax/
- Component catalog: https://grafana.com/docs/alloy/latest/reference/components/
- CLI reference: https://grafana.com/docs/alloy/latest/reference/cli/

## 1. Goals and non-goals

**Goals**

- Recognize `*.alloy` files as a first-class language with a lexer, parser, and PSI tree.
- Full-line completion of component declarations (`prometheus.scrape "name" { ... }`) driven by a shipped component catalog.
- Semantic, port-type-aware completion and validation for cross-component references — `forward_to = [ ... ]` on a metrics producer may only contain `MetricsReceiver` exports, etc.
- On-demand validation by shelling out to `alloy validate` on macOS/Linux with clickable diagnostics gutter-linked back to the source.
- Embedded view of the Alloy web UI (default `http://localhost:12345`) in an IDE tool window, so users can inspect a running Alloy instance without leaving the editor.
- Standard IDE niceties: brace matching, commenter, structure view, find-usages across component references, go-to-definition on `component.label.export` references, rename refactoring for the label.

**Non-goals (for v1)**

- A from-scratch reimplementation of `alloy validate`'s semantic checks in JVM code. We rely on the external binary for deep validation; the plugin's own checks target fast, in-editor feedback (unknown component, wrong port type, missing required attribute).
- Windows parity for validation. Windows gets syntax + completion + inspections; shellout is disabled with a clear status message.
- Running or debugging Alloy pipelines from the IDE.
- A graphical pipeline view. Nice-to-have for a later milestone; not in scope for v1.

## 2. Architecture overview

Three layers, each independently testable:

1. **Language layer** — file type, lexer (JFlex), parser (GrammarKit BNF), PSI tree with stubs for top-level blocks, references, psi-aware formatter and commenter. Pure IntelliJ platform code, no Alloy-specific knowledge beyond syntax.
2. **Semantic layer** — a static *component catalog* (generated from the Alloy docs) plus a per-file *resolution model* that walks the PSI and answers: "what component is this block?", "what exports does it expose?", "does this reference resolve to a compatible port type?". This is where type-aware completion, inspections, and find-usages live.
3. **External tooling layer** — discovery of the `alloy` binary (OS-gated), configurable path, execution of `alloy validate` with streaming stderr parsed back into `Annotation`s / `ProblemDescriptor`s on the right `PsiElement`, and an embedded browser tool window pointed at the running Alloy UI.

The boundary between (2) and (3) matters: the in-IDE semantic layer must be fast enough to run on every keystroke (annotator + completion); the external validator is a separate user-invoked or save-triggered pass whose results decorate the file asynchronously.

## 3. The component catalog (the backbone)

Most features depend on having, for every Alloy component, a record like:

```
Component {
  name: "prometheus.scrape",
  namespace: "prometheus",
  stability: GA | PublicPreview | Experimental | Community,
  arguments: [ Argument { name, type, required, default, doc } ],
  blocks:    [ Block { name, min, max, arguments: [...] } ],
  exports:   [ Export { name, portType: "MetricsReceiver" | "LogsReceiver" | "Targets" | "TracesReceiver" | ... } ],
  acceptsPortTypes: [ "MetricsReceiver", ... ],   // for fields like forward_to
  docUrl: "https://grafana.com/docs/alloy/latest/reference/components/prometheus/prometheus.scrape/",
}
```

Decisions:

- **Ship it as a JSON resource** in `src/main/resources/alloy/components.json`, loaded at plugin startup into an immutable in-memory index. No network calls at runtime.
- **Generate the JSON** with a Kotlin/Gradle task (`:generateComponentCatalog`) that scrapes the docs site (or, preferably, a structured source if one exists upstream — open question below). Keep the generator in `buildSrc/` so it doesn't ship to users.
- Namespaces to cover initially: `beyla`, `database_observability`, `discovery`, `faro`, `local`, `loki`, `mimir`, `otelcol`, `prometheus`, `pyroscope`, `remote`.
- Port types to model as enum-ish strings (first pass): `MetricsReceiver`, `LogsReceiver`, `TracesReceiver`, `Targets`, plus whatever else the scrape surfaces. Unknown port types flow through as opaque strings so an out-of-date catalog degrades gracefully rather than crashing completion.
- Pin the **catalog version** to a specific Alloy release and surface it under Settings so users know what they're getting; include a "this was generated from Alloy X.Y.Z" string in the JSON header.

## 4. Syntax model

Authoritative source: the Go implementation in `grafana/alloy` at `syntax/token/token.go`, `syntax/scanner/scanner.go`, `syntax/parser/internal.go`. The prose docs are summaries; the Go code is truth.

**Lexer**

- **Comments**: `//` line and `/* ... */` block (block comments may span newlines). No `#`.
- **Strings**: `"..."` with Go-style escapes (`\a \b \f \n \r \t \v \\ \" \' \NNN \xNN \uNNNN \UNNNNNNNN`); literal newline inside is an error. Backtick raw strings `` `...` `` skip escape processing and may span newlines. Single-quoted `'...'` is explicitly illegal.
- **Numbers**: decimal `NUMBER` and `FLOAT = ( digits | "." digits ) [ "e" [ "+" | "-" ] digits ]`. No hex/octal/binary. No underscores. No leading `+/-` (unary minus is a parser construct).
- **Keywords**: only `true` / `false` / `null` (resolved via identifier lookup). `import`, `declare`, `foreach`, `argument`, `export` are **ordinary identifiers** — no special lexer or parser handling.
- **Identifiers**: first char = letter (Unicode-letter or `_`); subsequent = letter or digit. No hyphens.
- **Operators**: `|| && ! == != < <= > >= + - * / % ^` — that's the complete set. No `=> ?: | & ; ...`.
- **Delimiters**: `= , . { } ( ) [ ]`.
- **Newlines are significant (Go-style automatic terminator insertion).** The scanner emits a synthetic `TERMINATOR` token for `\n`/EOF *only* when the previous token was in the "can end a statement" set: `IDENT / STRING / NUMBER / FLOAT / BOOL / NULL / } / ) / ]`. Otherwise the newline is whitespace. This means newlines inside `[ ... ]`, `( ... )`, after a binary operator, etc. don't terminate anything. We'll mirror this state machine in the JFlex lexer — it's the single trickiest piece to port.

**Parser**

- **Block**: `ident ("." ident)* ("LABEL_STRING")? "{" body "}"`. Label must be a double-quoted string whose contents pass `IsValidIdentifier` (empty string tolerated). Backtick-quoted labels rejected.
- **Attribute**: `ident "=" Expression` — key is a single bare identifier (no dots, no label, no quoting).
- **Body**: `[ Statement { TERMINATOR Statement } ]` where `Statement = Attribute | Block`. Nested blocks use the same grammar as top-level.
- **Expressions** (precedence low → high):
  `|| / && / == != < <= > >= / + - / * / % / ^ (right-assoc) / unary - ! / primary { .ident | [expr] | (args) }`.
  Primary = literal | identifier | `(` expr `)` | `[` list `]` (array, trailing comma ok) | `{` fields `}` (object, keys are `string | ident`, trailing comma ok).
- **References** (`prometheus.remote_write.default.receiver`) are just expressions: `IdentifierExpr` + chained `.ident` access. Arbitrary depth. The label segment is an identifier at parse time — so block labels that can be referenced must themselves be valid bare identifiers.
- **No ternary, no statement separator other than newline.**

Parser strategy: **GrammarKit** BNF + JFlex lexer, generated into `src/main/gen/` and committed. The BNF will follow the upstream EBNF comments in `syntax/parser/internal.go` (those comments are essentially the grammar, already written for us). Upstream's `syntax/parser/testdata/valid/` gives us a ready-made golden-test corpus.

## 5. IDE integrations (extension-point map)

Each bullet = one `<extensions>` registration in `plugin.xml` plus a Kotlin class.

- `com.intellij.fileType` — `AlloyFileType` bound to `*.alloy`.
- `com.intellij.lang.parserDefinition` — `AlloyParserDefinition`.
- `com.intellij.lang.syntaxHighlighterFactory` — token colors for component names, labels, attribute keys, strings, numbers, references, comments.
- `com.intellij.completion.contributor` — two contributors:
  - **Top-level**: full-line component templates (`prometheus.scrape "example" { ... }` with live-template-style tab stops).
  - **Inside-block**: arguments (from catalog `arguments`), nested blocks, and for reference-valued fields (`forward_to`, etc.), only references whose exported port type is accepted here.
- `com.intellij.psi.referenceContributor` — resolve `a.b.c` references to the declaring block's PSI.
- `com.intellij.annotator` — fast in-editor inspections:
  - Unknown component name.
  - Duplicate label within a namespace.
  - Unknown argument / missing required argument.
  - Port-type mismatch on reference.
  - Stability-level warning (using a component below configured minimum stability).
- `com.intellij.codeInsight.lineMarkerProvider` — gutter marker linking a component to its docs page (`docUrl`).
- `com.intellij.lang.commenter` — `//` line comments.
- `com.intellij.lang.braceMatcher` / `foldingBuilder` / `formatter` — standard platform stuff.
- `com.intellij.lang.findUsagesProvider` and `com.intellij.refactoring.renameHandler` — rename label propagates to all `component.LABEL.*` references.
- `com.intellij.externalAnnotator` — the `alloy validate` driver (see §6). Use `ExternalAnnotator` rather than `Annotator` because it's expensive and off-EDT.
- `com.intellij.toolWindow` — "Alloy UI" tool window hosting an embedded browser pointed at the running Alloy instance (see §7).
- `com.intellij.projectConfigurable` — Settings pane: path to `alloy` binary, **Alloy version dropdown** (selects which bundled catalog drives completion / inspections, see §8), "run validate on save" toggle, minimum stability level, Alloy UI URL (default `http://localhost:12345`).

No `<depends>` on Java/other bundled plugins is required for any of the above — keep the depend list limited to `com.intellij.modules.platform` so the plugin loads in all JetBrains IDEs (IDEA, GoLand, PyCharm, WebStorm, etc.), which is a real user-experience win since Alloy users are rarely Java developers.

## 6. External validator integration

- **Detection**: on startup and on settings change, probe `alloy --version` at the configured path. Cache the result. If missing / non-executable / on Windows, disable the validator feature and show a one-line notice in the Settings page.
- **Invocation**: `alloy validate [--stability.level=...] <path>`. Run via `OSProcessHandler` off the EDT, with a `ProgressIndicator`-bound cancellation so the user can interrupt.
- **Diagnostic parsing**: `alloy validate` writes human-readable diagnostics to stderr. We'll have to parse them pragmatically (regex for `path:line:col: message`) and degrade to a single file-level annotation if parsing fails. **Open question below** about whether a machine-readable output exists.
- **Trigger modes** (user-configurable): manual action (`Tools → Validate Alloy Config`), on-save, and/or debounced on-idle. Default to manual for v1 to avoid surprising users with binary invocation.
- **Result mapping**: attach `HighlightSeverity.ERROR` / `WARNING` annotations to the PSI element whose text range contains the reported line/column. When the parse fails, surface the raw stderr in an editor banner with "show full output" so users aren't stuck.

## 7. Embedded Alloy web UI

A running `alloy` process serves a web UI on `http://localhost:12345` by default (configurable via `--server.http.listen-addr`). Exposing it inside the IDE removes the need to tab out to a browser to inspect the live component graph, debug info, and metrics.

- **Tool window**: register an `Alloy UI` tool window (right-side by default, toggleable). Its content is a `JBCefBrowser` loaded with the configured URL.
- **Rendering backend**: `JBCefBrowser` (the IntelliJ-bundled JCEF Chromium) rather than `JEditorPane` — the Alloy UI is a modern single-page app and JEditorPane won't run its JS. JCEF is bundled in recent IntelliJ Platform runtimes; gate the tool window's creation on `JBCefApp.isSupported()` and fall back to an "Open in browser" button if not.
- **Configuration**: URL (default `http://localhost:12345`) lives in the same Settings pane as the validator. Expose a refresh button, a "back/forward/reload" toolbar, and an "open in system browser" action for users on constrained runtimes.
- **Reachability**: before loading, probe the URL with a short-timeout HTTP HEAD off-EDT. If the probe fails, show an in-tool-window empty state ("Alloy isn't running at <url>") with a one-click retry — don't block the tool window on a hung socket.
- **Lifecycle**: reuse one `JBCefBrowser` per project; dispose it with the tool window. Don't auto-poll in the background — let the embedded UI handle its own refresh.
- **Non-goals for v1**: launching/managing the Alloy process, auth (Alloy's UI is unauthenticated by default), remote-host discovery. Users point the tool window at whatever URL their Alloy instance exposes.

## 8. Multi-version catalog support

Real users run a mix of Alloy versions in production (a team might be on v1.7 while newer fleets have v1.9). Shipping a single catalog pinned to "the latest" release causes false-positive inspections (a v1.9-only argument flagged in a v1.7 config) and missing completions (a v1.7 argument removed in v1.9 invisible to v1.7 users). The plugin should let users pick which Alloy version it validates against.

**Data layout**

- Ship multiple catalogs in the plugin jar: `src/main/resources/alloy/catalogs/<version>/components.json`, one per supported Alloy minor release. A small `manifest.json` alongside lists available versions and which one is the default.
- Generator in `catalog-generator/build-catalog.sh` already takes `ALLOY_VERSION` — just run it N times, once per version, committing each JSON. Keep the set small (3–4 releases, rolling window).
- Plugin binary size is the cost: ~500 KB per version. At 4 versions ~2 MB total, acceptable.

**Settings surface**

- New settings page under *Settings → Languages & Frameworks → Alloy*:
  - **Alloy version** dropdown. Default: "latest bundled". Options: each bundled version + "auto-detect" (see below).
  - Read-only display of what catalog is currently active (version tag + component count) so users know what they're getting.
- Persisted as a project-level setting (different projects may target different Alloy versions). Fall back to an application-level default when no project override exists.

**Auto-detect**

- If the user has the Alloy binary configured (M4 validator path), run `alloy --version` once at project open and select the nearest bundled catalog. Cache the result.
- If no binary is configured, fall back to the latest bundled catalog.
- Keep this optional; some users deliberately author configs for a different version than their local install.

**Runtime wiring**

- `AlloyCatalogService` is currently `@Service(Level.APP)` with a single bundled catalog. Move to `@Service(Level.PROJECT)` and pick the right JSON based on the project setting. Keep the singleton load cached so the file isn't re-parsed on every lookup.
- A settings change fires a re-load + DaemonCodeAnalyzer restart so open files get their inspections refreshed.

**Ties to the validator** (§6)

When M4 lands, the version dropdown and the `alloy` binary path live in the same settings page — mismatches are surface-able there: if the user picked catalog v1.7 but the binary reports v1.9, show a subtle "version mismatch" banner with a one-click "sync to binary" action.

**Non-goals**

- Downloading catalogs on demand. All versions ship inside the plugin jar; no network calls at runtime. If a new Alloy release is needed we ship a plugin update.
- Per-file version overrides. Project-level granularity is enough — nobody targets two Alloy versions in the same directory.

## 9. Milestones

Each milestone is independently mergeable and leaves the plugin usable.

**M1 — File type + lexer + parser** *(foundation; nothing else works without this)*

- `AlloyFileType`, `AlloyLanguage`, `AlloyFileElementType`.
- JFlex lexer covering identifiers, dotted names, strings, numbers, booleans, operators, comments.
- GrammarKit BNF for blocks, attributes, expressions.
- Syntax highlighter with placeholder color keys.
- Golden-file parser tests (`ParsingTestCase`) for a handful of real Alloy configs pulled from the upstream docs.

**M2 — Component catalog + basic completion**

- Catalog generator in `buildSrc/`, JSON resource, runtime loader.
- Top-level completion contributor offering full-line component templates (tab-stops for label + required args).
- Inside-block completion for known argument names; values typed as string/bool/enum get basic hints.

**M3 — References + semantic inspections**

- PSI references for `component.LABEL.export`.
- Reference completion filtered by accepted port type in the target field.
- Annotator checks: unknown component, duplicate label, unknown/missing argument, port-type mismatch, stability warning.
- Find-usages + rename for component labels.

**M4 — External validator + embedded Alloy UI**

- Settings UI for `alloy` binary path, trigger mode, minimum stability, and Alloy UI URL.
- OS-gated detection and invocation of `alloy validate`.
- Stderr parser, annotation mapping, error-output fallback.
- "Run Alloy validate" action + on-save hook behind a flag.
- `Alloy UI` tool window backed by `JBCefBrowser`, with JCEF-availability fallback and a reachability probe + retry empty state.

**M5 — Polish**

- Formatter, brace matcher, folding, commenter.
- Structure view grouped by namespace.
- Doc-link gutter icons.
- `verifyPlugin` clean run against the pinned platform version.

**M6 — Multi-version catalog (§8)**

- Bundle 3–4 catalogs under `resources/alloy/catalogs/<version>/components.json` + `manifest.json`.
- Promote `AlloyCatalogService` to a project-level service that picks its catalog from a persisted setting.
- Settings page with an Alloy-version dropdown; optional auto-detect from the `alloy` binary (depends on M4).
- On settings change, refresh the DaemonCodeAnalyzer so inspections re-run against the new catalog.

**M7 — Free-tier quality-of-life (§10)**

- No-destination + dead-config warnings (catalog + directory index driven).
- Autocomplete pre-fills required fields on block-template insertion.
- Live templates + *File → New → Alloy Pipeline* templates.
- Breadcrumb bar (`BreadcrumbsProvider`).
- TODO tool-window integration (`IndexPatternBuilder`).

**M8 — Pro tier (gated, §10)**

- Secrets check using catalog's `alloytypes.Secret` type information.
- Deprecated-argument warning (depends on M6 multi-version catalogs).
- `alloy run` Run Configuration with streaming output + embedded localhost viewer (composes M4 validator plumbing + §7 `JBCefBrowser`).
- Settings-level feature flag for the paid tier (license-key mechanism TBD based on post-v0.1.0 monetization decision).

## 10. Backlog — post-M5 features

Selected ideas sized and flagged for eventual work. Ordering here is rough priority; items are grouped with the milestone that would own them.

**Free-tier quality-of-life** (M7)

- **No-destination warning** — WARNING on a receiver-style export that no `forward_to` references. Catches dangling `prometheus.remote_write "x" {}` blocks. Uses the same directory-scoped block index we already have. Size: ~1 hour.
- **Dead-config warning** — WARNING on a labeled block that's declared but never referenced *and* has no exports consumed elsewhere. Overlaps with no-destination but also catches dead discovery blocks. Size: ~1 hour once no-destination lands (shares the "who references whom?" pass).
- **Autocomplete pre-fills required fields** — when inserting a block template from completion, stub out every required arg with `name = <caret>` lines so the user sees what's mandatory. Requires walking the catalog for required args on insert. Size: ~1–2 hours.
- **Live templates** — `scrape<Tab>` expands to a `prometheus.scrape "…" { … }` block with tab-stops. Separate muscle-memory from completion. Also `pipeline<Tab>` for a canonical discovery→scrape→remote_write scaffold. Ship a small starter set; users can author more via `Settings → Live Templates`. Size: ~1 hour.
- **File → New templates** — menu items like *New → Alloy → Prometheus pipeline*, *Loki pipeline*, *OTel receiver*, etc. Separate UI affordance from the in-editor live templates. Size: ~30 min.
- **Breadcrumb bar** — the strip at the top of the editor showing enclosing-block path, e.g. `prometheus.remote_write "rw" › endpoint › basic_auth`. Useful in deeply nested OTel configs. Implementation is a `BreadcrumbsProvider` extension. Size: ~30 min.
- **TODO tool-window integration** — make `// TODO` / `// FIXME` inside Alloy files picked up by the platform's TODO view. One-line `IndexPatternBuilder` registration once we declare the language participates in the platform's comment-based indexer. Size: ~15 min.
- **Envfile `${…}` completion** *(shipped post-v0.1.0)* — project-level envfile setting + completion inside `${…}` placeholders in Alloy strings + unknown-var warning, gated by a "show values" toggle that defaults off to avoid leaking secrets on a screenshare.

**Pro-tier** (M8, gated)

- **Secrets check** — WARNING on likely-plaintext secrets: a string value assigned to `password`, `bearer_token`, `api_key`, etc. when the value doesn't look like a `sys.env(…)`, `local.file(…)`, `remote.vault(…)`, or `${…}` reference. Heuristic per-attribute: we already know from the catalog which args are `alloytypes.Secret` / `alloytypes.OptionalSecret`. Size: ~2 hours for the core detector; additional time if we ship a "convert to `local.file` secret" quick fix.
- **Deprecated-argument warning** — relies on §8 multi-version catalogs. Compare the selected catalog to a "next version" catalog bundled alongside it; any arg in the current version but removed/renamed in next gets a strike-through + weak warning with the replacement name. Requires at least two bundled versions to be useful. Size: ~half day on top of M6.
- **`alloy run` + built-in localhost viewer** — combines M4's validator work with §7's embedded UI: new Run Configuration that starts `alloy run <file>` as an `OSProcessHandler`, streams stdout/stderr to a Run tool window, and opens the `JBCefBrowser` viewer at the instance's listen address (default `http://localhost:12345`) once the process reports ready. Lifecycle: process stops → viewer collapses to an empty state. Settings: binary path (shared with validator), listen address override. Multi-day.

The free/pro split here is deliberate: items 1–8 add polish everyone will use; items 9–11 are the ones platform engineers pay for (correctness + running). Revisit after v0.1.0 install data tells us whether a paid tier is worth pursuing at all.

## 11. Testing approach

- **Lexer/parser**: `LexerTestCase`, `ParsingTestCase` with fixture files under `src/test/testData/parser/`.
- **Completion**: `BasePlatformTestCase` + `myFixture.completeBasic()`; assert the full set of lookup strings for representative contexts.
- **Annotator/inspections**: `myFixture.testHighlighting()` against fixtures annotated with `<error>`, `<warning>` markers.
- **Catalog loader**: plain JVM unit test over a checked-in miniature JSON.
- **External validator**: unit test the stderr parser against recorded fixture strings. Integration test gated behind an env var (`ALLOY_BIN`), skipped in CI when unset.

## 12. Open questions

These need to be resolved before or during the affected milestone, not up front:

- **Structured catalog source**: is there a machine-readable component catalog upstream (generated docs, JSON schema, Go source)? If yes, use it instead of scraping HTML — it's more stable and version-pinnable. First check the `grafana/alloy` repo for anything like `component/<ns>/*/component.go` metadata.
- **Comment syntax**: confirm `//` vs `#` vs both, and whether block comments exist. The summary above didn't pin this down.
- **Expression grammar**: the docs page summarizes types but doesn't fully specify operator precedence / function-call syntax. Decide whether v1 parses expressions strictly or keeps a permissive fallback for anything unrecognized.
- **`alloy validate` machine-readable output**: docs don't mention a `--format=json` flag. If stderr parsing turns out brittle, consider filing an upstream issue; short-term we live with regex.
- **Labels scope**: confirm whether labels are unique per component name, per namespace, or globally. Affects duplicate-label inspection.
- **Multi-file configs**: `alloy validate` accepts a directory. Decide whether in-IDE resolution should cross files in the same directory for reference checks (probably yes, but needs a caching story).

## 13. Risks

- **Catalog staleness.** Every Alloy release can add/rename components. Mitigation: regenerate catalog in a scheduled CI job, ship a new plugin version when upstream releases, degrade gracefully (unknown components become warnings, not errors).
- **Grammar drift.** Alloy's syntax has been stable but isn't frozen. Mitigation: keep the parser permissive around expressions; lean on `alloy validate` as the source of truth for deep checks.
- **Binary-shellout UX.** Users without `alloy` installed will hit the feature and get confused. Mitigation: disable cleanly, surface one actionable message pointing at the install docs, never pop modal errors.
- **JCEF availability.** Some JetBrains runtimes ship without JCEF (custom IDE builds, certain Linux distributions). Mitigation: feature-detect via `JBCefApp.isSupported()`; when unavailable, hide the embedded view and degrade to an "Open Alloy UI in browser" action.
