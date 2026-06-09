package com.maomaocake.grafanaalloyplugin.validator

import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import com.intellij.openapi.project.Project

/**
 * Project-level settings for the external `alloy validate` integration.
 *
 *  - [binaryPath] — absolute path to the `alloy` binary. Empty means "use PATH".
 *  - [triggerMode] — `Manual` (menu action only) or `OnIdle` (run after typing pauses).
 *  - [stabilityLevel] — passed to `--stability.level`, one of the three Alloy stability
 *    levels. Matters because components flagged as experimental or public-preview fail
 *    validation by default.
 *  - [communityComponentsEnabled] — passed as `--feature.community-components.enabled`.
 *
 * Persisted in `.idea/grafanaAlloy.xml`.
 */
@Service(Service.Level.PROJECT)
@State(
    name = "AlloyValidatorSettings",
    storages = [Storage("grafanaAlloy.xml")],
)
class AlloyValidatorSettings : PersistentStateComponent<AlloyValidatorSettings.State> {

    enum class TriggerMode { Manual, OnIdle }

    enum class Stability(val flag: String) {
        GenerallyAvailable("generally-available"),
        PublicPreview("public-preview"),
        Experimental("experimental"),
    }

    data class State(
        var binaryPath: String = "",
        var triggerMode: TriggerMode = TriggerMode.Manual,
        var stabilityLevel: Stability = Stability.GenerallyAvailable,
        var communityComponentsEnabled: Boolean = false,
    )

    private var state = State()

    var binaryPath: String
        get() = state.binaryPath
        set(value) { state.binaryPath = value }

    var triggerMode: TriggerMode
        get() = state.triggerMode
        set(value) { state.triggerMode = value }

    var stabilityLevel: Stability
        get() = state.stabilityLevel
        set(value) { state.stabilityLevel = value }

    var communityComponentsEnabled: Boolean
        get() = state.communityComponentsEnabled
        set(value) { state.communityComponentsEnabled = value }

    override fun getState(): State = state
    override fun loadState(loaded: State) { this.state = loaded }

    companion object {
        fun getInstance(project: Project): AlloyValidatorSettings =
            project.getService(AlloyValidatorSettings::class.java)
    }
}
