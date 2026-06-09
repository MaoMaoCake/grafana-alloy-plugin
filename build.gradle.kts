import org.jetbrains.grammarkit.tasks.GenerateLexerTask
import org.jetbrains.grammarkit.tasks.GenerateParserTask

plugins {
    id("java")
    id("org.jetbrains.kotlin.jvm") version "2.1.20"
    id("org.jetbrains.intellij.platform") version "2.10.2"
    id("org.jetbrains.grammarkit") version "2022.3.2.2"
}

group = "com.maomaocake"
version = "0.2.0"

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
            sinceBuild = "231"
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
            Update for compatibility and improvements:

            - Updated plugin icon.
            - Added autocomplete from the ENV file.
            - Adjusted IDE version compatibility.
            - Updated test data and configuration files.
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
