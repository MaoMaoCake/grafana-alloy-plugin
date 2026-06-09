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

## 9. Kubernetes ConfigMap integration

In production, Alloy configs almost always ship inside a Kubernetes `ConfigMap` — a YAML document with the Alloy source as a multi-line string under `data:`. Today the plugin only activates on `*.alloy` files, so the moment a user opens the YAML where the config actually lives, every feature (highlighting, completion, references, inspections, inline docs) disappears. This is the highest-impact gap we have.

**Phase 1 — language injection (the bulk of the win)**

- Implement a `LanguageInjectionContributor` (or a `MultiHostInjector` for the legacy API) that injects `AlloyLanguage` into YAML scalar values. Activation criteria:
  - Containing document declares `kind: ConfigMap` *or* the parent key matches a configurable allow-list of names (default: `config.alloy`, `alloy.alloy`, `config.river`, plus any key ending in `.alloy`).
  - The value is a block scalar (`|` / `|-` / `|+` / `>`); ignore single-line scalars.
- Once injection works, the existing parser, completion, references, inspections, and docs should "just work" inside the injected fragment because IntelliJ's injection host machinery routes carets through to the injected PSI for free.
- **Caveats to nail down before shipping**:
  - YAML block-scalar indent is stripped before the injected language sees it; line/column reporting in the validator (§6) must round-trip back through the host document.
  - Cross-file references break inside ConfigMaps (no sibling files), so the resolver should fall back to `(YAML host document) + (other ConfigMaps in the same project)` rather than using the directory-scoped index.
  - Envfile templating still applies — `${VAR}` placeholders should highlight inside the injected fragment the same way they do in `*.alloy` files.
- **Settings**: a project-level toggle ("Enable Alloy injection in YAML ConfigMaps") plus the editable allow-list of key names. Default on.
- **Dependency**: add `<depends optional="true" config-file="alloy-yaml.xml">com.intellij.modules.yaml</depends>` so the injector only loads in IDEs that bundle YAML support (every modern JetBrains IDE does, but optional-depends keeps us robust).
- Size: ~half day for the injector + tests. Most existing tests should pass unchanged because PSI is shared.

**Phase 2 — Services tool window integration (later)**

PyCharm/IntelliJ Ultimate ship a Kubernetes plugin that surfaces clusters in the *Services* tool window. The eventual goal is to let users:

- Right-click a `ConfigMap` in the Services tree → *Edit Alloy config* → opens the embedded YAML scalar in a synthetic editor with full Alloy support.
- Run `alloy validate` (§6) against the live ConfigMap content without first writing it to disk — the validator runner takes a `String` payload, writes it to a tempdir, and runs against that.
- Optional: a "Push back to cluster" action that diffs the edited content against the live ConfigMap before applying via `kubectl apply -f -`.

**Open questions for Phase 2** (resolve when we get there, not now):

- Does the Kubernetes plugin expose stable extension points for adding context-menu actions on `ConfigMap` nodes, or do we need to subscribe to the Services tree and inject actions ourselves?
- Auth/cluster context comes from the Kubernetes plugin; we should **never** embed our own kubeconfig handling.
- The Kubernetes plugin is bundled in IDEA Ultimate / PyCharm Pro / GoLand but not Community editions. Phase 2 is opt-in for Ultimate users; the injection in Phase 1 covers everyone else.

Phase 1 is straightforward and unblocks the majority of real-world use. Phase 2 is the "delight" tier — defer until Phase 1 ships and we have signal on how often users actually edit ConfigMaps directly vs through Helm/Kustomize templates (which we'd want to handle separately, see *Risks*).

## 10. Milestones

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

**M7 — Free-tier quality-of-life (§11)**

- No-destination + dead-config warnings (catalog + directory index driven).
- Autocomplete pre-fills required fields on block-template insertion.
- Live templates + *File → New → Alloy Pipeline* templates.
- Breadcrumb bar (`BreadcrumbsProvider`).
- TODO tool-window integration (`IndexPatternBuilder`).

**M8 — Pro tier (gated, §11)**

- Secrets check using catalog's `alloytypes.Secret` type information.
- Deprecated-argument warning (depends on M6 multi-version catalogs).
- `alloy run` Run Configuration with streaming output + embedded localhost viewer (composes M4 validator plumbing + §7 `JBCefBrowser`).
- Settings-level feature flag for the paid tier (license-key mechanism TBD based on post-v0.1.0 monetization decision).

**M9 — Kubernetes ConfigMap injection (§9)**

- `LanguageInjectionContributor` injecting `AlloyLanguage` into YAML block scalars under known keys / `kind: ConfigMap` documents.
- Optional dependency on `com.intellij.modules.yaml`; injection silently disabled in IDEs without YAML support.
- Project-level toggle + editable allow-list of YAML keys.
- Validator (M4) host-document line/column round-trip so `alloy validate` errors land on the right line of the YAML, not the stripped fragment.
- Phase 2 (later, separate milestone): Services-tool-window integration — right-click `ConfigMap` → *Edit Alloy config* → validate live → push back. Requires the bundled Kubernetes plugin and is opt-in for IDEA Ultimate / PyCharm Pro / GoLand.

**M10 — Remote config viewer (§15)**

- Profile model + Settings page; secrets in `PasswordSafe`.
- `AlloyRemoteConfigService` doing single-shot `GetConfig` over connect-RPC HTTP+JSON, with hash-based `not_modified` short-circuit.
- *Open Remote Config…* action that materialises the response into a per-profile `LightVirtualFile` of `AlloyFileType`, opened read-only with an `EditorNotificationProvider` banner.
- Project-scoped polling on `Alarm`, auto-paused when the editor closes.
- Click-to-jump rides M1–M3; no language-layer changes required.
- Punt multi-file responses, status reporting back to the server, and poll-diff history to follow-ups.

## 11. Backlog — post-M5 features

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

## 12. Testing approach

- **Lexer/parser**: `LexerTestCase`, `ParsingTestCase` with fixture files under `src/test/testData/parser/`.
- **Completion**: `BasePlatformTestCase` + `myFixture.completeBasic()`; assert the full set of lookup strings for representative contexts.
- **Annotator/inspections**: `myFixture.testHighlighting()` against fixtures annotated with `<error>`, `<warning>` markers.
- **Catalog loader**: plain JVM unit test over a checked-in miniature JSON.
- **External validator**: unit test the stderr parser against recorded fixture strings. Integration test gated behind an env var (`ALLOY_BIN`), skipped in CI when unset.

## 13. Open questions

These need to be resolved before or during the affected milestone, not up front:

- **Structured catalog source**: is there a machine-readable component catalog upstream (generated docs, JSON schema, Go source)? If yes, use it instead of scraping HTML — it's more stable and version-pinnable. First check the `grafana/alloy` repo for anything like `component/<ns>/*/component.go` metadata.
- **Comment syntax**: confirm `//` vs `#` vs both, and whether block comments exist. The summary above didn't pin this down.
- **Expression grammar**: the docs page summarizes types but doesn't fully specify operator precedence / function-call syntax. Decide whether v1 parses expressions strictly or keeps a permissive fallback for anything unrecognized.
- **`alloy validate` machine-readable output**: docs don't mention a `--format=json` flag. If stderr parsing turns out brittle, consider filing an upstream issue; short-term we live with regex.
- **Labels scope**: confirm whether labels are unique per component name, per namespace, or globally. Affects duplicate-label inspection.
- **Multi-file configs**: `alloy validate` accepts a directory. Decide whether in-IDE resolution should cross files in the same directory for reference checks (probably yes, but needs a caching story).

## 14. Risks

- **Catalog staleness.** Every Alloy release can add/rename components. Mitigation: regenerate catalog in a scheduled CI job, ship a new plugin version when upstream releases, degrade gracefully (unknown components become warnings, not errors).
- **Grammar drift.** Alloy's syntax has been stable but isn't frozen. Mitigation: keep the parser permissive around expressions; lean on `alloy validate` as the source of truth for deep checks.
- **Binary-shellout UX.** Users without `alloy` installed will hit the feature and get confused. Mitigation: disable cleanly, surface one actionable message pointing at the install docs, never pop modal errors.
- **JCEF availability.** Some JetBrains runtimes ship without JCEF (custom IDE builds, certain Linux distributions). Mitigation: feature-detect via `JBCefApp.isSupported()`; when unavailable, hide the embedded view and degrade to an "Open Alloy UI in browser" action.

## 15. Remote config viewer (M10)

A common operational task is "what config does my fleet member *actually* have right now?" — answered today by `curl`-ing a remote config server and reading raw output in a terminal. Bringing the result into the IDE means users get the same parser, completion, references, and inspections from M1–M3 against fetched configs, including click-through navigation when debugging cross-component wiring. The contract is the upstream proto at https://github.com/grafana/alloy-remote-config: `collector.v1.CollectorService.GetConfig(GetConfigRequest) → GetConfigResponse`, served via [connect-go](https://connectrpc.com/) which exposes plain HTTP+JSON in addition to gRPC.

**Goals**

- Fetch the config a remote-config server would hand to a given collector ID, render it in a normal Alloy editor, and let the existing language features work on it as if it were a local file.
- Optional polling so users can leave the editor open and watch the rendered config change as the server's view of the fleet changes.
- Multiple named profiles (a user typically has dev/staging/prod servers and a handful of collector IDs they investigate often).

**Non-goals (v1)**

- Editing and pushing config back. The viewer is read-only; modifying remote config goes through the server's own UI / API.
- Proper RPC client. We hit the connect HTTP+JSON endpoint directly with `HttpClient`, not the generated Java client — connect-go is Go-only and we don't want to vendor a generated Java connect runtime for one method.
- Streaming / server-push. Polling is good enough for an interactive viewer.
- Multi-file `AgentConfigMap` responses. The proto allows a `map<string, AgentConfigFile>` payload (one config per filename) but in practice Alloy collectors take a single file. Read `GetConfigResponse.content` (top-level scalar) first; if a future server hands us a map we surface a "multi-file response not yet supported" notice and fall back to concatenating with separator comments. Cross-file references will be wrong in that fallback — accept it for v1.

**Wire contract** (verbatim from the upstream proto so we don't drift):

- `CollectorService.GetConfig` over connect-RPC. HTTP+JSON URL: `POST {base}/collector.v1.CollectorService/GetConfig`, content-type `application/json`.
- `GetConfigRequest`: `{ id: string, local_attributes?: map<string,string>, hash?: string, remote_config_status?: …, effective_config?: … }`. We send `id` and `hash`; we do **not** send `remote_config_status` or `effective_config` because we're not a real collector and shouldn't be reporting back into the server's effective-config view.
- `GetConfigResponse`: `{ content: string, hash: string, not_modified: bool }`. When `not_modified == true` we keep the prior body and just update the "last polled at" timestamp.
- `local_attributes` is a free-form `map<string,string>` — many servers route configs by these (e.g. `cluster=prod, region=us-east-1`). Expose them as editable key/value pairs in the profile.

**Profile model** (project-level, `PersistentStateComponent`):

```
RemoteConfigProfile {
  id: UUID,                     // stable key for the LightVirtualFile + storage
  name: String,                 // display name in the action menu
  baseUrl: String,              // e.g. https://alloy-config.example.com
  collectorId: String,
  localAttributes: Map<String, String>,
  authMode: NONE | BEARER | BASIC,
  // secrets stored in PasswordSafe under "alloy-remote-config:{id}", NOT in the state component
  pollIntervalSeconds: Int?,    // null = manual refresh only
  insecureSkipVerify: Boolean,  // for self-signed dev servers; defaults false
}
```

Secrets (bearer tokens, basic-auth passwords) live in `PasswordSafe` keyed by profile UUID — never in the persisted state file. `RemoteConfigProfileService` is `@Service(Level.PROJECT)` so different projects can target different fleets, mirroring §8.

**Settings UI** (under *Settings → Languages & Frameworks → Alloy → Remote Config*):

- Master list of profiles with add/edit/delete/duplicate.
- Per-profile editor: name, base URL, collector ID, local attributes table, auth mode + credential field, polling interval (None / 30 s / 1 min / 5 min / custom), TLS toggle.
- "Test connection" button → runs a single `GetConfig` and reports HTTP status / first 100 chars of body / parse error.

**Action surface**:

- *Tools → Alloy → Open Remote Config…* — opens a popup listing profiles; selecting one fetches and opens the viewer.
- *Refresh* gutter button on the open viewer (always available regardless of poll setting).
- *Edit Profile* link in the editor banner so users can jump to settings without hunting through menus.

**Fetching pipeline** (`AlloyRemoteConfigService`, `@Service(Level.PROJECT)`):

1. Build a `GetConfigRequest` JSON: `{ id: collectorId, local_attributes: {...}, hash: lastHash ?: "" }`. Connect-JSON uses `snake_case` field names matching the proto, not the proto3 JSON `camelCase` default — we pin this in tests.
2. POST to `{baseUrl}/collector.v1.CollectorService/GetConfig`. Headers: `Content-Type: application/json`, `Accept: application/json`, `Connect-Protocol-Version: 1`, plus auth.
3. Off the EDT via `java.net.http.HttpClient` (already in the bundled JDK 21 — no new dep). Wrap the call in a `ProgressIndicator`-bound future for cancellation.
4. On 2xx: parse `GetConfigResponse`. If `not_modified == true`, leave the existing `LightVirtualFile` content alone and just refresh the banner timestamp. Otherwise, update content, store the new `hash` in the profile's runtime state (not persisted), reload the editor.
5. On 4xx/5xx: keep the old content, surface a `Notification` with the status code and first line of the error body, log the full response. Connect errors come back as `{ "code": "unavailable", "message": "..." }` — show that message verbatim.
6. On network failure / timeout: same path as above, with the exception message. Treat 401/403 as auth-config problems and surface a "fix profile" link in the notification.

**Editor integration**:

- One `LightVirtualFile` per profile, named `<profile.name>.alloy`, file type `AlloyFileType`. Content set via `setContent(...)`. Mark `isWritable = false`.
- Open via `FileEditorManager.openFile`. Opening the same profile twice reuses the existing virtual file so polling updates land in the visible editor.
- `EditorNotificationProvider` shows a banner with: profile name, base URL, collector ID, last-fetched timestamp, current hash, polling state, and a "Refresh now" button. Refresh on click via the same service.
- Read-only is enforced by both `isWritable = false` and a `FileDocumentManagerListener` veto — IntelliJ does honour `isWritable` but the listener gives us a place to show a friendly "this file is a remote-config snapshot; edit your remote config server instead" message rather than a generic IDE warning.
- **Click-to-jump comes free**: because the `LightVirtualFile` is `AlloyFileType`, M1–M3's parser, references, and find-usages all activate on it. Same code, no special-casing.

**Polling**:

- One `Alarm` (`Alarm.ThreadToUse.POOLED_THREAD`) per project, single timer reused across profiles to keep thread count bounded.
- Poll only when at least one remote-config viewer is open in the project. `FileEditorManagerListener` toggles polling on first-open / last-close — this avoids burning request quota on minimised IDE windows.
- Per-profile interval, with a global floor of 10 s to protect users from accidentally hammering production servers.
- Polls are coalesced: if a poll is in flight when its next tick fires, skip the tick.
- Polling pauses while the IDE is in modal state (settings dialog open, etc.) — `ApplicationManager.getApplication().isDispatchThread`-aware scheduling handles this naturally.

**Open questions** (resolve at M10):

- **Self-identification ethics**. Sending a `GetConfigRequest` with a real production collector's `id` may make the server think that collector phoned home — depending on the server implementation, this could affect heartbeat tracking or active-collector counts. Default to a synthetic ID prefix (e.g. `idea-plugin-{user}-{uuid}`) and document that users who want the *exact* config a specific collector receives must override it. **This is a real risk** and worth flagging in settings UI copy.
- **`RegisterCollector` / `UnregisterCollector`**. Some servers require a prior `RegisterCollector` call before `GetConfig` will return content. Punt this to a "Register on first fetch" toggle in the profile if real-world testing shows it's needed. Default off.
- **Connect protocol negotiation**. The connect-go server also supports gRPC and gRPC-web on the same endpoint; we deliberately use connect-JSON only, since it's the simplest wire format. If a server is configured `Connect-only=false` and rejects JSON, we'll find out at "Test connection" time.
- **Response size**. Large fleets can push multi-MB configs. Set a hard cap (e.g. 10 MB) on response body size before parsing — the editor handles MB-sized files fine, but unbounded reads from a malicious / misconfigured server are a denial-of-service vector.

**Testing**:

- Unit-test the request-builder + response-parser with hand-rolled JSON fixtures (no network). Cover: `not_modified=true`, content update, error responses, malformed JSON.
- Lightweight HTTP fixture using `com.sun.net.httpserver.HttpServer` (bundled in the JDK) for end-to-end fetch tests. No external test infrastructure.
- Manually verify against a real connect-go server (run `alloy-remote-config`'s example server) before shipping; not gated in CI.
