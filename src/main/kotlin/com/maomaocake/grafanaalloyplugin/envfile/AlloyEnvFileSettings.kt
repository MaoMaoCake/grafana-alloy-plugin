package com.maomaocake.grafanaalloyplugin.envfile

import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import com.intellij.openapi.project.Project

/**
 * Project-level settings for envfile templating. Two fields:
 *  - [envFilePath] — path to a dotenv-style file whose keys feed `${...}` completion inside
 *    Alloy string literals. Blank disables the feature.
 *  - [showValuesInCompletion] — when true, the completion popup shows each variable's value
 *    next to its name. Default **off** so secrets don't leak into the popup on a screenshare.
 *
 * Persisted in `.idea/grafanaAlloy.xml` so settings travel with the project.
 */
@Service(Service.Level.PROJECT)
@State(
    name = "AlloyEnvFileSettings",
    storages = [Storage("grafanaAlloy.xml")],
)
class AlloyEnvFileSettings : PersistentStateComponent<AlloyEnvFileSettings.State> {

    data class State(
        var envFilePath: String = "",
        var showValuesInCompletion: Boolean = false,
    )

    private var state = State()

    var envFilePath: String
        get() = state.envFilePath
        set(value) { state.envFilePath = value }

    var showValuesInCompletion: Boolean
        get() = state.showValuesInCompletion
        set(value) { state.showValuesInCompletion = value }

    override fun getState(): State = state
    override fun loadState(loaded: State) { this.state = loaded }

    companion object {
        fun getInstance(project: Project): AlloyEnvFileSettings =
            project.getService(AlloyEnvFileSettings::class.java)
    }
}
