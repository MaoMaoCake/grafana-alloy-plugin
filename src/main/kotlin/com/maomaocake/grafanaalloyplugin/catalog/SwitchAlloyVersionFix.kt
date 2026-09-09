package com.maomaocake.grafanaalloyplugin.catalog

import com.intellij.codeInsight.daemon.DaemonCodeAnalyzer
import com.intellij.codeInsight.intention.IntentionAction
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiFile

/**
 * Quick-fix offered on a version-mismatch diagnostic: pin this project's Alloy catalog to
 * [targetVersion] (a version that actually defines the flagged component/argument/block), then
 * reload the catalog and restart the daemon so the file re-highlights against it.
 *
 * Not a PSI edit — it changes a project setting — so [startInWriteAction] is false.
 */
class SwitchAlloyVersionFix(private val targetVersion: String) : IntentionAction {

    override fun getText(): String = "Switch Alloy catalog version to $targetVersion"

    override fun getFamilyName(): String = "Switch Alloy catalog version"

    override fun isAvailable(project: Project, editor: Editor?, file: PsiFile?): Boolean = true

    override fun startInWriteAction(): Boolean = false

    override fun invoke(project: Project, editor: Editor?, file: PsiFile?) {
        val settings = AlloyCatalogSettings.getInstance(project)
        settings.versionMode = AlloyCatalogSettings.VersionMode.Pinned
        settings.pinnedVersion = targetVersion
        AlloyCatalogService.getInstance(project).reload()
        DaemonCodeAnalyzer.getInstance(project).restart()
    }
}
