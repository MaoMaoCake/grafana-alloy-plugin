package com.maomaocake.grafanaalloyplugin.injection

import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import com.intellij.openapi.project.Project

/**
 * Project-level settings for the Alloy-in-YAML injector.
 *
 * Currently a single toggle — kept in its own service so future injection knobs (e.g. an
 * editable allow-list of YAML keys) can land here without touching unrelated settings files.
 *
 * Default for [autoConvertQuotedScalars] is **on**: whitespace has no meaning in Alloy
 * configs, so the only "loss" from converting `"...\n..."` to a `|` block is meaningless
 * trailing whitespace, and the win is that highlighting/completion/inspections light up
 * immediately without an Alt-Enter dance.
 */
@Service(Service.Level.PROJECT)
@State(
    name = "AlloyInjectionSettings",
    storages = [Storage("grafanaAlloy.xml")],
)
class AlloyInjectionSettings : PersistentStateComponent<AlloyInjectionSettings.State> {

    data class State(
        var autoConvertQuotedScalars: Boolean = true,
    )

    private var state = State()

    var autoConvertQuotedScalars: Boolean
        get() = state.autoConvertQuotedScalars
        set(value) { state.autoConvertQuotedScalars = value }

    override fun getState(): State = state
    override fun loadState(loaded: State) { this.state = loaded }

    companion object {
        fun getInstance(project: Project): AlloyInjectionSettings =
            project.getService(AlloyInjectionSettings::class.java)
    }
}
