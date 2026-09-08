package com.maomaocake.grafanaalloyplugin

import com.intellij.testFramework.fixtures.BasePlatformTestCase
import com.maomaocake.grafanaalloyplugin.catalog.AlloyCatalogConfigurable
import com.maomaocake.grafanaalloyplugin.catalog.AlloyCatalogService
import com.maomaocake.grafanaalloyplugin.catalog.AlloyCatalogSettings

/**
 * Guards the Configurable contract for the Version settings page: after [reset] with no user
 * interaction, [isModified] must be false for every persisted state — including a Pinned version
 * that is no longer bundled (which otherwise made the page open "dirty" and let Apply silently
 * rewrite the stored mode).
 */
class AlloyCatalogConfigurableTest : BasePlatformTestCase() {

    private fun freshlyReset(): AlloyCatalogConfigurable {
        val configurable = AlloyCatalogConfigurable(project)
        configurable.createComponent() // populates the combo model
        configurable.reset()
        return configurable
    }

    fun testNotModifiedAfterResetForLatestBundled() {
        AlloyCatalogSettings.getInstance(project).versionMode = AlloyCatalogSettings.VersionMode.LatestBundled
        val configurable = freshlyReset()
        try {
            assertFalse("LatestBundled must not report modified after reset", configurable.isModified())
        } finally {
            configurable.disposeUIResources()
        }
    }

    fun testNotModifiedAfterResetForAutoDetect() {
        AlloyCatalogSettings.getInstance(project).versionMode = AlloyCatalogSettings.VersionMode.AutoDetect
        val configurable = freshlyReset()
        try {
            assertFalse("AutoDetect must not report modified after reset", configurable.isModified())
        } finally {
            configurable.disposeUIResources()
        }
    }

    fun testNotModifiedAfterResetForBundledPinned() {
        val bundled = AlloyCatalogService.getInstance(project).manifest.versionTags().first()
        AlloyCatalogSettings.getInstance(project).apply {
            versionMode = AlloyCatalogSettings.VersionMode.Pinned
            pinnedVersion = bundled
        }
        val configurable = freshlyReset()
        try {
            assertFalse("Pinned-to-bundled must not report modified after reset", configurable.isModified())
        } finally {
            configurable.disposeUIResources()
        }
    }

    fun testNotModifiedAfterResetForUnbundledPinned() {
        // A pin left over from a plugin build that used to bundle this version.
        AlloyCatalogSettings.getInstance(project).apply {
            versionMode = AlloyCatalogSettings.VersionMode.Pinned
            pinnedVersion = "v99.99.99"
        }
        val configurable = freshlyReset()
        try {
            assertFalse(
                "an unbundled pin must not make the page open dirty (Apply would silently drop the pin)",
                configurable.isModified(),
            )
        } finally {
            configurable.disposeUIResources()
        }
    }
}
