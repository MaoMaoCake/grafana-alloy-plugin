package com.maomaocake.grafanaalloyplugin.injection

import com.intellij.openapi.options.Configurable
import com.intellij.openapi.project.Project
import com.intellij.ui.components.JBCheckBox
import com.intellij.ui.components.JBLabel
import com.intellij.util.ui.FormBuilder
import javax.swing.JComponent
import javax.swing.JPanel

/**
 * Settings page: *Settings → Languages & Frameworks → Alloy → ConfigMap injection*.
 *
 * Surfaces only the auto-convert toggle for now; future YAML-injection knobs (e.g. an
 * editable allow-list of keys) belong here so they're hidden in IDEs without YAML support.
 *
 * Registered via the optional `alloy-yaml.xml` config file so it never appears in IDEs that
 * don't bundle YAML support — the underlying [AlloyInjectionSettings] is still loadable
 * (it's a plain project service) but the page would be useless without injection running.
 */
class AlloyInjectionConfigurable(private val project: Project) : Configurable {

    private val autoConvertCheckbox = JBCheckBox(
        "Auto-convert quoted Alloy ConfigMap values to `|` block scalars on file open",
    )
    private var panel: JPanel? = null

    override fun getDisplayName(): String = "ConfigMap injection"

    override fun createComponent(): JComponent {
        autoConvertCheckbox.isSelected =
            AlloyInjectionSettings.getInstance(project).autoConvertQuotedScalars

        return FormBuilder.createFormBuilder()
            .addComponent(autoConvertCheckbox)
            .addComponent(
                JBLabel(
                    "<html><i>Quoted scalars (`config.alloy: \"...\"`) are rewritten to " +
                        "<code>|</code> block scalars when the YAML file is opened, so " +
                        "highlighting / completion / inspections work without an Alt-Enter " +
                        "dance. Whitespace has no meaning in Alloy, so the conversion " +
                        "doesn't change config behaviour. Disable if you want bytes-exact " +
                        "round-trips with the cluster.</i></html>",
                ),
            )
            .addComponentFillVertically(JPanel(), 0)
            .panel
            .also { panel = it }
    }

    override fun isModified(): Boolean =
        autoConvertCheckbox.isSelected !=
            AlloyInjectionSettings.getInstance(project).autoConvertQuotedScalars

    override fun apply() {
        AlloyInjectionSettings.getInstance(project).autoConvertQuotedScalars =
            autoConvertCheckbox.isSelected
    }

    override fun reset() {
        autoConvertCheckbox.isSelected =
            AlloyInjectionSettings.getInstance(project).autoConvertQuotedScalars
    }

    override fun disposeUIResources() {
        panel = null
    }
}
