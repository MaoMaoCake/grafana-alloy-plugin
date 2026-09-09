package com.maomaocake.grafanaalloyplugin

import com.intellij.testFramework.fixtures.BasePlatformTestCase
import com.maomaocake.grafanaalloyplugin.catalog.AlloyCrossVersion

/**
 * Verifies the cross-version index against the actual bundled catalogs, using components/args
 * that were genuinely added or removed between v1.9.2 and v1.19.2.
 */
class AlloyCrossVersionTest : BasePlatformTestCase() {

    fun testComponentAddedInLaterVersions() {
        // otelcol.exporter.file: added after v1.9.2 (present v1.17.1, v1.18.1, v1.19.2).
        val versions = AlloyCrossVersion.versionsWithComponent("otelcol.exporter.file")
        assertFalse("should not be in v1.9.2", versions.contains("v1.9.2"))
        assertTrue("should be in v1.19.2", versions.contains("v1.19.2"))
        assertTrue("should be in v1.17.1", versions.contains("v1.17.1"))
    }

    fun testComponentRemovedInLaterVersions() {
        // otelcol.receiver.opencensus: present only in v1.9.2, removed later.
        val versions = AlloyCrossVersion.versionsWithComponent("otelcol.receiver.opencensus")
        assertTrue("should be in v1.9.2", versions.contains("v1.9.2"))
        assertFalse("should not be in v1.19.2", versions.contains("v1.19.2"))
    }

    fun testComponentPresentEverywhereInSemverOrder() {
        val versions = AlloyCrossVersion.versionsWithComponent("prometheus.scrape")
        // Present in every bundled version, returned in ascending *semantic* order (not lexical —
        // lexically "v1.9.2" would sort last).
        assertEquals(listOf("v1.9.2", "v1.17.1", "v1.18.1", "v1.19.2"), versions)
    }

    fun testUnknownComponentEverywhereIsEmpty() {
        assertTrue(AlloyCrossVersion.versionsWithComponent("does.not.exist").isEmpty())
        // A single-segment / custom-module-shaped name is never a catalog component.
        assertTrue(AlloyCrossVersion.versionsWithComponent("mymodule").isEmpty())
    }

    fun testArgAddedInLaterVersion() {
        // otelcol.exporter.debug gained `output_paths` after v1.9.2.
        val versions = AlloyCrossVersion.versionsWithArg("otelcol.exporter.debug", emptyList(), "output_paths")
        assertFalse("output_paths should not be in v1.9.2", versions.contains("v1.9.2"))
        assertTrue("output_paths should be in v1.19.2", versions.contains("v1.19.2"))
    }

    fun testBlockAddedInLaterVersion() {
        // beyla.ebpf gained a `nodejs` nested block after v1.9.2.
        val versions = AlloyCrossVersion.versionsWithBlock("beyla.ebpf", emptyList(), "nodejs")
        assertFalse("nodejs block should not be in v1.9.2", versions.contains("v1.9.2"))
        assertTrue("nodejs block should be in v1.19.2", versions.contains("v1.19.2"))
    }

    fun testDottedNestedBlockResolvesAcrossVersions() {
        // loki.process `stage.truncate` is a DOTTED nested block — stored as `stage > truncate`,
        // never as a literal `stage.truncate`. A flat name match would miss it entirely; the
        // dotted-aware resolver must find it in v1.17.1+ and not v1.9.2.
        val truncate = AlloyCrossVersion.versionsWithBlock("loki.process", emptyList(), "stage.truncate")
        assertEquals(listOf("v1.17.1", "v1.18.1", "v1.19.2"), truncate)

        // `stage.split_json` was added only in v1.19.2.
        val splitJson = AlloyCrossVersion.versionsWithBlock("loki.process", emptyList(), "stage.split_json")
        assertEquals(listOf("v1.19.2"), splitJson)
    }

    fun testArgOnMissingComponentIsEmpty() {
        assertTrue(AlloyCrossVersion.versionsWithArg("does.not.exist", emptyList(), "whatever").isEmpty())
    }
}
