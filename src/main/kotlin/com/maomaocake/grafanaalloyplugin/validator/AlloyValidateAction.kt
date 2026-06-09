package com.maomaocake.grafanaalloyplugin.validator

import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.Task
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.maomaocake.grafanaalloyplugin.AlloyFileType
import java.io.File

/**
 * `Tools → Validate Alloy Config` — runs `alloy validate` against the directory containing
 * the currently focused Alloy file. Surfaces the result as a balloon notification with a
 * short summary; any located diagnostics are also shown in the editor via the annotator if
 * auto-refresh is on, otherwise the user can re-run to see inline squiggles.
 *
 * Hidden on Windows since `alloy validate` isn't in those binaries. Hidden everywhere when
 * the focused file isn't an Alloy file.
 */
class AlloyValidateAction : AnAction() {

    override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

    override fun update(e: AnActionEvent) {
        e.presentation.isEnabledAndVisible =
            AlloyValidatorAvailability.isSupportedOs && focusedAlloyFile(e) != null
    }

    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        val file = focusedAlloyFile(e) ?: return
        val parent = file.parent ?: return
        val dir = File(parent.path)

        object : Task.Backgroundable(project, "Running alloy validate", /* canBeCancelled = */ true) {
            override fun run(indicator: ProgressIndicator) {
                indicator.text = "alloy validate ${parent.name}"
                val result = AlloyValidatorRunner.run(project, dir)
                notify(project, file, result)
            }
        }.queue()
    }

    private fun focusedAlloyFile(e: AnActionEvent): VirtualFile? {
        val vf = e.getData(CommonDataKeys.VIRTUAL_FILE) ?: return null
        return vf.takeIf { it.fileType === AlloyFileType }
    }

    private fun notify(project: Project, file: VirtualFile, result: AlloyValidatorRunner.RunResult) {
        val group = NotificationGroupManager.getInstance().getNotificationGroup(NOTIFICATION_GROUP)
        val (title, body, type) = summarize(file, result)
        group.createNotification(title, body, type).notify(project)
    }

    private fun summarize(
        file: VirtualFile,
        result: AlloyValidatorRunner.RunResult,
    ): Triple<String, String, NotificationType> {
        if (result.crashedBeforeRunning) {
            return Triple(
                "Alloy validate failed to start",
                "${result.failureReason}. Check the Alloy binary path under " +
                    "Settings → Languages & Frameworks → Alloy → Validate.",
                NotificationType.ERROR,
            )
        }
        val diagnostics = AlloyValidatorOutputParser.parse(result.stderr)
        if (result.exitCode == 0 && diagnostics.isEmpty()) {
            return Triple(
                "Alloy validate: no issues",
                "${file.parent?.name ?: file.name} validated successfully.",
                NotificationType.INFORMATION,
            )
        }
        val first = diagnostics.firstOrNull()
        val summary = when {
            diagnostics.size == 1 && first != null ->
                "1 issue: ${first.message.take(140)}"
            diagnostics.isNotEmpty() ->
                "${diagnostics.size} issues. First: ${first!!.message.take(140)}"
            else ->
                "alloy exited ${result.exitCode}. See IDE log for raw stderr."
        }
        val type = if (diagnostics.isNotEmpty() || result.exitCode != 0) NotificationType.WARNING
                   else NotificationType.INFORMATION
        return Triple("Alloy validate: ${file.parent?.name ?: "results"}", summary, type)
    }

    companion object {
        const val NOTIFICATION_GROUP = "Grafana Alloy"
    }
}
