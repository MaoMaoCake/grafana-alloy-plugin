import org.jetbrains.grammarkit.tasks.GenerateLexerTask
import org.jetbrains.grammarkit.tasks.GenerateParserTask

plugins {
    id("java")
    id("org.jetbrains.kotlin.jvm") version "2.1.20"
    id("org.jetbrains.intellij.platform") version "2.10.2"
    id("org.jetbrains.grammarkit") version "2022.3.2.2"
}

group = "com.maomaocake"
version = "1.0-SNAPSHOT"

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


        // Add plugin dependencies for compilation here, example:
        // bundledPlugin("com.intellij.java")
    }

    // ParsingTestCase extends JUnit 3's TestCase; keep JUnit 3 on the test classpath.
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.opentest4j:opentest4j:1.3.0")
}

intellijPlatform {
    pluginConfiguration {
        ideaVersion {
            sinceBuild = "252.25557"
        }

        changeNotes = """
            Initial version
        """.trimIndent()
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
        sourceCompatibility = "21"
        targetCompatibility = "21"
        dependsOn(generateAlloyLexer, generateAlloyParser)
    }
    withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile> {
        dependsOn(generateAlloyLexer, generateAlloyParser)
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_21)
    }
}
