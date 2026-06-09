package com.maomaocake.grafanaalloyplugin.validator

import com.intellij.notification.Notification
import com.intellij.notification.NotificationAction
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
 * `Tools → Validate Alloy Config` and the right-click *Validate Alloy Config* item in the
 * Project View / editor tab. Resolves the validation target from whatever the user clicked:
 *
 *  - Right-clicked an `*.alloy` file → validate just that file.
 *  - Right-clicked a directory → validate the directory (mirrors `alloy validate <dir>`).
 *  - Invoked from the editor with no explicit selection → validate the currently focused
 *    `*.alloy` file.
 *
 * Hidden on Windows since `alloy validate` isn't in those binaries. Hidden everywhere when
 * the click target isn't an Alloy file or a directory containing one.
 */
class AlloyValidateAction : AnAction() {

    override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

    override fun update(e: AnActionEvent) {
        if (!AlloyValidatorAvailability.isSupportedOs) {
            e.presentation.isEnabledAndVisible = false
            return
        }
        val target = resolveTarget(e)
        e.presentation.isEnabledAndVisible = target != null
        if (target != null) {
            e.presentation.text = when {
                target.isDirectory -> "Validate Alloy Config in '${target.name}'"
                else -> "Validate '${target.name}'"
            }
        }
    }

    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        val target = resolveTarget(e) ?: return
        val file = File(target.path)

        object : Task.Backgroundable(project, "Running alloy validate", /* canBeCancelled = */ true) {
            override fun run(indicator: ProgressIndicator) {
                indicator.text = "alloy validate ${target.name}"
                val result = AlloyValidatorRunner.run(project, file)
                val diagnostics = if (result.crashedBeforeRunning) emptyList()
                                  else AlloyValidatorOutputParser.parse(result.stderr)
                AlloyValidateConsole.getInstance(project).show(target.name, result, diagnostics)
                notify(project, target, result, diagnostics)
            }
        }.queue()
    }

    /**
     * Where to point `alloy validate`. Priority:
     *  1. The right-clicked `VirtualFile`s — first selection wins. Directory or `.alloy` file
     *     are both fine; anything else gets filtered.
     *  2. Fallback to the focused editor file when invoked from the Tools menu.
     */
    private fun resolveTarget(e: AnActionEvent): VirtualFile? {
        val multi = e.getData(CommonDataKeys.VIRTUAL_FILE_ARRAY)
        if (multi != null) {
            multi.firstOrNull(::isValidatable)?.let { return it }
        }
        val single = e.getData(CommonDataKeys.VIRTUAL_FILE)
        return single?.takeIf(::isValidatable)
    }

    private fun isValidatable(vf: VirtualFile): Boolean =
        vf.isDirectory || vf.fileType === AlloyFileType

    private fun notify(
        project: Project,
        target: VirtualFile,
        result: AlloyValidatorRunner.RunResult,
        diagnostics: List<AlloyValidatorOutputParser.Diagnostic>,
    ) {
        val group = NotificationGroupManager.getInstance().getNotificationGroup(NOTIFICATION_GROUP)
        val (title, body, type) = summarize(target, result, diagnostics)
        val notification = group.createNotification(title, body, type)
        if (!result.crashedBeforeRunning) {
            notification.addAction(object : NotificationAction("Show details") {
                override fun actionPerformed(e: AnActionEvent, n: Notification) {
                    AlloyValidateConsole.getInstance(project).show(target.name, result, diagnostics)
                    n.expire()
                }
            })
        }
        notification.notify(project)
    }

    private fun summarize(
        target: VirtualFile,
        result: AlloyValidatorRunner.RunResult,
        diagnostics: List<AlloyValidatorOutputParser.Diagnostic>,
    ): Triple<String, String, NotificationType> {
        if (result.crashedBeforeRunning) {
            return Triple(
                "Alloy validate failed to start",
                "${result.failureReason}. Check the Alloy binary path under " +
                    "Settings → Languages & Frameworks → Alloy → Validate.",
                NotificationType.ERROR,
            )
        }
        if (result.exitCode == 0 && diagnostics.isEmpty()) {
            return Triple(
                "Alloy validate: no issues",
                "${target.name} validated successfully.",
                NotificationType.INFORMATION,
            )
        }
        val body = buildString {
            if (diagnostics.isEmpty()) {
                append("alloy exited ${result.exitCode}. See \"Alloy Validate\" tool window for output.")
            } else {
                append("${diagnostics.size} issue${if (diagnostics.size == 1) "" else "s"}:")
                diagnostics.take(MAX_NOTIFICATION_ROWS).forEach { d ->
                    append("<br>")
                    if (d.line != null) {
                        append("• <code>${d.path?.substringAfterLast('/') ?: "?"}:${d.line}</code> — ")
                    } else {
                        append("• ")
                    }
                    append(escape(d.message))
                }
                if (diagnostics.size > MAX_NOTIFICATION_ROWS) {
                    append("<br>… ${diagnostics.size - MAX_NOTIFICATION_ROWS} more in \"Alloy Validate\".")
                }
            }
        }
        val type = if (diagnostics.isNotEmpty() || result.exitCode != 0) NotificationType.WARNING
                   else NotificationType.INFORMATION
        return Triple("Alloy validate: ${target.name}", body, type)
    }

    /** Notification HTML content has to be escaped — diagnostic messages can contain `<` etc. */
    private fun escape(s: String): String =
        s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")

    companion object {
        const val NOTIFICATION_GROUP = "Grafana Alloy"
        private const val MAX_NOTIFICATION_ROWS = 5
    }
}
