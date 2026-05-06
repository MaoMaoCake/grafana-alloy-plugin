package com.maomaocake.grafanaalloyplugin.envfile

import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory
import com.intellij.openapi.options.Configurable
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.TextFieldWithBrowseButton
import com.intellij.ui.components.JBCheckBox
import com.intellij.ui.components.JBLabel
import com.intellij.util.ui.FormBuilder
import javax.swing.JComponent
import javax.swing.JPanel

/**
 * Settings page: *Settings → Languages & Frameworks → Alloy*.
 *
 * Two fields: a path picker for the envfile, and a toggle controlling whether env values are
 * shown in completion popups. Toggle defaults off so secrets don't leak on a screenshare;
 * users with non-sensitive env vars can flip it on for richer completion.
 */
class AlloyEnvFileConfigurable(private val project: Project) : Configurable {

    private val envFilePathField = TextFieldWithBrowseButton().apply {
        addBrowseFolderListener(
            /* title = */ "Select Envfile",
            /* description = */ "Path to a dotenv-style file whose keys feed `\${...}` completion",
            /* project = */ project,
            /* descriptor = */ FileChooserDescriptorFactory.createSingleFileDescriptor(),
        )
    }
    private val showValuesCheckbox = JBCheckBox("Show variable values in completion popup")
    private var panel: JPanel? = null

    override fun getDisplayName(): String = "Alloy"

    override fun createComponent(): JComponent {
        val settings = AlloyEnvFileSettings.getInstance(project)
        envFilePathField.text = settings.envFilePath
        showValuesCheckbox.isSelected = settings.showValuesInCompletion

        return FormBuilder.createFormBuilder()
            .addLabeledComponent(JBLabel("Envfile for \${...} completion:"), envFilePathField, 1, false)
            .addComponent(
                JBLabel(
                    "<html><i>Path is absolute or relative to the project root. " +
                        "Leave blank to disable envfile completion.</i></html>",
                ),
            )
            .addComponent(showValuesCheckbox)
            .addComponent(
                JBLabel(
                    "<html><i>Off by default so secret values don't appear in the popup on a screenshare.</i></html>",
                ),
            )
            .addComponentFillVertically(JPanel(), 0)
            .panel
            .also { panel = it }
    }

    override fun isModified(): Boolean {
        val s = AlloyEnvFileSettings.getInstance(project)
        return envFilePathField.text != s.envFilePath ||
            showValuesCheckbox.isSelected != s.showValuesInCompletion
    }

    override fun apply() {
        val s = AlloyEnvFileSettings.getInstance(project)
        s.envFilePath = envFilePathField.text.trim()
        s.showValuesInCompletion = showValuesCheckbox.isSelected
    }

    override fun reset() {
        val s = AlloyEnvFileSettings.getInstance(project)
        envFilePathField.text = s.envFilePath
        showValuesCheckbox.isSelected = s.showValuesInCompletion
    }

    override fun disposeUIResources() {
        panel = null
    }
}
