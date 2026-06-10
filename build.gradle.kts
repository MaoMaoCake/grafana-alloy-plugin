import org.jetbrains.grammarkit.tasks.GenerateLexerTask
import org.jetbrains.grammarkit.tasks.GenerateParserTask

plugins {
    id("java")
    id("org.jetbrains.kotlin.jvm") version "2.1.20"
    id("org.jetbrains.intellij.platform") version "2.10.2"
    id("org.jetbrains.grammarkit") version "2022.3.2.2"
}

group = "com.maomaocake"
version = "0.3.1"

repositories {
    mavenCentral()
    intellijPlatform {
        defaultRepositories()
    }
}

// Read more: https://plugins.jetbrains.com/docs/intellij/tools-intellij-platform-gradle-plugin.html
dependencies {
    intellijPlatform {
        intellijIdea("2025.2.4")
        testFramework(org.jetbrains.intellij.platform.gradle.TestFrameworkType.Platform)

        // YAML support for the Alloy-in-ConfigMap injector. JSON is pulled in because the
        // platform's YAML plugin transitively depends on `intellij.json` modules at runtime;
        // without it, `Plugin 'YAML' has module dependency 'intellij.json' which cannot be
        // loaded or missing` and our optional dep on YAML never resolves.
        bundledPlugin("org.jetbrains.plugins.yaml")
        bundledPlugin("com.intellij.modules.json")
    }

    // ParsingTestCase extends JUnit 3's TestCase; keep JUnit 3 on the test classpath.
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.opentest4j:opentest4j:1.3.0")
}

intellijPlatform {
    pluginConfiguration {
        ideaVersion {
            sinceBuild = "252"
            // Keep this installable on newer IDE branches.
            untilBuild = providers.provider { null }
        }

        // Surfaces on the Marketplace listing: clickable links to the project and the author,
        // license, support email. Keep in sync with `<vendor>` in plugin.xml.
        vendor {
            name = "maomaocake"
            url = "https://github.com/maomaocake/grafana-alloy-plugin"
        }

        changeNotes = """
            <h3>0.3.0</h3>
            <p><strong>Run, validate, and edit Alloy where it actually runs.</strong></p>
            <ul>
              <li><b>External validator</b> — right-click an <code>.alloy</code> file or
                folder → <i>Validate</i>. Runs <code>alloy validate</code>, opens an
                <i>Alloy Validate</i> tool window with hyperlinked diagnostics that jump
                straight to the offending line. Optional on-idle trigger for live
                squiggles. macOS / Linux only (the subcommand doesn't ship in Windows
                binaries).</li>
              <li><b>Run with Alloy</b> — right-click an <code>.alloy</code> file or
                folder → <i>Run with Alloy</i>. Spawns <code>alloy run</code> with an
                auto-picked free port (default 12345), streams stdout / stderr to the
                Run tool window, and embeds the Alloy UI in a dedicated tool window
                backed by JCEF — Back / Forward / Reload / Home buttons included.
                Per-run storage in a tempdir, cleaned up on stop, so
                <code>data-alloy/</code> never lands next to your config.</li>
              <li><b>Kubernetes ConfigMap support</b> — full editor experience
                (highlighting, completion, references, inspections, Cmd-Q docs) now
                works inside YAML block scalars and multi-line quoted scalars under keys
                named <code>config.alloy</code> or <code>*.alloy</code>. Single-line
                quoted scalars (pasted-snippet shape) are auto-converted to <code>|</code>
                block scalars on file open, so the experience is live the moment you
                open a <code>kubectl get cm</code> dump. Configurable; read-only files
                are skipped.</li>
            </ul>
            <p>See <a href="https://github.com/maomaocake/grafana-alloy-plugin/blob/main/CHANGELOG.md">CHANGELOG.md</a>
            for the full list.</p>
        """.trimIndent()
    }

    // `./gradlew verifyPlugin` downloads each of these IDEs and runs IntelliJ's Plugin
    // Verifier against our jar. Covers every minor release from 2023.1 onwards.
    pluginVerification {
        ides {
            ide("IC", "2023.1")
            ide("IC", "2023.2")
            ide("IC", "2023.3")
            ide("IC", "2024.1")
            ide("IC", "2024.2")
            ide("IC", "2024.3")
            ide("IC", "2025.1")
            ide("IC", "2025.2")
            ide("IC", "2026.1")
        }
    }
}

sourceSets {
    named("main") {
        java.srcDirs("src/main/gen")
    }
}

val generateAlloyLexer = tasks.register<GenerateLexerTask>("generateAlloyLexer") {
    sourceFile.set(file("src/main/grammar/Alloy.flex"))
    targetOutputDir.set(file("src/main/gen/com/maomaocake/grafanaalloyplugin/lexer"))
    purgeOldFiles.set(true)
}

val generateAlloyParser = tasks.register<GenerateParserTask>("generateAlloyParser") {
    sourceFile.set(file("src/main/grammar/Alloy.bnf"))
    targetRootOutputDir.set(file("src/main/gen"))
    pathToParser.set("com/maomaocake/grafanaalloyplugin/parser/AlloyParser.java")
    pathToPsiRoot.set("com/maomaocake/grafanaalloyplugin/psi")
    purgeOldFiles.set(true)
}

tasks {
    withType<JavaCompile> {
        // 2023.x IDEs run on JVM 17, so we target 17. 2022.x runs on 17 too; 2024.2+ runs
        // on 21 but is backwards-compatible with 17 bytecode.
        sourceCompatibility = "17"
        targetCompatibility = "17"
        dependsOn(generateAlloyLexer, generateAlloyParser)
    }
    withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile> {
        dependsOn(generateAlloyLexer, generateAlloyParser)
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}
