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

    fun testInsideBlockBodyOffersCatalogMembers() {
        myFixture.configureByFile("insideLokiWrite.alloy")
        val lookups = myFixture.completeBasic()?.map { it.lookupString } ?: emptyList()
        assertTrue(
            "expected `endpoint` nested block inside loki.write, got ${lookups.take(15)}",
            lookups.contains("endpoint"),
        )
        assertTrue(
            "expected `external_labels` attribute inside loki.write, got ${lookups.take(15)}",
            lookups.contains("external_labels"),
        )
        assertFalse(
            "should NOT offer top-level components like prometheus.scrape inside loki.write, got ${lookups.take(15)}",
            lookups.contains("prometheus.scrape"),
        )
    }

    fun testInsideBlockBodyWithPrefixFilters() {
        myFixture.configureByFile("insideLokiWriteWithPrefix.alloy")
        val lookups = myFixture.completeBasic()?.map { it.lookupString } ?: emptyList()
        assertFalse(
            "should NOT offer prometheus.scrape when typing `prom` inside loki.write body, got $lookups",
            lookups.contains("prometheus.scrape"),
        )
    }

    fun testInsideNestedEndpointBlockOffersEndpointFields() {
        myFixture.configureByFile("insideNestedEndpoint.alloy")
        val lookups = myFixture.completeBasic()?.map { it.lookupString } ?: emptyList()
        assertTrue(
            "expected `url` attribute inside prometheus.remote_write.endpoint, got ${lookups.take(15)}",
            lookups.contains("url"),
        )
        assertTrue(
            "expected `basic_auth` nested block inside prometheus.remote_write.endpoint, got ${lookups.take(15)}",
            lookups.contains("basic_auth"),
        )
    }

    fun testInsideComponentNestedInDeclareStillOffersComponentMembers() {
        myFixture.configureByFile("insideDeclareNested.alloy")
        val lookups = myFixture.completeBasic()?.map { it.lookupString } ?: emptyList()
        assertTrue(
            "loki.write body inside a declare should still offer endpoint, got ${lookups.take(15)}",
            lookups.contains("endpoint"),
        )
        assertFalse(
            "loki.write body inside a declare should not offer top-level components, got ${lookups.take(15)}",
            lookups.contains("prometheus.scrape"),
        )
    }

    fun testInsideDeclareBodyBehavesLikeTopLevel() {
        myFixture.configureByFile("insideDeclare.alloy")
        val lookups = myFixture.completeBasic()?.map { it.lookupString } ?: emptyList()
        assertTrue(
            "declare body should offer full component catalog, expected prometheus.scrape, got ${lookups.take(15)}",
            lookups.contains("prometheus.scrape"),
        )
    }

    fun testForwardToMetricsOffersOnlyMetricsReceivers() {
        myFixture.configureByFile("forwardToMetrics.alloy")
        val lookups = myFixture.completeBasic()?.map { it.lookupString } ?: emptyList()
        assertTrue(
            "expected prometheus.remote_write.rw.receiver among completions, got ${lookups.take(15)}",
            lookups.any { it == "prometheus.remote_write.rw.receiver" },
        )
        assertFalse(
            "should NOT offer loki.write references in a metrics forward_to, got $lookups",
            lookups.any { it.startsWith("loki.write") },
        )
    }

    fun testForwardToLogsOffersOnlyLogsReceivers() {
        myFixture.configureByFile("forwardToLogs.alloy")
        val lookups = myFixture.completeBasic()?.map { it.lookupString } ?: emptyList()
        assertTrue(
            "expected loki.write.logs.receiver in a logs forward_to, got ${lookups.take(15)}",
            lookups.any { it == "loki.write.logs.receiver" },
        )
        assertFalse(
            "should NOT offer prometheus.remote_write references in a logs forward_to, got $lookups",
            lookups.any { it.startsWith("prometheus.remote_write") },
        )
    }

    fun testTargetsOffersOnlyTargetExporters() {
        myFixture.configureByFile("targetsReference.alloy")
        val lookups = myFixture.completeBasic()?.map { it.lookupString } ?: emptyList()
        assertTrue(
            "expected discovery.kubernetes.pods.targets in a targets = [ ... ], got ${lookups.take(15)}",
            lookups.any { it == "discovery.kubernetes.pods.targets" },
        )
        assertTrue(
            "expected prometheus.exporter.cadvisor.cadvisor.targets in a targets = [ ... ], got ${lookups.take(15)}",
            lookups.any { it == "prometheus.exporter.cadvisor.cadvisor.targets" },
        )
        assertFalse(
            "should NOT offer prometheus.remote_write receivers in a targets list, got $lookups",
            lookups.any { it.startsWith("prometheus.remote_write") },
        )
    }

    fun testStringAttributeInsertsEmptyQuotedValue() {
        myFixture.configureByFile("attrInsertString_before.alloy")
        val items = myFixture.completeBasic()
        val urlItem = items.first { it.lookupString == "url" }
        myFixture.lookup.currentItem = urlItem
        myFixture.finishLookup('\n')
        myFixture.checkResultByFile("attrInsertString_after.alloy")
    }

    fun testListAttributeInsertsEmptyBrackets() {
        myFixture.configureByFile("attrInsertList_before.alloy")
        // `forward_<caret>` is a unique prefix match; completeBasic auto-inserts.
        myFixture.completeBasic()
        myFixture.checkResultByFile("attrInsertList_after.alloy")
    }

    fun testTargetsAttributeInsertsBareRhs() {
        myFixture.configureByFile("attrInsertTargets_before.alloy")
        val items = myFixture.completeBasic()
        val targetsItem = items.first { it.lookupString == "targets" }
        myFixture.lookup.currentItem = targetsItem
        myFixture.finishLookup('\n')
        myFixture.checkResultByFile("attrInsertTargets_after.alloy")
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
