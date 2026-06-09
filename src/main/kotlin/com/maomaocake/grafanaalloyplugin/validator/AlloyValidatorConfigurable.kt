package com.maomaocake.grafanaalloyplugin.validator

import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory
import com.intellij.openapi.options.Configurable
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.TextFieldWithBrowseButton
import com.intellij.ui.components.JBCheckBox
import com.intellij.ui.components.JBLabel
import com.intellij.util.ui.FormBuilder
import javax.swing.ButtonGroup
import javax.swing.JComponent
import javax.swing.JPanel
import javax.swing.JRadioButton

/**
 * *Settings → Languages & Frameworks → Alloy → Validate* (nested under the main Alloy page
 * via `parentId=com.maomaocake.grafanaalloyplugin.settings.envfile`). Surfaces:
 *
 *  - Binary path (with a browse button; blank falls back to `PATH`).
 *  - `Alloy validate` version probe — tells the user whether the binary is actually runnable.
 *  - Manual vs on-idle trigger selector.
 *  - Stability level dropdown + community-components toggle (passed as `alloy validate` flags).
 *
 * On Windows the page shows a single banner explaining why the feature is disabled; the
 * fields are grayed out since the subcommand doesn't ship in Windows binaries.
 */
class AlloyValidatorConfigurable(private val project: Project) : Configurable {

    private val binaryPathField = TextFieldWithBrowseButton().apply {
        addBrowseFolderListener(
            "Select alloy Binary",
            "Absolute path to the alloy executable. Leave blank to use PATH.",
            project,
            FileChooserDescriptorFactory.createSingleFileDescriptor(),
        )
    }

    private val manualRadio = JRadioButton("Manual (run from Tools → Validate Alloy Config)")
    private val onIdleRadio = JRadioButton("On idle (re-validate after typing pauses)")
    private val stabilityCombo = javax.swing.JComboBox(AlloyValidatorSettings.Stability.values())
    private val communityToggle = JBCheckBox("Enable community components (--feature.community-components.enabled)")
    private val probeLabel = JBLabel(" ")

    private var panel: JPanel? = null

    override fun getDisplayName(): String = "Validate"

    override fun createComponent(): JComponent {
        val s = AlloyValidatorSettings.getInstance(project)
        binaryPathField.text = s.binaryPath
        manualRadio.isSelected = s.triggerMode == AlloyValidatorSettings.TriggerMode.Manual
        onIdleRadio.isSelected = s.triggerMode == AlloyValidatorSettings.TriggerMode.OnIdle
        stabilityCombo.selectedItem = s.stabilityLevel
        communityToggle.isSelected = s.communityComponentsEnabled

        val triggerGroup = ButtonGroup().apply {
            add(manualRadio)
            add(onIdleRadio)
        }
        // JRadioButton is group-free by default; we need both-or-nothing toggle.
        val triggerPanel = JPanel().apply {
            layout = javax.swing.BoxLayout(this, javax.swing.BoxLayout.Y_AXIS)
            add(manualRadio)
            add(onIdleRadio)
        }
        // Silence the warning about `triggerGroup` being unused: it owns the radio exclusion.
        @Suppress("UNUSED_VARIABLE") val forceGroup = triggerGroup

        val probeButton = javax.swing.JButton("Test binary").apply {
            addActionListener { runProbe() }
        }

        val formBuilder = FormBuilder.createFormBuilder()
            .addLabeledComponent(JBLabel("Alloy binary:"), binaryPathField, 1, false)
            .addComponent(
                JBLabel(
                    "<html><i>Blank uses <code>PATH</code>. The <code>alloy validate</code> " +
                        "subcommand ships with macOS and Linux binaries only.</i></html>",
                ),
            )
            .addLabeledComponent(JBLabel("Test:"), probeButton)
            .addComponent(probeLabel)
            .addSeparator()
            .addComponent(JBLabel("Trigger:"))
            .addComponent(triggerPanel)
            .addSeparator()
            .addLabeledComponent(JBLabel("Minimum stability:"), stabilityCombo, 1, false)
            .addComponent(communityToggle)

        if (!AlloyValidatorAvailability.isSupportedOs) {
            formBuilder.addComponent(
                JBLabel(
                    "<html><b>Validation unavailable on this OS.</b><br>" +
                        "<i>The <code>alloy validate</code> subcommand is not shipped in Windows " +
                        "binaries — this is an upstream limitation in Grafana Alloy itself. " +
                        "Other plugin features (completion, inspections, inline docs) still work.</i></html>",
                ),
            )
            binaryPathField.isEnabled = false
            manualRadio.isEnabled = false
            onIdleRadio.isEnabled = false
            stabilityCombo.isEnabled = false
            communityToggle.isEnabled = false
            probeButton.isEnabled = false
        }

        return formBuilder
            .addComponentFillVertically(JPanel(), 0)
            .panel
            .also { panel = it }
    }

    private fun runProbe() {
        val version = AlloyValidatorRunner.probeVersion(binaryPathField.text.trim())
        probeLabel.text = if (version != null) {
            "<html><font color='green'>OK — $version</font></html>"
        } else {
            "<html><font color='red'>Could not run <code>alloy --version</code> at this path.</font></html>"
        }
    }

    override fun isModified(): Boolean {
        val s = AlloyValidatorSettings.getInstance(project)
        return binaryPathField.text != s.binaryPath ||
            currentTriggerMode() != s.triggerMode ||
            stabilityCombo.selectedItem != s.stabilityLevel ||
            communityToggle.isSelected != s.communityComponentsEnabled
    }

    override fun apply() {
        val s = AlloyValidatorSettings.getInstance(project)
        s.binaryPath = binaryPathField.text.trim()
        s.triggerMode = currentTriggerMode()
        s.stabilityLevel = stabilityCombo.selectedItem as AlloyValidatorSettings.Stability
        s.communityComponentsEnabled = communityToggle.isSelected
    }

    override fun reset() {
        val s = AlloyValidatorSettings.getInstance(project)
        binaryPathField.text = s.binaryPath
        manualRadio.isSelected = s.triggerMode == AlloyValidatorSettings.TriggerMode.Manual
        onIdleRadio.isSelected = s.triggerMode == AlloyValidatorSettings.TriggerMode.OnIdle
        stabilityCombo.selectedItem = s.stabilityLevel
        communityToggle.isSelected = s.communityComponentsEnabled
        probeLabel.text = " "
    }

    override fun disposeUIResources() {
        panel = null
    }

    private fun currentTriggerMode(): AlloyValidatorSettings.TriggerMode =
        if (onIdleRadio.isSelected) AlloyValidatorSettings.TriggerMode.OnIdle
        else AlloyValidatorSettings.TriggerMode.Manual
}
