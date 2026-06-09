package com.maomaocake.grafanaalloyplugin.injection

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.fileEditor.FileEditorManagerListener
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiManager
import com.intellij.psi.util.PsiTreeUtil
import org.jetbrains.yaml.YAMLFileType
import org.jetbrains.yaml.psi.YAMLFile
import org.jetbrains.yaml.psi.YAMLQuotedText

/**
 * On YAML file open, scans for `config.alloy` / `*.alloy` quoted-scalar values and rewrites
 * them as `|` block scalars in a single write action.
 *
 * Why eagerly: a quoted-scalar Alloy value renders as one giant string of escapes, no
 * highlighting, no completion, no inspections. Whitespace has no meaning in Alloy so the
 * conversion can't break behaviour — the only "loss" is meaningless trailing whitespace.
 * Doing it on open means the user gets the full editor experience without an Alt-Enter
 * dance per ConfigMap.
 *
 * Gates:
 *  - [AlloyInjectionSettings.autoConvertQuotedScalars] (default on; user can opt out).
 *  - The file's `VirtualFile` must be writable. Read-only K8s-plugin overlay views are
 *    silently skipped — the inspection's manual quick fix still appears for those, so
 *    users who really want to convert can still trigger it.
 *
 * Loaded via the optional `alloy-yaml.xml` config file alongside the injector, so projects
 * in IDEs without YAML support never see this class.
 */
class AlloyAutoConvertOnOpen : FileEditorManagerListener {

    override fun fileOpened(source: FileEditorManager, file: VirtualFile) {
        if (file.fileType !is YAMLFileType) return
        if (!file.isWritable) return
        val project = source.project
        if (!AlloyInjectionSettings.getInstance(project).autoConvertQuotedScalars) return

        // PSI access has to happen on the EDT under read action; the conversion itself
        // runs in a write action a moment later. invokeLater keeps us out of the file-open
        // critical path so the editor opens snappily even if we have lots of work to do.
        ApplicationManager.getApplication().invokeLater {
            convertAllInFile(project, file)
        }
    }

    private fun convertAllInFile(project: Project, file: VirtualFile) {
        if (project.isDisposed) return
        if (!file.isValid || !file.isWritable) return

        val psiFile = PsiManager.getInstance(project).findFile(file) as? YAMLFile ?: return
        // Snapshot the candidates *before* the write action — the write action will mutate
        // the PSI as it replaces each quoted scalar, and live iteration would skip siblings
        // or trip a CME.
        val targets = PsiTreeUtil.findChildrenOfType(psiFile, YAMLQuotedText::class.java)
            .filter { shouldConvertToBlockScalar(it) }
        if (targets.isEmpty()) return

        WriteCommandAction.runWriteCommandAction(
            project,
            "Convert Alloy ConfigMap Values to Block Scalars",
            /* groupId = */ null,
            {
                for (target in targets) {
                    if (!target.isValid) continue
                    convertQuotedToBlockScalar(project, target)
                }
                PsiDocumentManager.getInstance(project).commitAllDocuments()
            },
            psiFile,
        )
    }

    companion object {
        @JvmField
        val TOPIC = FileEditorManagerListener.FILE_EDITOR_MANAGER
    }
}
