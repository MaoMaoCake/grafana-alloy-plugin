package com.maomaocake.grafanaalloyplugin

import com.intellij.lang.annotation.HighlightSeverity
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import com.maomaocake.grafanaalloyplugin.catalog.AlloyCatalogService
import com.maomaocake.grafanaalloyplugin.catalog.AlloyCatalogSettings

/**
 * End-to-end tests for version-mismatch inspections: a component/arg/block that exists in another
 * bundled Alloy version but not the selected one is flagged as an ERROR (not the vague "unknown"
 * warning), and a "switch version" quick-fix re-pins the project.
 */
class AlloyVersionMismatchTest : BasePlatformTestCase() {

    private fun pin(version: String) {
        AlloyCatalogSettings.getInstance(project).apply {
            versionMode = AlloyCatalogSettings.VersionMode.Pinned
            pinnedVersion = version
        }
        AlloyCatalogService.getInstance(project).reload()
    }

    private fun errorsFor(text: String): List<String> {
        myFixture.configureByText("t.alloy", text)
        return myFixture.doHighlighting()
            .filter { it.severity == HighlightSeverity.ERROR }
            .mapNotNull { it.description }
    }

    fun testComponentAddedLaterErrorsWhenPinnedToOlder() {
        pin("v1.9.2")
        val errors = errorsFor("""otelcol.exporter.file "x" {}""")
        assertTrue(
            "expected a version-mismatch error mentioning otelcol.exporter.file, got: $errors",
            errors.any { it.contains("otelcol.exporter.file") && it.contains("not available") && it.contains("v1.19.2") },
        )
    }

    fun testComponentRemovedLaterErrorsWhenPinnedToNewer() {
        pin("v1.19.2")
        val errors = errorsFor("""otelcol.receiver.opencensus "x" {}""")
        assertTrue(
            "expected a version-mismatch error offering to switch to v1.9.2, got: $errors",
            errors.any { it.contains("otelcol.receiver.opencensus") && it.contains("v1.9.2") },
        )
    }

    fun testArgAddedLaterErrorsWhenPinnedToOlder() {
        pin("v1.9.2")
        val errors = errorsFor("""otelcol.exporter.debug "x" { output_paths = [] }""")
        assertTrue(
            "expected a version-mismatch error mentioning output_paths, got: $errors",
            errors.any { it.contains("output_paths") && it.contains("not available") },
        )
    }

    fun testValidComponentOnItsVersionHasNoVersionMismatch() {
        pin("v1.19.2")
        val errors = errorsFor("""otelcol.exporter.file "x" {}""")
        assertFalse(
            "otelcol.exporter.file is valid in v1.19.2; no version-mismatch error expected: $errors",
            errors.any { it.contains("not available in the selected Alloy version") },
        )
    }

    fun testUnknownEverywhereIsNotAVersionMismatch() {
        pin("v1.19.2")
        val errors = errorsFor("""totally.made.up "x" {}""")
        assertTrue(
            "a name unknown in every bundled version must not be a version mismatch: $errors",
            errors.none { it.contains("not available in the selected Alloy version") },
        )
    }

    fun testDottedNestedBlockAddedLaterErrorsWhenPinnedToOlder() {
        pin("v1.9.2")
        // `stage.truncate` is a dotted nested block added after v1.9.2 — the case a flat
        // name-match would silently miss.
        val errors = errorsFor(
            """
            loki.process "x" {
              forward_to = []
              stage.truncate {}
            }
            """.trimIndent(),
        )
        assertTrue(
            "expected a version-mismatch error mentioning stage.truncate, got: $errors",
            errors.any { it.contains("stage.truncate") && it.contains("not available") && it.contains("v1.19.2") },
        )
    }

    fun testSingleSegmentNestedBlockAddedLaterErrors() {
        pin("v1.9.2")
        val errors = errorsFor(
            """
            beyla.ebpf "x" {
              nodejs {}
            }
            """.trimIndent(),
        )
        assertTrue(
            "expected a version-mismatch error mentioning the nodejs block, got: $errors",
            errors.any { it.contains("nodejs") && it.contains("not available") },
        )
    }

    fun testAllCandidateSwitchFixesAreOffered() {
        pin("v1.9.2")
        myFixture.configureByText("t.alloy", """otelcol.exporter.fi<caret>le "x" {}""")
        val texts = myFixture.filterAvailableIntentions("Switch Alloy catalog version to").map { it.text }.toSet()
        // One fix per version that actually has the component (v1.17.1, v1.18.1, v1.19.2).
        assertTrue("missing v1.17.1 fix: $texts", texts.any { it.endsWith("v1.17.1") })
        assertTrue("missing v1.18.1 fix: $texts", texts.any { it.endsWith("v1.18.1") })
        assertTrue("missing v1.19.2 fix: $texts", texts.any { it.endsWith("v1.19.2") })
    }

    fun testNestedComponentDoesNotCascade() {
        pin("v1.9.2")
        // A valid-in-v1.9.2 component nested inside an unknown-in-v1.9.2 one: only the outer block
        // should be flagged, not its children.
        val errors = errorsFor(
            """
            otelcol.exporter.file "x" {
              otelcol.receiver.otlp "y" {}
            }
            """.trimIndent(),
        )
        assertEquals(
            "only the outermost unknown component should get a version-mismatch error: $errors",
            1, errors.count { it.contains("not available in the selected Alloy version") },
        )
    }

    fun testComponentInsideDeclareIsStillFlagged() {
        pin("v1.9.2")
        val errors = errorsFor(
            """
            declare "m" {
              otelcol.exporter.file "x" {}
            }
            """.trimIndent(),
        )
        assertTrue(
            "a version-mismatched component inside a declare module should still be flagged: $errors",
            errors.any { it.contains("otelcol.exporter.file") && it.contains("not available") },
        )
    }

    fun testSwitchQuickFixRepinsProject() {
        pin("v1.9.2")
        myFixture.configureByText("t.alloy", """otelcol.exporter.fi<caret>le "x" {}""")
        val intention = myFixture.findSingleIntention("Switch Alloy catalog version to v1.19.2")
        myFixture.launchAction(intention)

        val settings = AlloyCatalogSettings.getInstance(project)
        assertEquals(AlloyCatalogSettings.VersionMode.Pinned, settings.versionMode)
        assertEquals("v1.19.2", settings.pinnedVersion)
        assertEquals("v1.19.2", AlloyCatalogService.getInstance(project).activeVersion)
    }

    fun testNestedBlockQuickFixRepinsProject() {
        pin("v1.9.2")
        myFixture.configureByText("t.alloy", "beyla.ebpf \"x\" {\n  node<caret>js {}\n}")
        val intention = myFixture.findSingleIntention("Switch Alloy catalog version to v1.19.2")
        myFixture.launchAction(intention)

        val settings = AlloyCatalogSettings.getInstance(project)
        assertEquals(AlloyCatalogSettings.VersionMode.Pinned, settings.versionMode)
        assertEquals("v1.19.2", settings.pinnedVersion)
    }
}
