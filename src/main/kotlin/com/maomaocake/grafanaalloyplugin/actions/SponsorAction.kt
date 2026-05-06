package com.maomaocake.grafanaalloyplugin.actions

import com.intellij.ide.BrowserUtil
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.maomaocake.grafanaalloyplugin.AlloyIcons

/**
 * Opens the plugin's sponsor page in the user's default browser.
 *
 * Shown under Help → "Sponsor the Grafana Alloy plugin".
 */
class SponsorAction : AnAction(
    /* text = */ "Sponsor the Grafana Alloy Plugin",
    /* description = */ "Open the sponsor page in your browser",
    /* icon = */ AlloyIcons.FILE,
) {
    override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT
    override fun actionPerformed(e: AnActionEvent) = BrowserUtil.browse(SPONSOR_URL)

    companion object {
        // Placeholder — replace with the real Ko-fi / GitHub Sponsors URL before release.
        const val SPONSOR_URL: String = "https://ko-fi.com/jirapongp"
    }
}
