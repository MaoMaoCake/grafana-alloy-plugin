# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## What this plugin is

A JetBrains IDE plugin providing language support for **Grafana Alloy** configuration files. Intended capabilities:

- Syntax-aware parsing of the Alloy config language (not just lexer-level highlighting).
- Component autocomplete, including full-line completion, driven by the catalog at https://grafana.com/docs/alloy/latest/reference/components/.
- Type-aware wiring between components — e.g. `prometheus.scrape` exports a `MetricsReceiver`, so `forward_to` completion/validation should only surface receivers of the matching port type. Treat component port types as first-class; a table of `(component → exported types, accepted types)` is the backbone the rest of the language features hang off.
- On-demand validation by shelling out to `alloy validate` on the user's machine. The `validate` subcommand ships with the macOS/Linux Alloy binaries only — **gate this feature on OS and a configurable path to the `alloy` binary**; don't assume it's on `PATH` and don't offer it on Windows.
- Broader Alloy reference: https://grafana.com/docs/alloy/latest/reference/.

## Project status

Scaffolded from the [IntelliJ Platform Plugin Template](https://github.com/JetBrains/intellij-platform-plugin-template). As of this writing the Kotlin source tree (`src/main/kotlin/com/maomaocake/grafanaalloyplugin/`) is empty and `plugin.xml` declares no extensions — file type, lexer, parser, PSI, completion contributors, inspections, and the external-tool integration all still need to be built. When adding a new IDE feature, remember both sides: the Kotlin implementation **and** the matching `<extensions>` entry in `plugin.xml`.

## Commands

Use the Gradle wrapper (`./gradlew`); do not rely on a system Gradle.

- `./gradlew runIde` — launch a sandbox IntelliJ IDEA with the plugin installed. There is a matching `.run/Run IDE with Plugin.run.xml` run configuration; its log tab tails `build/idea-sandbox/system/log/idea.log`.
- `./gradlew test` — run the JUnit test suite. Single test: `./gradlew test --tests "com.maomaocake.grafanaalloyplugin.SomeTest.someMethod"`.
- `./gradlew verifyPlugin` — run the IntelliJ Plugin Verifier against the IDE version declared in `build.gradle.kts`. Required before publishing.
- `./gradlew buildPlugin` — produce the distributable zip in `build/distributions/`.
- `./gradlew publishPlugin` — publish to JetBrains Marketplace (requires credentials; don't run unprompted).
- `catalog-generator/build-catalog.sh` — regenerates `src/main/resources/alloy/components.json` from a pinned Alloy release. Requires Go 1.24+ and network. Run once per Alloy version bump (see `catalog-generator/README.md`). Shallow-clones Alloy into a temp dir and writes JSON only — doesn't touch anything else in the repo.

Gradle **configuration cache** and **build cache** are both enabled (`gradle.properties`). If a task behaves strangely after edits to build logic, rerun with `--no-configuration-cache` to rule it out.

## Platform/toolchain pins

Changing any of these together usually means updating `sinceBuild` as well — check IntelliJ Platform compatibility before bumping.

- IntelliJ Platform target: **IDEA 2025.2.4**, `sinceBuild = "252.25557"` (`build.gradle.kts`).
- Kotlin **2.1.20**, JVM target **21**; Java `sourceCompatibility`/`targetCompatibility` also **21**.
- IntelliJ Platform Gradle Plugin **2.10.2** (the 2.x plugin, not the legacy `org.jetbrains.intellij`).

## Plugin structure notes

- Plugin ID (immutable across versions): `com.maomaocake.grafana-alloy-plugin` in `src/main/resources/META-INF/plugin.xml`.
- Only `com.intellij.modules.platform` is currently depended on. Features that need Java PSI, VCS, etc. must add the appropriate `<depends>` and the matching `bundledPlugin(...)` in `build.gradle.kts` — both sides are required or `runIde` will fail at load time.
- Sources default to `src/main/kotlin`. Add `src/main/java` only if Java sources are actually needed.
- Test framework is wired via `testFramework(TestFrameworkType.Platform)` — tests can extend `BasePlatformTestCase` and friends from the IntelliJ test framework.
