package com.maomaocake.grafanaalloyplugin

import com.intellij.testFramework.fixtures.BasePlatformTestCase
import com.maomaocake.grafanaalloyplugin.catalog.AlloyCatalogService
import com.maomaocake.grafanaalloyplugin.catalog.AlloyCatalogSettings
import com.maomaocake.grafanaalloyplugin.validator.AlloyValidatorSettings

/**
 * Exercises the multi-version wiring: the manifest, the default resolution, and pinning a
 * specific bundled version. Robust to how many catalogs are actually bundled — it iterates
 * whatever the manifest lists rather than hard-coding a version set.
 */
class AlloyCatalogVersionTest : BasePlatformTestCase() {

    fun testManifestListsAtLeastOneLoadableCatalog() {
        val service = AlloyCatalogService.getInstance(project)
        val manifest = service.manifest
        assertTrue("manifest should list at least one bundled version", manifest.versions.isNotEmpty())
        assertTrue(
            "default version ${manifest.defaultVersion} should be one of ${manifest.versionTags()}",
            manifest.hasVersion(manifest.resolvedDefault()),
        )
    }

    fun testEveryBundledVersionLoads() {
        val service = AlloyCatalogService.getInstance(project)
        val settings = AlloyCatalogSettings.getInstance(project)
        for (entry in service.manifest.versions) {
            settings.versionMode = AlloyCatalogSettings.VersionMode.Pinned
            settings.pinnedVersion = entry.version
            service.reload()

            assertEquals("activeVersion should reflect the pinned version", entry.version, service.activeVersion)
            val catalog = service.catalog
            assertEquals(
                "loaded catalog's alloyVersion should match the manifest entry",
                entry.version, catalog.alloyVersion,
            )
            assertEquals(
                "loaded component count should match the manifest for ${entry.version}",
                entry.components, catalog.components.size,
            )
        }
    }

    fun testDefaultModeUsesManifestDefault() {
        val service = AlloyCatalogService.getInstance(project)
        val settings = AlloyCatalogSettings.getInstance(project)
        settings.versionMode = AlloyCatalogSettings.VersionMode.LatestBundled
        service.reload()
        assertEquals(service.manifest.resolvedDefault(), service.activeVersion)
    }

    fun testAutoDetectFallsBackToDefaultWhenBinaryMissing() {
        val service = AlloyCatalogService.getInstance(project)
        // Point at a binary that can't start so the probe fails fast (no 5s timeout wait).
        AlloyValidatorSettings.getInstance(project).binaryPath = "/nonexistent/definitely-not-alloy"
        val settings = AlloyCatalogSettings.getInstance(project)
        settings.versionMode = AlloyCatalogSettings.VersionMode.AutoDetect
        service.reload()
        // maybeStartAutoDetect only *spawns* a background probe; the synchronous result is always
        // the fallback (latest bundled), so this assertion is race-free.
        assertEquals(service.manifest.resolvedDefault(), service.activeVersion)
        assertTrue("auto-detect fallback catalog should be populated", service.catalog.components.isNotEmpty())
    }

    fun testPinnedUnknownVersionFallsBackToDefault() {
        val service = AlloyCatalogService.getInstance(project)
        val settings = AlloyCatalogSettings.getInstance(project)
        settings.versionMode = AlloyCatalogSettings.VersionMode.Pinned
        settings.pinnedVersion = "v99.99.99"
        service.reload()
        assertEquals(service.manifest.resolvedDefault(), service.activeVersion)
        assertTrue("fallback catalog should be populated", service.catalog.components.isNotEmpty())
    }
}
