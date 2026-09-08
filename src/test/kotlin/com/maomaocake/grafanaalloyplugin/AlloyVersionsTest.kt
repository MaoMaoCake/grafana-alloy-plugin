package com.maomaocake.grafanaalloyplugin

import com.maomaocake.grafanaalloyplugin.catalog.AlloyVersions
import junit.framework.TestCase

/** Pure-JVM tests for the version parsing / nearest-bundled logic (no IntelliJ fixture needed). */
class AlloyVersionsTest : TestCase() {

    fun testParsesVersionShapes() {
        assertEquals(AlloyVersions.SemVer(1, 9, 2), AlloyVersions.parse("v1.9.2"))
        assertEquals(AlloyVersions.SemVer(1, 19, 2), AlloyVersions.parse("1.19.2"))
        // Real `alloy --version` output shape.
        assertEquals(
            AlloyVersions.SemVer(1, 17, 1),
            AlloyVersions.parse("alloy, version v1.17.1 (branch: HEAD, revision: abc123)"),
        )
    }

    fun testParseRejectsGarbage() {
        assertNull(AlloyVersions.parse(null))
        assertNull(AlloyVersions.parse(""))
        assertNull(AlloyVersions.parse("not a version"))
        assertNull(AlloyVersions.parse("v1.2")) // needs all three components
    }

    fun testParseStripsPreReleaseAndBuildSuffixes() {
        // A real `alloy --version` on a pre-release build must still resolve to X.Y.Z.
        assertEquals(AlloyVersions.SemVer(1, 2, 0), AlloyVersions.parse("v1.2.0-rc.1"))
        assertEquals(AlloyVersions.SemVer(1, 2, 0), AlloyVersions.parse("1.2.0+build.5"))
        assertEquals(
            AlloyVersions.SemVer(1, 19, 0),
            AlloyVersions.parse("alloy, version v1.19.0-rc.2 (branch: HEAD)"),
        )
    }

    fun testComparisonIsNumericNotLexical() {
        // The classic trap: lexically "1.9.2" > "1.10.0", but numerically it must be smaller.
        assertTrue(AlloyVersions.parse("v1.9.2")!! < AlloyVersions.parse("v1.10.0")!!)
        assertTrue(AlloyVersions.parse("v1.9.2")!! < AlloyVersions.parse("v1.19.2")!!)
        assertTrue(AlloyVersions.parse("v1.18.1")!! < AlloyVersions.parse("v1.18.10")!!)
    }

    fun testNearestBundledPicksHighestAtOrBelow() {
        val bundled = listOf("v1.9.2", "v1.17.1", "v1.18.1", "v1.19.2")
        // Exact match.
        assertEquals("v1.18.1", AlloyVersions.nearestBundled(AlloyVersions.parse("v1.18.1")!!, bundled))
        // Between bundled versions -> highest that is <= detected.
        assertEquals("v1.18.1", AlloyVersions.nearestBundled(AlloyVersions.parse("v1.18.5")!!, bundled))
        // Newer than everything bundled -> latest bundled.
        assertEquals("v1.19.2", AlloyVersions.nearestBundled(AlloyVersions.parse("v1.25.0")!!, bundled))
        // Older than everything bundled -> lowest bundled (don't claim features it lacks... but
        // we have nothing older to offer).
        assertEquals("v1.9.2", AlloyVersions.nearestBundled(AlloyVersions.parse("v1.5.0")!!, bundled))
    }

    fun testNearestBundledOnUnsortedInput() {
        // The manifest is normally ascending, but nearestBundled must not depend on that — it
        // sorts internally. Feed a shuffled list and expect the same answers.
        val shuffled = listOf("v1.19.2", "v1.9.2", "v1.18.1", "v1.17.1")
        assertEquals("v1.18.1", AlloyVersions.nearestBundled(AlloyVersions.parse("v1.18.5")!!, shuffled))
        assertEquals("v1.19.2", AlloyVersions.nearestBundled(AlloyVersions.parse("v1.25.0")!!, shuffled))
        assertEquals("v1.9.2", AlloyVersions.nearestBundled(AlloyVersions.parse("v1.5.0")!!, shuffled))
    }

    fun testNearestBundledIgnoresUnparseableEntries() {
        val withGarbage = listOf("garbage", "v1.9.2", "", "v1.19.2", "not.a.version")
        assertEquals("v1.9.2", AlloyVersions.nearestBundled(AlloyVersions.parse("v1.10.0")!!, withGarbage))
        assertEquals("v1.19.2", AlloyVersions.nearestBundled(AlloyVersions.parse("v2.0.0")!!, withGarbage))
    }

    fun testNearestBundledOnEmptyIsNull() {
        assertNull(AlloyVersions.nearestBundled(AlloyVersions.parse("v1.19.2")!!, emptyList()))
        assertNull(AlloyVersions.nearestBundled(AlloyVersions.parse("v1.19.2")!!, listOf("garbage", "")))
    }

    fun testLatest() {
        assertEquals("v1.19.2", AlloyVersions.latest(listOf("v1.9.2", "v1.19.2", "v1.17.1")))
        assertNull(AlloyVersions.latest(emptyList()))
    }
}
