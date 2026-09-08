package com.maomaocake.grafanaalloyplugin.catalog

import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import com.intellij.openapi.project.Project

/**
 * Project-level setting for which bundled Alloy catalog drives completion, inspections, and
 * inline docs. Different projects may target different Alloy versions, so this is per-project
 * (mirrors [com.maomaocake.grafanaalloyplugin.validator.AlloyValidatorSettings]).
 *
 *  - [VersionMode.LatestBundled] — always use the newest catalog shipped in the plugin. Default.
 *  - [VersionMode.AutoDetect] — probe the configured `alloy` binary's `--version` once and pick
 *    the nearest bundled catalog (see [AlloyCatalogService]). Falls back to latest bundled when
 *    no binary is configured or the probe fails.
 *  - [VersionMode.Pinned] — use exactly [pinnedVersion] (a version tag from the manifest). Falls
 *    back to latest bundled if that version isn't actually bundled.
 *
 * Persisted in `.idea/grafanaAlloy.xml` (shared file, distinct `name` from the validator).
 */
@Service(Service.Level.PROJECT)
@State(
    name = "AlloyCatalogSettings",
    storages = [Storage("grafanaAlloy.xml")],
)
class AlloyCatalogSettings : PersistentStateComponent<AlloyCatalogSettings.State> {

    enum class VersionMode { LatestBundled, AutoDetect, Pinned }

    data class State(
        var versionMode: VersionMode = VersionMode.LatestBundled,
        var pinnedVersion: String = "",
    )

    private var state = State()

    var versionMode: VersionMode
        get() = state.versionMode
        set(value) { state.versionMode = value }

    var pinnedVersion: String
        get() = state.pinnedVersion
        set(value) { state.pinnedVersion = value }

    override fun getState(): State = state
    override fun loadState(loaded: State) { this.state = loaded }

    companion object {
        fun getInstance(project: Project): AlloyCatalogSettings =
            project.getService(AlloyCatalogSettings::class.java)
    }
}
