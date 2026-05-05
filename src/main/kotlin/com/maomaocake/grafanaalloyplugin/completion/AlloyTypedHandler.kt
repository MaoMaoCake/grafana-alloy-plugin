package com.maomaocake.grafanaalloyplugin.completion

import com.intellij.codeInsight.AutoPopupController
import com.intellij.codeInsight.editorActions.TypedHandlerDelegate
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.fileTypes.FileType
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiFile
import com.maomaocake.grafanaalloyplugin.AlloyFileType

/**
 * Triggers completion auto-popup when the user types an identifier character inside an Alloy
 * file. Without this, IntelliJ only pops the completion list on Ctrl+Space for custom
 * languages — users expect it to appear as they type.
 */
class AlloyTypedHandler : TypedHandlerDelegate() {

    override fun checkAutoPopup(
        charTyped: Char,
        project: Project,
        editor: Editor,
        file: PsiFile,
    ): Result {
        if (file.fileType !== AlloyFileType) return Result.CONTINUE
        if (!isIdentifierChar(charTyped)) return Result.CONTINUE
        AutoPopupController.getInstance(project).scheduleAutoPopup(editor)
        return Result.STOP
    }

    private fun isIdentifierChar(c: Char): Boolean =
        c.isLetterOrDigit() || c == '_'
}
