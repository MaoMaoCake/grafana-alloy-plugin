package com.maomaocake.grafanaalloyplugin.psi

import com.intellij.openapi.util.TextRange
import com.intellij.psi.AbstractElementManipulator

/**
 * Lets the platform apply text edits to an [AlloyOperExpr] range — in particular, lets the
 * default [com.intellij.psi.PsiReferenceBase.handleElementRename] implementation do its job by
 * substituting new text into the reference range and reparsing the result.
 */
class AlloyOperExprManipulator : AbstractElementManipulator<AlloyOperExpr>() {
    override fun handleContentChange(
        element: AlloyOperExpr,
        range: TextRange,
        newContent: String,
    ): AlloyOperExpr {
        val oldText = element.text
        val newText = oldText.substring(0, range.startOffset) + newContent + oldText.substring(range.endOffset)
        val replacement = AlloyElementFactory.createOperExprFromChain(element.project, newText)
            ?: return element
        return element.replace(replacement) as AlloyOperExpr
    }
}
