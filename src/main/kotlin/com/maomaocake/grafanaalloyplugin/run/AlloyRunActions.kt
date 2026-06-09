package com.maomaocake.grafanaalloyplugin.run

import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.Task
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.wm.ToolWindowManager
import com.maomaocake.grafanaalloyplugin.AlloyFileType
import java.io.File

/**
 * `Run with Alloy` — right-click on an `.alloy` file or a folder containing one. Spawns
 * `alloy run <target>` via [AlloyRunService] and pops the *Alloy UI* tool window so the
 * user sees the embedded browser as soon as the HTTP server comes up.
 *
 * The tool window subscribes to the run service's state topic, so we don't need to push
 * the URL into it manually — activating the window is enough.
 */
class AlloyRunAction : AnAction() {

    override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

    override fun update(e: AnActionEvent) {
        val target = resolveTarget(e)
        e.presentation.isEnabledAndVisible = target != null
        if (target != null) {
            e.presentation.text = if (target.isDirectory)
                "Run with Alloy — '${target.name}'"
            else "Run '${target.name}' with Alloy"
        }
    }

    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        val target = resolveTarget(e) ?: return
        val file = File(target.path)

        // Pop the tool window first so the user sees the loading card immediately rather
        // than after the process is already up.
        ToolWindowManager.getInstance(project).getToolWindow(TOOL_WINDOW_ID)?.show()

        object : Task.Backgroundable(project, "Starting alloy run", /* canBeCancelled = */ true) {
            override fun run(indicator: ProgressIndicator) {
                indicator.text = "alloy run ${target.name}"
                AlloyRunService.getInstance(project).start(file)
            }
        }.queue()
    }

    private fun resolveTarget(e: AnActionEvent): VirtualFile? {
        val multi = e.getData(CommonDataKeys.VIRTUAL_FILE_ARRAY)
        if (multi != null) multi.firstOrNull(::isRunnable)?.let { return it }
        return e.getData(CommonDataKeys.VIRTUAL_FILE)?.takeIf(::isRunnable)
    }

    private fun isRunnable(vf: VirtualFile): Boolean =
        vf.isDirectory || vf.fileType === AlloyFileType

    companion object {
        const val TOOL_WINDOW_ID = "Alloy UI"
    }
}

