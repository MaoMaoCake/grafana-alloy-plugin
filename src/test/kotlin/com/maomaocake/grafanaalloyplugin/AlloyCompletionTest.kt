package com.maomaocake.grafanaalloyplugin

import com.intellij.testFramework.fixtures.BasePlatformTestCase

class AlloyCompletionTest : BasePlatformTestCase() {

    override fun getTestDataPath(): String = "src/test/testData/completion"

    fun testTopLevelCompletesComponents() {
        myFixture.configureByFile("topLevelPrefix.alloy")
        val lookups = myFixture.completeBasic()?.map { it.lookupString } ?: emptyList()
        assertTrue(
            "expected prometheus.scrape among completions, got ${lookups.take(10)}",
            lookups.contains("prometheus.scrape"),
        )
        assertTrue(
            "expected prometheus.remote_write among completions",
            lookups.contains("prometheus.remote_write"),
        )
    }

    fun testInsideBlockBodyCompletesComponents() {
        myFixture.configureByFile("insideBlockBodyPrefix.alloy")
        val lookups = myFixture.completeBasic()?.map { it.lookupString } ?: emptyList()
        assertTrue(
            "expected prometheus.scrape when inside another block body, got ${lookups.take(10)}",
            lookups.contains("prometheus.scrape"),
        )
    }

    fun testDottedPrefixMatchesOnlyThatNamespace() {
        myFixture.configureByFile("dottedPrefix.alloy")
        val lookups = myFixture.completeBasic()?.map { it.lookupString } ?: emptyList()
        // prometheus.ex* — prometheus.exporter.* must appear, otelcol.exporter.* must not.
        assertTrue(
            "expected at least one prometheus.exporter.* completion, got ${lookups.take(10)}",
            lookups.any { it.startsWith("prometheus.exporter") },
        )
        assertFalse(
            "should NOT offer otelcol.exporter.* when the prefix is prometheus.ex, but got $lookups",
            lookups.any { it.startsWith("otelcol.exporter") },
        )
    }

    fun testInsideAttributeValueDoesNotCompleteComponents() {
        myFixture.configureByFile("insideAttributeValue.alloy")
        val lookups = myFixture.completeBasic()?.map { it.lookupString } ?: emptyList()
        assertFalse(
            "should NOT offer component completion on RHS of an attribute, but got $lookups",
            lookups.contains("prometheus.scrape"),
        )
    }
}
