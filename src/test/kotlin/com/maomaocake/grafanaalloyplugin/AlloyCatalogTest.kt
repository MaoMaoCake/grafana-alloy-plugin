package com.maomaocake.grafanaalloyplugin

import com.intellij.testFramework.fixtures.BasePlatformTestCase
import com.maomaocake.grafanaalloyplugin.catalog.AlloyCatalogService

class AlloyCatalogTest : BasePlatformTestCase() {

    fun testBundledCatalogLoads() {
        val catalog = AlloyCatalogService.getInstance().catalog
        assertTrue("catalog should have a populated alloyVersion", catalog.alloyVersion.isNotBlank())
        assertTrue("expected >100 components, got ${catalog.components.size}", catalog.components.size > 100)
    }

    fun testKnownComponentsPresent() {
        val catalog = AlloyCatalogService.getInstance().catalog
        val byName = catalog.byName()
        for (name in listOf("prometheus.scrape", "prometheus.remote_write", "loki.source.file", "otelcol.receiver.otlp")) {
            assertNotNull("missing known component $name", byName[name])
        }
    }

    fun testPortTypesPopulated() {
        val catalog = AlloyCatalogService.getInstance().catalog
        val byName = catalog.byName()
        val scrape = byName["prometheus.scrape"]!!
        val remoteWrite = byName["prometheus.remote_write"]!!

        assertTrue("prometheus.scrape should accept MetricsReceiver",
            scrape.accepted().any { it.contains("MetricsReceiver") })
        assertTrue("prometheus.remote_write should export MetricsReceiver",
            remoteWrite.exported().any { it.contains("MetricsReceiver") })
        assertEquals("receiver", remoteWrite.exportsList().firstOrNull()?.name)
    }

    fun testArgsAndBlocksExtracted() {
        val catalog = AlloyCatalogService.getInstance().catalog
        val byName = catalog.byName()
        val scrape = byName["prometheus.scrape"]!!
        val forwardTo = scrape.argsList().firstOrNull { it.name == "forward_to" }
        assertNotNull("prometheus.scrape must expose forward_to arg", forwardTo)
        assertTrue("forward_to should be required", forwardTo!!.required)

        val remoteWrite = byName["prometheus.remote_write"]!!
        val endpoint = remoteWrite.blocksList().firstOrNull { it.name == "endpoint" }
        assertNotNull("prometheus.remote_write must expose endpoint block", endpoint)
        assertTrue("endpoint should be repeatable", endpoint!!.repeated)
        assertTrue("endpoint should nest a basic_auth block",
            endpoint.blocksList().any { it.name == "basic_auth" })
    }
}
