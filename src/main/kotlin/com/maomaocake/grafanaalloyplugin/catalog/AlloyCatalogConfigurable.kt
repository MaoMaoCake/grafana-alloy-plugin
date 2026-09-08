package com.maomaocake.grafanaalloyplugin.catalog

import com.intellij.codeInsight.daemon.DaemonCodeAnalyzer
import com.intellij.openapi.options.Configurable
import com.intellij.openapi.project.Project
import com.intellij.ui.components.JBLabel
import com.intellij.util.ui.FormBuilder
import javax.swing.JComboBox
import javax.swing.JComponent
import javax.swing.JPanel

/**
 * *Settings → Languages & Frameworks → Alloy → Version* (nested under the main Alloy page via
 * `parentId=com.maomaocake.grafanaalloyplugin.settings.envfile`).
 *
 * Picks which bundled Alloy catalog drives completion, inspections, and inline docs:
 *   - **Latest bundled** (default) — always the newest catalog shipped in the plugin.
 *   - **Auto-detect** — probe the configured `alloy` binary (see the *Validate* page) and use
 *     the nearest bundled catalog. Best-effort; falls back to latest bundled.
 *   - a specific **pinned version** — for teams targeting an older/other Alloy release than the
 *     one installed locally.
 *
 * Applying a change reloads the catalog service and restarts the daemon so open files re-run
 * their inspections against the newly-selected catalog.
 */
class AlloyCatalogConfigurable(private val project: Project) : Configurable {

    private sealed class Choice {
        abstract val label: String
        override fun toString(): String = label

        class Latest(defaultVersion: String) : Choice() {
            override val label = if (defaultVersion.isBlank()) "Latest bundled"
            else "Latest bundled ($defaultVersion)"
        }

        object Auto : Choice() {
            override val label = "Auto-detect from alloy binary"
        }

        class Pinned(val version: String, components: Int) : Choice() {
            override val label = "$version ($components components)"
        }
    }

    private val manifest = AlloyCatalogService.getInstance(project).manifest
    private val combo = JComboBox<Choice>()
    private val activeLabel = JBLabel(" ")
    private var panel: JPanel? = null

    override fun getDisplayName(): String = "Version"

    override fun createComponent(): JComponent {
        val latest = Choice.Latest(manifest.resolvedDefault())
        combo.addItem(latest)
        combo.addItem(Choice.Auto)
        // Newest first so the common "pin to a recent release" case is near the top.
        for (entry in manifest.versions.sortedByDescending { AlloyVersions.parse(it.version) }) {
            combo.addItem(Choice.Pinned(entry.version, entry.components))
        }
        combo.addActionListener { updateActiveLabel() }

        reset()

        val builder = FormBuilder.createFormBuilder()
            .addLabeledComponent(JBLabel("Alloy version:"), combo, 1, false)
            .addComponent(
                JBLabel(
                    "<html><i>Selects which bundled catalog drives completion, inspections, and " +
                        "inline docs. Auto-detect reads the <code>alloy</code> binary configured on " +
                        "the <b>Validate</b> page.</i></html>",
                ),
            )
            .addSeparator()
            .addComponent(activeLabel)

        if (manifest.versions.isEmpty()) {
            combo.isEnabled = false
            builder.addComponent(
                JBLabel("<html><b>No catalogs are bundled.</b> Completion and inspections are disabled.</html>"),
            )
        }

        return builder.addComponentFillVertically(JPanel(), 0).panel.also { panel = it }
    }

    /** The version a selection would resolve to right now (for the read-only readout). */
    private fun previewVersion(choice: Choice?): String = when (choice) {
        is Choice.Latest -> manifest.resolvedDefault()
        is Choice.Pinned -> choice.version
        Choice.Auto, null -> AlloyCatalogService.getInstance(project).activeVersion
    }

    private fun updateActiveLabel() {
        val choice = combo.selectedItem as? Choice
        val version = previewVersion(choice)
        val components = manifest.entry(version)?.components
        activeLabel.text = when {
            version.isBlank() -> "<html>Active catalog: <b>none</b></html>"
            choice is Choice.Auto ->
                "<html>Active catalog: <b>$version</b>" +
                    (components?.let { " — $it components" } ?: "") +
                    " <i>(falls back here until the alloy binary is probed)</i></html>"
            else ->
                "<html>Active catalog: <b>$version</b>" + (components?.let { " — $it components" } ?: "") + "</html>"
        }
    }

    private fun currentChoice(): Choice = combo.selectedItem as? Choice ?: Choice.Latest(manifest.resolvedDefault())

    /**
     * The combo item that represents the persisted state — the single source of truth for both
     * [reset] and [isModified] so they can never disagree (a mismatch would make the page open
     * "dirty" and let Apply silently rewrite the stored mode). A persisted Pinned version that is
     * no longer bundled has no matching item, so it collapses to "Latest bundled" here; because
     * both methods use this same mapping, isModified() is correctly false right after reset().
     */
    private fun storedChoice(): Choice {
        val s = AlloyCatalogSettings.getInstance(project)
        return when (s.versionMode) {
            AlloyCatalogSettings.VersionMode.LatestBundled -> combo.getItemAt(0) // Latest is always first
            AlloyCatalogSettings.VersionMode.AutoDetect -> Choice.Auto
            AlloyCatalogSettings.VersionMode.Pinned ->
                (0 until combo.itemCount).map { combo.getItemAt(it) }
                    .firstOrNull { it is Choice.Pinned && it.version == s.pinnedVersion }
                    ?: combo.getItemAt(0)
        }
    }

    /** Stable value key so Choices (which have no equals) compare by mode + version, not identity. */
    private fun choiceKey(c: Choice): Pair<AlloyCatalogSettings.VersionMode, String> = when (c) {
        is Choice.Latest -> AlloyCatalogSettings.VersionMode.LatestBundled to ""
        Choice.Auto -> AlloyCatalogSettings.VersionMode.AutoDetect to ""
        is Choice.Pinned -> AlloyCatalogSettings.VersionMode.Pinned to c.version
    }

    override fun isModified(): Boolean = choiceKey(currentChoice()) != choiceKey(storedChoice())

    override fun apply() {
        val s = AlloyCatalogSettings.getInstance(project)
        when (val choice = currentChoice()) {
            is Choice.Latest -> s.versionMode = AlloyCatalogSettings.VersionMode.LatestBundled
            Choice.Auto -> s.versionMode = AlloyCatalogSettings.VersionMode.AutoDetect
            is Choice.Pinned -> {
                s.versionMode = AlloyCatalogSettings.VersionMode.Pinned
                s.pinnedVersion = choice.version
            }
        }
        // Drop cached state and re-highlight open files against the new catalog.
        AlloyCatalogService.getInstance(project).reload()
        DaemonCodeAnalyzer.getInstance(project).restart()
    }

    override fun reset() {
        combo.selectedItem = storedChoice()
        updateActiveLabel()
    }

    override fun disposeUIResources() {
        panel = null
    }
}
